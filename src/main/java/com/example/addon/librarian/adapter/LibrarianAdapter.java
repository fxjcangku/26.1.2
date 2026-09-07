package com.example.addon.librarian.adapter;

import com.example.addon.librarian.recognition.LibrarianTradeResult;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 图书管理员适配器接口 - 统一交易识别入口。
 *
 * <p>职责：封装图书管理员交易识别逻辑。</p>
 */
public interface LibrarianAdapter {

    /**
     * 识别交易是否为目标附魔书。
     *
     * @param offer 交易报价
     * @return 识别结果
     */
    LibrarianTradeResult recognizeTrade(MerchantOffer offer);
}
