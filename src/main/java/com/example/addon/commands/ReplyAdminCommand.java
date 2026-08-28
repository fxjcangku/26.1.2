package com.example.addon.commands;

import com.example.addon.core.AddonTemplate;
import com.example.addon.utils.YiyiaddonIdentity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.minecraft.commands.Commands.argument;

/**
 * 回复管理员指令
 * 用法：.回复 <消息> 或 .reply <message>
 * 功能：玩家收到管理员消息后，可以使用此命令回复管理员
 */
public class ReplyAdminCommand extends Command {
    // HTTP客户端，用于发送回复到后台API
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public ReplyAdminCommand() {
        super("回复", "回复管理员消息", "reply");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // 注册命令参数：接收玩家输入的消息内容
        builder.then(argument("message", StringArgumentType.greedyString())
            .executes(context -> {
                String message = StringArgumentType.getString(context, "message");
                sendReplyAsync(message);  // 异步发送回复
                return SINGLE_SUCCESS;
            }));
    }

    /**
     * 异步发送回复到后台API
     * @param message 玩家输入的回复内容
     */
    private void sendReplyAsync(String message) {
        new Thread(() -> {
            try {
                // 获取玩家信息（会话身份，正版=微软 UUID）
                String uuid = YiyiaddonIdentity.uuid(mc);
                String username = YiyiaddonIdentity.name(mc);

                // 构建JSON请求体
                String jsonBody = String.format(
                    "{\"uuid\":\"%s\",\"username\":\"%s\",\"message\":\"%s\"}",
                    uuid,
                    username,
                    escapeJson(message)
                );

                // 发送POST请求到 /api/messages/reply 接口
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/messages/reply"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                // 在主线程显示发送结果
                mc.execute(() -> {
                    if (response.statusCode() == 200) {
                        mc.player.sendSystemMessage(Component.literal("§a✅ 回复已发送给管理员！"));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§c❌ 回复发送失败，请稍后重试"));
                    }
                });

            } catch (Exception e) {
                // 捕获异常并显示错误信息
                mc.execute(() -> {
                    mc.player.sendSystemMessage(Component.literal("§c❌ 回复失败: " + e.getMessage()));
                });
            }
        }, "ReplyAdmin-Thread").start();
    }

    /**
     * 转义JSON特殊字符
     * @param text 原始文本
     * @return 转义后的文本
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
