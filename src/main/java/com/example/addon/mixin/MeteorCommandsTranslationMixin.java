package com.example.addon.mixin;

import com.example.addon.MeteorCommandTranslations;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.CommandsCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CommandsCommand.class, remap = false)
public abstract class MeteorCommandsTranslationMixin {
    @Redirect(
        method = "getCommandText(Lmeteordevelopment/meteorclient/commands/Command;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getDescription()Ljava/lang/String;")
    )
    private String yiyiaddon$translateCommandDescription(Command command) {
        return MeteorCommandTranslations.translate(command.getName(), command.getDescription());
    }
}
