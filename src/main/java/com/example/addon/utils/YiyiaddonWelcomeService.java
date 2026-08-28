package com.example.addon.utils;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YiyiaddonWelcomeService {
    private static final String REPOSITORY_URL = "https://github.com/fxjcangku/26.1.2";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/fxjcangku/26.1.2/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BODY_PATTERN = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RELEASE_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    
    // 用户统计 API（部署在 Cloudflare Workers）
    // 配置集中管理在 AddonTemplate.STATS_API_URL
    private static final String STATS_API_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/register";
    private static final String STATS_QUERY_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/stats";
    
    // 更新检查配置
    private static final long CHECK_INTERVAL_HOURS = 24; // 每天检查一次
    private static final Path LAST_CHECK_FILE = Paths.get(FabricLoader.getInstance().getConfigDir().toString(), "yiyiaddon-last-check.txt");
    private static final Path SKIP_VERSION_FILE = Paths.get(FabricLoader.getInstance().getConfigDir().toString(), "yiyiaddon-skip-version.txt");
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public static void register() {
        MeteorClient.EVENT_BUS.subscribe(YiyiaddonWelcomeService.class);
    }

    // 玩家断开服务器时立即上报离线，后台第一时间标记离线（避免假在线）
    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        YiyiaddonHeartbeatService.reportOffline();
    }

    @EventHandler
    private static void onGameJoined(GameJoinedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 获取当前版本号并检测是否为测试版
        String currentVersion = getCurrentVersion();
        String versionDisplay = currentVersion.toLowerCase().contains("beta") 
            ? "§6§l" + currentVersion + " §c§l[测试版]" 
            : "§6§l" + currentVersion;

        // 把欢迎消息和统计消息全部放在一起发送，避免顺序错乱
        registerUserAndShowRank(versionDisplay);
        
        // 启动消息轮询（每30秒检查一次）
        startMessagePolling();

        // 检查是否需要更新检查（频率控制 + 远程开关热更新）
        if (!YiyiaddonTelemetryService.configEnabled("update_notice_enabled") || !shouldCheckUpdate()) {
            return;
        }

        new Thread(() -> {
            try {
                ReleaseInfo latest = fetchLatestRelease();
                
                if (latest == null) return;
                
                // 检查是否跳过此版本
                String skipVersion = loadSkipVersion();
                if (skipVersion != null && skipVersion.equals(latest.version)) {
                    return;
                }

                int comparison = compareVersions(currentVersion, latest.version);
                
                if (comparison < 0) {
                    // 在主线程显示更新提示
                    showUpdateNotification(mc, currentVersion, latest);
                }
                
                // 更新最后检查时间
                saveLastCheckTime();
                
            } catch (Exception e) {
                // 静默失败，不影响游戏体验
            }
        }, "yiyiaddon-update-checker").start();
    }

    /**
     * 手动检查更新（供指令调用）
     * 
     * @param mc Minecraft 实例
     * @param callback 回调函数，参数：(成功/失败, 消息)
     */
    public static void checkForUpdatesManually(Minecraft mc, java.util.function.BiConsumer<Boolean, String> callback) {
        String currentVersion = getCurrentVersion();
        
        try {
            ReleaseInfo latest = fetchLatestRelease();
            
            if (latest == null) {
                callback.accept(false, "§c未能获取最新版本信息（网络超时或 GitHub API 限流）");
                return;
            }
            
            // 检查是否跳过此版本
            String skipVersion = loadSkipVersion();
            if (skipVersion != null && skipVersion.equals(latest.version)) {
                callback.accept(true, "§7当前版本：§e" + currentVersion + " §7最新版本：§a" + latest.version + " §7(已跳过)");
                return;
            }
            
            int comparison = compareVersions(currentVersion, latest.version);
            
            if (comparison < 0) {
                callback.accept(true, "§a发现新版本 §f§l" + latest.version + " §7(当前 " + currentVersion + ")");
                // 显示更新提示框
                showUpdateNotification(mc, currentVersion, latest);
            } else if (comparison == 0) {
                callback.accept(true, "§a已是最新版本 §f§l" + currentVersion);
            } else {
                callback.accept(true, "§7当前版本：§e" + currentVersion + " §7最新版本：§a" + latest.version + " §7(开发版)");
            }
        } catch (Exception e) {
            callback.accept(false, "§c检查失败：" + e.getMessage());
        }
    }
    
    /**
     * 显示更新通知框
     */
    private static void showUpdateNotification(Minecraft mc, String currentVersion, ReleaseInfo latest) {
        mc.execute(() -> {
            if (mc.player == null) return;
            
            // 更新提示（分割线样式）
            mc.player.sendSystemMessage(Component.literal(
                "§3§m═══════════════════════════════════"
            ));
            mc.player.sendSystemMessage(Component.literal(
                "§7发现§f§l新版本 §a§l" + latest.version + " §7当前 §e§l" + currentVersion
            ));
            
            // 显示更新内容前 3 行
            String preview = extractPreview(latest.body, 3);
            if (!preview.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal(
                    "§2§l更新内容："
                ));
                for (String line : preview.split("\n")) {
                    mc.player.sendSystemMessage(Component.literal("§f" + line));
                }
                mc.player.sendSystemMessage(Component.literal(
                    "§3§m───────────────────────────────────"
                ));
            }
            
            // 下载链接
            mc.player.sendSystemMessage(Component.literal("§2§l下载地址: ")
                .append(Component.literal("§b§n点击下载")
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(latest.url)))
                        .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("§7点击打开 GitHub Release\n§7下载最新版本")))
                    )
                )
            );
            
            mc.player.sendSystemMessage(Component.literal(
                "§7输入 §e§l.yiyiaddon skip §f§l跳过此版本"
            ));
            mc.player.sendSystemMessage(Component.literal(
                "§3§m═══════════════════════════════════"
            ));
        });
    }

    private static boolean shouldCheckUpdate() {
        try {
            if (!Files.exists(LAST_CHECK_FILE)) {
                return true;
            }
            
            String content = Files.readString(LAST_CHECK_FILE);
            long lastCheck = Long.parseLong(content.trim());
            long now = Instant.now().getEpochSecond();
            long hoursSinceLastCheck = (now - lastCheck) / 3600;
            
            return hoursSinceLastCheck >= CHECK_INTERVAL_HOURS;
        } catch (Exception e) {
            return true; // 出错时允许检查
        }
    }

    private static void saveLastCheckTime() {
        try {
            Files.createDirectories(LAST_CHECK_FILE.getParent());
            Files.writeString(LAST_CHECK_FILE, String.valueOf(Instant.now().getEpochSecond()));
        } catch (IOException ignored) {
        }
    }

    public static void skipCurrentVersion(String version) {
        try {
            Files.createDirectories(SKIP_VERSION_FILE.getParent());
            Files.writeString(SKIP_VERSION_FILE, version);
        } catch (IOException ignored) {
        }
    }

    private static String loadSkipVersion() {
        try {
            if (!Files.exists(SKIP_VERSION_FILE)) {
                return null;
            }
            return Files.readString(SKIP_VERSION_FILE).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractPreview(String body, int maxLines) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        
        // 解码 JSON 转义
        body = body.replace("\\n", "\n")
                   .replace("\\r", "")
                   .replace("\\'", "'")
                   .replace("\\\"", "\"");
        
        // 提取前几行非空内容
        String[] lines = body.split("\n");
        StringBuilder preview = new StringBuilder();
        int count = 0;
        
        for (String line : lines) {
            line = line.trim();
            // 跳过 Markdown 标题、分隔线、引用块、空行、编号列表（安装说明）
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("---") 
                || line.startsWith("**") || line.startsWith(">") 
                || line.matches("^\\d+\\..*")) {
                continue;
            }
            
            // 只提取无序列表项（- 或 *）
            if (line.startsWith("-") || line.startsWith("*")) {
                line = line.substring(1).trim();
                preview.append(line).append("\n");
                count++;
                
                if (count >= maxLines) {
                    break;
                }
            }
        }
        
        return preview.toString().trim();
    }

    /**
     * 智能识别本地开发测试环境
     * 单人游戏、localhost、回环地址、局域网保留地址均视为本地测试，不上报统计
     */
    private static boolean isLocalTestEnvironment(Minecraft mc) {
        try {
            if (mc.getCurrentServer() == null) {
                return true; // 单人游戏（未连接远程服务器）
            }
            String ip = mc.getCurrentServer().ip;
            if (ip == null) return true;
            ip = ip.toLowerCase();
            // 本地回环 / 局域网保留地址
            return ip.equals("localhost")
                || ip.startsWith("127.")
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.startsWith("0.")
                || ip.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\.")
                || ip.equals("::1") || ip.equals("[::1]");
        } catch (Exception e) {
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // IP 地理位置和代理检测
    // ══════════════════════════════════════════════════════════════

    private static String getCurrentVersion() {
        return FabricLoader.getInstance()
            .getModContainer("yiyiaddon")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    private static ReleaseInfo fetchLatestRelease() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE_API))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) return null;

            String json = response.body();
            
            Matcher tagMatcher = TAG_PATTERN.matcher(json);
            Matcher urlMatcher = RELEASE_URL_PATTERN.matcher(json);
            Matcher bodyMatcher = BODY_PATTERN.matcher(json);
            Matcher prereleaseMatcher = Pattern.compile("\"prerelease\"\\s*:\\s*(true|false)").matcher(json);
            
            if (!tagMatcher.find() || !urlMatcher.find()) return null;
            
            String rawVersion = tagMatcher.group(1);
            String url = urlMatcher.group(1).replace("\\/", "/");
            String body = bodyMatcher.find() ? bodyMatcher.group(1) : "";
            
            // 不过滤 prerelease，测试版也提示更新
            
            // 保留完整版本号（包括 -beta 后缀），去掉 v 前缀
            String version = rawVersion.replaceFirst("^[vV]", "");
            
            return new ReleaseInfo(version, url, body);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 比较两个版本号
     * 支持语义化版本（1.2.3-beta4）
     * 
     * @return < 0 表示 current < latest（需要更新）
     *         = 0 表示版本相同
     *         > 0 表示 current > latest
     */
    private static int compareVersions(String current, String latest) {
        // 去掉 v 前缀
        current = current.replaceFirst("^[vV]", "");
        latest = latest.replaceFirst("^[vV]", "");
        
        // 分离主版本号和预发布标识
        String[] currentParts = current.split("-", 2);
        String[] latestParts = latest.split("-", 2);
        
        String currentMain = currentParts[0];
        String latestMain = latestParts[0];
        String currentPre = currentParts.length > 1 ? currentParts[1] : null;
        String latestPre = latestParts.length > 1 ? latestParts[1] : null;
        
        // 先比较主版本号
        int mainComparison = compareMainVersion(currentMain, latestMain);
        if (mainComparison != 0) {
            return mainComparison;
        }
        
        // 主版本号相同，比较预发布标识
        if (currentPre == null && latestPre == null) {
            return 0; // 都是正式版，相同
        } else if (currentPre != null && latestPre == null) {
            return -1; // current 是测试版，latest 是正式版，current < latest
        } else if (currentPre == null && latestPre != null) {
            return 1; // current 是正式版，latest 是测试版，current > latest
        } else {
            // 都是测试版，比较预发布号
            return currentPre.compareTo(latestPre);
        }
    }
    
    private static int compareMainVersion(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        
        int length = Math.max(leftParts.length, rightParts.length);
        
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.length ? parseNumber(leftParts[i]) : 0;
            int rightPart = i < rightParts.length ? parseNumber(rightParts[i]) : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    private static String normalizeVersion(String version) {
        return version.replaceFirst("^[vV]", "").split("[-+]", 2)[0];
    }

    private static int parseNumber(String value) {
        Matcher matcher = Pattern.compile("^\\d+").matcher(value);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 注册用户并显示排名
     * 发送玩家 UUID、游戏名、扩展版本、MC版本到统计服务器
     * 返回玩家排名并在公屏显示
     * 
     * @param versionDisplay 版本显示字符串（带颜色代码）
     */
    private static void registerUserAndShowRank(String versionDisplay) {
        new Thread(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) {
                    return;
                }

                // 智能识别：本地开发测试（单人/回环/局域网）不上报
                if (isLocalTestEnvironment(mc)) {
                    System.out.println("[YiyiaddonWelcomeService] 本地测试环境，跳过上报");
                    return;
                }

                // 远程开关：后台可关闭统计上报
                if (!YiyiaddonTelemetryService.configEnabled("stats_report_enabled")) {
                    System.out.println("[YiyiaddonWelcomeService] 远程配置关闭了统计上报，跳过");
                    return;
                }

                // 统一用会话身份 UUID（正版=微软 UUID），盗版服务器不会影响玩家真实身份
                String uuid = YiyiaddonIdentity.uuid(mc);
                String name = YiyiaddonIdentity.name(mc);
                
                // 智能识别假玩家：离线模式默认名 "Player+数字"（如 Player166）不纳入统计
                if (YiyiaddonIdentity.isFakePlayer(name)) {
                    System.out.println("[YiyiaddonWelcomeService] 跳过假玩家: " + name);
                    return;
                }
                
                String version = getCurrentVersion();
                String mcVersion = mc.getVersionType();
                
                // 检测是否为正版账户
                boolean isPremium = YiyiaddonIdentity.isPremium(mc);
                
                // 获取服务器信息
                String serverIp = null;
                String serverName = null;
                
                if (mc.getCurrentServer() != null) {
                    serverIp = mc.getCurrentServer().ip;
                    serverName = mc.getCurrentServer().name;
                } else if (mc.isLocalServer()) {
                    serverIp = "localhost";
                    serverName = "单人游戏";
                }
                
                // 收集玩家活动数据
                double posX = mc.player.getX();
                double posY = mc.player.getY();
                double posZ = mc.player.getZ();
                String dimension = getDimensionName();
                float health = mc.player.getHealth();
                int foodLevel = mc.player.getFoodData().getFoodLevel();
                String gameMode = mc.gameMode.getPlayerMode().getName();
                String currentActivity = detectPlayerActivity();

                // 获取IP信息（带代理检测）
                IpInfo ipInfo = fetchIpAndCountryWithProxyDetection();
                String realIp = ipInfo != null ? ipInfo.ip : null;
                String realCountry = ipInfo != null ? ipInfo.countryCode : null;
                boolean isUsingProxy = ipInfo != null && ipInfo.isProxy;
                String proxyType = ipInfo != null ? ipInfo.proxyType : null;
                
                // 微软账号 XUID（Xbox User ID）：只有正版（微软登录）账户才有，离线账户为空
                // xuid 是微软账号的唯一标识，后台「正版账号」页面靠它展示正版玩家花名册
                String xuid = YiyiaddonIdentity.xuid(mc);

                // Gamertag：Java 版微软正版账号的展示名即游戏名，这里沿用玩家名
                String gamertag = isPremium ? YiyiaddonIdentity.name(mc) : null;
                
                // 获取启用的模块列表
                String enabledModules = YiyiaddonHeartbeatService.getEnabledModulesStatic();
                
                // 服务器延迟
                Integer serverLatency = null;
                try {
                    if (mc.getConnection() != null) {
                        // getPlayerInfo() 是 protected，经连接层 getPlayerInfo(uuid) 获取延迟
                        var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                        if (entry != null) serverLatency = entry.getLatency();
                    }
                } catch (Exception ignored) {}
                
                // 网络延迟
                Integer networkLatency = YiyiaddonHeartbeatService.measureLatencyStatic();

                // 构造 JSON 请求体（包含所有新字段）
                String jsonBody = String.format(
                    "{\"uuid\":\"%s\",\"name\":\"%s\",\"version\":\"%s\",\"minecraft_version\":\"%s\",\"server_ip\":\"%s\",\"server_name\":\"%s\",\"is_premium\":%b," +
                    "\"real_ip\":%s,\"real_country\":%s,\"is_using_proxy\":%b,\"proxy_type\":%s,\"server_latency\":%s,\"network_latency\":%s,\"gamertag\":%s,\"xuid\":%s,\"enabled_modules\":%s," +
                    "\"player_activity\":{\"pos_x\":%.2f,\"pos_y\":%.2f,\"pos_z\":%.2f,\"dimension\":\"%s\",\"health\":%.1f,\"food_level\":%d,\"game_mode\":\"%s\",\"current_activity\":\"%s\",\"is_online\":true}}",
                    uuid, name, version, mcVersion, 
                    serverIp != null ? serverIp : "unknown",
                    serverName != null ? serverName : "unknown",
                    isPremium,
                    realIp != null ? "\"" + realIp + "\"" : "null",
                    realCountry != null ? "\"" + realCountry + "\"" : "null",
                    isUsingProxy,
                    proxyType != null ? "\"" + proxyType + "\"" : "null",
                    serverLatency != null ? String.valueOf(serverLatency) : "null",
                    networkLatency != null ? String.valueOf(networkLatency) : "null",
                    gamertag != null ? "\"" + gamertag + "\"" : "null",
                    xuid != null ? "\"" + xuid + "\"" : "null",
                    enabledModules != null ? "\"" + enabledModules.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : "null",
                    posX, posY, posZ, dimension, health, foodLevel, gameMode, currentActivity
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STATS_API_ENDPOINT))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, 
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String body = response.body();
                    
                    // 解析返回的排名和总用户数
                    Matcher rankMatcher = Pattern.compile("\"rank\":(\\d+)").matcher(body);
                    Matcher totalMatcher = Pattern.compile("\"total_users\":(\\d+)").matcher(body);
                    Matcher isNewMatcher = Pattern.compile("\"is_new_user\":(true|false)").matcher(body);
                    
                    if (rankMatcher.find() && totalMatcher.find() && isNewMatcher.find()) {
                        int rank = Integer.parseInt(rankMatcher.group(1));
                        int total = Integer.parseInt(totalMatcher.group(1));
                        boolean isNew = Boolean.parseBoolean(isNewMatcher.group(1));
                        
                        // 复用上面已获取的 ipInfo（IP 与国家信息），避免重复网络请求
                        
                        // 先获取最近活跃信息（在子线程完成所有 I/O），然后一次性显示
                        String recentActivityInfo = fetchRecentActivity();
                        
                        // 等待 200ms 让欢迎消息先显示，避免统计信息插队
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // 必须在渲染线程（主线程）里发消息，子线程直接调 sendSystemMessage 会崩溃
                        // 首次进入世界时 player 可能还没完全加载，延迟一下确保能发送
                        new java.util.Timer().schedule(new java.util.TimerTask() {
                            @Override
                            public void run() {
                                mc.execute(() -> {
                                    if (mc.player != null) {
                                        // 检测正版/离线（正版绿色，离线红色）
                                        String accountType = YiyiaddonIdentity.isPremium(mc) ? "§a§l[正版]" : "§c§l[离线]";
                                        
                                        // 顶部分割线
                                        mc.player.sendSystemMessage(Component.literal(
                                            "§3§m═══════════════════════════════════"
                                        ));
                                        
                                        // 欢迎消息（护眼配色：深青边框+深绿强调）
                                        mc.player.sendSystemMessage(Component.literal(
                                            "§7本扩展已整合§f§l简体中文汉化§7跟汉化§f§lBaritone"
                                        ));
                                        mc.player.sendSystemMessage(Component.literal(
                                            "§7免费 为爱发电 §8| §f版本 " + versionDisplay
                                        ));
                                        
                                        // GitHub 仓库链接（深绿前缀 + 下划线可点击链接）
                                        mc.player.sendSystemMessage(Component.literal("§2§lGitHub: ")
                                            .append(Component.literal("§b§n" + REPOSITORY_URL.replace("https://", ""))
                                                .withStyle(style -> style
                                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(REPOSITORY_URL)))
                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                        Component.literal("§7点击打开 GitHub 仓库")))
                                                )
                                            )
                                        );
                                        
                                        // Bug 反馈链接（深绿前缀 + 下划线可点击链接）
                                        mc.player.sendSystemMessage(Component.literal("§2§lBug反馈: ")
                                            .append(Component.literal("§b§n点击反馈")
                                                .withStyle(style -> style
                                                    .withClickEvent(new ClickEvent.OpenUrl(
                                                        URI.create("https://github.com/fxjcangku/26.1.2/issues")))
                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                        Component.literal("§7点击打开 GitHub Issues\n§7提交 Bug 或功能建议")))
                                                )
                                            )
                                        );
                                        
                                        // 中间分割线
                                        mc.player.sendSystemMessage(Component.literal(
                                            "§3§m───────────────────────────────────"
                                        ));
                                        
                                        // 统计信息（账户类型+玩家名加粗）
                                        if (isNew) {
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§3│ " + accountType + " §6§l" + name
                                            ));
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§3│ §7你是第 §e§l#" + rank + " §7个使用者 §a§l✓"
                                            ));
                                        } else {
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§3│ §7欢迎回来 " + accountType + " §6§l" + name
                                            ));
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§3│ §7你是第 §e§l#" + rank + " §7个使用者"
                                            ));
                                        }
                                        
                                        mc.player.sendSystemMessage(Component.literal(
                                            "§7当前已有 §2§l" + total + " §f§l位玩家使用"
                                        ));
                                        
                                        // IP 和国家信息（带国旗 emoji 和代理检测）
                                        if (ipInfo != null && ipInfo.ip != null) {
                                            String flag = countryCodeToFlag(ipInfo.countryCode);
                                            String countryDisplay = ipInfo.countryCode != null 
                                                ? " §8| " + flag + " §e§l" + ipInfo.countryCode 
                                                : "";
                                            
                                            // 显示代理状态
                                            String proxyStatus = "";
                                            if (ipInfo.isProxy && ipInfo.proxyType != null) {
                                                proxyStatus = " §c§l[" + ipInfo.proxyType + "]";
                                            }
                                            
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§7IP: §b" + ipInfo.ip + countryDisplay + proxyStatus
                                            ));
                                        }
                                        
                                        // 底部分割线
                                        mc.player.sendSystemMessage(Component.literal(
                                            "§3§m═══════════════════════════════════"
                                        ));
                                    }
                                });
                            }
                        }, 500); // 延迟 500ms 确保 player 已加载
                    }
                }
            } catch (Exception e) {
                // 调试：打印异常信息以便排查问题
                e.printStackTrace();
                System.err.println("[YiyiaddonWelcomeService] 统计请求失败: " + e.getMessage());
            }
        }, "yiyiaddon-user-register").start();
    }
    
    /**
     * 获取客户端 IP 地址和国家代码（带代理检测和网络测试）
     * 三重降级策略：ipapi.co -> ip-api.com -> cloudflare
     * 
     * @return IP 信息对象，失败返回备用信息
     */
    private static IpInfo fetchIpAndCountryWithProxyDetection() {
        // 收集多个来源的 IP 进行对比（检测代理）
        IpInfo result1 = tryFetchFromIpApiCoWithProxy();
        IpInfo result2 = tryFetchFromIpApiWithProxy();
        IpInfo result3 = tryFetchFromCloudflareSimple();
        
        // 判断是否使用代理
        boolean isUsingProxy = false;
        String proxyType = null;
        
        // 策略 1: API 返回的代理标记
        if (result1 != null && result1.isProxy) {
            isUsingProxy = true;
            proxyType = result1.proxyType;
        } else if (result2 != null && result2.isProxy) {
            isUsingProxy = true;
            proxyType = result2.proxyType;
        }
        
        // 策略 2: IP 不一致检测（多个 API 返回不同 IP）
        if (result1 != null && result2 != null && !result1.ip.equals(result2.ip)) {
            isUsingProxy = true;
            if (proxyType == null) proxyType = "VPN";
        }
        
        // 选择最可靠的结果
        IpInfo selected = result1 != null ? result1 : (result2 != null ? result2 : result3);
        
        if (selected != null) {
            return new IpInfo(selected.ip, selected.countryCode, isUsingProxy, proxyType);
        }
        
        // 完全失败，进行端口连通性测试
        if (!testNetworkConnectivity()) {
            return new IpInfo("Network Offline", "??", false, null);
        } else {
            return new IpInfo("Unknown", "??", false, null);
        }
    }
    
    /**
     * 测试网络连通性（多端口测试）
     */
    private static boolean testNetworkConnectivity() {
        String[] testHosts = {
            "1.1.1.1:443",      // Cloudflare HTTPS
            "8.8.8.8:443",      // Google DNS HTTPS
            "1.1.1.1:80",       // Cloudflare HTTP
        };
        
        for (String hostPort : testHosts) {
            try {
                String[] parts = hostPort.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 2000);
                socket.close();
                return true;
            } catch (Exception ignored) {
            }
        }
        
        return false;
    }
    
    private static IpInfo tryFetchFromIpApiCoWithProxy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ipapi.co/json/"))
                .timeout(Duration.ofSeconds(3))
                .header("User-Agent", "yiyiaddon-minecraft-client")
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher ipMatcher = Pattern.compile("\"ip\":\\s*\"([^\"]+)\"").matcher(body);
                Matcher countryMatcher = Pattern.compile("\"country_code\":\\s*\"([^\"]+)\"").matcher(body);
                
                String ip = ipMatcher.find() ? ipMatcher.group(1) : null;
                String countryCode = countryMatcher.find() ? countryMatcher.group(1) : null;
                
                // 检测代理/VPN/TOR
                boolean isProxy = false;
                String proxyType = null;
                
                if (body.contains("\"is_tor\":true") || body.contains("\"tor\":true")) {
                    isProxy = true;
                    proxyType = "TOR";
                } else if (body.contains("\"is_proxy\":true") || body.contains("\"proxy\":true")) {
                    isProxy = true;
                    proxyType = "PROXY";
                } else if (body.contains("\"is_vpn\":true") || body.contains("\"vpn\":true")) {
                    isProxy = true;
                    proxyType = "VPN";
                }
                
                if (ip != null && countryCode != null) {
                    return new IpInfo(ip, countryCode, isProxy, proxyType);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private static IpInfo tryFetchFromIpApiWithProxy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://ip-api.com/json/?fields=query,countryCode,proxy,mobile,hosting"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher ipMatcher = Pattern.compile("\"query\":\\s*\"([^\"]+)\"").matcher(body);
                Matcher countryMatcher = Pattern.compile("\"countryCode\":\\s*\"([^\"]+)\"").matcher(body);
                
                String ip = ipMatcher.find() ? ipMatcher.group(1) : null;
                String countryCode = countryMatcher.find() ? countryMatcher.group(1) : null;
                
                // 检测代理/VPN
                boolean isProxy = body.contains("\"proxy\":true") || body.contains("\"hosting\":true");
                String proxyType = null;
                
                if (body.contains("\"proxy\":true")) {
                    proxyType = "PROXY";
                } else if (body.contains("\"hosting\":true")) {
                    proxyType = "VPN";
                }
                
                if (ip != null && countryCode != null) {
                    return new IpInfo(ip, countryCode, isProxy, proxyType);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private static IpInfo tryFetchFromCloudflareSimple() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://1.1.1.1/cdn-cgi/trace"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher ipMatcher = Pattern.compile("ip=([^\\s]+)").matcher(body);
                Matcher countryMatcher = Pattern.compile("loc=([A-Z]{2})").matcher(body);
                
                String ip = ipMatcher.find() ? ipMatcher.group(1) : null;
                String countryCode = countryMatcher.find() ? countryMatcher.group(1) : null;
                
                if (ip != null && countryCode != null) {
                    return new IpInfo(ip, countryCode, false, null);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * 将国家代码转换为国旗 emoji
     * 使用 Unicode 区域指示符号（Regional Indicator Symbols）
     * 原理：国旗 emoji = U+1F1E6~U+1F1FF 组合（A-Z 映射）
     */
    private static String countryCodeToFlag(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            return "🌐"; // 未知国家用地球图标
        }
        
        countryCode = countryCode.toUpperCase();
        int firstLetter = countryCode.charAt(0) - 'A' + 0x1F1E6;
        int secondLetter = countryCode.charAt(1) - 'A' + 0x1F1E6;
        
        return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
    }
    
    /**
     * 获取最近活跃信息
     * 查询 24h 内活跃用户数和最近上线的玩家
     * 
     * @return 格式化的活跃信息字符串，失败返回 null
     */
    private static String fetchRecentActivity() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STATS_QUERY_ENDPOINT))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                
                // 解析 24h 活跃用户数
                Matcher activeMatcher = Pattern.compile("\"active_users_24h\":(\\d+)").matcher(body);
                
                // 解析最近活跃用户列表
                Matcher usersMatcher = Pattern.compile("\"recent_users\":\\[(.*?)\\]").matcher(body);
                
                if (activeMatcher.find()) {
                    int activeCount = Integer.parseInt(activeMatcher.group(1));
                    
                    // 尝试获取最近上线的玩家
                    String recentUserName = null;
                    long recentUserTime = 0;
                    
                    if (usersMatcher.find()) {
                        String usersJson = usersMatcher.group(1);
                        Matcher nameMatcher = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(usersJson);
                        Matcher timeMatcher = Pattern.compile("\"last_seen\":(\\d+)").matcher(usersJson);
                        
                        if (nameMatcher.find() && timeMatcher.find()) {
                            recentUserName = nameMatcher.group(1);
                            recentUserTime = Long.parseLong(timeMatcher.group(1));
                        }
                    }
                    
                    // 构建活跃信息
                    StringBuilder info = new StringBuilder();
                    info.append("§f§l24h §7活跃 §b§l").append(activeCount).append(" §f§l人");
                    
                    if (recentUserName != null) {
                        long hoursAgo = (System.currentTimeMillis() / 1000 - recentUserTime) / 3600;
                        String timeDesc = hoursAgo == 0 ? "§a§l刚刚在线" : "§7" + hoursAgo + "§f§lh §7前";
                        info.append(" §7§l| §7最近 §e§l").append(recentUserName).append(" ").append(timeDesc);
                    }
                    
                    return info.toString();
                }
            }
        } catch (Exception e) {
            // 静默失败
        }
        return null;
    }

    private static record ReleaseInfo(String version, String url, String body) {
    }
    
    private static record IpInfo(String ip, String countryCode, boolean isProxy, String proxyType) {
    }
    
    /**
     * 获取维度名称
     */
    private static String getDimensionName() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return "unknown";
            
            String dimensionKey = mc.player.level().dimension().toString();
            if (dimensionKey.contains("overworld")) {
                return "overworld";
            } else if (dimensionKey.contains("the_nether")) {
                return "the_nether";
            } else if (dimensionKey.contains("the_end")) {
                return "the_end";
            }
            return dimensionKey;
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 检测玩家当前活动
     */
    private static String detectPlayerActivity() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "unknown";
        
        try {
            // 检测玩家速度判断是否在移动
            double velocityX = mc.player.getX() - mc.player.xOld;
            double velocityZ = mc.player.getZ() - mc.player.zOld;
            double velocityY = mc.player.getY() - mc.player.yOld;
            double speed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            
            // 检测是否在战斗（最近受伤）
            if (mc.player.hurtTime > 0) {
                return "战斗中";
            }
            
            // 检测是否在飞行
            if (mc.player.getAbilities().flying) {
                return "飞行中";
            }
            
            // 检测是否在疾跑
            if (mc.player.isSprinting() && speed > 0.1) {
                return "疾跑中";
            }
            
            // 检测是否在潜行
            if (mc.player.isShiftKeyDown()) {
                return "潜行中";
            }
            
            // 检测是否在移动
            if (speed > 0.05) {
                return "移动中";
            }
            
            // 检测是否在跳跃/下落
            if (Math.abs(velocityY) > 0.1) {
                return velocityY > 0 ? "跳跃中" : "下落中";
            }
            
            // 检测是否在水中
            if (mc.player.isInWater()) {
                return "游泳中";
            }
            
            // 检测是否在挖掘
            if (mc.gameMode != null && mc.gameMode.isDestroying()) {
                return "挖掘中";
            }
            
            // 默认静止
            return "静止";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    // ==================== 消息轮询系统 ====================
    
    private static volatile boolean pollingRunning = false;
    private static Thread pollingThread = null;
    
    /**
     * 启动消息轮询线程
     * 每30秒从服务器获取一次新消息并显示在聊天栏
     */
    private static void startMessagePolling() {
        if (pollingRunning) {
            return; // 已经在运行
        }
        
        pollingRunning = true;
        pollingThread = new Thread(() -> {
            while (pollingRunning) {
                try {
                    pollMessages();
                    Thread.sleep(30000); // 30秒轮询一次
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // 静默处理错误，不影响游戏
                }
            }
        }, "yiyiaddon-message-poller");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }
    
    /**
     * 轮询服务器获取新消息
     */
    private static void pollMessages() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        String uuid = YiyiaddonIdentity.uuid(mc);
        
        try {
            // 构建请求体
            String requestBody = String.format("{\"uuid\":\"%s\"}", uuid);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/messages/poll"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                
                // 解析消息数量
                Matcher countMatcher = Pattern.compile("\"count\":(\\d+)").matcher(body);
                if (countMatcher.find()) {
                    int count = Integer.parseInt(countMatcher.group(1));
                    
                    if (count > 0) {
                        // 解析消息列表
                        Matcher messagesMatcher = Pattern.compile("\"messages\":\\[(.*?)\\]").matcher(body);
                        if (messagesMatcher.find()) {
                            String messagesJson = messagesMatcher.group(1);
                            
                            // 提取所有消息
                            Matcher messageMatcher = Pattern.compile("\\{[^}]+\\}").matcher(messagesJson);
                            while (messageMatcher.find()) {
                                String messageObj = messageMatcher.group();
                                
                                // 解析单条消息
                                Matcher textMatcher = Pattern.compile("\"message\":\"([^\"]+)\"").matcher(messageObj);
                                Matcher senderMatcher = Pattern.compile("\"sender\":\"([^\"]+)\"").matcher(messageObj);
                                
                                if (textMatcher.find() && senderMatcher.find()) {
                                    String messageText = textMatcher.group(1);
                                    String sender = senderMatcher.group(1);
                                    
                                    // 在主线程显示消息
                                    mc.execute(() -> {
                                        if (mc.player != null) {
                                            // 显示华丽的管理员消息
                                            mc.player.sendSystemMessage(Component.literal(""));
                                            mc.player.sendSystemMessage(Component.literal("§8§m                                                  "));
                                            mc.player.sendSystemMessage(Component.literal("  §6§l✉ §e管理员消息"));
                                            mc.player.sendSystemMessage(Component.literal(""));
                                            mc.player.sendSystemMessage(Component.literal("  §7来自: §b§l" + sender));
                                            mc.player.sendSystemMessage(Component.literal("  §7内容: §f" + messageText));
                                            mc.player.sendSystemMessage(Component.literal(""));
                                            mc.player.sendSystemMessage(Component.literal("  §a§l提示: §7输入 §e.回复 <消息> §7或 §e.reply <message> §7回复管理员"));
                                            mc.player.sendSystemMessage(Component.literal("§8§m                                                  "));
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // 静默失败，不影响游戏体验
        }
    }
    
    /**
     * 停止消息轮询
     */
    public static void stopMessagePolling() {
        pollingRunning = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }
}
