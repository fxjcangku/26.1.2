package com.example.addon.stardew.ui;

import com.example.addon.stardew.StardewFarmStrategy;
import com.example.addon.stardew.model.StardewSeedProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.Minecraft;

/**
 * 星露谷种子选择器：列出当前档案的种子，支持选择 / 删除 / 添加当前手持物品为种子。
 *
 * <p>无资源包时回退为「物品 ID + 名称」展示，不因缺少资源包而不可用。</p>
 */
public final class StardewSeedSelector extends WindowScreen {

    private final StardewFarmStrategy strategy;

    public StardewSeedSelector(GuiTheme theme, StardewFarmStrategy strategy) {
        super(theme, "星露谷 · 种子选择器");
        this.strategy = strategy;
    }

    @Override
    public void initWidgets() {
        WVerticalList list = add(theme.verticalList()).expandX().widget();

        StardewServerProfile profile = strategy.currentProfile();
        if (profile == null || profile.seeds().isEmpty()) {
            list.add(theme.label("§8暂未添加种子")).expandX();
        } else {
            for (StardewSeedProfile seed : profile.seeds()) {
                WTable row = list.add(theme.table()).expandX().widget();
                row.add(theme.label((seed.enabled() ? "§a" : "§8") + seed.displayName()
                    + " §8▸ §7" + seed.minecraftItemId())).expandX();

                WButton select = row.add(theme.button(strategy.selectedSeedId() != null
                    && strategy.selectedSeedId().equals(seed.seedId()) ? "§a已选" : "§7选择")).widget();
                select.action = () -> {
                    strategy.selectSeed(seed.seedId());
                    Minecraft.getInstance().setScreen(null);
                };

                WButton delete = row.add(theme.button("§c删除")).widget();
                delete.action = () -> {
                    strategy.removeSeed(seed.seedId());
                    Minecraft.getInstance().setScreen(null);
                };
            }
        }

        list.add(theme.horizontalSeparator()).expandX();

        WButton add = list.add(theme.button("§a添加当前手持物品为种子")).expandX().widget();
        add.action = () -> {
            strategy.addSeedFromHeld();
            Minecraft.getInstance().setScreen(null);
        };

        WButton close = list.add(theme.button("关闭")).expandX().widget();
        close.action = () -> Minecraft.getInstance().setScreen(null);
    }
}
