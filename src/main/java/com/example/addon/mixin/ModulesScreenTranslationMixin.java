package com.example.addon.mixin;

import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ModulesScreen.class)
public abstract class ModulesScreenTranslationMixin {
    @ModifyConstant(method = "createSearch", constant = @Constant(stringValue = "Search"))
    private String yiyiaddon$translateSearch(String value) {
        return "搜索";
    }

    @ModifyConstant(method = "createFavorites", constant = @Constant(stringValue = "Favorites"))
    private String yiyiaddon$translateFavorites(String value) {
        return "收藏";
    }
}
