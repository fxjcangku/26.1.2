package com.example.addon.hud;

import com.example.addon.core.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HudExample extends HudElement {
    /**
     * name 参数必须使用 kebab-case 格式（小写+连字符）
     */
    public static final HudElementInfo<HudExample> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "example", "HUD element example.", HudExample::new);

    public HudExample() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("Example element", true), renderer.textHeight(true));

        // 渲染背景
        renderer.quad(x, y, getWidth(), getHeight(), Color.LIGHT_GRAY);

        // 渲染文本
        renderer.text("Example element", x, y, Color.WHITE, true);
    }
}
