package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * GUI 字符串文本翻译 Mixin
 * 
 * 拦截原版 GuiGraphicsExtractor 的字符串绘制入口，
 * 翻译 Meteor 直接以 String 渲染的界面文字。
 * 
 * 影响范围：所有通过 GuiGraphicsExtractor.text(Font, String, ...) 渲染的文本，
 * 包括多人游戏界面右上角的账户状态「Logged in as」与代理状态「Not using a proxy」。
 * 
 * 说明：Meteor 在 JoinMultiplayerScreen 中通过 graphics.text(mc.font, String, ...)
 * 直接绘制账户/代理状态，走的是 String 重载，而非 Component 重载，
 * 因此无法被现有 JoinMultiplayerScreenTranslationMixin（仅拦截 Component 重载）覆盖。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsTextTranslationMixin {

    /**
     * 翻译字符串文本
     * 
     * 注入点：text(Font, String, int, int, int, boolean) 的参数
     * 效果：在绘制前把可翻译的英文字符串替换为中文，未命中时原样返回
     * 
     * @param str 原始字符串
     * @return 翻译后的字符串
     */
    @ModifyVariable(
        method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String yiyiaddon$translateStringText(String str) {
        return YiyiaddonTranslator.translateVisible(str);
    }
}
