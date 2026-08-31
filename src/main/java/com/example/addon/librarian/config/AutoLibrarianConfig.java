// 附魔交易所 配置模型
package com.example.addon.librarian.config;

import com.example.addon.librarian.model.EnchantmentTarget;

import java.util.List;
import java.util.Objects;

/**
 * 附魔交易所 · 配置模型。
 *
 * <p>汇聚附魔交易所运行所需的全部业务参数（目标、半径、各类超时与延迟），
 * 与 Meteor 的 {@code AutoLibrarianSettings} 解耦，供编排器与状态机使用。</p>
 */
public record AutoLibrarianConfig(
    /** 目标附魔列表（至少一个） */
    List<EnchantmentTarget> enchantmentTargets,
    /** 村民搜索半径（格） */
    int villagerSearchRadius,
    /** 移动到达判定半径（格） */
    int movementArrivalRadius,
    /** 允许的最高绿宝石价格 */
    int maximumEmeraldPrice,
    /** 等待村民职业同步的超时（tick） */
    int professionTimeoutTicks,
    /** 等待交易界面打开的超时（tick） */
    int tradeScreenTimeoutTicks,
    /** 等待交易同步的超时（tick） */
    int tradeSyncTimeoutTicks,
    /** 移动超时（tick） */
    int movementTimeoutTicks,
    /** 普通业务动作间隔（tick） */
    int actionDelayTicks,
    /** 拆除与重新放置讲台的间隔（tick） */
    int resetDelayTicks,
    /** 完成后是否移除目标 */
    boolean removeCompletedTarget,
    /** 是否输出调试日志 */
    boolean debugLogging,
    /** 是否播放调试音效 */
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

    /** 默认配置：目标修复（mending 最高等级），半径 32，价格上限 64 */
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

    /** 校验数值必须大于 0 */
    private static void requirePositive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " 必须大于 0");
    }
}
