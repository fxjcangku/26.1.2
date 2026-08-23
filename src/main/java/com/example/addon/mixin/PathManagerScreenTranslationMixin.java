package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import meteordevelopment.meteorclient.pathing.PathManagers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meteordevelopment.meteorclient.gui.tabs.builtin.PathManagerTab$PathManagerScreen", remap = false)
public abstract class PathManagerScreenTranslationMixin {
    @Inject(method = "initWidgets()V", at = @At("HEAD"))
    private void yiyiaddon$localizeBaritoneSettings(CallbackInfo info) {
        YiyiaddonTranslator.localizeBaritoneSettings(PathManagers.get().getSettings().get());
    }
}
