// 附魔交易所 配置模型
package com.example.addon.librarian.config;

import com.example.addon.librarian.model.EnchantmentTarget;

import java.util.List;
import java.util.Objects;

public record AutoLibrarianConfig(
    List<EnchantmentTarget> enchantmentTargets,
    int villagerSearchRadius,
    int movementArrivalRadius,
    int maximumEmeraldPrice,
    int professionTimeoutTicks,
    int tradeScreenTimeoutTicks,
    int tradeSyncTimeoutTicks,
    int movementTimeoutTicks,
    int actionDelayTicks,
    int resetDelayTicks,
    boolean removeCompletedTarget,
    boolean debugLogging,
    boolean debugSound
) {
    public AutoLibrarianConfig {
        enchantmentTargets = List.copyOf(Objects.requireNonNull(enchantmentTargets, "enchantmentTargets"));
        if (enchantmentTargets.isEmpty()) throw new IllegalArgumentException("至少需要一个目标附魔");
        requirePositive(villagerSearchRadius, "villagerSearchRadius");
        requirePositive(movementArrivalRadius, "movementArrivalRadius");
        requirePositive(maximumEmeraldPrice, "maximumEmeraldPrice");
        requirePositive(professionTimeoutTicks, "professionTimeoutTicks");
        requirePositive(tradeScreenTimeoutTicks, "tradeScreenTimeoutTicks");
        requirePositive(tradeSyncTimeoutTicks, "tradeSyncTimeoutTicks");
        requirePositive(movementTimeoutTicks, "movementTimeoutTicks");
        requirePositive(actionDelayTicks, "actionDelayTicks");
        requirePositive(resetDelayTicks, "resetDelayTicks");
    }

    public static AutoLibrarianConfig defaults() {
        return new AutoLibrarianConfig(
            List.of(new EnchantmentTarget("minecraft:mending", 1, true)),
            32,
            3,
            64,
            200,
            100,
            100,
            1200,
            2,
            10,
            false,
            true,
            true
        );
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " 必须大于 0");
    }
}
