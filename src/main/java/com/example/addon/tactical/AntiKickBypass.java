package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 发包防踢模块
 * 
 * 全方位发包拦截与Masa伪装
 * 
 * @author yiyijia
 */
public class AntiKickBypass extends YiyiaddonModule {

    private final SettingGroup sgGeneral = settings.createGroup("基础功能");

    private final Setting<Boolean> antiAfk = sgGeneral.add(new BoolSetting.Builder()
        .name("防挂机检测")
        .description("后台发送假活跃包防止被踢")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatQueue = sgGeneral.add(new BoolSetting.Builder()
        .name("聊天缓冲池")
        .description("高频聊天送入队列，按间隔发送")
        .defaultValue(true)
        .build()
    );

    public AntiKickBypass() {
        super(CATEGORY_TACTICAL, "发包防踢", "全方位发包拦截，Masa伪装，聊天队列。开发中。");
    }

    @Override
    public void onActivate() {
        notify("发包防踢模块已启动（开发中）");
    }

    @Override
    public void onDeactivate() {
        notify("发包防踢模块已关闭");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l发包防踢 · 功能说明" },
            new String[]{
                "§e§l▌ 计划功能",
                "§f  1. §a§l防挂机检测§r§f - 自动发送活跃包",
                "§f  2. §a§l聊天缓冲池§r§f - 队列化聊天消息",
                "§f  3. §a§lMasa伪装§r§f - 隐藏Mod特征",
                "§f  4. §a§l过载断流§r§f - 智能限速"
            },
            new String[]{
                "§c§l▌ 开发中",
                "§f  功能将在后续版本实现"
            }
        );
    }
}
