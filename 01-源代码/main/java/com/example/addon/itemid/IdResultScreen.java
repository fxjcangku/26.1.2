package com.example.addon.itemid;

import com.example.addon.core.YiyiaddonModule;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 识别结果屏幕：完整展示一次识别产出的 {@link ItemIdentity}，并提供快捷操作。
 *
 * <p>对应「聊天复制/显示」识别模式——识别结果先在这里呈现，玩家可复制 Item ID /
 * 复制完整信息 / 保存 ID / 添加到 ID 配置，全部直接调用 {@link ItemIdManager} 或
 * 剪贴板，不模拟键盘、鼠标或聊天输入。</p>
 */
public final class IdResultScreen extends WindowScreen {

    private final ItemIdentity identity;
    private final ItemIdManager idManager;

    public IdResultScreen(GuiTheme theme, ItemIdentity identity, ItemIdManager idManager) {
        super(theme, "识别结果");
        this.identity = identity;
        this.idManager = idManager;
    }

    @Override
    public void initWidgets() {
        add(theme.label("§b§l▌ 识别结果")).expandX();
        addRow("物品ID", identity.itemId());
        addRow("显示名称", identity.displayName());
        addRow("原始名称", identity.baseName());
        addRow("物品类型", identity.typeName());
        addRow("数量", String.valueOf(identity.quantity()));
        if (identity.customName() != null) {
            addRow("自定义名称", identity.customName());
        }
        addRow("数据版本", String.valueOf(identity.dataVersion()));
        addRow("数据组件", identity.dataComponents() == null ? "无" : "有（见 JSON 文件）");

        if (identity.hasEnchantments()) {
            add(theme.label("§7附魔")).expandX();
            for (ItemIdentity.EnchantmentEntry e : identity.enchantments()) {
                add(theme.label("  §8▸ §a" + e.displayName()
                    + " §8▸ §f" + e.id() + " §8▸ §e等级 " + e.level())).expandX();
            }
        }

        add(theme.label(" ")).expandX();

        WTable buttons = add(theme.table()).expandX().widget();
        addButton(buttons, "复制 Item ID", () -> copy(identity.itemId(), "已复制 Item ID"));
        addButton(buttons, "复制完整信息", () -> copy(fullJson(), "已复制完整识别信息"));
        addButton(buttons, "保存 ID", this::saveQuietly);
        addButton(buttons, "添加到 ID 配置", this::saveAndClose);
    }

    /** 添加一行「标签 §8▸ 值」 */
    private void addRow(String label, String value) {
        add(theme.label("§7" + label + " §8▸ §f" + value)).expandX();
    }

    /** 添加铺满整行的操作按钮 */
    private void addButton(WTable table, String title, Runnable action) {
        WButton button = table.add(theme.button(title)).expandX().minWidth(200).widget();
        button.action = action;
        table.row();
    }

    /** 生成完整身份的 JSON 字符串（供复制完整信息） */
    private String fullJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(identity.toJsonObject());
    }

    /** 复制文本到剪贴板（直接调客户端剪贴板，不模拟键盘） */
    private void copy(String text, String success) {
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(text);
        feedback("§a§l✓ " + success);
    }

    /** 保存 ID：写入 ID 配置，屏幕保持打开供继续操作 */
    private void saveQuietly() {
        save();
    }

    /** 添加到 ID 配置：保存后关闭屏幕并播报 */
    private void saveAndClose() {
        if (save()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    /** 写入 ID 配置；返回是否保存成功 */
    private boolean save() {
        String fileName = idManager.add(identity);
        if (fileName == null) {
            feedback("§e该物品已在 ID 配置中");
            return false;
        }
        feedback("§a§l✓ 已添加到 ID 配置 §8▸ §a§l" + identity.displayName() + " §8▸ §f" + fileName);
        return true;
    }

    private void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            YiyiaddonModule.formatMessage("ID识别", message)));
    }
}
