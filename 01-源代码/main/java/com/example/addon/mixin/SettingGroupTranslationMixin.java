package com.example.addon.mixin;

import meteordevelopment.meteorclient.settings.SettingGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 设置组翻译 Mixin
 * 
 * 将 SettingGroup 的 name 字段从 final 改为可变
 * 用于运行时替换设置组的名称，实现中文化
 * 
 * 配合 SettingGroupTranslationAccess 接口使用
 * 影响范围：模块设置界面中的设置分组标题
 */
@Mixin(value = SettingGroup.class, remap = false)
public abstract class SettingGroupTranslationMixin implements com.example.addon.accessor.SettingGroupTranslationAccess {
    
    /** 设置组名称，通过 @Mutable 移除 final 限制 */
    @Mutable @Shadow @Final public String name;

    /**
     * 设置组名称设置器
     * 
     * @param value 新的组名称（中文化后的名称）
     */
    @Override
    public void yiyiaddon$setName(String value) {
        name = value;
    }
}
