package com.example.addon.mixin;

import com.example.addon.translations.MeteorCommandTranslations;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.CommandsCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Meteor 指令列表翻译 Mixin
 * 
 * 拦截 CommandsCommand（.commands 指令）的输出
 * 将指令列表中的指令名称和描述翻译为中文
 * 
 * 影响范围：使用 .commands 查看所有指令列表时的输出
 */
@Mixin(value = CommandsCommand.class, remap = false)
public abstract class MeteorCommandsTranslationMixin {
    
    /**
     * 翻译指令列表中的指令名称
     * 
     * 注入点：getCommandText 方法中的 getName() 调用
     * 效果：将英文指令名替换为中文名（如 help -> 帮助）
     */
    @Redirect(
        method = "getCommandText(Lmeteordevelopment/meteorclient/commands/Command;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getName()Ljava/lang/String;")
    )
    private String yiyiaddon$translateCommandName(Command command) {
        return MeteorCommandTranslations.translateCommandName(command.getName());
    }

    /**
     * 翻译指令列表中的指令描述
     * 
     * 注入点：getCommandText 方法中的 getDescription() 调用
     * 效果：将英文描述替换为中文描述
     */
    @Redirect(
        method = "getCommandText(Lmeteordevelopment/meteorclient/commands/Command;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/commands/Command;getDescription()Ljava/lang/String;")
    )
    private String yiyiaddon$translateCommandDescription(Command command) {
        return MeteorCommandTranslations.translate(command.getName(), command.getDescription());
    }
}
