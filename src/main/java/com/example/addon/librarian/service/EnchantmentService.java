// 自动图书管理员 附魔匹配服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;

import java.util.Collection;
import java.util.Optional;

public interface EnchantmentService {
    /** 判断交易报价是否命中目标附魔（附魔 ID + 等级 + 价格上限三重匹配） */
    boolean matches(TradeOfferSnapshot offer, EnchantmentTarget target, int maximumEmeraldPrice);

    /** 解析指定附魔的可交易最高等级（图书馆管理员实际能刷出的上限） */
    default int getMaximumTradeLevel(String enchantmentIdentifier) {
        throw new UnsupportedOperationException("当前附魔服务尚未提供最高交易等级解析");
    }

    /** 该附魔是否可通过图书馆管理员交易获得 */
    default boolean isLibrarianTradeable(String enchantmentIdentifier) {
        return true;
    }

    /** 在目标集合中查找第一个命中的未完成目标 */
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
