package com.example.addon.autochest.ui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.Minecraft;

/**
 * 通用确认弹窗：用于「清空点位 / 清除处理记录」等不可逆操作前的二次确认。
 *
 * <p>点「确认」执行回调并关闭；点「取消」直接关闭。避免误触造成数据丢失。</p>
 */
public final class ConfirmScreen extends WindowScreen {

    private final String message;
    private final Runnable onConfirm;

    public ConfirmScreen(GuiTheme theme, String title, String message, Runnable onConfirm) {
        super(theme, title);
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    public void initWidgets() {
        add(theme.label(message)).expandX();

        WTable buttons = add(theme.table()).expandX().widget();
        WButton confirm = buttons.add(theme.button("§c§l确认")).expandX().minWidth(120).widget();
        confirm.action = () -> {
            onConfirm.run();
            Minecraft.getInstance().setScreen(null);
        };
        WButton cancel = buttons.add(theme.button("§7取消")).expandX().minWidth(120).widget();
        cancel.action = () -> Minecraft.getInstance().setScreen(null);
    }
}
