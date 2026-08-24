package com.example.addon.mixin;

import com.example.addon.translations.MeteorCommandTranslations;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.HelpCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = HelpCommand.class, remap = false)
public abstract class MeteorCommandTranslationMixin {
    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getDescription()Ljava/lang/String;")
    )
    private String yiyiaddon$translateHelpDescription(Command command) {
        return MeteorCommandTranslations.translate(command.getName(), command.getDescription());
    }

    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getName()Ljava/lang/String;")
    )
    private String yiyiaddon$translateHelpCommandName(Command command) {
        return MeteorCommandTranslations.translateCommandName(command.getName());
    }

    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getAliases()Ljava/util/List;")
    )
    private List<String> yiyiaddon$translateHelpAliases(Command command) {
        return MeteorCommandTranslations.translateAliases(command);
    }

    @ModifyArg(
        method = {
            "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
            "getUsageText(Lmeteordevelopment/meteorclient/commands/Command;)Lnet/minecraft/network/chat/MutableComponent;"
        },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;")
    )
    private String yiyiaddon$translateHelpLabel(String label) {
        return MeteorCommandTranslations.translateHelpLabel(label);
    }

    @Redirect(
        method = "getUsageText(Lmeteordevelopment/meteorclient/commands/Command;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getName()Ljava/lang/String;")
    )
    private String yiyiaddon$translateUsageCommandName(Command command) {
        return MeteorCommandTranslations.translateCommandName(command.getName());
    }
}
