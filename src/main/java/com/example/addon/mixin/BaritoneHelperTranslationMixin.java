package com.example.addon.mixin;

import baritone.api.utils.Helper;
import com.example.addon.translations.BaritoneChatTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Helper.class, remap = false)
public interface BaritoneHelperTranslationMixin {
    @Inject(
        method = "getPrefix()Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void yiyiaddon$colorBaritonePrefix(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(Component.literal("[Baritone]").withStyle(style ->
            style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true)
        ));
    }

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
