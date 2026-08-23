package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleTranslationMixin {
}
