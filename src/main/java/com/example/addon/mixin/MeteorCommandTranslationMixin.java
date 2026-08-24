package com.example.addon.mixin;

import com.example.addon.MeteorCommandTranslations;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.HelpCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = HelpCommand.class, remap = false)
public abstract class MeteorCommandTranslationMixin {
    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getDescription()Ljava/lang/String;")
    )
    private String yiyiaddon$translateHelpDescription(Command command) {
        return MeteorCommandTranslations.translate(command.getName(), command.getDescription());
    }
}
