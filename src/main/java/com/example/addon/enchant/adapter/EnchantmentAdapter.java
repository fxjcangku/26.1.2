package com.example.addon.enchant.adapter;

import com.example.addon.enchant.recognition.EnchantmentResult;
import net.minecraft.world.item.ItemStack;

/**
 * 附魔适配器接口 - 统一附魔识别入口。
 *
 * <p>职责：封装附魔识别逻辑，隔离服务器差异。</p>
 */
public interface EnchantmentAdapter {

    /**
     * 识别物品附魔是否符合目标。
     *
     * @param stack 物品堆
     * @return 识别结果
     */
    EnchantmentResult recognizeEnchantment(ItemStack stack);
}
