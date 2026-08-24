package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModuleScreen.class, remap = false)
public abstract class ModuleScreenTranslationMixin {
    @Shadow private meteordevelopment.meteorclient.systems.modules.Module module;

    @Inject(method = "initWidgets()V", at = @At("HEAD"))
    private void yiyiaddon$localize(CallbackInfo info) {
        YiyiaddonTranslator.localizeModule(module);
    }
}
