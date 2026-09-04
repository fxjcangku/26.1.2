package com.example.addon.autofarm.task;

import com.example.addon.autofarm.navigation.FarmNav;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 拾取任务：在收割点附近拾取掉落物。
 *
 * 成功标准是「收割点附近不再有掉落物」这一实际观察结果，而非固定等待若干 tick。
 * 掉落物在磁吸范围外时主动导航过去，让磁吸生效；Baritone 不可用则兜底等待后结束。
 * 前几个 tick 是掉落物生成宽限期，避免物品实体还没刷出来就误判「拾取完成」。
 */
public final class CollectTask implements FarmTask {

    /** 掉落物生成宽限期（tick）：给服务端时间把破坏后的产物刷成 ItemEntity */
    private static final int SPAWN_GRACE = 15;
    /** 观察兜底上限（tick）：防止附近持续有刷新掉落物导致无限等待 */
    private static final int MAX_OBSERVE = 200;
    /** 玩家磁吸掉落物的半径平方（约 1.5 格），在此范围内等待自然入包 */
    private static final double PICKUP_RADIUS_SQ = 2.25;

    private final BlockPos pos;
    private final double collectRange;
    private int waited;

    public CollectTask(BlockPos pos, double collectRange) {
        this.pos = pos;
        this.collectRange = collectRange;
    }

    @Override
    public TaskResult tick() {
        waited++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return TaskResult.IN_PROGRESS;

        ItemEntity nearest = nearestItem(mc);
        if (nearest == null) {
            // 附近无掉落物，且已过生成宽限期 → 观察判定拾取完成
            return waited >= SPAWN_GRACE ? TaskResult.SUCCESS : TaskResult.IN_PROGRESS;
        }

        // 掉落物在磁吸范围外 → 主动导航过去，让磁吸生效
        if (mc.player.distanceToSqr(nearest) > PICKUP_RADIUS_SQ) {
            if (!FarmNav.available()) {
                // 无法导航：兜底等待，超过观察上限结束，避免无限卡住
                return waited >= MAX_OBSERVE ? TaskResult.SUCCESS : TaskResult.IN_PROGRESS;
            }
            if (!FarmNav.pathing()) FarmNav.goTo(nearest.blockPosition(), 1);
            return TaskResult.IN_PROGRESS;
        }
        FarmNav.cancel();

        // 掉落物在磁吸范围内 → 等待自然入包（兜底超时）
        return waited >= MAX_OBSERVE ? TaskResult.SUCCESS : TaskResult.IN_PROGRESS;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    @Override
    public void cancel() {
        FarmNav.cancel();
    }

    /** 收割点附近收集范围内最近的掉落物（以收割点为中心，而非玩家） */
    private ItemEntity nearestItem(Minecraft mc) {
        double rangeSq = collectRange * collectRange;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        ItemEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item) {
                double d = item.distanceToSqr(cx, cy, cz);
                if (d <= rangeSq && d < bestSq) {
                    bestSq = d;
                    best = item;
                }
            }
        }
        return best;
    }
}
