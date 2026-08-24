package com.example.addon.farm;

import net.minecraft.core.BlockPos;

/**
 * Baritone 隔离层。
 *
 * 工程虽然把 Baritone 打包进了 META-INF/jars，但用户实例里 Baritone 仍有可能加载失败
 * （版本不匹配、被其他模组抢先加载、jar 被裁剪）。如果主模块直接硬引用 BaritoneAPI，
 * 类加载阶段就会抛 NoClassDefFoundError，整个模块连开都开不起来。
 *
 * 所以这里把全部 Baritone 调用包在 try/catch(Throwable) 里，一次失败就永久降级：
 * 之后所有导航请求直接返回 false，主模块改成"原地干活不移动"，其他功能照常。
 */
public final class FarmNav {

    /** 是否已确认 Baritone 不可用，一旦置位不再重试 */
    private static boolean disabled;

    private FarmNav() {
    }

    /** Baritone 当前是否可用 */
    public static boolean available() {
        if (disabled) return false;
        try {
            return baritone() != null;
        } catch (Throwable ignored) {
            disabled = true;
            return false;
        }
    }

    /**
     * 前往目标坐标附近。
     *
     * @param radius 允许的停靠半径，箱子交互给 3 以内，田里巡逻给 1
     * @return 是否成功下发了寻路任务
     */
    public static boolean goTo(BlockPos pos, int radius) {
        if (disabled) return false;
        try {
            var b = baritone();
            if (b == null) return false;
            b.getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalNear(pos, radius));
            return true;
        } catch (Throwable ignored) {
            disabled = true;
            return false;
        }
    }

    /** 是否正在寻路中 */
    public static boolean pathing() {
        if (disabled) return false;
        try {
            var b = baritone();
            return b != null && b.getPathingBehavior().isPathing();
        } catch (Throwable ignored) {
            disabled = true;
            return false;
        }
    }

    /** 取消当前寻路任务。模块关闭或状态切换时必须调用，否则 Baritone 会继续跑 */
    public static void cancel() {
        if (disabled) return;
        try {
            var b = baritone();
            if (b == null) return;
            b.getPathingBehavior().cancelEverything();
            b.getCustomGoalProcess().setGoal(null);
        } catch (Throwable ignored) {
            disabled = true;
        }
    }

    private static baritone.api.IBaritone baritone() {
        return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
    }
}
