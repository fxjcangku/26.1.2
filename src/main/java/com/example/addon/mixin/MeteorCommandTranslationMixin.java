package com.example.addon.mixin;

import com.example.addon.translations.MeteorCommandTranslations;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.HelpCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Meteor Help 指令翻译 Mixin
 * 
 * 拦截 HelpCommand（.help 指令）的输出内容
 * 将帮助信息中的指令名、描述、别名和标签翻译为中文
 * 
 * 影响范围：使用 .help 查看指令帮助时的输出
 */
@Mixin(value = HelpCommand.class, remap = false)
public abstract class MeteorCommandTranslationMixin {
    
    /**
     * 翻译帮助界面中的指令描述
     * 
     * 注入点：showHelp 方法中的 getDescription() 调用
     */
    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getDescription()Ljava/lang/String;")
    )
    private String yiyiaddon$translateHelpDescription(Command command) {
        return MeteorCommandTranslations.translate(command.getName(), command.getDescription());
    }

    /**
     * 翻译帮助界面中的指令名称
     * 
     * 注入点：showHelp 方法中的 getName() 调用
     */
    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getName()Ljava/lang/String;")
    )
    private String yiyiaddon$translateHelpCommandName(Command command) {
        return MeteorCommandTranslations.translateCommandName(command.getName());
    }

    /**
     * 翻译帮助界面中的指令别名列表
     * 
     * 注入点：showHelp 方法中的 getAliases() 调用
     * 效果：将英文别名转换为中文别名
     */
    @Redirect(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getAliases()Ljava/util/List;")
    )
    private List<String> yiyiaddon$translateHelpAliases(Command command) {
        return MeteorCommandTranslations.translateAliases(command);
    }

    /**
     * 翻译帮助界面中的标签文本
     * 
     * 注入点：showHelp 和 getUsageText 方法中的 Component.literal 调用
     * 效果：将 "Usage:"、"Aliases:" 等标签翻译为中文
     */
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

    /**
     * 翻译用法文本中的指令名称
     * 
     * 注入点：getUsageText 方法中的 getName() 调用
     */
    @Redirect(
        method = "getUsageText(Lmeteordevelopment/meteorclient/commands/Command;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getName()Ljava/lang/String;")
    )
    private String yiyiaddon$translateUsageCommandName(Command command) {
        return MeteorCommandTranslations.translateCommandName(command.getName());
    }
}
