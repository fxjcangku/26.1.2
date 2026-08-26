package com.example.addon.utils;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
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

        // 检查是否需要更新检查（频率控制）
        if (!shouldCheckUpdate()) {
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
            // 跳过 Markdown 标题、分隔线、空行
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("---") || line.startsWith("**")) {
                continue;
            }
            
            // 提取列表项或普通文本
            if (line.startsWith("-") || line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            
            preview.append(line).append("\n");
            count++;
            
            if (count >= maxLines) {
                break;
            }
        }
        
        return preview.toString().trim();
    }

    /**
     * 检测是否为正版账户
     * 正版账户（微软登录）有 xuid（Xbox User ID），离线账户没有
     */
    private static boolean isOnlineMode(Minecraft mc) {
        try {
            // 26.1.2 的 User 类：正版账户的 xuid 字段不为空
            return mc.getUser().getXuid().isPresent();
        } catch (Exception e) {
            // 如果无法获取，默认认为是离线
            return false;
        }
    }

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

                String uuid = mc.player.getUUID().toString();
                String name = mc.player.getName().getString();
                String version = getCurrentVersion();
                String mcVersion = mc.getVersionType();

                // 构造 JSON 请求体
                String jsonBody = String.format(
                    "{\"uuid\":\"%s\",\"name\":\"%s\",\"version\":\"%s\",\"minecraft_version\":\"%s\"}",
                    uuid, name, version, mcVersion
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
                                        String accountType = isOnlineMode(mc) ? "§a§l[正版]" : "§c§l[离线]";
                                        
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
}
