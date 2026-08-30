// 附魔交易所 库存服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.service.InventoryService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class FabricInventoryService implements InventoryService {
    @Override
    public boolean canAfford(TradeOfferSnapshot offer) {
        return count(Items.EMERALD) >= offer.emeraldCost()
            && (offer.secondCostItemIdentifier() == null
                || !"minecraft:book".equals(offer.secondCostItemIdentifier())
                || count(Items.BOOK) >= offer.secondCostCount());
    }

    @Override
    public boolean hasOutputCapacity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (int slot = 0; slot < 36; slot++) if (mc.player.getInventory().getItem(slot).isEmpty()) return true;
        return false;
    }

    @Override
    public int countMatchingBooks(EnchantmentTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.is(Items.ENCHANTED_BOOK)) continue;
            ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (enchantments == null) continue;
            boolean matches = false;
            for (var entry : enchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
                int level = entry.getIntValue();
                if (id.equals(target.identifier()) && level == target.level()) {
                    matches = true;
                    break;
                }
            }
            if (matches) count += stack.getCount();
        }
        return count;
    }

    private int count(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
