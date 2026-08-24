package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;

import static com.example.addon.tactical.TacticalCategory.TACTICAL;

/**
 * 飞行绕过模块
 * 
 * 四种飞行模式绕过反作弊
 * 
 * @author yiyijia
 */
public class FlightBypass extends YiyiaddonModule {

    private final SettingGroup sgMode = settings.createGroup("模式选择");

    private final Setting<FlightMode> mode = sgMode.add(new EnumSetting.Builder<FlightMode>()
        .name("飞行模式")
        .description("选择绕过策略")
        .defaultValue(FlightMode.VANILLA_MIMIC)
        .build()
    );

    public FlightBypass() {
        super(TACTICAL, "飞行绕过", "四种模式绕过 GrimAC/Matrix 等顶级反作弊。开发中。");
    }

    @Override
    public void onActivate() {
        notify("飞行绕过模块已启动（开发中）");
    }

    @Override
    public void onDeactivate() {
        notify("飞行绕过模块已关闭");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l飞行绕过 · 功能说明" },
            new String[]{
                "§e§l▌ 计划模式",
                "§f  1. §a§l原版模拟§r§f - 高频跳跃伪装",
                "§f  2. §a§l安全滑翔§r§f - 微下降规避检测",
                "§f  3. §a§l烟花加速§r§f - 模拟鞘翅飞行",
                "§f  4. §a§l序列垫脚§r§f - 预测方块放置"
            },
            new String[]{
                "§c§l▌ 开发中",
                "§f  功能将在后续版本实现"
            }
        );
    }

    public enum FlightMode {
        VANILLA_MIMIC("原版模拟"),
        SAFE_GLIDE("安全滑翔"),
        FIREWORK_BOOST("烟花加速"),
        SEQUENCE_SCAFFOLD("序列垫脚");

        public final String displayName;

        FlightMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
