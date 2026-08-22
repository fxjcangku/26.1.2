package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;

public final class YiyiaddonTranslationModule extends YiyiaddonModule {
    public final Setting<Boolean> simplifiedChinese = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("简体中文")
        .description("启用简体中文界面汉化。")
        .defaultValue(true)
        .build()
    );

    public YiyiaddonTranslationModule() {
        super(AddonTemplate.CATEGORY, "界面汉化", "控制 Meteor Client 和 Baritone 的简体中文界面汉化。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l界面汉化 · 使用说明" },
            new String[]{
                "§e§l▌ 使用方法",
                "§f  · 默认开启简体中文汉化。",
                "§f  · 关闭模块后恢复原始界面文字。"
            },
            new String[]{
                "§a§l▌ 汉化范围",
                "§f  · Meteor Client 界面、模块和设置。",
                "§f  · Baritone 界面和设置。"
            },
            new String[]{
                "§b§l▌ 说明",
                "§f  · 中文资源已整合进 yiyiaddon。",
                "§f  · Baritone 已整合，无需单独安装。"
            }
        );
    }
}
