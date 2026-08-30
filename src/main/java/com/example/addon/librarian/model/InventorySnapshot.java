// 附魔交易所 目标附魔书库存快照
package com.example.addon.librarian.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record InventorySnapshot(
    String enchantmentIdentifier,
    int enchantmentLevel,
    int matchingBookCount,
    long sampleId
) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public InventorySnapshot {
        enchantmentIdentifier = Objects.requireNonNull(enchantmentIdentifier, "enchantmentIdentifier")
            .toLowerCase(Locale.ROOT);
        if (!IDENTIFIER_PATTERN.matcher(enchantmentIdentifier).matches()) {
            throw new IllegalArgumentException("附魔 ID 必须包含合法命名空间: " + enchantmentIdentifier);
        }
        if (enchantmentLevel < 1) throw new IllegalArgumentException("enchantmentLevel 必须大于 0");
        if (matchingBookCount < 0) throw new IllegalArgumentException("matchingBookCount 不能小于 0");
        if (sampleId < 0) throw new IllegalArgumentException("sampleId 不能小于 0");
    }

    public boolean matchesTarget(EnchantmentTarget target) {
        Objects.requireNonNull(target, "target");
        return enchantmentIdentifier.equals(target.identifier()) && enchantmentLevel == target.level();
    }

    public boolean sameTargetAs(InventorySnapshot other) {
        Objects.requireNonNull(other, "other");
        return enchantmentIdentifier.equals(other.enchantmentIdentifier)
            && enchantmentLevel == other.enchantmentLevel;
    }

    public boolean hasIncreaseFrom(InventorySnapshot before) {
        Objects.requireNonNull(before, "before");
        return sameTargetAs(before) && matchingBookCount > before.matchingBookCount;
    }
}
