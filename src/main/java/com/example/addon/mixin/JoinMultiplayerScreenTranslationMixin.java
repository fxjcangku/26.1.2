package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = JoinMultiplayerScreen.class, priority = 1100)
public abstract class JoinMultiplayerScreenTranslationMixin {
    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
        ),
        index = 1,
        require = 0,
        remap = false
    )
    private String yiyiaddon$translateStatusText(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }
}
