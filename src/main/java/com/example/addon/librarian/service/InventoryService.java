// 附魔交易所 库存服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;

public interface InventoryService {
    boolean canAfford(TradeOfferSnapshot offer);

    boolean hasOutputCapacity();

    int countMatchingBooks(EnchantmentTarget target);
}
