// 附魔交易所 附魔目标模型
package com.example.addon.librarian.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EnchantmentTarget(
    String identifier,
    int level,
    boolean requireMaximumLevel,
    boolean completed,
    TradeOfferSnapshot tradeOffer,
    String displayName,
    String iconIdentifier
) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public EnchantmentTarget {
        identifier = Objects.requireNonNull(identifier, "identifier").toLowerCase(Locale.ROOT);
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("附魔 ID 必须包含合法命名空间: " + identifier);
        }
        if (level < 1) throw new IllegalArgumentException("level 必须大于 0");
        if (tradeOffer != null
            && (!identifier.equals(tradeOffer.enchantmentIdentifier()) || level != tradeOffer.enchantmentLevel())) {
            throw new IllegalArgumentException("锁定报价必须匹配目标附魔 ID 和准确等级");
        }
        displayName = Objects.requireNonNull(displayName, "displayName");
        iconIdentifier = Objects.requireNonNull(iconIdentifier, "iconIdentifier").toLowerCase(Locale.ROOT);
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName 不能为空");
        if (!IDENTIFIER_PATTERN.matcher(iconIdentifier).matches()) {
            throw new IllegalArgumentException("图标 ID 必须包含合法命名空间: " + iconIdentifier);
        }
    }

    public EnchantmentTarget(
        String identifier,
        int level,
        boolean requireMaximumLevel,
        boolean completed,
        TradeOfferSnapshot tradeOffer
    ) {
        this(identifier, level, requireMaximumLevel, completed, tradeOffer, identifier, "minecraft:enchanted_book");
    }

    public EnchantmentTarget(String identifier, int level, boolean requireMaximumLevel) {
        this(identifier, level, requireMaximumLevel, false, null);
    }

    public static EnchantmentTarget resolved(
        String identifier,
        String displayName,
        String iconIdentifier,
        int maximumTradeLevel
    ) {
        return new EnchantmentTarget(
            identifier,
            maximumTradeLevel,
            true,
            false,
            null,
            displayName,
            iconIdentifier
        );
    }

    public int maximumTradeLevel() {
        return level;
    }

    public int minimumLevel() {
        return level;
    }

    public EnchantmentTarget withTradeOffer(TradeOfferSnapshot currentTradeOffer) {
        return new EnchantmentTarget(
            identifier, level, requireMaximumLevel, completed, currentTradeOffer, displayName, iconIdentifier
        );
    }

    public EnchantmentTarget withoutTradeOffer() {
        return new EnchantmentTarget(identifier, level, requireMaximumLevel, completed, null, displayName, iconIdentifier);
    }

    public EnchantmentTarget asCompleted() {
        return completed
            ? this
            : new EnchantmentTarget(
                identifier, level, requireMaximumLevel, true, tradeOffer, displayName, iconIdentifier
            );
    }
}
