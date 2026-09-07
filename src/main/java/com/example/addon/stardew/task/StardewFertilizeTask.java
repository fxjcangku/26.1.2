package com.example.addon.stardew.task;

import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.farm.FarmPacketOps;
import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.model.FertilizerResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/**
 * 星露谷施肥任务：确认未施肥 → 准备肥料 → 使用 → 验证已施肥。
 *
 * <p>施肥状态未知时安全返回 TARGET_INVALID（不盲目施肥），已施肥直接成功。</p>
 */
public final class StardewFertilizeTask implements FarmTask {

    private static final int WAIT_TICKS = 2;
    private static final int MAX_RETRIES = 2;

    private final BlockPos soilPos;
    private final Item fertilizerItem;
    private final StardewServerAdapter adapter;
    private final double reachDistance;

    private InteractionHand hand;
    private boolean acted;
    private int waitTicks;
    private int retries;

    public StardewFertilizeTask(BlockPos soilPos, Item fertilizerItem,
                                StardewServerAdapter adapter, double reachDistance) {
        this.soilPos = soilPos;
        this.fertilizerItem = fertilizerItem;
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

        // 执行前重新观察：未知停止，已施肥直接成功
        FertilizerResult state = adapter.detectFertilizer(soilPos);
        if (!state.known()) return TaskResult.TARGET_INVALID;
        if (state.fertilized()) return TaskResult.SUCCESS;

        if (!StardewTaskSupport.inReach(soilPos, reachDistance)) {
            if (!FarmNav.available()) return TaskResult.NAVIGATION_FAILED;
            if (!FarmNav.pathing()) FarmNav.goTo(soilPos, 1);
            return TaskResult.IN_PROGRESS;
        }
        FarmNav.cancel();

        if (hand == null) {
            hand = StardewTaskSupport.prepare(fertilizerItem);
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

        FertilizerResult after = adapter.detectFertilizer(soilPos);
        if (after.known() && after.fertilized()) return TaskResult.SUCCESS;

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
