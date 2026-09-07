package com.example.addon.stardew.task;

import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.farm.FarmPacketOps;
import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.model.WaterState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/**
 * 星露谷浇水任务：确认干旱 → 准备浇水工具 → 使用 → 验证变湿润。
 *
 * <p>浇水状态未知时安全返回 TARGET_INVALID（不盲目浇水），已湿润直接成功。</p>
 */
public final class StardewWaterTask implements FarmTask {

    private static final int WAIT_TICKS = 2;
    private static final int MAX_RETRIES = 2;

    private final BlockPos soilPos;
    private final Item toolItem;
    private final StardewServerAdapter adapter;
    private final double reachDistance;

    private InteractionHand hand;
    private boolean acted;
    private int waitTicks;
    private int retries;

    public StardewWaterTask(BlockPos soilPos, Item toolItem,
                            StardewServerAdapter adapter, double reachDistance) {
        this.soilPos = soilPos;
        this.toolItem = toolItem;
        this.adapter = adapter;
        this.reachDistance = reachDistance;
    }

    public BlockPos soilPos() {
        return soilPos;
    }

    @Override
    public TaskResult tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return TaskResult.TARGET_INVALID;

        // 执行前重新观察：未知停止，已湿润直接成功
        WaterState state = adapter.detectWaterState(soilPos, mc.level.getBlockState(soilPos)).state();
        if (state == WaterState.UNKNOWN) return TaskResult.TARGET_INVALID;
        if (state == WaterState.WET) return TaskResult.SUCCESS;

        if (!StardewTaskSupport.inReach(soilPos, reachDistance)) {
            if (!FarmNav.available()) return TaskResult.NAVIGATION_FAILED;
            if (!FarmNav.pathing()) FarmNav.goTo(soilPos, 1);
            return TaskResult.IN_PROGRESS;
        }
        FarmNav.cancel();

        if (hand == null) {
            hand = StardewTaskSupport.prepare(toolItem);
            if (hand == null) return TaskResult.RESOURCE_INSUFFICIENT;
        }

        if (!acted) {
            FarmPacketOps.useOnBlock(hand, soilPos);
            acted = true;
            waitTicks = 0;
            return TaskResult.IN_PROGRESS;
        }

        if (waitTicks < WAIT_TICKS) {
            waitTicks++;
            return TaskResult.IN_PROGRESS;
        }

        WaterState after = adapter.detectWaterState(soilPos, mc.level.getBlockState(soilPos)).state();
        if (after == WaterState.WET) return TaskResult.SUCCESS;

        if (retries < MAX_RETRIES) {
            retries++;
            acted = false;
            waitTicks = 0;
            return TaskResult.IN_PROGRESS;
        }
        return TaskResult.TARGET_INVALID;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    @Override
    public void cancel() {
        FarmNav.cancel();
    }
}
