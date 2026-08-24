package com.example.addon.mixin;

import meteordevelopment.meteorclient.settings.SettingGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SettingGroup.class, remap = false)
public abstract class SettingGroupTranslationMixin implements com.example.addon.accessor.SettingGroupTranslationAccess {
    @Mutable @Shadow @Final public String name;

    @Override
    public void yiyiaddon$setName(String value) {
        name = value;
    }
}
