package com.example.addon.autofarm.task;

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

    private final int bpt;

    public PoisonDumpTask(BlockPos boxPos, ContainerBroker broker, double reachDistance, int bpt) {
        super(boxPos, broker, reachDistance);
        this.bpt = bpt;
    }

    @Override
    protected TaskResult transfer() {
        // 每 tick 最多搬 bpt 次，只读本地菜单 menu.slots 判定，
        // 避免与真实背包数据源交叉造成「本地已清空但真实背包未同步」的误判
        boolean moved = false;
        ContainerBroker.DepositResult last = ContainerBroker.DepositResult.NONE;
        for (int i = 0; i < bpt; i++) {
            last = broker.depositOne(stack -> stack.is(Items.POISONOUS_POTATO));
            if (last == ContainerBroker.DepositResult.MOVED) {
                moved = true;
                continue;
            }
            break;
        }

        if (moved) return TaskResult.IN_PROGRESS;
        if (last == ContainerBroker.DepositResult.NOT_READY) return TaskResult.IN_PROGRESS;

        ContainerBroker.closeContainer();
        broker.reset();
        return last == ContainerBroker.DepositResult.CHEST_FULL
            ? TaskResult.POISON_CONTAINER_FULL
            : TaskResult.SUCCESS;
    }
}
