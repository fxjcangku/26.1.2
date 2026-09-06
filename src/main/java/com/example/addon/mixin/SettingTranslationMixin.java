package com.example.addon.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 设置项翻译 Mixin
 * 
 * 将 Setting 的 title 和 description 字段从 final 改为可变
 * 用于运行时替换设置项的标题和描述文本，实现中文化
 * 
 * 配合 SettingTranslationAccess 接口使用
 */
@Mixin(value = Setting.class, remap = false)
public abstract class SettingTranslationMixin implements com.example.addon.accessor.SettingTranslationAccess {
    /** 设置项标题，通过 @Mutable 移除 final 限制 */
    @Mutable @Shadow @Final public String title;
    
    /** 设置项描述，通过 @Mutable 移除 final 限制 */
    @Mutable @Shadow @Final public String description;

    @Override
    public void yiyiaddon$setTitle(String value) {
        title = value;
    }

    @Override
    public void yiyiaddon$setDescription(String value) {
        description = value;
    }
}
