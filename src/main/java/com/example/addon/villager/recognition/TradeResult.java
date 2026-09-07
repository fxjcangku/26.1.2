package com.example.addon.villager.recognition;

import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 交易识别结果。
 *
 * @param known 是否识别为目标交易
 * @param offer 交易报价（已知时非空）
 * @param reason 未知原因
 */
public record TradeResult(boolean known, MerchantOffer offer, String reason) {

    /**
     * 创建已知交易结果。
     */
    public static TradeResult known(MerchantOffer offer) {
        return new TradeResult(true, offer, "");
    }

    /**
     * 创建未知结果。
     */
    public static TradeResult unknown(String reason) {
        return new TradeResult(false, null, reason);
    }
}
