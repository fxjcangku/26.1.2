package com.example.addon.hud;

import com.example.addon.core.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * HUD 示例元素
 * 
 * 在屏幕上显示自定义信息
 */
public class HudExample extends HudElement {
    
    /**
     * HUD 元素信息
     * 
     * @param group HUD 分组
     * @param name 元素名称（必须使用 kebab-case 格式：小写+连字符）
     * @param description 元素描述
     * @param factory 元素工厂方法
     */
    public static final HudElementInfo<HudExample> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, 
        "example", 
        "HUD element example.", 
        HudExample::new
    );

    public HudExample() {
        super(INFO);
    }

    /**
     * 渲染 HUD 元素
     * 每帧调用一次
     * 
     * @param renderer HUD 渲染器
     */
    @Override
    public void render(HudRenderer renderer) {
        // 计算并设置元素尺寸
        setSize(renderer.textWidth("Example element", true), renderer.textHeight(true));

        // 渲染背景矩形
        renderer.quad(x, y, getWidth(), getHeight(), Color.LIGHT_GRAY);

        // 渲染文本
        renderer.text("Example element", x, y, Color.WHITE, true);
    }
}
