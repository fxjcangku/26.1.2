package com.example.addon.itemid;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 手动添加物品屏幕：支持「物品 ID」或「中文名称」两种输入，并可填写自定义名称。
 *
 * <p>解析顺序：</p>
 * <ol>
 *   <li>直接物品 ID（{@code minecraft:diamond}）→ 经 Registry 验证。</li>
 *   <li>已保存 ID 配置的中文名精确匹配（如之前识别保存过「超级钻石」）。</li>
 *   <li>注册表原版中文名精确匹配（输入「钻石」→ {@code minecraft:diamond}），
 *       多个命中展示候选列表，不得随机选择。</li>
 * </ol>
 *
 * <p>填写「自定义名称」时，在命中的原版物品基础上构造改名物品（customName 非空），
 * 用于不拿实物直接添加自定义 / 改名物品。</p>
 */
public final class IdAddScreen extends WindowScreen {

    private final ItemIdManager idManager;
    private WTextBox input;
    private WTextBox customNameInput;
    private WTable candidateTable;

    public IdAddScreen(GuiTheme theme, ItemIdManager idManager) {
        super(theme, "添加物品");
        this.idManager = idManager;
    }

    @Override
    public void initWidgets() {
        add(theme.label("§7输入物品 ID 或中文名称（如 §f§eminecraft:diamond§7 或 §f§e钻石§7）")).expandX();
        input = add(theme.textBox("minecraft:diamond")).minWidth(300).expandX().widget();
        input.setFocused(true);

        add(theme.label("§7自定义名称（可选，填写后添加为自定义/改名物品）")).expandX();
        customNameInput = add(theme.textBox("")).minWidth(300).expandX().widget();

        WTable buttons = add(theme.table()).expandX().widget();
        WButton add = buttons.add(theme.button("§a查找并添加")).expandX().widget();
        add.action = this::resolveAndAdd;
        WButton cancel = buttons.add(theme.button("§c取消")).expandX().widget();
        cancel.action = () -> Minecraft.getInstance().setScreen(null);

        // 候选列表：多个中文名命中时动态展示
        candidateTable = add(theme.table()).expandX().widget();
    }

    /** 解析输入 → 构造身份 → 写入配置 → 播报并关闭（多命中则展示候选） */
    private void resolveAndAdd() {
        String raw = input.get().trim();
        String customName = customNameInput.get().trim();
        if (raw.isEmpty()) {
            feedback("§c✗ 请输入物品 ID 或中文名称");
            return;
        }

        // 1. 直接物品 ID
        ItemIdentity byId = ItemIdentity.fromItemId(raw);
        if (byId != null) {
            addIdentity(customName.isEmpty()
                ? byId
                : ItemIdentity.fromItemAndCustomName(byId.item(), customName));
            return;
        }

        // 2. 已保存 ID 配置的中文名精确匹配
        ItemIdentity saved = findSavedByName(raw);
        if (saved != null) {
            addIdentity(saved);
            return;
        }

        // 3. 注册表原版中文名精确匹配
        List<Item> items = ItemIdentifier.findVanillaByChineseName(raw);
        if (items.size() == 1) {
            Item item = items.get(0);
            addIdentity(customName.isEmpty()
                ? ItemIdentity.fromItemId(BuiltInRegistries.ITEM.getKey(item).toString())
                : ItemIdentity.fromItemAndCustomName(item, customName));
            return;
        }
        if (items.size() > 1) {
            showCandidates(items, customName);
            return;
        }

        // 0 命中：无法确定身份，不能猜测
        feedback("§c✗ 未找到对应物品 §8▸ 请先手持识别，或填写真实物品 ID（如 minecraft:diamond）");
    }

    /** 在已保存 ID 配置中按显示名精确匹配（非 contains），无命中返回 null */
    private ItemIdentity findSavedByName(String name) {
        if (name.isEmpty()) return null;
        for (ItemIdentity id : idManager.all()) {
            if (id.displayName().equals(name)) return id;
        }
        return null;
    }

    /** 展示多个中文名命中候选，点击选择其一 */
    private void showCandidates(List<Item> items, String customName) {
        candidateTable.clear();
        candidateTable.add(theme.label("§e§l▌ 找到多个匹配，请选择：")).expandX();
        candidateTable.row();
        for (Item item : items) {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            String name = item.getDefaultInstance().getHoverName().getString();
            WButton choose = candidateTable.add(theme.button("§a" + name + " §8▸ §7" + itemId)).expandX().widget();
            choose.action = () -> addIdentity(customName.isEmpty()
                ? ItemIdentity.fromItemId(itemId)
                : ItemIdentity.fromItemAndCustomName(item, customName));
            candidateTable.row();
        }
    }

    /** 写入 ID 配置：成功关闭屏幕，重复保持打开 */
    private void addIdentity(ItemIdentity identity) {
        if (identity == null) {
            feedback("§c✗ 未找到对应物品 §8▸ 请先手持识别，或填写真实物品 ID");
            return;
        }
        String fileName = idManager.add(identity);
        if (fileName == null) {
            feedback("§e该物品已在 ID 配置中 §8▸ " + identity.displayName());
            return;
        }
        feedback("§a§l✓ 已添加物品 §8▸ §a§l" + identity.displayName()
            + " §8▸ §f" + identity.itemId()
            + (identity.isRenamed() ? " §8▸ §d自定义" : ""));
        Minecraft.getInstance().setScreen(null);
    }

    private void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("ID配置管理", message)));
    }
}
