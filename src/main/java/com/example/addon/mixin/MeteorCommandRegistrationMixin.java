package com.example.addon.mixin;

import com.example.addon.translations.MeteorCommandTranslations;
import com.example.addon.translations.YiyiaddonTranslator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 为 Meteor 命令注册中文 Brigadier 节点。
 * 中文节点与英文节点共用同一命令构建逻辑，因此执行和 Tab 补全保持一致。
 */
@Mixin(value = { Command.class, ChatUtils.class }, remap = false)
public abstract class MeteorCommandRegistrationMixin {
    @Inject(
        method = "registerTo(Lcom/mojang/brigadier/CommandDispatcher;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void yiyiaddon$registerChineseCommandNames(
        CommandDispatcher<ClientSuggestionProvider> dispatcher,
        CallbackInfo ci
    ) {
        if (!YiyiaddonTranslator.enabled()) return;

        Command command = (Command) (Object) this;
        for (String chineseName : MeteorCommandTranslations.getChineseNames(command)) {
            command.register(dispatcher, chineseName);
            CommandNode<ClientSuggestionProvider> root = dispatcher.getRoot().getChild(chineseName);
            if (root != null) yiyiaddon$registerChineseSubcommands(root);
        }
    }

    @SuppressWarnings("unchecked")
    private static void yiyiaddon$registerChineseSubcommands(CommandNode<ClientSuggestionProvider> parent) {
        for (CommandNode<ClientSuggestionProvider> child : List.copyOf(parent.getChildren())) {
            if (child instanceof LiteralCommandNode<ClientSuggestionProvider> literal) {
                String chineseName = MeteorCommandTranslations.translateSubcommandName(literal.getLiteral());
                if (!chineseName.equals(literal.getLiteral()) && parent.getChild(chineseName) == null) {
                    LiteralArgumentBuilder<ClientSuggestionProvider> alias = LiteralArgumentBuilder.<ClientSuggestionProvider>literal(chineseName)
                        .requires(literal.getRequirement())
                        .redirect(literal);
                    parent.addChild(alias.build());
                }
            }
            yiyiaddon$registerChineseSubcommands(child);
        }
    }

    @ModifyVariable(
        method = "sendMsg(ILjava/lang/String;Lnet/minecraft/ChatFormatting;Lnet/minecraft/ChatFormatting;Ljava/lang/String;[Ljava/lang/Object;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1,
        require = 0
    )
    private static String yiyiaddon$translateMeteorChatOutput(String message) {
        return MeteorCommandTranslations.translateChatMessage(message);
    }

    @Inject(
        method = "getPrefix()Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private static void yiyiaddon$colorMeteorPrefix(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(Component.literal("[Meteor]").withStyle(style ->
            style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true)
        ));
    }
}
