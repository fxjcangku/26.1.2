package com.example.addon.villager.adapter;

import com.example.addon.villager.recognition.TradeResult;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 村民交易适配器接口 - 统一服务器差异识别入口。
 *
 * <p>职责：封装交易识别逻辑，隔离服务器差异。</p>
 */
public interface VillagerTradeAdapter {

    /**
     * 识别交易是否匹配目标。
     *
     * @param offer 交易报价
     * @return 识别结果
     */
    TradeResult recognizeTrade(MerchantOffer offer);
}
