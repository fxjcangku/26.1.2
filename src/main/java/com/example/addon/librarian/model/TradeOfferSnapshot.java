// 附魔交易所 交易报价快照
package com.example.addon.librarian.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record TradeOfferSnapshot(
    int tradeIndex,
    String synchronizationId,
    String outputItemIdentifier,
    int outputCount,
    String enchantmentIdentifier,
    int enchantmentLevel,
    int maximumEnchantmentLevel,
    int emeraldCost,
    String secondCostItemIdentifier,
    int secondCostCount,
    boolean tradable,
    boolean invalid
) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public TradeOfferSnapshot {
        if (tradeIndex < 0) throw new IllegalArgumentException("tradeIndex 不能小于 0");
        synchronizationId = Objects.requireNonNull(synchronizationId, "synchronizationId");
        if (synchronizationId.isBlank()) throw new IllegalArgumentException("synchronizationId 不能为空");
        outputItemIdentifier = normalizeIdentifier(outputItemIdentifier, "outputItemIdentifier");
        enchantmentIdentifier = normalizeIdentifier(enchantmentIdentifier, "enchantmentIdentifier");
        if (outputCount < 1) throw new IllegalArgumentException("outputCount 必须大于 0");
        if (enchantmentLevel < 1) throw new IllegalArgumentException("enchantmentLevel 必须大于 0");
        if (maximumEnchantmentLevel < enchantmentLevel) {
            throw new IllegalArgumentException("maximumEnchantmentLevel 不能小于当前等级");
        }
        if (emeraldCost < 0 || secondCostCount < 0) throw new IllegalArgumentException("交易成本不能小于 0");
        if (secondCostItemIdentifier == null) {
            if (secondCostCount != 0) throw new IllegalArgumentException("第二成本数量缺少物品标识");
        } else {
            secondCostItemIdentifier = normalizeIdentifier(secondCostItemIdentifier, "secondCostItemIdentifier");
            if (secondCostCount < 1) throw new IllegalArgumentException("第二成本数量必须大于 0");
        }
        if (invalid && tradable) throw new IllegalArgumentException("失效报价不能处于可交易状态");
    }

    public TradeOfferSnapshot(
        int tradeIndex,
        String enchantmentIdentifier,
        int enchantmentLevel,
        int maximumEnchantmentLevel,
        int emeraldCost,
        int bookCost,
        boolean soldOut
    ) {
        this(
            tradeIndex,
            "legacy:" + tradeIndex,
            "minecraft:enchanted_book",
            1,
            enchantmentIdentifier,
            enchantmentLevel,
            maximumEnchantmentLevel,
            emeraldCost,
            bookCost == 0 ? null : "minecraft:book",
            bookCost,
            !soldOut,
            false
        );
    }

    public int bookCost() {
        return "minecraft:book".equals(secondCostItemIdentifier) ? secondCostCount : 0;
    }

    public boolean soldOut() {
        return !tradable && !invalid;
    }

    private static String normalizeIdentifier(String identifier, String name) {
        String normalized = Objects.requireNonNull(identifier, name).toLowerCase(Locale.ROOT);
        if (!IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " 必须包含合法命名空间: " + normalized);
        }
        return normalized;
    }
}
