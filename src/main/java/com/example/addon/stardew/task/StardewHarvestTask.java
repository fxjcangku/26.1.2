package com.example.addon.stardew.task;

import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.farm.FarmPacketOps;
import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.model.MaturityResult;
import com.example.addon.stardew.model.StardewCropProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星露谷收割任务：破坏成熟作物 → 等待更新 → 验证已不再成熟。
 *
 * <p>执行前重新观察成熟状态，未知成熟（unknown）安全返回 TARGET_INVALID，宁可不收也不误收。</p>
 */
public final class StardewHarvestTask implements FarmTask {

    private static final int WAIT_TICKS = 2;
    private static final int MAX_RETRIES = 2;

    private final BlockPos soilPos;
    private final StardewCropProfile crop;
    private final StardewServerAdapter adapter;
    private final double reachDistance;

    private boolean acted;
    private int waitTicks;
    private int retries;

    public StardewHarvestTask(BlockPos soilPos, StardewCropProfile crop,
                              StardewServerAdapter adapter, double reachDistance) {
        this.soilPos = soilPos;
        this.crop = crop;
        this.adapter = adapter;
        this.reachDistance = reachDistance;
    }

    public BlockPos soilPos() {
        return soilPos;
    }

    public StardewCropProfile crop() {
        return crop;
    }

    private BlockPos cropPos() {
        return soilPos.above();
    }

    @Override
    public TaskResult tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return TaskResult.TARGET_INVALID;

        BlockPos target = cropPos();

        // 执行前校验：只在尚未破坏时重新观察成熟状态，未知/未成熟安全停止
        if (!acted) {
            MaturityResult maturity = adapter.detectMaturity(target, mc.level.getBlockState(target));
            if (!maturity.known() || !maturity.mature()) return TaskResult.TARGET_INVALID;
        }

        if (!StardewTaskSupport.inReach(target, reachDistance)) {
            if (!FarmNav.available()) return TaskResult.NAVIGATION_FAILED;
            if (!FarmNav.pathing()) FarmNav.goTo(target, 1);
            return TaskResult.IN_PROGRESS;
        }
        FarmNav.cancel();

        if (!acted) {
            FarmPacketOps.breakBlock(target, Direction.UP);
            acted = true;
            waitTicks = 0;
            return TaskResult.IN_PROGRESS;
        }

        if (waitTicks < WAIT_TICKS) {
            waitTicks++;
            return TaskResult.IN_PROGRESS;
        }

        if (harvestSucceeded(target)) return TaskResult.SUCCESS;

        if (retries < MAX_RETRIES) {
            retries++;
            acted = false;
            waitTicks = 0;
            return TaskResult.IN_PROGRESS;
        }
        return TaskResult.HARVEST_FAILED;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    @Override
    public void cancel() {
        FarmNav.cancel();
    }

    /** 收割成功：方块已变成空气，或已确定为「已知且未成熟」状态（未知状态不算成功，避免误判） */
    private boolean harvestSucceeded(BlockPos target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(target);
        if (state.isAir()) return true;
        MaturityResult maturity = adapter.detectMaturity(target, state);
        return maturity.known() && !maturity.mature();
    }
}
