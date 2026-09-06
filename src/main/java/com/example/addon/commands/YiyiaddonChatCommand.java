package com.example.addon.commands;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.minecraft.commands.Commands.argument;

/**
 * yiyiaddon 跨世界聊天指令。
 *
 * 聊天只在安装本扩展并已完成注册的玩家之间同步，不读取服务器原生聊天内容，
 * 也不把玩家输入的服务器指令上报后台。
 */
public final class YiyiaddonChatCommand extends Command {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    private static final Pattern ONLINE_NAME_PATTERN = Pattern.compile("\\\"name\\\":\\\"([^\\\"]+)\\\"");

    public YiyiaddonChatCommand() {
        super("聊天", "yiyiaddon 跨世界聊天与在线玩家查询", "chat");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // 在线查询限制为后台心跳仍有效的玩家，避免把已经离线的旧记录展示出来。
        builder.then(literal("在线").executes(context -> {
            listOnlineAsync();
            return SINGLE_SUCCESS;
        }));
        // 帮助菜单把常用操作集中展示，玩家不需要记忆英文别名。
        builder.then(literal("帮助").executes(context -> {
            chatInfo("§b.聊天 在线 §7查看当前在线玩家");
            chatInfo("§b.聊天 说 <内容> §7发送频道消息");
            chatInfo("§b.聊天 私聊 <玩家> <内容> §7发送定向私信");
            chatInfo("§b.聊天 回复 <玩家> <内容> §7回复玩家消息");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("说").then(argument("内容", StringArgumentType.greedyString()).executes(context -> {
            sendAsync(null, StringArgumentType.getString(context, "内容"));
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("私聊").then(argument("玩家", StringArgumentType.word()).then(argument("内容", StringArgumentType.greedyString()).executes(context -> {
            sendAsync(StringArgumentType.getString(context, "玩家"), StringArgumentType.getString(context, "内容"));
            return SINGLE_SUCCESS;
        }))));
        // 回复玩家消息（与私聊功能一致，只是语义更清晰）
        builder.then(literal("回复").then(argument("玩家", StringArgumentType.word()).then(argument("内容", StringArgumentType.greedyString()).executes(context -> {
            sendAsync(StringArgumentType.getString(context, "玩家"), StringArgumentType.getString(context, "内容"));
            return SINGLE_SUCCESS;
        }))));
        builder.executes(context -> {
            chatInfo("§b聊天系统");
            chatInfo("§f  · §e.聊天 在线 §7查看当前使用扩展的在线玩家");
            chatInfo("§f  · §e.聊天 帮助 §7查看全部指令");
            chatInfo("§f  · §e.聊天 说 <内容> §7发送给在线频道");
            chatInfo("§f  · §e.聊天 私聊 <玩家> <内容> §7发送私信");
            chatInfo("§f  · §e.聊天 回复 <玩家> <内容> §7回复玩家");
            chatInfo("§8仅同步本扩展聊天内容，不读取服务器聊天或其他指令。");
            return SINGLE_SUCCESS;
        });
    }

    /** 在后台线程请求在线名单，避免网络请求阻塞 Minecraft 主线程。 */
    private void listOnlineAsync() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/chat/online"))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
                Matcher matcher = ONLINE_NAME_PATTERN.matcher(response.body());
                StringBuilder names = new StringBuilder();
                int count = 0;
                while (matcher.find()) {
                    if (count++ > 0) names.append("§8、§f");
                    names.append(matcher.group(1));
                }
                int finalCount = count;
                mc.execute(() -> chatInfo(finalCount == 0 ? "§7当前没有在线玩家" : "§a在线 " + finalCount + " 人：§f" + names));
                reportActivity("查询在线", "查询");
            } catch (Exception ignored) {
                mc.execute(() -> chatInfo("§c在线列表获取失败"));
            }
        }, "yiyiaddon-chat-online").start();
    }

    /** 发送频道消息或定向私信，并限制长度与空消息，降低刷屏和数据库膨胀风险。 */
    private void sendAsync(String targetName, String message) {
        if (message == null || message.isBlank() || message.length() > 300) {
            chatInfo("§c消息不能为空且不能超过 300 个字符");
            return;
        }
        new Thread(() -> {
            try {
                String uuid = YiyiaddonIdentity.uuid(mc);
                String name = YiyiaddonIdentity.name(mc);
                String body = "{\"uuid\":\"" + escape(uuid) + "\",\"username\":\"" + escape(name)
                    + "\",\"target_name\":" + (targetName == null ? "null" : "\"" + escape(targetName) + "\"")
                    + ",\"message\":\"" + escape(message) + "\"}";
                HttpResponse<String> response = HTTP_CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/chat/send"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() == 200;
                mc.execute(() -> chatInfo(success ? "§a消息已发送" : "§c消息发送失败，请稍后重试"));
                if (success) reportActivity(targetName == null ? "频道消息" : "私聊消息", "聊天");
            } catch (Exception ignored) {
                mc.execute(() -> chatInfo("§c消息发送失败，请检查网络"));
            }
        }, "yiyiaddon-chat-send").start();
    }

    /** 只上报功能名称和分类，用于后台统计功能使用情况，不上传实际聊天文本。 */
    private void reportActivity(String commandName, String category) {
        String uuid = YiyiaddonIdentity.uuid(mc);
        String name = YiyiaddonIdentity.name(mc);
        if (uuid == null || name == null) return;
        new Thread(() -> {
            try {
                String body = "{\"uuid\":\"" + escape(uuid) + "\",\"username\":\"" + escape(name)
                    + "\",\"command_name\":\"" + escape(commandName) + "\",\"category\":\"" + escape(category) + "\"}";
                HTTP_CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create(AddonTemplate.STATS_API_URL + "/api/command-activity"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        }, "yiyiaddon-command-activity").start();
    }

    /** 转义 JSON 控制字符，确保玩家名称和消息不会破坏请求体结构。 */
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** 统一使用 yiyiaddon 聊天前缀，保证游戏内消息样式一致。 */
    private void chatInfo(String message) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("聊天", message)));
    }
}
