// 附魔交易所 服务集合
package com.example.addon.librarian;

import com.example.addon.librarian.service.EnchantmentService;
import com.example.addon.librarian.service.LecternPlacementService;
import com.example.addon.librarian.service.TradeService;
import com.example.addon.librarian.service.VillagerStationService;

import java.util.Objects;

public record AutoLibrarianServices(
    VillagerStationService villagerStationService,
    LecternPlacementService lecternPlacementService,
    EnchantmentService enchantmentService,
    TradeService tradeService
) {
    public AutoLibrarianServices {
        Objects.requireNonNull(villagerStationService, "villagerStationService");
        Objects.requireNonNull(lecternPlacementService, "lecternPlacementService");
        Objects.requireNonNull(enchantmentService, "enchantmentService");
        Objects.requireNonNull(tradeService, "tradeService");
    }
}
