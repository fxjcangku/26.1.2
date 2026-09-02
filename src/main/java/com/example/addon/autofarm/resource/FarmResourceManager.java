package com.example.addon.autofarm.resource;

import com.example.addon.autofarm.model.CropProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/**
 * 农场资源管理器：负责种植材料的安全库存、卸货/补货判定与毒马铃薯识别。
 *
 * 每个启用作物独立维护种植材料需求，安全库存逐作物计算（组 × 64 个）。
 * 只有实际数量 >= 安全库存才认为补货达标，绝不再用「数量 > 0 就算补好」。
 */
public final class FarmResourceManager {

    private final Set<CropProfile> enabled = EnumSet.noneOf(CropProfile.class);

    /** 种植材料统一安全库存组数，每个启用作物独立按此截留（组 × 64） */
    private int safetyStockGroups = 3;

    /** 更新启用作物集合与种植材料安全库存组数 */
    public void configure(Set<CropProfile> crops, int safetyStockGroups) {
        this.enabled.clear();
        this.enabled.addAll(crops);
        this.safetyStockGroups = safetyStockGroups;
    }

    /** 某作物种植材料的安全库存数量（组 × 64），统一按配置截留 */
    public int safetyStock(CropProfile crop) {
        return safetyStockGroups * 64;
    }

    /** 背包里指定物品的总数量（主背包 36 格 + 副手） */
    public int countItem(Item item) {
        if (item == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) total += stack.getCount();
        }
        if (mc.player.getOffhandItem().is(item)) total += mc.player.getOffhandItem().getCount();
        return total;
    }

    /** 某作物是否需要补货（种植材料低于安全库存） */
    public boolean needsRestock(CropProfile crop) {
        return crop.needsReplant() && countItem(crop.plantItem()) < safetyStock(crop);
    }

    /** 是否存在任一启用作物需要补货 */
    public boolean anyNeedsRestock() {
        for (CropProfile crop : enabled) {
            if (needsRestock(crop)) return true;
        }
        return false;
    }

    /** 取第一个需要补货的作物，没有返回 null */
    public CropProfile firstNeedsRestock() {
        for (CropProfile crop : enabled) {
            if (needsRestock(crop)) return crop;
        }
        return null;
    }

    /** 毒马铃薯当前数量（毒马铃薯必须独立处理，不进普通作物箱） */
    public int countPoisonousPotato() {
        return countItem(Items.POISONOUS_POTATO);
    }

    /**
     * 判断某个物品 stack 当前是否应该被卸入普通作物箱。
     *
     * 规则：毒马铃薯永不进普通箱；种植材料只在超出安全库存时才卸；
     * 纯产物（果实与种子分离的作物）全部可卸。
     */
    public boolean shouldDepositItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.POISONOUS_POTATO) return false;

        for (CropProfile crop : enabled) {
            // 种植材料：超出安全库存才允许卸
            if (item == crop.plantItem()) {
                return countItem(item) > safetyStock(crop);
            }
            // 纯产物（如小麦果实）：全部可卸
            if (item == crop.harvestItem() && crop.plantItem() != crop.harvestItem()) {
                return true;
            }
        }
        return false;
    }

    /** 背包里可卸货物品的总组数（向上取整），用于卸货阈值判断 */
    public int depositableStacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && shouldDepositItem(stack)) total += stack.getCount();
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty() && shouldDepositItem(offhand)) total += offhand.getCount();
        return (total + 63) / 64;
    }

    /** 是否还有可卸货物（用于卸货完成判定） */
    public boolean hasDepositable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && shouldDepositItem(stack)) return true;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        return !offhand.isEmpty() && shouldDepositItem(offhand);
    }
}
