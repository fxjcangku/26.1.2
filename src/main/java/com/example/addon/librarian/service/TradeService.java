// 附魔交易所 交易服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;
import java.util.List;

public interface TradeService {
    ActionResult open(VillagerTarget target);

    boolean isTradeScreenReady();

    Optional<TradeOfferSnapshot> readFirstEnchantedBookTrade();

    default List<TradeOfferSnapshot> scanTrades() {
        return readFirstEnchantedBookTrade().stream().toList();
    }

    ActionResult select(TradeOfferSnapshot offer);

    boolean isSelectedTradeSynchronized(TradeOfferSnapshot offer);

    ActionResult takeOutput();

    void close();
}
