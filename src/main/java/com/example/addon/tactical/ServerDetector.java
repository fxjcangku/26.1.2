package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 服务器检测模块
 * 
 * 服务器核心/反作弊侦测与资源包劫持
 * 
 * @author yiyijia
 */
public class ServerDetector extends YiyiaddonModule {

    private final SettingGroup sgDetection = settings.createGroup("底裤侦测");

    private final Setting<Boolean> detectCore = sgDetection.add(new BoolSetting.Builder()
        .name("检测服务器核心")
        .description("识别 Paper/Purpur/Leaves 等核心类型")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectAntiCheat = sgDetection.add(new BoolSetting.Builder()
        .name("检测反作弊")
        .description("嗅探 GrimAC/Matrix 等反作弊指纹")
        .defaultValue(true)
        .build()
    );

    public ServerDetector() {
        super(CATEGORY_TACTICAL, "服务器检测", "服务器核心/反作弊侦测，资源包自动下载。开发中。");
    }

    @Override
    public void onActivate() {
        notify("服务器检测模块已启动（开发中）");
    }

    @Override
    public void onDeactivate() {
        notify("服务器检测模块已关闭");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l服务器检测 · 功能说明" },
            new String[]{
                "§e§l▌ 计划功能",
                "§f  1. §a§l底裤侦测§r§f - 识别服务器核心类型",
                "§f  2. §a§l反作弊检测§r§f - 嗅探反作弊指纹",
                "§f  3. §a§l资源包劫持§r§f - 自动下载资源包",
                "§f  4. §a§l智能播报§r§f - 中文公屏提示"
            },
            new String[]{
                "§c§l▌ 开发中",
                "§f  功能将在后续版本实现"
            }
        );
    }
}
