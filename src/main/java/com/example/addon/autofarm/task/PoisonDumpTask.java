package com.example.addon.autofarm.task;

import com.example.addon.autofarm.resource.FarmResourceManager;
import com.example.addon.farm.ContainerBroker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

/**
 * 毒马铃薯处理任务：把背包里的毒马铃薯全部卸入独立的毒马铃薯箱。
 *
 * 毒马铃薯绝不进入单/双/三作物箱；毒马铃薯箱满时返回 POISON_CONTAINER_FULL，
 * 由 Controller 明确记录，不无限循环、也不塞进普通箱。
 */
public final class PoisonDumpTask extends ContainerTask {

    private final FarmResourceManager resources;
    private final int bpt;

    public PoisonDumpTask(BlockPos boxPos, ContainerBroker broker, double reachDistance,
                          FarmResourceManager resources, int bpt) {
        super(boxPos, broker, reachDistance);
        this.resources = resources;
        this.bpt = bpt;
    }

    @Override
    protected TaskResult transfer() {
        // 没有毒马铃薯 → 完成
        if (resources.countPoisonousPotato() == 0) {
            ContainerBroker.closeContainer();
            broker.reset();
            return TaskResult.SUCCESS;
        }

        boolean moved = false;
        for (int i = 0; i < bpt; i++) {
            if (!broker.depositOne(stack -> stack.is(Items.POISONOUS_POTATO))) break;
            moved = true;
        }

        if (!moved) {
            ContainerBroker.closeContainer();
            broker.reset();
            // 还有毒马铃薯但搬不动 → 毒箱满
            return resources.countPoisonousPotato() > 0
                ? TaskResult.POISON_CONTAINER_FULL
                : TaskResult.SUCCESS;
        }
        return TaskResult.IN_PROGRESS;
    }
}
