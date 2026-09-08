package com.example.addon.autochest.ui;

import com.example.addon.autochest.config.ItemQuantitySetting;
import com.example.addon.itemid.ItemIdentity;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;

import java.util.List;

/**
 * 每种目标物品数量配置界面。
 *
 * <p>列出目标物品选择器当前选中的物品，每一项附带数量输入框；数量含义为
 * 「玩家最终想持有的该物品总数」（如钻石 64），取物时只补差额。</p>
 */
public final class ItemQuantityScreen extends WindowScreen {

    /** 单种物品数量的上限（36 格 × 64 个） */
    private static final int MAX_COUNT = 36 * 64;

    private final ItemQuantitySetting setting;
    private WTable table;

    public ItemQuantityScreen(GuiTheme theme, ItemQuantitySetting setting) {
        super(theme, setting.title);
        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        add(theme.label("§7为每种目标物品设置目标数量（已有部分只补差额）")).expandX();
        table = add(theme.table()).expandX().widget();
        rebuild();
    }

    private void rebuild() {
        table.clear();
        List<ItemIdentity> targets = setting.targetSetting().selectedIdentities();
        if (targets.isEmpty()) {
            table.add(theme.label("§8尚未选择目标物品，请先在上方「目标物品」中添加")).expandX();
            table.row();
            return;
        }

        for (ItemIdentity id : targets) {
            String key = id.identityKey();
            table.add(theme.label("§a" + id.displayName())).expandX();
            WIntEdit edit = table.add(theme.intEdit(setting.quantityOf(key), 1, MAX_COUNT, 1, 64, true)).expandX().widget();
            edit.action = () -> setting.setQuantity(key, edit.get());
            table.row();
        }
    }
}
