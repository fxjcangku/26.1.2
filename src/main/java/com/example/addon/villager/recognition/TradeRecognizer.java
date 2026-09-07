package com.example.addon.villager.recognition;

import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 交易识别器接口 - 判断交易是否匹配目标。
 *
 * <p>职责：判断村民交易报价是否匹配目标物品/附魔/价格。</p>
 */
public interface TradeRecognizer {

    /**
     * 识别交易是否匹配目标。
     *
     * @param offer 交易报价
     * @return 识别结果
     */
    TradeResult recognize(MerchantOffer offer);
}
