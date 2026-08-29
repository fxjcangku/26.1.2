package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户统计模块
 * 提供一个美观的界面实时查看扩展使用统计
 * 
 * 【注意】进入世界时的自动播报由 YiyiaddonWelcomeService 负责
 * 本模块只负责界面查看，不做自动播报
 * 
 * 数据来自 Cloudflare Workers 统计后端
 */
public final class UserStatsModule extends YiyiaddonModule {
    
    // ══════════════════════════════════════════════════════════════
    //  设置项
    // ══════════════════════════════════════════════════════════════
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Integer> refreshInterval = sgGeneral.add(new IntSetting.Builder()
        .name("自动刷新间隔")
        .description("界面数据每隔多少秒自动刷新一次（0 = 仅手动刷新；建议保持 3 秒）")
        .defaultValue(3)
        .min(0)
        .max(300)
        .sliderMax(120)
        .build()
    );
    
    private final Setting<Integer> maxDisplayUsers = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("最多显示用户数")
        .description("界面最多显示多少个最近活跃用户")
        .defaultValue(15)
        .min(5)
        .max(50)
        .sliderMax(30)
        .build()
    );
    
    // ══════════════════════════════════════════════════════════════
    //  统计数据
    // ══════════════════════════════════════════════════════════════
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    
    private int totalUsers = 0;              // 总用户数
    private int totalUses = 0;
    private int activeUsers24h = 0;          // 24小时内活跃用户数
    private int onlineUsers = 0;             // 当前在线用户数
    private List<RecentUser> recentUsers = new ArrayList<>();  // 最近活跃用户
    private long lastUpdateTime = 0;         // 上次更新时间戳
    private boolean isLoading = false;       // 是否正在加载
    private String errorMessage = null;      // 错误信息
    private String clientIp = null;          // 客户端 IP
    private String clientCountry = null;     // 客户端国家代码
    private boolean isUsingProxy = false;    // 是否使用代理/VPN
    private String proxyType = null;         // 代理类型（VPN/Proxy/TOR）
    private long lastProfileLookupTime = 0;
    
    private int tickCounter = 0;             // Tick 计数器
    
    // ══════════════════════════════════════════════════════════════
    //  构造函数
    // ══════════════════════════════════════════════════════════════
    
    public UserStatsModule() {
        super(AddonTemplate.CATEGORY, "用户统计", "实时查看当前有多少玩家正在使用该扩展");
    }
    
    @Override
    public void onActivate() {
        tickCounter = 0;
        // 开启时立即刷新一次
        refreshStats();
    }
    
    @EventHandler
    private void onTick(TickEvent.Post event) {
        int interval = refreshInterval.get();
        if (interval <= 0) return;  // 禁用自动刷新
        
        tickCounter++;
        // 20 ticks = 1 秒
        if (tickCounter >= interval * 20) {
            tickCounter = 0;
            refreshStats();
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    //  数据刷新
    // ══════════════════════════════════════════════════════════════
    
    /**
     * 刷新统计数据
     * 异步请求后端 API，更新 totalUsers 和 recentUsers
     */
    private void refreshStats() {
        if (isLoading) return;  // 防止重复请求
        
        isLoading = true;
        errorMessage = null;
        reloadCurrentScreen();
        
        new Thread(() -> {
            try {
                if (clientIp == null || System.currentTimeMillis() - lastProfileLookupTime >= 10 * 60 * 1000L) {
                    fetchIpAndCountry();
                    lastProfileLookupTime = System.currentTimeMillis();
                }
                
                Exception lastFailure = null;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/stats?t=" + System.currentTimeMillis()))
                            .timeout(Duration.ofSeconds(12))
                            .header("Accept", "application/json")
                            .GET()
                            .build();
                        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
                        parseStatsResponse(response.body());
                        if (errorMessage == null) lastUpdateTime = System.currentTimeMillis();
                        lastFailure = null;
                        break;
                    } catch (Exception e) {
                        lastFailure = e;
                        if (attempt < 3) Thread.sleep(attempt * 500L);
                    }
                }
                if (lastFailure != null) {
                    String message = lastFailure.getMessage();
                    errorMessage = message == null || message.isBlank()
                        ? lastFailure.getClass().getSimpleName()
                        : lastFailure.getClass().getSimpleName() + "：" + message;
                }
            } catch (Exception e) {
                String message = e.getMessage();
                errorMessage = message == null || message.isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getClass().getSimpleName() + "：" + message;
            } finally {
                isLoading = false;
                reloadCurrentScreen();
            }
        }, "yiyiaddon-stats-refresh").start();
    }

    private void reloadCurrentScreen() {
        mc.execute(() -> {
            if (mc.screen instanceof WidgetScreen screen) screen.reload();
        });
    }
    
    /**
     * 解析 API 返回的 JSON
     * 提取 total_users 和 recent_users 列表
     */
    private void parseStatsResponse(String json) {
        try {
            // 解析总用户数
            Matcher totalMatcher = Pattern.compile("\"total_users\":(\\d+)").matcher(json);
            if (!totalMatcher.find()) throw new IllegalArgumentException("缺少 total_users");
            int parsedTotalUsers = Integer.parseInt(totalMatcher.group(1));

            Matcher usesMatcher = Pattern.compile("\"total_uses\":(\\d+)").matcher(json);
            int parsedTotalUses = usesMatcher.find() ? Integer.parseInt(usesMatcher.group(1)) : parsedTotalUsers;
            
            // 解析 24h 活跃用户数
            Matcher activeMatcher = Pattern.compile("\"(?:active_24h|active_users_24h)\":(\\d+)").matcher(json);
            int parsedActiveUsers24h = activeMatcher.find() ? Integer.parseInt(activeMatcher.group(1)) : 0;

            Matcher onlineMatcher = Pattern.compile("\"online_users\":(\\d+)").matcher(json);
            int parsedOnlineUsers = onlineMatcher.find() ? Integer.parseInt(onlineMatcher.group(1)) : 0;
            
            // 解析最近活跃用户列表
            List<RecentUser> newRecentUsers = new ArrayList<>();
            Pattern userPattern = Pattern.compile(
                "\\{\"uuid\":\"[^\"]*\",\"name\":\"([^\"]+)\"[^}]*?\"server_name\":(null|\"([^\"]*)\")[^}]*?\"is_online\":(true|false|0|1)[^}]*?\"last_heartbeat\":(null|\\d+)[^}]*?\"last_seen\":(?:\"([^\"]+)\"|(\\d+))[^}]*}"
            );
            Matcher userMatcher = userPattern.matcher(json);
            
            while (userMatcher.find()) {
                String name = userMatcher.group(1);
                String serverName = userMatcher.group(3);
                boolean isOnline = "true".equals(userMatcher.group(4)) || "1".equals(userMatcher.group(4));
                long lastSeen = userMatcher.group(8) != null
                    ? normalizeTimestamp(Long.parseLong(userMatcher.group(8)))
                    : Instant.parse(userMatcher.group(7)).getEpochSecond();
                newRecentUsers.add(new RecentUser(name, lastSeen, isOnline, serverName));
            }
            
            totalUsers = parsedTotalUsers;
            totalUses = parsedTotalUses;
            activeUsers24h = parsedActiveUsers24h;
            onlineUsers = parsedOnlineUsers;
            recentUsers = newRecentUsers;
            errorMessage = null;
            
        } catch (Exception e) {
            errorMessage = "解析失败: " + e.getMessage();
        }
    }

    private long normalizeTimestamp(long timestamp) {
        return timestamp > 10_000_000_000L ? timestamp / 1000 : timestamp;
    }
    
    // ══════════════════════════════════════════════════════════════
    //  说明面板
    // ══════════════════════════════════════════════════════════════
    
    @Override
    public WWidget getWidget(GuiTheme theme) {
        // 创建带按钮的面板
        return buildInfoWidget(theme, table -> {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
            
            WButton refreshButton = table.add(theme.button(isLoading ? "§e加载中..." : "§a立即刷新")).expandX().widget();
            refreshButton.action = this::refreshStats;
        }, buildStatsSections());
    }
    
    /**
     * 构建统计信息的各个区域
     * 每次调用都重新生成，确保数据实时更新
     */
    private String[][] buildStatsSections() {
        List<String[]> sections = new ArrayList<>();
        
        // 构建统计数据区
        List<String> statsSection = new ArrayList<>();
        statsSection.add("§6§l▌ 实时概览");
        statsSection.add("§f  · 当前在线：" + highlightText(String.valueOf(onlineUsers)) + " §7人");
        statsSection.add("§f  · 24 小时活跃：" + highlightText(String.valueOf(activeUsers24h)) + " §7人");
        statsSection.add("§f  · 累计用户：" + highlightText(String.valueOf(totalUsers)) + " §7人");
        statsSection.add("§f  · 累计启动：" + highlightText(String.valueOf(totalUses)) + " §7次");
        
        // 显示客户端 IP 和国家信息（带国旗和代理检测）
         if (clientIp != null) {
             String flag = countryCodeToFlag(clientCountry);
             String countryDisplay = isValidCountryCode(clientCountry) ? flag + " §e§l" + translateCountryCode(clientCountry) : "§7未知国家";
             
             // 显示代理状态
             String proxyStatus = "";
             if (isUsingProxy && proxyType != null) {
                 proxyStatus = " §c§l[" + proxyType + "]";
             }
             
             statsSection.add("§f  · 你的 IP：§b" + clientIp + " §8| " + countryDisplay + proxyStatus);
         }
        
        statsSection.add(formatUpdateTime());
        statsSection.add("§f  · 数据刷新：§a每 3 秒 §8| " + formatStatus());
        sections.add(statsSection.toArray(new String[0]));
        
        // 添加最近活跃用户列表（工整对齐）
        if (!recentUsers.isEmpty()) {
            List<String> usersSection = new ArrayList<>();
            usersSection.add("§b§l▌ 在线与最近活跃");
            
            int displayCount = Math.min(maxDisplayUsers.get(), recentUsers.size());
            for (int i = 0; i < displayCount; i++) {
                RecentUser user = recentUsers.get(i);
                long hoursAgo = (System.currentTimeMillis() / 1000 - user.lastSeen) / 3600;
                String timeDesc = user.isOnline ? "§a在线" : (hoursAgo == 0 ? "§e刚刚离线" : "§7" + hoursAgo + "h 前");
                
                // 格式：序号. 玩家名 - 时间描述
                String server = user.serverName == null || user.serverName.isBlank() ? " §8| §7未连接服务器" : " §8| §7" + user.serverName;
                String line = String.format("§f  %2d. §e%s §8· %s%s",
                    i + 1, user.name, timeDesc, server);
                usersSection.add(line);
            }
            
            if (recentUsers.size() > displayCount) {
                usersSection.add("§7  ... 还有 " + (recentUsers.size() - displayCount) + " 人未显示");
            }
            
            sections.add(usersSection.toArray(new String[0]));
        }
        
        // 添加使用说明
        sections.add(new String[]{
            "§e§l▌ 使用说明",
            "§f  · " + highlightText("实时更新") + "：后台和本模块均按 3 秒刷新",
            "§f  · " + highlightText("在线判定") + "：3 秒心跳，12 秒无心跳自动离线",
            "§f  · " + highlightText("自动刷新") + "：默认每 3 秒更新一次",
            "§f  · " + highlightText("手动刷新") + "：点击绿色按钮立即获取最新数据",
            "§f  · " + highlightText("聊天栏") + "：不再发送统计公屏消息"
        });
        
        // 添加当前在线标识
        sections.add(new String[]{
            "§d§l▌ 当前使用该扩展的玩家",
            "§f  当前有 " + highlightText(String.valueOf(onlineUsers)) + " §f位玩家在线",
            "§f  累计有 " + highlightText(String.valueOf(totalUsers)) + " §f位玩家使用过扩展"
        });
        
        return sections.toArray(new String[0][]);
    }
    
    // ══════════════════════════════════════════════════════════════
    //  格式化辅助
    // ══════════════════════════════════════════════════════════════
    
    private String formatUpdateTime() {
        if (lastUpdateTime == 0) {
            return "§f  · 最后更新：" + highlightText("未刷新");
        }
        
        long secondsAgo = (System.currentTimeMillis() - lastUpdateTime) / 1000;
        String timeAgo;
        
        if (secondsAgo < 60) {
            timeAgo = secondsAgo + " 秒前";
        } else if (secondsAgo < 3600) {
            timeAgo = (secondsAgo / 60) + " 分钟前";
        } else {
            timeAgo = (secondsAgo / 3600) + " 小时前";
        }
        
        return "§f  · 最后更新：" + highlightText(timeAgo);
    }
    
    private String formatStatus() {
        if (isLoading) {
            return "§f  · 状态：§e正在加载...";
        } else if (errorMessage != null) {
            return "§f  · 状态：§c错误 (" + errorMessage + ")";
        } else if (lastUpdateTime > 0) {
            return "§f  · 状态：§a正常";
        } else {
            return "§f  · 状态：§7待刷新";
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    //  数据类
    // ══════════════════════════════════════════════════════════════
    
    // ══════════════════════════════════════════════════════════════
    //  IP 地理位置查询（三重降级策略 + VPN/代理检测）
    // ══════════════════════════════════════════════════════════════
    
    private void fetchIpAndCountry() {
        // 重置状态
        isUsingProxy = false;
        proxyType = null;
        
        // 收集多个来源的 IP 进行对比（检测代理）
        String ip1 = null, ip2 = null, ip3 = null;
        String country1 = null, country2 = null, country3 = null;
        
        // 策略 1: ipapi.co（提供代理检测信息）
        IpResult result1 = tryFetchFromIpApiCoWithProxy();
        if (result1 != null) {
            ip1 = result1.ip;
            country1 = result1.country;
            if (result1.isProxy) {
                isUsingProxy = true;
                proxyType = result1.proxyType;
            }
        }
        
        // 策略 2: ip-api.com（提供代理和移动网络检测）
        IpResult result2 = tryFetchFromIpApiWithProxy();
        if (result2 != null) {
            ip2 = result2.ip;
            country2 = result2.country;
            if (result2.isProxy) {
                isUsingProxy = true;
                if (proxyType == null) proxyType = result2.proxyType;
            }
        }
        
        // 策略 3: cloudflare trace
        IpResult result3 = tryFetchFromCloudflareSimple();
        if (result3 != null) {
            ip3 = result3.ip;
            country3 = result3.country;
        }
        
        // 对比 IP 一致性（如果多个 API 返回的 IP 不同，可能使用了代理）
        if (ip1 != null && ip2 != null && !ip1.equals(ip2)) {
            isUsingProxy = true;
            if (proxyType == null) proxyType = "VPN";
        }
        
        // 选择最可靠的结果（优先使用第一个成功的）
        if (ip1 != null) {
            clientIp = ip1;
            clientCountry = country1;
        } else if (ip2 != null) {
            clientIp = ip2;
            clientCountry = country2;
        } else if (ip3 != null) {
            clientIp = ip3;
            clientCountry = country3;
        } else {
            // 完全失败，进行端口连通性测试
            if (!testNetworkConnectivity()) {
                clientIp = "Network Offline";
                clientCountry = "??";
            } else {
                clientIp = "Unknown";
                clientCountry = "??";
            }
        }
    }
    
    /**
     * 测试网络连通性（多端口测试）
     * 测试常用公共服务器的端口是否可达
     */
    private boolean testNetworkConnectivity() {
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
                return true; // 只要有一个端口通就认为网络正常
            } catch (Exception ignored) {
            }
        }
        
        return false; // 所有端口都不通
    }
    
    private IpResult tryFetchFromIpApiCoWithProxy() {
        try {
            // ipapi.co 提供 VPN/代理检测字段
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ipapi.co/json/"))
                .timeout(Duration.ofSeconds(3))
                .header("User-Agent", "yiyiaddon-minecraft-client")
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher ipMatcher = Pattern.compile("\"ip\":\\s*\"([^\"]+)\"").matcher(body);
                Matcher countryMatcher = Pattern.compile("\"country_code\":\\s*\"([^\"]+)\"").matcher(body);
                
                // 检测是否为代理/VPN/TOR
                boolean isProxy = body.contains("\"threat\"") || body.contains("\"proxy\"");
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
                
                if (ipMatcher.find() && countryMatcher.find()) {
                    return new IpResult(ipMatcher.group(1), countryMatcher.group(1), isProxy, proxyType);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private IpResult tryFetchFromIpApiWithProxy() {
        try {
            // ip-api.com 提供 proxy/mobile 检测字段
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://ip-api.com/json/?fields=query,countryCode,proxy,mobile,hosting"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher ipMatcher = Pattern.compile("\"query\":\\s*\"([^\"]+)\"").matcher(body);
                Matcher countryMatcher = Pattern.compile("\"countryCode\":\\s*\"([^\"]+)\"").matcher(body);
                
                // 检测代理/VPN
                boolean isProxy = body.contains("\"proxy\":true") || body.contains("\"hosting\":true");
                String proxyType = null;
                
                if (body.contains("\"proxy\":true")) {
                    proxyType = "PROXY";
                } else if (body.contains("\"hosting\":true")) {
                    proxyType = "VPN"; // 托管IP通常是VPN
                }
                
                if (ipMatcher.find() && countryMatcher.find()) {
                    return new IpResult(ipMatcher.group(1), countryMatcher.group(1), isProxy, proxyType);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private IpResult tryFetchFromCloudflareSimple() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://1.1.1.1/cdn-cgi/trace"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher ipMatcher = Pattern.compile("ip=([^\\s]+)").matcher(body);
                Matcher countryMatcher = Pattern.compile("loc=([A-Z]{2})").matcher(body);
                
                if (ipMatcher.find() && countryMatcher.find()) {
                    return new IpResult(ipMatcher.group(1), countryMatcher.group(1), false, null);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * 将国家代码转换为国旗 emoji
     * CN -> 🇨🇳, US -> 🇺🇸, AU -> 🇦🇺 等
     */
    private static String countryCodeToFlag(String countryCode) {
        if (!isValidCountryCode(countryCode)) {
            return "🌐";
        }
        
        countryCode = countryCode.toUpperCase();
        int firstLetter = countryCode.charAt(0) - 'A' + 0x1F1E6;
        int secondLetter = countryCode.charAt(1) - 'A' + 0x1F1E6;
        
        return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
    }

    private static boolean isValidCountryCode(String countryCode) {
        return countryCode != null && countryCode.matches("[A-Za-z]{2}");
    }

    private static String translateCountryCode(String countryCode) {
        String countryName = Locale.of("", countryCode.toUpperCase()).getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
        return countryName == null || countryName.isBlank() ? "未知国家" : countryName;
    }
    
    // ══════════════════════════════════════════════════════════════
    //  数据类
    // ══════════════════════════════════════════════════════════════
    
    private record RecentUser(String name, long lastSeen, boolean isOnline, String serverName) {
    }
    
    private record IpResult(String ip, String country, boolean isProxy, String proxyType) {
    }
}
