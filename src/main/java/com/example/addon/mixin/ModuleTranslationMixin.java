package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 模块翻译 Mixin：将 Module 的 title 和 description 字段从 final 改为可变
 * 用于运行时替换模块名称和描述文本，实现中文化
 */
@Mixin(value = Module.class, remap = false)
public abstract class ModuleTranslationMixin implements com.example.addon.accessor.ModuleTranslationAccess {
    @Mutable @Shadow @Final public String title;
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
