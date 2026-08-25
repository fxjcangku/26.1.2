package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleToggleMessageMixin {
    @Shadow public abstract boolean isActive();
    @Shadow public String title;

    @Inject(method = "sendToggledMsg", at = @At("HEAD"), cancellable = true)
    private void yiyiaddon$translateToggleMessage(CallbackInfo ci) {
        if (!YiyiaddonTranslator.enabled()) return;

        String localizedTitle = YiyiaddonTranslator.translateVisible(title);
        String coloredTitle = "§b§l" + localizedTitle;
        String status = isActive() ? "§a已开启" : "§c已关闭";
        ChatUtils.sendMsg(coloredTitle, Component.literal("%s §f%s§7.".formatted(status, localizedTitle)));
        ci.cancel();
    }
}
