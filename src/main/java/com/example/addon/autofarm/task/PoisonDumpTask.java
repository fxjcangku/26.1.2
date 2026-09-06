package com.example.addon.autofarm.task;

import com.example.addon.farm.ContainerBroker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 杂物卸货任务：把背包里的杂物（毒马铃薯 + 仙人掌花）全部卸入独立的杂物箱。
 *
 * 杂物绝不进入单/双/多作物箱；杂物箱满时返回 POISON_CONTAINER_FULL，
 * 由 Controller 明确记录，不无限循环、也不塞进普通箱。
 */
public final class PoisonDumpTask extends ContainerTask {

    private final int bpt;
    private final Predicate<ItemStack> depositFilter;

    public PoisonDumpTask(BlockPos boxPos, ContainerBroker broker, double reachDistance,
                          int bpt, Predicate<ItemStack> depositFilter) {
        super(boxPos, broker, reachDistance);
        this.bpt = bpt;
        this.depositFilter = depositFilter;
    }

    @Override
    protected TaskResult transfer() {
        // 每 tick 最多搬 bpt 次，只读本地菜单 menu.slots 判定，
        // 避免与真实背包数据源交叉造成「本地已清空但真实背包未同步」的误判
        boolean moved = false;
        ContainerBroker.DepositResult last = ContainerBroker.DepositResult.NONE;
        for (int i = 0; i < bpt; i++) {
            last = broker.depositOne(depositFilter);
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
