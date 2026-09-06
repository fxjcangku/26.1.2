package com.example.addon.autochest;

import com.example.addon.autochest.model.ContainerType;
import com.example.addon.autochest.model.ContainerTypeRegistry;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器类型选择器设置：存储「已选中的容器类型 id」。
 *
 * <p>数据源唯一来自 {@link ContainerTypeRegistry}，本设置只持久化选中类型 id，
 * 运行时按 id 回查完整 {@link ContainerType}。默认全部类型启用，新增类型后
 * 自动出现在选择界面，无需改扫描核心。</p>
 */
public final class ContainerTypeSetting extends StringListSetting {

    /** 计数标签列表，供实时刷新「已选 N / 总 M 类」 */
    private final List<WLabel> countLabels = new ArrayList<>();

    public ContainerTypeSetting(String name, String description) {
        super(name, description, defaultIds(), null, null, null, null, null);
    }

    /** 默认选中全部已注册容器类型 */
    private static List<String> defaultIds() {
        List<String> ids = new ArrayList<>();
        for (ContainerType type : ContainerTypeRegistry.all()) ids.add(type.id());
        return ids;
    }

    /** 是否启用了某容器类型 */
    public boolean isEnabled(ContainerType type) {
        return type != null && get().contains(type.id());
    }

    /** 解析出已选中的完整容器类型（保持注册表顺序，无副本） */
    public List<ContainerType> enabledTypes() {
        List<ContainerType> result = new ArrayList<>();
        for (ContainerType type : ContainerTypeRegistry.all()) {
            if (get().contains(type.id())) result.add(type);
        }
        return result;
    }

    /** 刷新全部计数标签显示 */
    public void refreshCount() {
        String text = countText();
        for (WLabel label : countLabels) label.set(text);
    }

    private String countText() {
        int total = ContainerTypeRegistry.all().size();
        int selected = get().size();
        return "已选 " + selected + " / " + total + " 类";
    }

    /** 注册自定义设置控件工厂（AddonTemplate 启动时调用一次） */
    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(ContainerTypeSetting.class,
            theme -> (table, setting) -> createWidget(theme, table, (ContainerTypeSetting) setting));
    }

    /** 构建选择器控件：选择按钮 + 计数标签 + 重置按钮 */
    private static void createWidget(GuiTheme theme, WTable table, ContainerTypeSetting setting) {
        WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();
        WButton select = list.add(theme.button("选择容器类型")).expandCellX().widget();
        WLabel count = list.add(theme.label(setting.countText())).widget();
        setting.countLabels.add(count);
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        select.action = () -> Minecraft.getInstance().setScreen(new ContainerTypeSelectScreen(theme, setting));
        reset.action = () -> {
            setting.reset();
            setting.refreshCount();
        };
    }
}
