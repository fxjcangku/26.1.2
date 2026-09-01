package com.example.addon.enchant;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔目标多选设置控件。
 * 底层的 BoolSetting 组被隐藏，本控件把勾选状态聚合成一个可点击的选择器，
 * 点击后打开 {@link EnchantmentSelectScreen} 进行可视化多选。
 */
public final class EnchantmentSelectSetting extends StringListSetting {
    public final List<String> options;
    private final List<WLabel> countLabels = new ArrayList<>();

    public EnchantmentSelectSetting(String name, List<String> options, List<BoolSetting> backing) {
        this(name, options, backing, null);
    }

    /** 带可见性条件的构造：用于三模式 UI 下按目标模式动态显示/隐藏 */
    public EnchantmentSelectSetting(String name, List<String> options, List<BoolSetting> backing, IVisible visible) {
        super(name, "选择需要收集的附魔属性", selected(backing), values -> apply(values, backing), null, visible, null, null);
        this.options = List.copyOf(options);
    }

    private static List<String> selected(List<BoolSetting> backing) {
        List<String> values = new ArrayList<>();
        for (BoolSetting setting : backing) if (Boolean.TRUE.equals(setting.get())) values.add(setting.name);
        return values;
    }

    private static void apply(List<String> values, List<BoolSetting> backing) {
        for (BoolSetting setting : backing) setting.set(values.contains(setting.name));
    }

    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(EnchantmentSelectSetting.class, theme -> (table, setting) -> createWidget(theme, table, (EnchantmentSelectSetting) setting));
    }

    private static void createWidget(GuiTheme theme, WTable table, EnchantmentSelectSetting setting) {
        WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();
        list.add(theme.item(enchantedBookIcon()));
        WButton select = list.add(theme.button("选择附魔")).expandCellX().widget();
        WLabel count = list.add(theme.label(setting.countText())).widget();
        setting.countLabels.add(count);
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        select.action = () -> Minecraft.getInstance().setScreen(new EnchantmentSelectScreen(theme, setting));
        reset.action = () -> {
            setting.reset();
            setting.refreshCount();
        };
    }

    /** 附魔书官方图标堆：主菜单等环境组件未绑定时 getDefaultInstance 会 NPE，兜底返回空堆 */
    private static ItemStack enchantedBookIcon() {
        try {
            return Items.ENCHANTED_BOOK.getDefaultInstance();
        } catch (NullPointerException e) {
            return ItemStack.EMPTY;
        }
    }

    public void refreshCount() {
        String text = countText();
        countLabels.removeIf(label -> {
            label.set(text);
            return false;
        });
    }

    public int selectedCount() {
        return get().size();
    }

    private String countText() {
        return "已选择 " + get().size() + " 项";
    }
}
