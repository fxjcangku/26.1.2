package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.HelpCommand;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HelpCommand.class, remap = false)
public abstract class MeteorHelpCommandTranslationMixin {
    
    // 拦截 showHelp 方法，完全重写翻译版本
    @Inject(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void yiyiaddon$translateShowHelp(Command command, CallbackInfo ci) {
        if (!YiyiaddonTranslator.enabled()) return;
        
        // 取消原版输出
        ci.cancel();
        
        // 输出翻译版本
        ChatUtils.info("命令帮助：%s", command.getName());
        
        if (command.getDescription() != null && !command.getDescription().isEmpty()) {
            String translated = translateDescription(command.getDescription());
            ChatUtils.info("说明：%s", translated);
        }
        
        if (!command.getAliases().isEmpty()) {
            ChatUtils.info("别名：%s", String.join(", ", command.getAliases()));
        }
    }
    
    // 翻译命令描述
    private String translateDescription(String desc) {
        return switch (desc) {
            case "Shows you what a command does." -> "显示指令的功能说明。";
            case "List of all bound modules." -> "显示所有已绑定快捷键的模块。";
            case "Manages fake players that you can use for testing." -> "管理用于测试的假玩家。";
            case "Damages self" -> "对自己造成伤害";
            case "Sends messages in chat." -> "在聊天框发送消息。";
            case "Drops selected items from your inventory." -> "从背包中丢弃选定的物品。";
            case "Gives you items." -> "给予物品。";
            case "Logs you out of your account." -> "退出当前账号登录。";
            case "Allows you to reload parts of the client." -> "重新加载客户端的部分功能。";
            case "Resets all settings to default." -> "重置所有设置为默认值。";
            case "Connects to a server." -> "连接到服务器。";
            case "Loads and saves profiles." -> "加载和保存配置文件。";
            case "Toggles a module on and off." -> "开启或关闭模块。";
            case "Shows information about Meteor." -> "显示 Meteor 客户端信息。";
            default -> desc;
        };
    }
}
