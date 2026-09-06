package com.example.addon.itemid;

import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * ID 识别指令：识别手持物品或准星对准的实体，并保存到 ID 配置。
 *
 * <p>保留既有「ID 识别」模块能力，同时提供指令入口：</p>
 * <ul>
 *   <li>{@code .id 物品} 识别手持物品（主手优先，主手空读副手，均空提示）</li>
 *   <li>{@code .id 实体} 识别准星对准的实体</li>
 * </ul>
 *
 * <p>识别结果落到 {@code AutoChest/items/} 与 {@code AutoChest/entities/}，
 * 中文名负责显示与文件命名，完整身份见 JSON。</p>
 */
public final class IdCommand extends Command {

    private final ItemIdManager itemIdManager;
    private final EntityIdManager entityIdManager;

    public IdCommand(ItemIdManager itemIdManager, EntityIdManager entityIdManager) {
        super("id", "识别物品或实体并保存ID（.id 物品 / .id 实体）");
        this.itemIdManager = itemIdManager;
        this.entityIdManager = entityIdManager;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            showHelp();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("物品").executes(ctx -> {
            identifyItem();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("实体").executes(ctx -> {
            identifyEntity();
            return SINGLE_SUCCESS;
        }));
    }

    /** 识别手持物品：主手优先，主手空读副手，均空提示；按「ID识别」模块的识别模式分流 */
    private void identifyItem() {
        if (mc.player == null) {
            info("§c玩家未加载");
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            held = mc.player.getOffhandItem();
        }
        if (held.isEmpty()) {
            info("§c没有可识别物品：主手和副手都是空的");
            return;
        }

        ItemIdentity identity = ItemIdentifier.identifyItem(held);
        if (identity == null) {
            info("§c识别失败");
            return;
        }

        // 读取「ID识别」模块当前的识别模式，作为统一命令入口的分流依据；命令自身不维护一份 mode
        IdIdentifyModule identifyModule = Modules.get().get(IdIdentifyModule.class);
        IdentifyMode mode = identifyModule != null
            ? identifyModule.identifyMode.get()
            : IdentifyMode.AUTO_SAVE;

        if (mode == IdentifyMode.AUTO_SAVE) {
            // 自动保存：直接写入 ID 配置，只发简短提示，不弹结果屏、不刷完整识别文本
            String fileName = itemIdManager.add(identity);
            if (fileName == null) {
                info("§e该物品已在 ID 配置中");
                return;
            }
            info("§a§l✓ 已自动保存 §8▸ §a§l" + identity.displayName());
        } else {
            // 聊天复制/显示：延迟到下一 tick 弹出结果屏幕。
            // 命令在聊天屏 sendChat 流程里执行，紧接着聊天屏会 setScreen(null) 关闭自身，
            // 若此处同步 setScreen 会被立即覆盖掉，导致「什么提示都没有」。
            mc.execute(() -> mc.setScreen(new IdResultScreen(GuiThemes.get(), identity, itemIdManager)));
        }
    }

    /** 识别准星对准的实体 */
    private void identifyEntity() {
        if (mc.player == null) {
            info("§c玩家未加载");
            return;
        }
        Entity entity = mc.crosshairPickEntity;
        if (entity == null) {
            info("§c准星未对准任何实体");
            return;
        }

        EntityIdentity identity = ItemIdentifier.identifyEntity(entity);
        if (identity == null) {
            info("§c识别失败");
            return;
        }
        String fileName = entityIdManager.add(identity);
        if (fileName == null) {
            info("§c该实体已在 ID 配置中");
            return;
        }

        info("§a§l✓ 已识别实体 §8▸ §a§l" + identity.displayName());
        info("§7实体ID　§8▸ §f" + identity.entityId());
        if (identity.isNamed()) {
            info("§7命名　§8▸ §a" + identity.customName() + " §7（类型 §f" + identity.baseName() + "§7）");
        }
        info("§7已保存　§8▸ §f" + fileName);
    }

    private void showHelp() {
        info("§b.id 物品 §7识别手持物品（主手→副手）并保存");
        info("§b.id 实体 §7识别准星对准的实体并保存");
    }

    private void info(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("ID识别", message)));
    }
}
