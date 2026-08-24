package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
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
                "§f  · 首次安装默认开启简体中文汉化。",
                "§f  · 关闭本模块后恢复原始英文界面。",
                "§f  · 已保存的开关状态会在下次启动时保留。"
            },
            new String[]{
                "§a§l▌ 汉化范围",
                "§f  · Meteor Client 界面、模块、设置和悬停说明。",
                "§f  · Baritone 界面、设置、命令和帮助文本。"
            },
            new String[]{
                "§b§l▌ 字体设置",
                "§f  · 配置与 HUD 的自定义字体首次默认关闭。",
                "§f  · 可避免中文字体错位，无需每次手动关闭。",
                "§f  · 以后手动修改的字体开关会正常保存。"
            },
            new String[]{
                "§d§l▌ 说明",
                "§f  · 中文资源已整合进 yiyiaddon。",
                "§f  · Baritone 已整合，无需单独安装。"
            }
        );
    }
}
