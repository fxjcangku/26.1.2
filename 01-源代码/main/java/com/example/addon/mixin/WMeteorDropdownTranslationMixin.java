package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Meteor 下拉框翻译 Mixin
 * 
 * 拦截 WMeteorDropdown 下拉框组件的渲染逻辑
 * 将下拉框中显示的枚举值转换为中文
 * 影响范围：设置界面中所有下拉框组件显示的当前值
 */
@Mixin(value = meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown.class, remap = false)
public abstract class WMeteorDropdownTranslationMixin {
    
    /**
     * 翻译下拉框显示的选中值
     * 
     * 注入点：onRender 方法中的 toString() 调用
     * 效果：将枚举值（如 ON/OFF、模式名等）转换为中文后显示
     */
    @Redirect(method = "onRender", at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"))
    private String yiyiaddon$translateSelectedValue(Object value) {
        return YiyiaddonTranslator.translateSettingValue(value);
    }
}
