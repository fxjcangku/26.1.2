// 自动图书管理员 附魔匹配服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.service.EnchantmentService;

/**
 * 自动图书管理员 · 附魔匹配服务实现（Fabric 客户端）。
 *
 * <p>纯逻辑匹配，不直接依赖游戏 API。判定一条交易报价是否命中目标附魔，
 * 规则：附魔书输出 + 附魔 ID 与等级精确匹配 + 必须是该附魔最高等级 + 绿宝石价格不超上限。</p>
 */
public final class FabricEnchantmentService implements EnchantmentService {
    @Override
    public boolean matches(TradeOfferSnapshot offer, EnchantmentTarget target, int maximumEmeraldPrice) {
        return offer.tradable()
            && !offer.invalid()
            && "minecraft:enchanted_book".equals(offer.outputItemIdentifier())
            && offer.enchantmentIdentifier().equals(target.identifier())
            && offer.enchantmentLevel() == target.level()
            && offer.maximumEnchantmentLevel() == target.level()
            && offer.emeraldCost() <= maximumEmeraldPrice;
    }
}
