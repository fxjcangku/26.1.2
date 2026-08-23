package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Button.Builder.class)
public abstract class ButtonBuilderTranslationMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Component yiyiaddon$translateMeteorMultiplayerButtons(Component text) {
        String original = text.getString();
        if (!original.equals("Proxies") && !original.equals("Accounts")) return text;
        return Component.literal(YiyiaddonTranslator.translateVisible(original));
    }
}
