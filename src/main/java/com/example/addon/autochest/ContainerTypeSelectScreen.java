package com.example.addon.autochest;

import com.example.addon.autochest.model.ContainerType;
import com.example.addon.autochest.model.ContainerTypeRegistry;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器类型多选界面。
 *
 * <p>遍历 {@link ContainerTypeRegistry} 展示全部合法容器类型，点击切换启用状态。
 * 数据源唯一来自注册表，新增类型后本界面自动呈现，无需改动。</p>
 */
public final class ContainerTypeSelectScreen extends WindowScreen {

    private final ContainerTypeSetting setting;
    private WTable table;

    public ContainerTypeSelectScreen(GuiTheme theme, ContainerTypeSetting setting) {
        super(theme, setting.title);
        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        add(theme.label("§7点击切换容器类型，默认全部启用")).expandX();
        table = add(theme.table()).expandX().widget();
        rebuild();
    }

    private void rebuild() {
        table.clear();
        for (ContainerType type : ContainerTypeRegistry.all()) {
            boolean enabled = setting.isEnabled(type);
            WButton toggle = table.add(theme.button((enabled ? "§a✓ " : "§c✗ ") + type.displayName())).expandX().widget();
            toggle.action = () -> setEnabled(type.id(), !enabled);
            table.row();
        }
    }

    private void setEnabled(String id, boolean enabled) {
        List<String> ids = new ArrayList<>(setting.get());
        if (enabled) {
            if (!ids.contains(id)) ids.add(id);
        } else {
            ids.remove(id);
        }
        setting.set(ids);
        setting.refreshCount();
        rebuild();
    }
}
