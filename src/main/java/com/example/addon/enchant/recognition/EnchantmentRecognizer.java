package com.example.addon.enchant.recognition;

import net.minecraft.world.item.ItemStack;

/**
 * 附魔识别器接口 - 判断物品附魔是否符合目标。
 *
 * <p>职责：判断给定物品的附魔组合是否满足目标档案。</p>
 */
public interface EnchantmentRecognizer {

    /**
     * 识别物品附魔是否符合目标。
     *
     * @param stack 物品堆
     * @return 识别结果
     */
    EnchantmentResult recognize(ItemStack stack);
}
