package com.example.addon.librarian.recognition;

import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 图书管理员交易识别结果。
 *
 * @param known 是否识别为目标交易
 * @param offer 交易报价（已知时非空）
 * @param enchantmentId 附魔ID
 * @param level 附魔等级
 * @param reason 未知原因
 */
public record LibrarianTradeResult(boolean known, MerchantOffer offer, String enchantmentId, int level, String reason) {

    /**
     * 创建已知交易结果。
     */
    public static LibrarianTradeResult known(MerchantOffer offer, String enchantmentId, int level) {
        return new LibrarianTradeResult(true, offer, enchantmentId, level, "");
    }

    /**
     * 创建未知结果。
     */
    public static LibrarianTradeResult unknown(String reason) {
        return new LibrarianTradeResult(false, null, "", 0, reason);
    }
}
