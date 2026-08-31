// 附魔交易所 Baritone 移动服务实现
package com.example.addon.librarian.integration;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.service.DebugLoggerService;
import com.example.addon.librarian.service.MovementService;
import com.example.addon.librarian.service.MovementStartResult;
import com.example.addon.librarian.service.MovementStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 附魔交易所 · Baritone 移动服务实现。
 *
 * <p>通过 Baritone 寻路移动到目标村民附近。寻路仅走已有通路（禁止挖方块），
 * 启动后给予 30 tick 宽限期等待路径计算，避免误判失败。</p>
 */
public final class FabricBaritoneMovementService implements MovementService {
    // Baritone 路径计算需要若干 tick，启动后给予 30 tick 的宽限期
    private static final int MOVEMENT_GRACE_TICKS = 30;

    private final DebugLoggerService logger;
    private BlockPos target;
    private int arrivalRadius;
    private boolean movementStarted;
    private long movementStartedAtMs;
    private MovementStatus status = MovementStatus.IDLE;

    public FabricBaritoneMovementService(DebugLoggerService logger) {
        this.logger = logger;
    }

    @Override
    public MovementStartResult gotoPosition(BlockPosition position, int radiusBlocks) {
        Minecraft mc = Minecraft.getInstance();
        if (position == null || radiusBlocks < 0) return MovementStartResult.REJECTED;
        if (mc.player == null || mc.level == null) {
            status = MovementStatus.UNAVAILABLE;
            logger.error("Baritone 移动启动失败：游戏世界尚未就绪。");
            return MovementStartResult.UNAVAILABLE;
        }
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                status = MovementStatus.UNAVAILABLE;
                logger.error("Baritone 移动启动失败：当前没有可用的 Baritone 实例。");
                return MovementStartResult.UNAVAILABLE;
            }
            BlockPos nextTarget = new BlockPos(position.x(), position.y(), position.z());
            if (nextTarget.equals(target) && isMovementActive(baritone)) {
                return MovementStartResult.ALREADY_RUNNING;
            }
            target = nextTarget;
            arrivalRadius = radiusBlocks;
            movementStarted = true;
            movementStartedAtMs = System.currentTimeMillis();
            status = MovementStatus.STARTING;
            // 禁止 Baritone 挖方块寻路，只走已有的通路
            BaritoneAPI.getSettings().allowBreak.value = false;
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(nextTarget, radiusBlocks));
            status = MovementStatus.PATHING;
            return MovementStartResult.STARTED;
        } catch (LinkageError error) {
            status = MovementStatus.UNAVAILABLE;
            logger.error("Baritone 移动启动失败（LinkageError，Baritone API 未正确加载): " + error.getMessage());
            return MovementStartResult.UNAVAILABLE;
        } catch (RuntimeException exception) {
            status = MovementStatus.FAILED;
            logger.error("Baritone 移动启动失败（" + exception.getClass().getSimpleName() + "): " + exception.getMessage());
            return MovementStartResult.FAILED;
        }
    }

    @Override
    public boolean hasArrived() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return false;
        boolean arrived = mc.player.blockPosition().distSqr(target) <= (double) arrivalRadius * arrivalRadius;
        if (arrived) status = MovementStatus.ARRIVED;
        return arrived;
    }

    @Override
    public boolean isPathing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            return baritone != null && isMovementActive(baritone);
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public MovementStatus getStatus() {
        if (hasArrived()) return MovementStatus.ARRIVED;
        if (movementStarted && status == MovementStatus.PATHING && !isPathing()) {
            // 宽限期内（30 tick ≈ 1.5s）Baritone 还在计算路径，不算失败
            long elapsedMs = System.currentTimeMillis() - movementStartedAtMs;
            if (elapsedMs > MOVEMENT_GRACE_TICKS * 50L) {
                status = MovementStatus.FAILED;
            }
        }
        return status;
    }

    @Override
    public void stop() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player != null) {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) baritone.getPathingBehavior().cancelEverything();
            }
        } catch (LinkageError | RuntimeException ignored) {
        }
        target = null;
        movementStarted = false;
        status = MovementStatus.CANCELED;
    }

    @Override
    public boolean isAvailable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone() != null;
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private boolean isMovementActive(IBaritone baritone) {
        return baritone.getPathingBehavior().isPathing() || baritone.getCustomGoalProcess().isActive();
    }
}
