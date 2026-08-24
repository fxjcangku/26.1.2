package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.client.gui.components.ChatComponent.class)
public abstract class ChatMessageTranslationMixin {
    
    // 拦截所有添加到聊天框的消息并翻译
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component yiyiaddon$translateChatMessage(Component message) {
        if (!YiyiaddonTranslator.enabled() || message == null) return message;
        
        String text = message.getString();
        String translated = translateCommonPhrases(text);
        
        if (!text.equals(translated)) {
            return Component.literal(translated);
        }
        
        return message;
    }
    
    // 翻译常见短语
    private String translateCommonPhrases(String text) {
        if (text == null) return text;
        
        return text
            // 命令错误相关
            .replace("Unknown or incomplete command, see below for error", "未知或不完整的命令，错误详情见下方")
            .replace("at position", "位置")
            .replace("Incorrect argument for command", "命令参数错误")
            
            // 绑定相关
            .replace("Binds a specified module to the next pressed key.", "将指定模块绑定到下一个按下的按键。")
            .replace("List of all bound modules.", "显示所有已绑定快捷键的模块列表。")
            .replace("Displays a list of all modules.", "显示所有模块列表。")
            .replace("--- Bound Modules (", "--- 已绑定模块 (")
            
            // 路径点相关
            .replace("Manages waypoints.", "管理路径点。")
            .replace("Sets the auto wasp target.", "设置自动黄蜂目标。")
            
            // Baritone 导航相关
            .replace("[Baritone] Death position saved.", "[Baritone] 死亡位置已保存。")
            .replace("Death position saved.", "死亡位置已保存。")
            .replace("Click to goto death", "点击前往死亡位置")
            .replace("> wp goto death @", "> 前往死亡点 @")
            .replace("> up goto death @", "> 向上前往死亡点 @")
            .replace("Going to:", "前往：")
            .replace("GoalBlock", "目标方块")
            
            // 模块状态
            .replace("Toggled", "已切换")
            .replace(" on.", " 为开启。")
            .replace(" off.", " 为关闭。")
            .replace("Air Jump", "空中跳跃")
            .replace("Enter", "回车")
            
            // 通用错误
            .replace("Module not found.", "未找到该模块。")
            .replace("Invalid module.", "无效的模块。")
            .replace("You must specify", "你必须指定")
            .replace("Player not found.", "未找到玩家。")
            .replace("No permission.", "没有权限。");
    }
}
