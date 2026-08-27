package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 文本渲染翻译 Mixin
 * 
 * 拦截 Meteor 的原版文本渲染器，实现界面文本的自动翻译
 * 同时修复翻译后文本宽度计算时的字符串越界问题
 * 
 * 影响范围：所有通过 VanillaTextRenderer 渲染的文本
 * 包括：GUI 界面元素、按钮文字、标签等
 */
@Mixin(value = VanillaTextRenderer.class, remap = false)
public abstract class TextTranslationMixin {
    
    /**
     * 翻译渲染的文本内容
     * 
     * 注入点：render 方法的参数
     * 效果：在文本渲染前自动翻译为中文
     */
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private String yiyiaddon$translate(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }

    /**
     * 翻译文本宽度计算时的文本
     * 
     * 注入点：getWidth 方法的参数
     * 效果：计算中文文本的实际宽度，避免布局错误
     */
    @ModifyVariable(
        method = "getWidth(Ljava/lang/String;IZ)D",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String yiyiaddon$translateWidth(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }

    /**
     * 修复翻译后文本截取时的越界问题
     * 
     * 注入点：getWidth 方法中的 substring 调用
     * 效果：防止因中英文长度差异导致的字符串索引越界
     * 
     * 原因：英文文本翻译为中文后长度可能变短，原有的结束索引可能超出范围
     */
    @Redirect(
        method = "getWidth(Ljava/lang/String;IZ)D",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/String;substring(II)Ljava/lang/String;"
        )
    )
    private String yiyiaddon$substringWithinTranslatedText(String text, int beginIndex, int endIndex) {
        return text.substring(beginIndex, Math.min(endIndex, text.length()));
    }
}
