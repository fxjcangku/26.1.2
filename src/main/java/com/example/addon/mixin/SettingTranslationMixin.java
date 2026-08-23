package com.example.addon.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Setting.class, remap = false)
public abstract class SettingTranslationMixin {
}
