package com.example.addon.mixin;

import com.example.addon.translations.MeteorCommandTranslations;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Meteor Component 级聊天输出翻译 Mixin
 *
 * 拦截 ChatUtils.sendMsg 最底层的 Component 重载，翻译组件文本。
 * 覆盖所有不经过字符串 format 分支、直接传 Component 的消息，
 * 例如 StashFinder 的「发现储藏点」、BetterChat 的坐标警告、Notifier 的进出提示等。
 */
@Mixin(value = ChatUtils.class, remap = false)
public abstract class MeteorComponentMessageMixin {

    /**
     * 翻译最底层 sendMsg(int, String, ChatFormatting, Component) 的 msg 参数
     *
     * 所有 sendMsg(Component) / sendMsg(String, Component) 最终都会收敛到此处，
     * 因此只需拦截这一个重载即可覆盖全部 Component 级输出。
     */
    @ModifyVariable(
        method = "sendMsg(ILjava/lang/String;Lnet/minecraft/ChatFormatting;Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private Component yiyiaddon$translateComponentMessage(Component msg) {
        return MeteorCommandTranslations.translateComponent(msg);
    }
}
