package com.example.addon.stardew.task;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/**
 * 星露谷任务公共辅助：物品解析、材料准备、可达性判断。
 *
 * <p>物品解析用 Mojang 官方映射 BuiltInRegistries；材料准备复用 Meteor InvUtils，
 * 与现有原版补种任务的种植材料搜索顺序一致（副手 → 快捷栏 → 主背包）。</p>
 */
public final class StardewTaskSupport {

    private StardewTaskSupport() {
    }

    /** 按注册表 ID 字符串解析物品，非法返回 null */
    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? null : item;
    }

    /** 把指定物品准备到副手（副手已有则直接返回，否则从快捷栏/主背包移动），失败返回 null */
    static InteractionHand prepare(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || item == null) return null;

        if (mc.player.getOffhandItem().is(item)) return InteractionHand.OFF_HAND;

        FindItemResult hotbar = InvUtils.findInHotbar(item);
        if (hotbar.found()) {
            InvUtils.move().from(hotbar.slot()).toOffhand();
            return InteractionHand.OFF_HAND;
        }

        FindItemResult inventory = InvUtils.find(item);
        if (inventory.found()) {
            InvUtils.move().from(inventory.slot()).toOffhand();
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** 玩家是否已进入目标方块的操作距离 */
    static boolean inReach(BlockPos pos, double reachDistance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getEyeY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz <= reachDistance * reachDistance;
    }
}
