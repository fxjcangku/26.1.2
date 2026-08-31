package com.example.addon.itemid;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 手动添加物品 ID 屏幕。
 *
 * <p>玩家输入 {@code minecraft:diamond} 等物品 ID，经当前 Minecraft Registry
 * 验证通过后写入 {@link ItemIdManager}；非法 ID（不存在 / 空气）禁止保存。</p>
 */
public final class IdAddScreen extends WindowScreen {

    private final ItemIdManager idManager;
    private WTextBox input;

    public IdAddScreen(GuiTheme theme, ItemIdManager idManager) {
        super(theme, "手动添加物品 ID");
        this.idManager = idManager;
    }

    @Override
    public void initWidgets() {
        add(theme.label("§7输入物品 ID（如 §f§eminecraft:diamond§7），经 Registry 验证后保存")).expandX();
        input = add(theme.textBox("minecraft:diamond")).minWidth(300).expandX().widget();
        input.setFocused(true);

        WTable buttons = add(theme.table()).expandX().widget();
        WButton add = buttons.add(theme.button("§a添加")).expandX().widget();
        add.action = this::tryAdd;
        WButton cancel = buttons.add(theme.button("§c取消")).expandX().widget();
        cancel.action = () -> Minecraft.getInstance().setScreen(null);
    }

    /** 校验输入 → 构造身份 → 写入配置 → 播报并关闭 */
    private void tryAdd() {
        String raw = input.get().trim();
        ItemIdentity identity = ItemIdentity.fromItemId(raw);
        if (identity == null) {
            feedback("§c✗ 非法物品 ID §8▸ " + raw + " §8▸ 注册表不存在或为空气");
            return;
        }
        String fileName = idManager.add(identity);
        if (fileName == null) {
            feedback("§e该物品已在 ID 配置中 §8▸ " + identity.displayName());
            return;
        }
        feedback("§a§l✓ 已添加物品 §8▸ §a§l" + identity.displayName() + " §8▸ §f" + identity.itemId());
        Minecraft.getInstance().setScreen(null);
    }

    private void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("ID配置管理", message)));
    }
}
