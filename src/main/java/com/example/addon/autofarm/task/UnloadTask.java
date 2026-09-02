package com.example.addon.autofarm.task;

import com.example.addon.autofarm.resource.FarmResourceManager;
import com.example.addon.farm.ContainerBroker;
import net.minecraft.core.BlockPos;

/**
 * 卸货任务：把超出安全库存的产物卸入当前模式对应的专用作物箱。
 *
 * 明确区分「卸完」与「箱子满」：箱子满时返回 CONTAINER_FULL，绝不再无脑折返；
 * 种植材料的安全库存由 FarmResourceManager 截留，不会被整堆卸空。
 */
public final class UnloadTask extends ContainerTask {

    private final FarmResourceManager resources;
    private final int bpt;

    public UnloadTask(BlockPos boxPos, ContainerBroker broker, double reachDistance,
                      FarmResourceManager resources, int bpt) {
        super(boxPos, broker, reachDistance);
        this.resources = resources;
        this.bpt = bpt;
    }

    @Override
    protected TaskResult transfer() {
        // 没有可卸货物 → 卸货完成
        if (!resources.hasDepositable()) {
            ContainerBroker.closeContainer();
            broker.reset();
            return TaskResult.SUCCESS;
        }

        // 每 tick 最多搬 bpt 次
        boolean moved = false;
        for (int i = 0; i < bpt; i++) {
            if (!broker.depositOne(resources::shouldDepositItem)) break;
            moved = true;
        }

        // 一个都搬不动：要么箱子满，要么没有可卸货物
        if (!moved) {
            ContainerBroker.closeContainer();
            broker.reset();
            return resources.hasDepositable() ? TaskResult.CONTAINER_FULL : TaskResult.SUCCESS;
        }
        return TaskResult.IN_PROGRESS;
    }
}
