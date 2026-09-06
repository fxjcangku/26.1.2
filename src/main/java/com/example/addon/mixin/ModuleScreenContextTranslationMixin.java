package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 模块设置界面上下文菜单翻译 Mixin
 *
 * 翻译 ModuleScreen 中的各种按钮和标签文本
 * 影响范围：单个模块的设置界面中的操作按钮和选项标签
 *
 * 注意：@ModifyConstant 的 stringValue 必须与源码字符串常量「精确匹配」（含尾随空格），
 * 否则注入检查失败导致启动崩溃（历史教训：26.1.2 中 "Toggle on bind release:" 带尾随空格）。
 */
@Mixin(value = ModuleScreen.class, remap = false)
public abstract class ModuleScreenContextTranslationMixin {

    /**
     * 翻译「重置」按钮（tooltip = "Reset"）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Reset"))
    private String yiyiaddon$translateReset(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译「绑定」分组标题（section = "Bind"）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Bind"))
    private String yiyiaddon$translateBind(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译「松开按键时切换」标签（label = "Toggle on bind release: "，注意尾随空格）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Toggle on bind release: "))
    private String yiyiaddon$translateToggleOnBindRelease(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译「聊天反馈」标签（label = "Chat Feedback: "，注意尾随空格）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Chat Feedback: "))
    private String yiyiaddon$translateChatFeedback(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译「激活」标签（label = "Active: "，注意尾随空格）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Active: "))
    private String yiyiaddon$translateActive(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译「复制配置」按钮（tooltip = "Copy config"）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Copy config"))
    private String yiyiaddon$translateCopyConfig(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }

    /**
     * 翻译「粘贴配置」按钮（tooltip = "Paste config"）
     */
    @ModifyConstant(method = "initWidgets()V", constant = @org.spongepowered.asm.mixin.injection.Constant(stringValue = "Paste config"))
    private String yiyiaddon$translatePasteConfig(String value) {
        return YiyiaddonTranslator.translateVisible(value);
    }
}
