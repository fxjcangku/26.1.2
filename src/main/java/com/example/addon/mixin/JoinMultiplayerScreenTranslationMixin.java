package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 多人游戏界面翻译 Mixin
 * 
 * 拦截 26.1.2 服务器列表中在线服务器状态文本的渲染
 * 26.1.2 状态文本渲染点：ServerSelectionList$OnlineServerEntry.extractContent(...)
 * 中调用 GuiGraphicsExtractor.text(Font, Component, int, int, int)
 * 
 * 影响范围：多人游戏服务器列表中显示的服务器状态文字
 */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry", priority = 1100)
public abstract class JoinMultiplayerScreenTranslationMixin {
    
    /**
     * 翻译服务器状态文本
     * 
     * 注入点：extractContent 方法中调用 GuiGraphicsExtractor.text 的第二个参数（状态组件）
     * 效果：将 Meteor 添加的英文服务器状态信息翻译为中文
     * 无样式纯文本且命中翻译时才替换，其他文本（如红色版本不兼容提示）原样保留
     * 
     * @param status 原始状态组件
     * @return 翻译后的状态组件
     */
    @ModifyArg(
        method = "extractContent(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        ),
        index = 1
    )
    private Component yiyiaddon$translateStatusText(Component status) {
        if (status == null || !YiyiaddonTranslator.enabled()) return status;
        // 仅处理无样式的纯文本组件，避免破坏红色不兼容提示等特殊样式
        if (!(status.getContents() instanceof PlainTextContents contents)) return status;
        String original = contents.text();
        String translated = YiyiaddonTranslator.translateVisible(original);
        return translated.equals(original) ? status : Component.literal(translated);
    }
}