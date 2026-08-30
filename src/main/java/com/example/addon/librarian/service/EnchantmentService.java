// 附魔交易所 附魔匹配服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;

import java.util.Collection;
import java.util.Optional;

public interface EnchantmentService {
    boolean matches(TradeOfferSnapshot offer, EnchantmentTarget target, int maximumEmeraldPrice);

    default int getMaximumTradeLevel(String enchantmentIdentifier) {
        throw new UnsupportedOperationException("当前附魔服务尚未提供最高交易等级解析");
    }

    default boolean isLibrarianTradeable(String enchantmentIdentifier) {
        return true;
    }

    default Optional<EnchantmentTarget> findMatch(
        TradeOfferSnapshot offer,
        Collection<EnchantmentTarget> targets,
        int maximumEmeraldPrice
    ) {
        return targets.stream()
            .filter(target -> !target.completed())
            .filter(target -> matches(offer, target, maximumEmeraldPrice))
            .findFirst();
    }
}
