package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模块列表界面翻译 Mixin
 * 
 * 拦截 ModulesScreen 的初始化，批量翻译所有模块
 * 同时翻译界面顶部的搜索框和收藏夹标签
 * 
 * 影响范围：Meteor 客户端的模块列表主界面
 */
@Mixin(value = ModulesScreen.class, remap = false)
public abstract class ModulesScreenTranslationMixin {
    
    /**
     * 在界面初始化前批量本地化所有模块
     * 
     * 注入点：initWidgets 方法头部
     * 效果：将所有模块的名称和描述翻译为中文
     */
    @Inject(method = "initWidgets()V", at = @At("HEAD"))
    private void yiyiaddon$localizeModulesBeforeWidgets(CallbackInfo info) {
        for (Module module : Modules.get().getAll()) {
            YiyiaddonTranslator.localizeModule(module);
        }
    }

    /**
     * 翻译搜索框文本
     * 
     * 注入点：createSearch 方法中的 "Search" 字符串常量
     */
    @ModifyConstant(method = "createSearch", constant = @Constant(stringValue = "Search"))
    private String yiyiaddon$translateSearch(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译收藏夹标签
     * 
     * 注入点：createFavorites 方法中的 "Favorites" 字符串常量
     */
    @ModifyConstant(method = "createFavorites", constant = @Constant(stringValue = "Favorites"))
    private String yiyiaddon$translateFavorites(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }
}
