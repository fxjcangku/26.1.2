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
 * Meteor 指令注册与聊天输出翻译 Mixin
 * 
 * 功能1：为 Meteor 命令注册中文 Brigadier 节点
 * 功能2：翻译 Meteor 聊天消息输出
 * 功能3：美化 Meteor 消息前缀
 * 
 * 中文节点与英文节点共用同一命令构建逻辑，因此执行和 Tab 补全保持一致
 * 影响范围：所有 Meteor 指令的中文输入支持和聊天消息
 */
@Mixin(value = { Command.class, ChatUtils.class }, remap = false)
public abstract class MeteorCommandRegistrationMixin {
    
    /**
     * 为每个 Meteor 指令注册中文命令节点
     * 
     * 注入点：Command.registerTo 方法尾部
     * 效果：在英文命令注册后，额外注册中文别名，支持中文输入
     * 
     * @param dispatcher Brigadier 命令调度器
     * @param ci 回调信息
     */
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

    /**
     * 递归注册中文子命令节点
     * 
     * 遍历命令树，为每个英文子命令创建对应的中文别名节点
     * 中文节点通过 redirect 指向英文节点，共享执行逻辑
     * 
     * @param parent 父命令节点
     */
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

    /**
     * 翻译 Meteor 聊天消息内容
     * 
     * 注入点：ChatUtils.sendMsg 方法的 message 参数
     * 效果：将英文消息转换为中文后输出
     * 
     * @param message 原始消息文本
     * @return 翻译后的消息文本
     */
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

    /**
     * 美化 Meteor 消息前缀
     * 
     * 注入点：ChatUtils.getPrefix 方法返回值
     * 效果：将前缀改为亮紫色加粗样式，与 Baritone 统一
     * 
     * 原始：[Meteor] （白色）
     * 修改：[Meteor] （亮紫色+加粗）
     */
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
