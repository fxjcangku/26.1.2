package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = VanillaTextRenderer.class, remap = false)
public abstract class TextTranslationMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private String yiyiaddon$translate(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }
}
