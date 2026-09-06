package com.example.addon.itemid;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoChest 目标物品选择器设置。
 *
 * <p>数据源唯一来自 {@link ItemIdManager}：本设置只持久化「选中的身份键」
 * （{@link ItemIdentity#identityKey()}），运行时按键从 ItemIdManager 解析回
 * 完整 ItemIdentity，绝不复制第二套物品数据库。ID 增删后由监听器驱动
 * {@link #pruneInvalid()} 同步移除失效项，做到无需重启。</p>
 */
public final class ItemTargetSetting extends StringListSetting {

    /** 唯一数据源（供选择屏幕与 AutoChest 消费） */
    public final ItemIdManager idManager;

    /** 计数标签列表，供实时刷新「已选 N / 总 M 项」 */
    private final List<WLabel> countLabels = new ArrayList<>();

    public ItemTargetSetting(String name, String description, ItemIdManager idManager) {
        this(name, description, idManager, null);
    }

    public ItemTargetSetting(String name, String description, ItemIdManager idManager, IVisible visible) {
        super(name, description, new ArrayList<>(), null, null, visible, null, null);
        this.idManager = idManager;
    }

    /**
     * 清理已失效的选中项（对应 ID 已从 ItemIdManager 删除），返回被移除数量。
     *
     * <p>由 AutoChestModule 注册的 ItemIdManager 监听器调用，保证删除 ID 后
     * 选择器同步移除、不崩溃。</p>
     */
    public int pruneInvalid() {
        List<String> current = new ArrayList<>(get());
        List<String> valid = new ArrayList<>();
        for (String key : current) {
            if (idManager.findByKey(key) != null) valid.add(key);
        }
        int removed = current.size() - valid.size();
        if (removed > 0) set(valid);
        return removed;
    }

    /** 刷新全部计数标签显示 */
    public void refreshCount() {
        String text = countText();
        for (WLabel label : countLabels) label.set(text);
    }

    /** 解析选中项为完整 ItemIdentity 集合（唯一数据源，无副本），供匹配层消费 */
    public List<ItemIdentity> selectedIdentities() {
        List<ItemIdentity> result = new ArrayList<>();
        for (String key : get()) {
            ItemIdentity id = idManager.findByKey(key);
            if (id != null) result.add(id);
        }
        return result;
    }

    private String countText() {
        int total = idManager.size();
        int selected = get().size();
        if (selected == 0) return "未选择目标（共 " + total + " 项）";
        return "已选 " + selected + " / " + total + " 项";
    }

    /** 注册自定义设置控件工厂（AddonTemplate 启动时调用一次） */
    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(ItemTargetSetting.class,
            theme -> (table, setting) -> createWidget(theme, table, (ItemTargetSetting) setting));
    }

    /** 构建选择器控件：选择按钮 + 计数标签 + 重置按钮 */
    private static void createWidget(GuiTheme theme, WTable table, ItemTargetSetting setting) {
        WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();
        WButton select = list.add(theme.button("选择目标物品")).expandCellX().widget();
        WLabel count = list.add(theme.label(setting.countText())).widget();
        setting.countLabels.add(count);
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        select.action = () -> Minecraft.getInstance().setScreen(new ItemTargetSelectScreen(theme, setting));
        reset.action = () -> {
            setting.reset();
            setting.refreshCount();
        };
    }
}
