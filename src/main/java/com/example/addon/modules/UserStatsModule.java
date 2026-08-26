package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.orbit.EventHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    
    private final SettingGroup sgGeneral = settings.createGroup("1️⃣ 统计设置", true);
    
    private final Setting<Integer> refreshInterval = sgGeneral.add(new IntSetting.Builder()
        .name("自动刷新间隔")
        .description("界面数据每隔多少秒自动刷新一次（0 = 仅手动刷新）")
        .defaultValue(30)
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
    private int activeUsers24h = 0;          // 24小时内活跃用户数
    private List<RecentUser> recentUsers = new ArrayList<>();  // 最近活跃用户
    private long lastUpdateTime = 0;         // 上次更新时间戳
    private boolean isLoading = false;       // 是否正在加载
    private String errorMessage = null;      // 错误信息
    
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
        
        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/stats"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
                
                HttpResponse<String> response = HTTP_CLIENT.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    parseStatsResponse(response.body());
                    lastUpdateTime = System.currentTimeMillis();
                } else {
                    errorMessage = "HTTP " + response.statusCode();
                }
            } catch (Exception e) {
                errorMessage = e.getClass().getSimpleName();
            } finally {
                isLoading = false;
            }
        }, "yiyiaddon-stats-refresh").start();
    }
    
    /**
     * 解析 API 返回的 JSON
     * 提取 total_users 和 recent_users 列表
     */
    private void parseStatsResponse(String json) {
        try {
            // 解析总用户数
            Matcher totalMatcher = Pattern.compile("\"total_users\":(\\d+)").matcher(json);
            if (totalMatcher.find()) {
                totalUsers = Integer.parseInt(totalMatcher.group(1));
            }
            
            // 解析最近活跃用户列表
            List<RecentUser> newRecentUsers = new ArrayList<>();
            Pattern userPattern = Pattern.compile(
                "\\{\"name\":\"([^\"]+)\",\"last_seen\":(\\d+)\\}"
            );
            Matcher userMatcher = userPattern.matcher(json);
            
            while (userMatcher.find()) {
                String name = userMatcher.group(1);
                long lastSeen = Long.parseLong(userMatcher.group(2));
                newRecentUsers.add(new RecentUser(name, lastSeen));
            }
            
            recentUsers = newRecentUsers;
            
        } catch (Exception e) {
            errorMessage = "解析失败: " + e.getMessage();
        }
    }
    
    // ══════════════════════════════════════════════════════════════
    //  说明面板
    // ══════════════════════════════════════════════════════════════
    
    @Override
    public WWidget getWidget(GuiTheme theme) {
        // 构建统计数据区
        List<String> statsSection = new ArrayList<>();
        statsSection.add("§6§l▌ 实时统计");
        statsSection.add("§f  · 总用户数：" + highlightText(String.valueOf(totalUsers)) + " §7人");
        statsSection.add("§f  · 24h 活跃：" + highlightText(String.valueOf(activeUsers24h)) + " §7人");
        statsSection.add("§f  · 最近活跃玩家数：" + highlightText(String.valueOf(recentUsers.size())) + " §7人");
        statsSection.add(formatUpdateTime());
        statsSection.add(formatStatus());
        
        List<String[]> sections = new ArrayList<>();
        sections.add(statsSection.toArray(new String[0]));
        
        // 添加最近活跃用户列表（工整对齐）
        if (!recentUsers.isEmpty()) {
            List<String> usersSection = new ArrayList<>();
            usersSection.add("§b§l▌ 最近活跃用户");
            
            int displayCount = Math.min(maxDisplayUsers.get(), recentUsers.size());
            for (int i = 0; i < displayCount; i++) {
                RecentUser user = recentUsers.get(i);
                long hoursAgo = (System.currentTimeMillis() / 1000 - user.lastSeen) / 3600;
                String timeDesc = hoursAgo == 0 ? "§a刚刚在线" : "§7" + hoursAgo + "h 前";
                
                // 格式：序号. 玩家名 - 时间描述
                String line = String.format("§f  %2d. §e%s §7- %s", 
                    i + 1, user.name, timeDesc);
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
            "§f  · " + highlightText("进入世界") + "：统计信息自动显示在聊天栏",
            "§f  · " + highlightText("查看详情") + "：打开本模块查看完整列表",
            "§f  · " + highlightText("自动刷新") + "：根据设定间隔定时更新数据",
            "§f  · " + highlightText("手动刷新") + "：点击下方按钮立即获取"
        });
        
        // 添加当前在线标识
        sections.add(new String[]{
            "§d§l▌ 当前使用该扩展的玩家",
            "§f  你就是其中之一！共有 " + highlightText(String.valueOf(totalUsers)) + " §f位玩家使用",
            "§f  最近 24h 内有 " + highlightText(String.valueOf(activeUsers24h)) + " §f位玩家活跃"
        });
        
        // 创建带按钮的面板
        return buildInfoWidget(theme, table -> {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
            
            WButton refreshButton = table.add(theme.button(isLoading ? "§e加载中..." : "§a立即刷新")).expandX().widget();
            refreshButton.action = this::refreshStats;
        }, sections.toArray(new String[0][]));
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
    
    private record RecentUser(String name, long lastSeen) {
    }
}
