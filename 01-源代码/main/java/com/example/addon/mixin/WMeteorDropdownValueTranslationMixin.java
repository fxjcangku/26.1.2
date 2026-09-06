package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Meteor 下拉框选中值翻译 Mixin
 * 
 * 拦截 WMeteorDropdown.WValue 内部类的值显示逻辑
 * 将下拉框中已选中的枚举值转换为中文显示
 * 影响范围：设置界面中所有下拉框组件的当前选中值
 */
@Mixin(targets = "meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown$WValue", remap = false)
public abstract class WMeteorDropdownValueTranslationMixin {
    
    /**
     * 翻译下拉框值的尺寸计算
     * 
     * 注入点：onCalculateSize 方法中的 toString() 调用
     * 效果：将枚举值转换为中文后再计算文本宽度，确保布局正确
     */
    @Redirect(method = "onCalculateSize", at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"))
    private String yiyiaddon$translateValueSize(Object value) {
        return YiyiaddonTranslator.translateSettingValue(value);
    }

    /**
     * 翻译下拉框值的渲染文本
     * 
     * 注入点：onRender 方法中的 toString() 调用
     * 效果：将枚举值转换为中文后显示在界面上
     */
    @Redirect(method = "onRender", at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"))
    private String yiyiaddon$translateValueText(Object value) {
        return YiyiaddonTranslator.translateSettingValue(value);
    }
}
