package com.example.addon.autofarm.navigation;

import net.minecraft.core.BlockPos;

/**
 * Baritone 隔离层。
 *
 * 与旧实现的关键区别：不再用「一次失败永久 disabled」的降级策略。
 * 每次调用独立判断，失败返回 false，由 Controller 把当前移动任务标记为
 * NavigationFailed 并重新规划，绝不因 Baritone 不可用而卡死或无限循环。
 */
public final class FarmNav {

    private FarmNav() {
    }

    /** Baritone 当前是否可用 */
    public static boolean available() {
        try {
            return baritone() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 前往目标坐标附近。
     *
     * @param radius 停靠半径（GoalNear 语义为距离平方）
     * @return 是否成功下发寻路任务
     */
    public static boolean goTo(BlockPos pos, int radius) {
        return goTo(pos, radius, false);
    }

    /**
     * 前往目标坐标附近。
     *
     * @param radius       停靠半径（GoalNear 语义为距离平方）
     * @param modifyBlocks 是否允许 Baritone 破坏/放置方块（回中心点等场景需要，拾取等场景禁止）
     * @return 是否成功下发寻路任务
     */
    public static boolean goTo(BlockPos pos, int radius, boolean modifyBlocks) {
        try {
            var b = baritone();
            if (b == null) return false;
            // 拾取/收割/补种等寻路禁止破坏与放置方块，避免把竹子/仙人掌等作物挖掉；
            // 回中心点寻路则需要允许，否则被栅栏/障碍挡住会半路停下。
            var settings = baritone.api.BaritoneAPI.getSettings();
            settings.allowBreak.value = modifyBlocks;
            settings.allowPlace.value = modifyBlocks;
            b.getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalNear(pos, radius));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 是否正在寻路中 */
    public static boolean pathing() {
        try {
            var b = baritone();
            return b != null && b.getPathingBehavior().isPathing();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 取消当前寻路任务 */
    public static void cancel() {
        try {
            var b = baritone();
            if (b == null) return;
            b.getPathingBehavior().cancelEverything();
            b.getCustomGoalProcess().setGoal(null);
        } catch (Throwable ignored) {
        }
    }

    /** 玩家是否已到达目标附近（用于任务层判断能否交互） */
    public static boolean arrived(BlockPos pos, double reach) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return false;
            double dx = pos.getX() + 0.5 - mc.player.getX();
            double dy = pos.getY() + 0.5 - mc.player.getY();
            double dz = pos.getZ() + 0.5 - mc.player.getZ();
            return dx * dx + dy * dy + dz * dz <= reach * reach;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static baritone.api.IBaritone baritone() {
        return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
    }
}
