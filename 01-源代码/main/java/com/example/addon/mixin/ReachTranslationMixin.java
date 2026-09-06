package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Reach 模块提示文本翻译 Mixin
 * 
 * 翻译 Reach（交互距离）模块设置界面的提示文本
 * 影响范围：Reach 模块配置界面中关于原版服务器交互距离的说明文字
 */
@Mixin(value = Reach.class, remap = false)
public abstract class ReachTranslationMixin {
    
    /**
     * 翻译 Reach 模块的提示说明
     * 
     * 注入点：getWidget 方法中的字符串常量
     * 效果：将关于原版服务器交互距离的英文说明转换为中文
     * 
     * 原文：Note: on vanilla servers you may give yourself up to 4 blocks...
     */
    @ModifyConstant(
        method = "getWidget(Lmeteordevelopment/meteorclient/gui/GuiTheme;)Lmeteordevelopment/meteorclient/gui/widgets/WWidget;",
        constant = @Constant(stringValue = "Note: on vanilla servers you may give yourself up to 4 blocks of additional reach for specific actions - interacting with block entities (chests, furnaces, etc.) or with vehicles. This does not work on paper servers.")
    )
    private String yiyiaddon$translateReachNote(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }
}
