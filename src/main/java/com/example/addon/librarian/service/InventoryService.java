// 附魔交易所 库存服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;

public interface InventoryService {
    /** 是否能支付该交易（绿宝石与第二成本均充足） */
    boolean canAfford(TradeOfferSnapshot offer);

    /** 背包是否还有空位存放交易产物 */
    boolean hasOutputCapacity();

    /** 统计背包中匹配目标附魔的附魔书数量 */
    int countMatchingBooks(EnchantmentTarget target);
}
