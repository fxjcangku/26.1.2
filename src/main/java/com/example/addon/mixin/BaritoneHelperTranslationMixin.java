package com.example.addon.mixin;

import baritone.api.utils.Helper;
import com.example.addon.BaritoneChatTranslations;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Helper.class, remap = false)
public interface BaritoneHelperTranslationMixin {
    @ModifyVariable(
        method = "logDirect(Z[Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component[] yiyiaddon$translateChatComponents(Component[] components) {
        return BaritoneChatTranslations.translate(components);
    }

    @ModifyVariable(
        method = "logNotificationDirect(Ljava/lang/String;Z)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String yiyiaddon$translateNotification(String message) {
        return BaritoneChatTranslations.translate(message);
    }
}
