package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模块设置界面翻译 Mixin
 * 
 * 拦截单个模块设置界面的初始化
 * 在界面构建前将模块及其设置项翻译为中文
 * 
 * 影响范围：点击模块名称后进入的详细设置界面
 */
@Mixin(value = ModuleScreen.class, remap = false)
public abstract class ModuleScreenTranslationMixin {
    
    /** 当前正在配置的模块 */
    @Shadow private meteordevelopment.meteorclient.systems.modules.Module module;

    /**
     * 在界面初始化时本地化模块
     * 
     * 注入点：initWidgets 方法头部
     * 效果：将模块名称、描述以及所有设置项翻译为中文
     */
    @Inject(method = "initWidgets()V", at = @At("HEAD"))
    private void yiyiaddon$localize(CallbackInfo info) {
        YiyiaddonTranslator.localizeModule(module);
    }
}
