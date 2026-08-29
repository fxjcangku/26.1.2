package com.example.addon.utils;

import com.example.addon.core.AddonTemplate;
import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * 心跳服务：每15秒上报一次，维持在线状态
 * 上报：延迟、模块列表、Gamertag、活动数据
 * 
 * 断开服务器时通过 reportOffline() 立即上报离线；后台 45 秒无心跳也会自动标记离线
 */
public final class YiyiaddonHeartbeatService {
    private static final String HEARTBEAT_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/heartbeat";
    private static final String OFFLINE_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/offline";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static volatile boolean running = false;
    private static Thread heartbeatThread = null;
    // 最近一次心跳的玩家 UUID，供断开时上报离线使用（断开瞬间 mc.player 可能已为 null）
    private static volatile String lastUuid = null;

    private YiyiaddonHeartbeatService() {}

    public static void start() {
        if (running) return;
        running = true;
        heartbeatThread = new Thread(() -> {
            while (running) {
                try {
                    sendHeartbeat();
                    Thread.sleep(15000); // 15秒一次
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "yiyiaddon-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public static void stop() {
        running = false;
        if (heartbeatThread != null) heartbeatThread.interrupt();
    }

    /**
     * 立即上报离线：玩家断开服务器（GameLeftEvent）时由 WelcomeService 调用，
     * 让后台第一时间标记为离线，而不是等超时衰减。
     */
    public static void reportOffline() {
        String uuid = lastUuid;
        if (uuid == null || uuid.isEmpty()) return;
        // 上报后清空，避免重复/误报
        lastUuid = null;
        try {
            String body = "{\"uuid\":\"" + uuid + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OFFLINE_ENDPOINT))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
        }
    }

    private static void sendHeartbeat() {
        Minecraft mc = Minecraft.getInstance();

        try {
            // 判定玩家当前状态：主菜单 / 单人世界 / 多人服务器
            String status;
            String uuid = null;
            String name = null;
            String serverIp = null;
            String serverName = null;

            // 统一使用会话身份 UUID（正版=微软 UUID）：盗版服务器会把
            // mc.player.getUUID() 换成离线派生 UUID，导致正版玩家身份无法关联 / 被误判盗版。
            uuid = YiyiaddonIdentity.uuid(mc);
            name = YiyiaddonIdentity.name(mc);

            if (uuid == null || uuid.isEmpty() || name == null || name.trim().isEmpty()) return;
            lastUuid = uuid; // 记录最近 UUID，供断开时上报离线

            // 过滤假玩家（离线默认名 Player+数字）
            if (YiyiaddonIdentity.isFakePlayer(name)) return;

            // 状态判定：菜单 / 单人 / 多人
            if (mc.player == null) {
                status = "menu";
                serverName = "主菜单";
            } else if (mc.getCurrentServer() != null) {
                status = "multiplayer";
                serverIp = mc.getCurrentServer().ip;
                serverName = mc.getCurrentServer().name;
                // 多人连接本地（回环/局域网）仍视为测试，跳过
                if (isLocalServer(serverIp)) return;
            } else {
                status = "singleplayer";
                serverName = "单人世界";
            }

            // 微软账号 XUID（正版才有，用于后台「正版账号」页面）
            String xuid = YiyiaddonIdentity.xuid(mc);

            // Gamertag（Java 版即玩家名）
            String gamertag = YiyiaddonIdentity.isPremium(mc) ? YiyiaddonIdentity.name(mc) : null;

            // 设备真实时区 + 连接出口 ISP/ASN（复用注册时解析并缓存的结果，避免每 15s 重复请求）
            String timezone = TimeZone.getDefault().getID();
            YiyiaddonWelcomeService.IpInfo ipInfo = YiyiaddonWelcomeService.getCachedIpInfo();
            String clientIsp = ipInfo != null ? ipInfo.isp() : null;
            String clientAsOrg = ipInfo != null ? ipInfo.asOrg() : null;
            String clientAsn = ipInfo != null ? ipInfo.asn() : null;
            boolean isUsingProxy = ipInfo != null && ipInfo.isProxy();

            // 获取启用的模块列表
            String modules = getEnabledModules();

            // 服务器延迟（仅多人连接有效）
            Integer serverLatency = null;
            try {
                if (mc.player != null && mc.getConnection() != null) {
                    // 26.1.2 API：getPlayerInfo() 是 protected 无法直接访问，
                    // 通过连接层 getPlayerInfo(uuid) 获取当前玩家的 PlayerListEntry 延迟
                    var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (entry != null) serverLatency = entry.getLatency();
                }
            } catch (Exception ignored) {}

            // 网络延迟（测量到后台API的延迟）
            Integer networkLatency = measureNetworkLatency();

            // 活动数据（菜单模式下 player 为 null，无法取坐标，报 null）
            String activity = "null";
            if (mc.player != null) {
                activity = String.format(
                    "{\"pos_x\":%.2f,\"pos_y\":%.2f,\"pos_z\":%.2f,\"dimension\":\"%s\",\"health\":%.1f,\"food_level\":%d,\"game_mode\":\"%s\",\"current_activity\":\"%s\",\"is_online\":true}",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    getDimensionName(), mc.player.getHealth(), mc.player.getFoodData().getFoodLevel(),
                    mc.gameMode.getPlayerMode().getName(), detectActivity()
                );
            }

            String body = String.format(
                "{\"uuid\":\"%s\",\"name\":\"%s\",\"status\":\"%s\",\"server_ip\":%s,\"server_name\":%s,\"server_latency\":%s,\"network_latency\":%s,\"gamertag\":%s,\"xuid\":%s,\"enabled_modules\":%s,\"client_timezone\":\"%s\",\"client_isp\":%s,\"client_as_org\":%s,\"client_asn\":%s,\"is_using_proxy\":%b,\"player_activity\":%s}",
                uuid, jsonEscape(name), status,
                serverIp != null ? "\"" + jsonEscape(serverIp) + "\"" : "null",
                serverName != null ? "\"" + jsonEscape(serverName) + "\"" : "null",
                serverLatency != null ? String.valueOf(serverLatency) : "null",
                networkLatency != null ? String.valueOf(networkLatency) : "null",
                gamertag != null ? "\"" + jsonEscape(gamertag) + "\"" : "null",
                xuid != null ? "\"" + jsonEscape(xuid) + "\"" : "null",
                modules != null ? "\"" + jsonEscape(modules) + "\"" : "null",
                jsonEscape(timezone),
                clientIsp != null ? "\"" + jsonEscape(clientIsp) + "\"" : "null",
                clientAsOrg != null ? "\"" + jsonEscape(clientAsOrg) + "\"" : "null",
                clientAsn != null ? "\"" + jsonEscape(clientAsn) + "\"" : "null",
                isUsingProxy,
                activity
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HEARTBEAT_ENDPOINT))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    // 判断是否为本地测试服务器（回环/局域网/域名），是则跳过上报
    private static boolean isLocalServer(String ip) {
        if (ip == null) return true;
        String i = ip.toLowerCase().trim();
        return i.equals("localhost")
            || i.startsWith("127.")
            || i.startsWith("192.168.")
            || i.startsWith("10.")
            || i.startsWith("0.")
            || i.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\.")
            || i.equals("::1") || i.equals("[::1]");
    }

    private static Integer measureNetworkLatency() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/stats"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return (int) (System.currentTimeMillis() - start);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getEnabledModules() {
        try {
            List<String> mods = new ArrayList<>();
            // Meteor 模块
            Modules.get().getAll().forEach(m -> {
                if (m.isActive()) {
                    mods.add(YiyiaddonTranslator.moduleTitle(m));
                }
            });
            if (mods.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < mods.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(jsonEscape(mods.get(i))).append("\"");
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String getDimensionName() {
        try {
            String key = Minecraft.getInstance().player.level().dimension().toString();
            if (key.contains("overworld")) return "overworld";
            if (key.contains("the_nether")) return "the_nether";
            if (key.contains("the_end")) return "the_end";
            return key;
        } catch (Exception e) { return "unknown"; }
    }

    private static String detectActivity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "unknown";
        try {
            if (mc.player.hurtTime > 0) return "战斗中";
            if (mc.player.getAbilities().flying) return "飞行中";
            if (mc.player.isSprinting()) return "疾跑中";
            if (mc.player.isShiftKeyDown()) return "潜行中";
            double dx = mc.player.getX() - mc.player.xOld, dz = mc.player.getZ() - mc.player.zOld;
            if (Math.sqrt(dx * dx + dz * dz) > 0.05) return "移动中";
            if (mc.player.isInWater()) return "游泳中";
            if (mc.gameMode != null && mc.gameMode.isDestroying()) return "挖掘中";
            return "静止";
        } catch (Exception e) { return "unknown"; }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    // ── 静态辅助方法（供其他服务调用） ──

    public static String getEnabledModulesStatic() {
        try {
            List<String> mods = new ArrayList<>();
            Modules.get().getAll().forEach(m -> {
                if (m.isActive()) mods.add(YiyiaddonTranslator.moduleTitle(m));
            });
            if (mods.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < mods.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(jsonEscape(mods.get(i))).append("\"");
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) { return "[]"; }
    }

    public static Integer measureLatencyStatic() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/stats"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return (int) (System.currentTimeMillis() - start);
        } catch (Exception e) { return null; }
    }
}