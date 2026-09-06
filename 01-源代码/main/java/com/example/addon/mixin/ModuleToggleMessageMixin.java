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

/**
 * 模块切换消息翻译 Mixin
 * 
 * 拦截模块开启/关闭时的聊天提示消息
 * 将英文提示替换为中文，并美化消息格式
 * 
 * 影响范围：所有模块通过快捷键或点击开关时的聊天反馈
 */
@Mixin(value = Module.class, remap = false)
public abstract class ModuleToggleMessageMixin {
    
    /** 获取模块当前激活状态 */
    @Shadow public abstract boolean isActive();
    
    /** 模块标题 */
    @Shadow public String title;

    /**
     * 翻译并美化模块切换消息
     * 
     * 注入点：sendToggledMsg 方法头部
     * 效果：显示中文化的开启/关闭提示，格式为"[模块名] 已开启/已关闭 模块名."
     * 
     * @param ci 回调信息，用于取消原版消息
     */
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
