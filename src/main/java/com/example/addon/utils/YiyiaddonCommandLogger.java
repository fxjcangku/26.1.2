package com.example.addon.utils;

import com.example.addon.core.AddonTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * yiyiaddon 指令活动记录器。
 *
 * <p>通过 Mixin 拦截所有以 / 开头的玩家指令（不包括内容和参数），按玩家分类后上报到后台，
 * 用于后台统计功能使用情况与异常行为检测。
 *
 * <p>安全约定：
 * <ul>
 *   <li>只上报指令名称（第一个空格前的部分），不上传参数、密码或坐标等敏感信息</li>
 *   <li>不拦截服务器原生聊天消息，仅识别客户端发出的指令</li>
 *   <li>后台 30 秒去重，避免频繁重复上报同一指令</li>
 * </ul>
 */
public final class YiyiaddonCommandLogger {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    /**
     * 由 Mixin 调用：拦截玩家发送的指令并异步上报。
     * 
     * @param command 完整指令字符串（含 / 前缀和参数）
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     */
    public static void logCommand(String command, String uuid, String name) {
        if (command == null || command.isBlank() || !command.startsWith("/")) return;
        if (uuid == null || name == null) return;
        
        // 提取指令名称（第一个空格前的部分，去除 / 前缀）
        String commandName = command.substring(1).split("\\s+", 2)[0];
        if (commandName.isBlank()) return;
        
        // 异步上报到后台，不阻塞游戏
        reportCommandAsync(commandName, uuid, name);
    }

    /**
     * 异步上报指令名称到后台，用于功能使用统计。
     * 
     * @param commandName 指令名称（不含 / 前缀和参数）
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     */
    private static void reportCommandAsync(String commandName, String uuid, String name) {
        new Thread(() -> {
            try {
                // 构建 JSON 请求体（只包含玩家身份和指令名称，不含参数）
                String body = "{\"uuid\":\"" + escape(uuid) + "\",\"username\":\"" + escape(name)
                    + "\",\"command_name\":\"" + escape(commandName) + "\",\"category\":\"指令\"}";
                
                HTTP_CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/command-activity"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // 网络错误静默忽略，不干扰游戏体验
            }
        }, "yiyiaddon-command-logger").start();
    }

    /** 转义 JSON 控制字符，确保玩家名称和指令名不会破坏请求体结构。 */
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
