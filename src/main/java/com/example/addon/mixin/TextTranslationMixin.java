package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VanillaTextRenderer.class, remap = false)
public abstract class TextTranslationMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private String yiyiaddon$translate(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }

    @ModifyVariable(
        method = "getWidth(Ljava/lang/String;IZ)D",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String yiyiaddon$translateWidth(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }

    @Redirect(
        method = "getWidth(Ljava/lang/String;IZ)D",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/String;substring(II)Ljava/lang/String;"
        )
    )
    private String yiyiaddon$substringWithinTranslatedText(String text, int beginIndex, int endIndex) {
        return text.substring(beginIndex, Math.min(endIndex, text.length()));
    }
}
