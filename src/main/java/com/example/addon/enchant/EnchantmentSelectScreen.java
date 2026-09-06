package com.example.addon.enchant;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔目标多选界面。
 * 左侧为未选中的词条，右侧为已选中的词条，支持顶部关键词过滤。
 */
public final class EnchantmentSelectScreen extends WindowScreen {
    private final EnchantmentSelectSetting setting;
    private WTable table;
    private String filter = "";

    public EnchantmentSelectScreen(GuiTheme theme, EnchantmentSelectSetting setting) {
        super(theme, setting.title);
        this.setting = setting;
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

        for (String option : setting.options) {
            if (setting.get().contains(option) || !matches(option)) continue;
            available.add(theme.label(option));
            WPlus add = available.add(theme.plus()).right().widget();
            add.action = () -> setSelected(option, true);
            available.row();
        }

        for (String option : setting.options) {
            if (!setting.get().contains(option) || !matches(option)) continue;
            selected.add(theme.label(option));
            WMinus remove = selected.add(theme.minus()).right().widget();
            remove.action = () -> setSelected(option, false);
            selected.row();
        }
    }

    private boolean matches(String option) {
        return filter.isEmpty() || option.toLowerCase().contains(filter);
    }

    private void setSelected(String option, boolean selected) {
        List<String> values = new ArrayList<>(setting.get());
        if (selected) {
            if (!values.contains(option)) values.add(option);
        } else {
            values.remove(option);
        }
        setting.set(values);
        setting.refreshCount();
        rebuild();
    }
}
