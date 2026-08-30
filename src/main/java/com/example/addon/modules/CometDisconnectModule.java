package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;

/**
 * 自动断线模块
 *
 * 应急保命：检测到管理员 / 危险玩家接近时立即断开服务器连接，躲避被抓现行。
 * 提供静态断线入口供「自动挖矿防管理员」等模块直接复用，也支持手动开启即断线。
 */
public final class CometDisconnectModule extends YiyiaddonModule {

    public CometDisconnectModule() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动断线",
            "应急断开服务器连接躲避管理员视察。开启立即断线，亦可被自动挖矿防管理员自动调用。点击按钮查看说明。");
    }

    /**
     * 开启即断线：手动触发一次后自动关闭，避免停留在开启态下次进服误触。
     */
    @Override
    public void onActivate() {
        // 单人/未进服时无连接可断，直接关掉
        if (mc.player == null || mc.player.connection == null) {
            chatFeedback = false;
            toggle();
            chatFeedback = true;
            return;
        }
        disconnect("手动触发自动断线");
        chatFeedback = false;
        toggle();
        chatFeedback = true;
    }

    /**
     * 静态断线入口：自动挖矿等模块检测到危险玩家时直接调用。
     * 断线前会强制关闭「自动重连」，否则刚断就重连回去等于白断。
     *
     * @param reason 断开界面显示的原因
     */
    public static void disconnect(String reason) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.player.connection == null) return;

        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect.isActive()) autoReconnect.toggle();

        MutableComponent text = Component.literal("§c§l[yiyiaddon]§r §f自动断线 §8▸ §c" + reason);
        MeteorClient.mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(text));
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            // 手动断线按钮：点击立即断开服务器连接
            WButton btn = theme.button("§c立即断线");
            btn.action = () -> disconnect("手动触发自动断线");
            table.add(btn).expandX().minWidth(200);
            table.row();
        },
        new String[]{"§l自动断线 · 使用说明"},
        new String[]{
            "§e§l▌ 功能",
            "§f  · 应急断开服务器连接，用于躲避管理员视察 / 封禁。",
            "§f  · 开启本模块即立即断线一次，断线后自动关闭。",
            "§f  · 自动挖矿的「防管理员」开关检测到危险玩家时会自动调用本模块。"
        },
        new String[]{
            "§c§l▌ 注意",
            "§c⚠ 断线时会强制关闭「自动重连」，避免刚断又连回去。",
            "§c⚠ 断线只退出服务器，不保证完全不被记录，请结合隐身 / 伪装使用。"
        });
    }
}
