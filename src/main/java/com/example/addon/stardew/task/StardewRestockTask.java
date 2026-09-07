package com.example.addon.stardew.task;

import com.example.addon.autofarm.task.ContainerTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.farm.ContainerBroker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;

import java.util.function.IntSupplier;

/**
 * 星露谷补货任务：从种子箱提取指定种子直到安全库存，复用现有 {@link ContainerTask}。
 *
 * <p>只补缺的那一种种子；箱子无货返回 CONTAINER_EMPTY（资源耗尽），绝不把「有其它种子」
 * 当成「这种也补好了」。</p>
 */
public final class StardewRestockTask extends ContainerTask {

    private final Item item;
    private final int safetyStock;
    private final IntSupplier count;

    private int syncCooldown;

    public StardewRestockTask(BlockPos boxPos, ContainerBroker broker, double reachDistance,
                              Item item, int safetyStock, IntSupplier count) {
        super(boxPos, broker, reachDistance);
        this.item = item;
        this.safetyStock = safetyStock;
        this.count = count;
    }

    public Item item() {
        return item;
    }

    @Override
    protected TaskResult transfer() {
        // 冷却中：等待上一次取货到账，避免本地菜单已空而真实背包未同步造成误判
        if (syncCooldown > 0) {
            syncCooldown--;
            return TaskResult.IN_PROGRESS;
        }

        // 已补到安全库存
        if (count.getAsInt() >= safetyStock) {
            ContainerBroker.closeContainer();
            broker.reset();
            return TaskResult.SUCCESS;
        }

        // 从箱子提取一个
        if (broker.withdrawOne(item)) {
            syncCooldown = 2;
            return TaskResult.IN_PROGRESS;
        }

        // 取不出：用本地菜单确认箱子侧是否真的没有该物品
        if (countInChest(item) == 0) {
            ContainerBroker.closeContainer();
            broker.reset();
            return TaskResult.CONTAINER_EMPTY;
        }
        return TaskResult.IN_PROGRESS;
    }

    private int countInChest(Item target) {
        AbstractContainerMenu menu = ContainerBroker.openMenu();
        if (menu == null) return 0;
        return ContainerBroker.countInChest(menu, target);
    }
}
