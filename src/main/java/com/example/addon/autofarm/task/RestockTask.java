package com.example.addon.autofarm.task;

import com.example.addon.autofarm.model.CropProfile;
import com.example.addon.autofarm.resource.FarmResourceManager;
import com.example.addon.farm.ContainerBroker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/**
 * 补货任务：从当前模式专用作物箱提取某个作物的种植材料，直到达到安全库存。
 *
 * 只补具体缺的那一种资源；箱子无货时返回 CONTAINER_EMPTY（明确资源耗尽），
 * 绝不把「有其它作物种子」当成「这个作物也补好了」。
 */
public final class RestockTask extends ContainerTask {

    private final FarmResourceManager resources;
    private final CropProfile crop;

    public RestockTask(BlockPos boxPos, ContainerBroker broker, double reachDistance,
                       FarmResourceManager resources, CropProfile crop) {
        super(boxPos, broker, reachDistance);
        this.resources = resources;
        this.crop = crop;
    }

    @Override
    protected TaskResult transfer() {
        Item plantItem = crop.plantItem();

        // 已补到安全库存 → 完成
        if (resources.countItem(plantItem) >= resources.safetyStock(crop)) {
            ContainerBroker.closeContainer();
            broker.reset();
            return TaskResult.SUCCESS;
        }

        // 从箱子提取一个种植材料
        boolean moved = broker.withdrawOne(plantItem);
        if (!moved) {
            // 箱子没有该物品 → 资源耗尽
            ContainerBroker.closeContainer();
            broker.reset();
            return TaskResult.CONTAINER_EMPTY;
        }
        return TaskResult.IN_PROGRESS;
    }
}
