package com.example.addon.autochest.config;

import com.example.addon.autochest.ui.ItemQuantityScreen;
import com.example.addon.itemid.ItemTargetSetting;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每种目标物品数量设置：存储「身份键 → 目标数量」映射，服务于「按目标数量取」。
 *
 * <p>数量以目标物品选择器（{@link ItemTargetSetting}）的选中项为准，本设置只补
 * 数量维度；身份键仍由目标物品选择器回查 ID 配置管理解析，无第二套物品数据。</p>
 */
public final class ItemQuantitySetting extends Setting<Map<String, Integer>> {

    /** 目标物品选择器（数量配置以选中项为基准） */
    private final ItemTargetSetting targetSetting;

    /** 计数标签列表，供实时刷新「已配置 N / M 项」 */
    private final List<WLabel> countLabels = new ArrayList<>();

    public ItemQuantitySetting(String name, String description, ItemTargetSetting targetSetting) {
        this(name, description, targetSetting, null);
    }

    public ItemQuantitySetting(String name, String description, ItemTargetSetting targetSetting, IVisible visible) {
        super(name, description, new LinkedHashMap<>(), null, null, visible);
        this.targetSetting = targetSetting;
    }

    public ItemTargetSetting targetSetting() {
        return targetSetting;
    }

    /** 读取某身份键的目标数量，未配置返回默认 64 */
    public int quantityOf(String key) {
        Integer q = get().get(key);
        return q == null ? 64 : q;
    }

    /** 设置某身份键的目标数量（覆盖） */
    public void setQuantity(String key, int count) {
        get().put(key, Math.max(1, count));
        onChanged();
    }

    /** 刷新全部计数标签显示 */
    public void refreshCount() {
        String text = countText();
        for (WLabel label : countLabels) label.set(text);
    }

    private String countText() {
        int total = targetSetting.get().size();
        int configured = get().size();
        return "已配置 " + configured + " / " + total + " 项";
    }

    // ── 序列化：身份键 → 数量 ──

    @Override
    protected Map<String, Integer> parseImpl(String str) {
        return new LinkedHashMap<>();
    }

    @Override
    protected boolean isValueValid(Map<String, Integer> value) {
        return value != null;
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        CompoundTag valueTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : get().entrySet()) {
            valueTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("value", valueTag);
        return tag;
    }

    @Override
    protected Map<String, Integer> load(CompoundTag tag) {
        get().clear();
        CompoundTag valueTag = tag.getCompoundOrEmpty("value");
        for (String key : valueTag.keySet()) {
            get().put(key, valueTag.getIntOr(key, 64));
        }
        return get();
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>();
    }

    /** 注册自定义设置控件工厂（AddonTemplate 启动时调用一次） */
    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(ItemQuantitySetting.class,
            theme -> (table, setting) -> createWidget(theme, table, (ItemQuantitySetting) setting));
    }

    /** 构建控件：配置按钮 + 计数标签 + 重置按钮 */
    private static void createWidget(GuiTheme theme, WTable table, ItemQuantitySetting setting) {
        WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();
        WButton config = list.add(theme.button("配置每种物品数量")).expandCellX().widget();
        WLabel count = list.add(theme.label(setting.countText())).widget();
        setting.countLabels.add(count);
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        config.action = () -> Minecraft.getInstance().setScreen(new ItemQuantityScreen(theme, setting));
        reset.action = () -> {
            setting.reset();
            setting.refreshCount();
        };
    }
}
