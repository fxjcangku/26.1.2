package com.example.addon.stardew.task;

import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.farm.FarmPacketOps;
import com.example.addon.stardew.adapter.StardewServerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星露谷种植任务：前往空地 → 准备种子 → 播种 → 等待更新 → 验证作物已出现。
 *
 * <p>底盘非农田或上方已被占用时安全返回 TARGET_INVALID，材料不足返回 RESOURCE_INSUFFICIENT。</p>
 */
public final class StardewPlantTask implements FarmTask {

    private static final int WAIT_TICKS = 2;
    private static final int MAX_RETRIES = 2;

    private final BlockPos soilPos;
    private final Item seedItem;
    private final StardewServerAdapter adapter;
    private final double reachDistance;

    private InteractionHand hand;
    private boolean acted;
    private int waitTicks;
    private int retries;

    public StardewPlantTask(BlockPos soilPos, Item seedItem,
                            StardewServerAdapter adapter, double reachDistance) {
        this.soilPos = soilPos;
        this.seedItem = seedItem;
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

        // 执行前校验：底盘仍为农田且上方为空
        if (!acted) {
            if (!adapter.isSoil(soilPos)) return TaskResult.TARGET_INVALID;
            BlockState above = mc.level.getBlockState(soilPos.above());
            if (!above.isAir()) return TaskResult.TARGET_INVALID;
        }

        if (!StardewTaskSupport.inReach(soilPos, reachDistance)) {
            if (!FarmNav.available()) return TaskResult.NAVIGATION_FAILED;
            if (!FarmNav.pathing()) FarmNav.goTo(soilPos, 1);
            return TaskResult.IN_PROGRESS;
        }
        FarmNav.cancel();

        if (hand == null) {
            hand = StardewTaskSupport.prepare(seedItem);
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

        if (plantSucceeded()) return TaskResult.SUCCESS;

        if (retries < MAX_RETRIES) {
            retries++;
            acted = false;
            waitTicks = 0;
            return TaskResult.IN_PROGRESS;
        }
        return TaskResult.PLANT_FAILED;
    }

    @Override
    public boolean exclusive() {
        return false;
    }

    @Override
    public void cancel() {
        FarmNav.cancel();
    }

    private boolean plantSucceeded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockState above = mc.level.getBlockState(soilPos.above());
        return adapter.recognizeCrop(above).known();
    }
}
