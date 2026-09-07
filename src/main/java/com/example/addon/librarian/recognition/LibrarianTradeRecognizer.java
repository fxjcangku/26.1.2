package com.example.addon.librarian.recognition;

import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 图书管理员交易识别器接口 - 判断交易是否匹配目标附魔书。
 *
 * <p>职责：判断村民交易报价是否为目标附魔书。</p>
 */
public interface LibrarianTradeRecognizer {

    /**
     * 识别交易是否为目标附魔书。
     *
     * @param offer 交易报价
     * @return 识别结果
     */
    LibrarianTradeResult recognize(MerchantOffer offer);
}
