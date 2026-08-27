package com.example.addon.mixin;

import meteordevelopment.meteorclient.systems.hud.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * HUD 自定义字体默认值 Mixin
 * 
 * 修改 Meteor HUD 系统的自定义字体默认开关
 * 将默认值从开启（1）改为关闭（0），避免中文显示问题
 * 
 * 影响范围：Meteor HUD 系统初始化时的字体设置
 * 原因：Meteor 的自定义字体不支持中文，默认关闭以正常显示中文
 */
@Mixin(value = Hud.class, remap = false)
public abstract class HudCustomFontDefaultMixin {
    
    /**
     * 将 HUD 自定义字体默认值改为关闭
     * 
     * 注入点：Hud 构造函数中的 intValue = 1 常量（第一个出现的）
     * 效果：用户首次启动时自定义字体默认关闭，使用原版字体以支持中文
     */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 1, ordinal = 0))
    private int yiyiaddon$disableCustomFontByDefault(int value) {
        return 0;
    }
}
