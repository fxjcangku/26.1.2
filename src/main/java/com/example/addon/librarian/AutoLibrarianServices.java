// 附魔交易所 服务集合
package com.example.addon.librarian;

import com.example.addon.librarian.service.EnchantmentService;
import com.example.addon.librarian.service.LecternPlacementService;
import com.example.addon.librarian.service.TradeService;
import com.example.addon.librarian.service.VillagerStationService;

import java.util.Objects;

/**
 * 附魔交易所 · 服务集合。
 *
 * <p>聚合附魔交易所所需的四个核心业务服务，作为编排器的依赖容器，
 * 便于在模块层统一装配并注入。</p>
 */
public record AutoLibrarianServices(
    /** 固定交易位服务 */
    VillagerStationService villagerStationService,
    /** 讲台放置服务 */
    LecternPlacementService lecternPlacementService,
    /** 附魔匹配服务 */
    EnchantmentService enchantmentService,
    /** 交易服务 */
    TradeService tradeService
) {
    public AutoLibrarianServices {
        Objects.requireNonNull(villagerStationService, "villagerStationService");
        Objects.requireNonNull(lecternPlacementService, "lecternPlacementService");
        Objects.requireNonNull(enchantmentService, "enchantmentService");
        Objects.requireNonNull(tradeService, "tradeService");
    }
}
