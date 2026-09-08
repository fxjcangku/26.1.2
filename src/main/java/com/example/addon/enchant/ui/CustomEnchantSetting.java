package com.example.addon.enchant.ui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 自定义附魔目标设置控件（带附魔书官方图标）。
 *
 * <p>复用 {@link StringListSetting#fillTable} 的内联字符串编辑能力（每行一个附魔名 + 删除，
 * 底部添加 / 重置），顶部追加附魔书官方图标，与 BOOK / GEAR 模式的选择器视觉统一。
 * 图标用 {@code Items.ENCHANTED_BOOK}（附魔书），本身自带附魔光效。</p>
 */
public final class CustomEnchantSetting extends StringListSetting {

    public CustomEnchantSetting(String name, String description, List<String> defaultValue, IVisible visible) {
        super(name, description, defaultValue, values -> {}, null, visible, null, null);
    }

    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(CustomEnchantSetting.class,
            theme -> (table, setting) -> createWidget(theme, table, (CustomEnchantSetting) setting));
    }

    private static void createWidget(GuiTheme theme, WTable table, CustomEnchantSetting setting) {
        // 附魔书图标行（自带光效，与 BOOK/GEAR 选择器统一）
        WHorizontalList header = table.add(theme.horizontalList()).expandX().widget();
        header.spacing = 6;
        header.add(theme.item(enchantedBookIcon()));
        table.row();
        // 内联编辑列表（复用 Meteor 官方 StringListSetting.fillTable）
        WTable wtable = table.add(theme.table()).expandX().widget();
        StringListSetting.fillTable(theme, wtable, setting);
    }

    /** 附魔书官方图标堆：主菜单等环境组件未绑定时 getDefaultInstance 会 NPE，兜底返回空堆 */
    private static ItemStack enchantedBookIcon() {
        try {
            return Items.ENCHANTED_BOOK.getDefaultInstance();
        } catch (NullPointerException e) {
            return ItemStack.EMPTY;
        }
    }
}
