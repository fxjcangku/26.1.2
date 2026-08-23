package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ModuleScreen.class, remap = false)
public abstract class ModuleScreenContextTranslationMixin {
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Reset"))
    private String yiyiaddon$translateReset(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Toggle on bind release:"))
    private String yiyiaddon$translateToggleOnBindRelease(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Chat Feedback:"))
    private String yiyiaddon$translateChatFeedback(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Active:"))
    private String yiyiaddon$translateActive(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Copy config"))
    private String yiyiaddon$translateCopyConfig(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Paste config"))
    private String yiyiaddon$translatePasteConfig(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }
}
