package com.example.addon.enchant.recognition;

import net.minecraft.world.item.ItemStack;

/**
 * 附魔识别结果。
 *
 * @param known 是否识别为目标附魔组合
 * @param stack 物品堆（已知时非空）
 * @param matchLevel 匹配程度（0-100）
 * @param reason 未知原因或不匹配原因
 */
public record EnchantmentResult(boolean known, ItemStack stack, int matchLevel, String reason) {

    /**
     * 创建已知结果。
     */
    public static EnchantmentResult known(ItemStack stack, int matchLevel) {
        return new EnchantmentResult(true, stack, matchLevel, "");
    }

    /**
     * 创建未知结果。
     */
    public static EnchantmentResult unknown(String reason) {
        return new EnchantmentResult(false, ItemStack.EMPTY, 0, reason);
    }
}
