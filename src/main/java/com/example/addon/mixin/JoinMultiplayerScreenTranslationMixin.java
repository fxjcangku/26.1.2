package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 多人游戏界面翻译 Mixin
 * 
 * 拦截多人游戏服务器列表界面的文本渲染
 * 翻译服务器状态文本（如 Meteor 添加的状态信息）
 * 
 * 影响范围：多人游戏服务器列表中显示的状态文字
 */
@Mixin(value = JoinMultiplayerScreen.class, priority = 1100)
public abstract class JoinMultiplayerScreenTranslationMixin {
    
    /**
     * 翻译服务器状态文本
     * 
     * 注入点：extractRenderState 方法中调用 GuiGraphicsExtractor.text 的第二个参数
     * 效果：将 Meteor 添加的服务器状态信息翻译为中文
     * 
     * @param text 原始状态文本
     * @return 翻译后的文本
     */
    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
        ),
        index = 1,
        require = 0,
        remap = false
    )
    private String yiyiaddon$translateStatusText(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }
}
