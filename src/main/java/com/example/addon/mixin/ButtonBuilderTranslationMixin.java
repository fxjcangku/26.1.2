package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 按钮构建器翻译 Mixin
 * 
 * 拦截原版按钮构建器的构造函数
 * 将按钮文本自动翻译为中文，特别针对 Meteor 在多人游戏界面添加的按钮
 * 
 * 影响范围：所有通过 Button.Builder 创建的按钮
 * 包括：Meteor 在多人游戏界面添加的自定义按钮
 */
@Mixin(Button.Builder.class)
public abstract class ButtonBuilderTranslationMixin {
    
    /**
     * 翻译按钮文本组件
     * 
     * 注入点：Button.Builder 构造函数的参数
     * 效果：如果按钮文本有对应的翻译，则替换为中文
     * 
     * @param text 原始按钮文本组件
     * @return 翻译后的按钮文本组件
     */
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Component yiyiaddon$translateMeteorMultiplayerButtons(Component text) {
        String original = text.getString();
        String translated = YiyiaddonTranslator.translateVisible(original);
        return translated.equals(original) ? text : Component.literal(translated);
    }
}
