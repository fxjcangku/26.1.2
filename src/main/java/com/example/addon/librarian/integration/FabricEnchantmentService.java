// 附魔交易所 附魔匹配服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.service.EnchantmentService;

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
