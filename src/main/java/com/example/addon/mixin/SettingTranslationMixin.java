package com.example.addon.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Setting.class, remap = false)
public abstract class SettingTranslationMixin implements com.example.addon.accessor.SettingTranslationAccess {
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
