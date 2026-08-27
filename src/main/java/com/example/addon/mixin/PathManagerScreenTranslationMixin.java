package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.pathing.PathManagers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 路径管理器界面翻译 Mixin
 * 
 * 拦截 PathManagerTab 内部的 PathManagerScreen 界面初始化
 * 在界面构建前将 Baritone 设置项翻译为中文
 * 
 * 影响范围：Meteor 客户端的路径管理标签页中显示的 Baritone 设置
 */
@Mixin(targets = "meteordevelopment.meteorclient.gui.tabs.builtin.PathManagerTab$PathManagerScreen", remap = false)
public abstract class PathManagerScreenTranslationMixin {
    
    /**
     * 在界面初始化时本地化 Baritone 设置
     * 
     * 注入点：initWidgets 方法头部
     * 效果：将路径管理器中的 Baritone 设置项名称和描述替换为中文
     */
    @Inject(method = "initWidgets()V", at = @At("HEAD"))
    private void yiyiaddon$localizeBaritoneSettings(CallbackInfo info) {
        YiyiaddonTranslator.localizeBaritoneSettings(PathManagers.get().getSettings().get());
    }
}
