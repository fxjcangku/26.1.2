package com.example.addon.itemid;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标物品多选界面。
 *
 * <p>左侧为未选中的 ID、右侧为已选中的 ID，数据源唯一来自 {@link ItemIdManager}，
 * 支持顶部按显示名 / 物品 ID 过滤。选中状态只存身份键，运行时回查完整身份。</p>
 */
public final class ItemTargetSelectScreen extends WindowScreen {

    private final ItemTargetSetting setting;
    private final ItemIdManager idManager;
    private WTable table;
    private String filter = "";

    public ItemTargetSelectScreen(GuiTheme theme, ItemTargetSetting setting) {
        super(theme, setting.title);
        this.setting = setting;
        this.idManager = setting.idManager;
    }

    @Override
    public void initWidgets() {
        WTextBox search = add(theme.textBox("")).minWidth(400).expandX().widget();
        search.setFocused(true);
        search.action = () -> {
            filter = search.get().trim().toLowerCase();
            rebuild();
        };
        table = add(theme.table()).expandX().widget();
        rebuild();
    }

    private void rebuild() {
        table.clear();
        WTable available = table.add(theme.table()).top().expandX().widget();
        table.add(theme.verticalSeparator()).expandWidgetY();
        WTable selected = table.add(theme.table()).top().expandX().widget();

        // 左侧：未选中且通过过滤的 ID，按原版 / 自定义分类
        available.add(theme.label("§a§l▌ 原版物品")).expandX();
        available.row();
        boolean hasVanilla = addAvailableByType(available, true);
        if (!hasVanilla) {
            available.add(theme.label("  §8无")).expandX();
            available.row();
        }

        available.add(theme.label("§d§l▌ 自定义物品")).expandX();
        available.row();
        boolean hasCustom = addAvailableByType(available, false);
        if (!hasCustom) {
            available.add(theme.label("  §8无")).expandX();
            available.row();
        }

        // 右侧：已选中且仍有效的 ID
        for (String key : new ArrayList<>(setting.get())) {
            ItemIdentity id = idManager.findByKey(key);
            if (id == null || !matches(id)) continue;
            selected.add(theme.label("§a" + id.displayName())).expandX();
            WMinus remove = selected.add(theme.minus()).right().widget();
            remove.action = () -> setSelected(key, false);
            selected.row();
        }
    }

    /** 在可用表中添加指定类型（原版/自定义）的未选中条目，返回是否有条目 */
    private boolean addAvailableByType(WTable available, boolean vanilla) {
        boolean has = false;
        for (ItemIdentity id : idManager.all()) {
            if (id.isVanilla() != vanilla) continue;
            String key = id.identityKey();
            if (setting.get().contains(key) || !matches(id)) continue;
            available.add(theme.label("§a" + id.displayName())).expandX();
            WPlus add = available.add(theme.plus()).right().widget();
            add.action = () -> setSelected(key, true);
            available.row();
            has = true;
        }
        return has;
    }

    /** 按显示名或物品 ID 做关键词过滤 */
    private boolean matches(ItemIdentity id) {
        if (filter.isEmpty()) return true;
        return id.displayName().toLowerCase().contains(filter)
            || id.itemId().toLowerCase().contains(filter);
    }

    private void setSelected(String key, boolean selected) {
        List<String> values = new ArrayList<>(setting.get());
        if (selected) {
            if (!values.contains(key)) values.add(key);
        } else {
            values.remove(key);
        }
        setting.set(values);
        setting.refreshCount();
        rebuild();
    }
}
