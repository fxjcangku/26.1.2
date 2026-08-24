package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.commands.commands.HelpCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = HelpCommand.class, remap = false)
public abstract class MeteorHelpCommandTranslationMixin {
    @ModifyConstant(method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V", constant = @Constant(stringValue = "Help for"))
    private String yiyiaddon$translateHelpFor(String value) {
        return YiyiaddonTranslator.enabled() ? "命令帮助：" : value;
    }

    @ModifyConstant(method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V", constant = @Constant(stringValue = "Description:"))
    private String yiyiaddon$translateDescription(String value) {
        return YiyiaddonTranslator.enabled() ? "说明：" : value;
    }

    @ModifyConstant(method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V", constant = @Constant(stringValue = "Aliases:"))
    private String yiyiaddon$translateAliases(String value) {
        return YiyiaddonTranslator.enabled() ? "别名：" : value;
    }

    @ModifyConstant(method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V", constant = @Constant(stringValue = "\n Usage:"))
    private String yiyiaddon$translateUsage(String value) {
        return YiyiaddonTranslator.enabled() ? "\n 用法：" : value;
    }
}
