// 附魔交易所 交易服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;
import java.util.List;

public interface TradeService {
    /** 打开目标村民的交易界面 */
    ActionResult open(VillagerTarget target);

    /** 交易界面是否已就绪（containerMenu 已同步为 MerchantMenu） */
    boolean isTradeScreenReady();

    /** 读取第一笔附魔书交易报价 */
    Optional<TradeOfferSnapshot> readFirstEnchantedBookTrade();

    /** 扫描全部交易报价 */
    default List<TradeOfferSnapshot> scanTrades() {
        return readFirstEnchantedBookTrade().stream().toList();
    }

    /** 选中指定交易 */
    ActionResult select(TradeOfferSnapshot offer);

    /** 选中的交易是否已与服务端同步 */
    boolean isSelectedTradeSynchronized(TradeOfferSnapshot offer);

    /** 取出交易产物 */
    ActionResult takeOutput();

    /** 关闭交易界面 */
    void close();
}
