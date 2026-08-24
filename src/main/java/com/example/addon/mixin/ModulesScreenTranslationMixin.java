package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModulesScreen.class, remap = false)
public abstract class ModulesScreenTranslationMixin {
    @Inject(method = "initWidgets()V", at = @At("HEAD"))
    private void yiyiaddon$localizeModulesBeforeWidgets(CallbackInfo info) {
        for (Module module : Modules.get().getAll()) {
            YiyiaddonTranslator.localizeModule(module);
        }
    }

    @ModifyConstant(method = "createSearch", constant = @Constant(stringValue = "Search"))
    private String yiyiaddon$translateSearch(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    @ModifyConstant(method = "createFavorites", constant = @Constant(stringValue = "Favorites"))
    private String yiyiaddon$translateFavorites(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }
}
