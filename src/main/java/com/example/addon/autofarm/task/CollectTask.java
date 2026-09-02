package com.example.addon.autofarm.task;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 拾取任务：在收割点附近等待掉落物自然进入背包。
 *
 * 成功标准是「附近不再有掉落物」这一实际观察结果，而非固定等待若干 tick。
 * 掉落物被其他玩家捡走不算 Harvest 失败，超出附近范围的掉落物直接忽略，绝不用 Baritone 追远。
 * 前几个 tick 是掉落物生成宽限期，避免物品实体还没刷出来就误判「拾取完成」。
 */
public final class CollectTask implements FarmTask {

    /** 掉落物生成宽限期（tick）：给服务端时间把破坏后的产物刷成 ItemEntity */
    private static final int SPAWN_GRACE = 15;
    /** 观察兜底上限（tick）：防止附近持续有刷新掉落物导致无限等待 */
    private static final int MAX_OBSERVE = 200;

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

        // 附近有掉落物 → 继续等待自然拾取（超过观察上限则兜底结束，防止无限等待）
        if (hasItemNearby()) {
            return waited >= MAX_OBSERVE ? TaskResult.SUCCESS : TaskResult.IN_PROGRESS;
        }

        // 附近无掉落物，且已过生成宽限期 → 观察判定拾取完成
        if (waited >= SPAWN_GRACE) return TaskResult.SUCCESS;
        return TaskResult.IN_PROGRESS;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    @Override
    public void cancel() {
    }

    /** 玩家附近收集范围内是否存在掉落物 */
    private boolean hasItemNearby() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        double rangeSq = collectRange * collectRange;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity && entity.distanceToSqr(mc.player) <= rangeSq) {
                return true;
            }
        }
        return false;
    }
}
