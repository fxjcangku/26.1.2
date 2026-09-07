package com.example.addon.stardew.integration;

import com.example.addon.farm.ContainerBroker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 现有资源管理 / 卸货 / 补货体系的星露谷接入层。
 *
 * <p>只做「我需要什么资源、背包里有多少、是否有可卸货物」这类薄封装，
 * 真正的容器同步 / 卸货 / 补货仍由 {@link ContainerBroker}、
 * {@link com.example.addon.autofarm.task.ContainerTask} 与
 * {@link com.example.addon.autofarm.task.UnloadTask} 完成，不另造
 * StardewResourceManager / StardewUnloadManager / StardewRestockManager。</p>
 */
public final class ResourceManagerIntegration {

    private final ContainerBroker broker;

    public ResourceManagerIntegration(ContainerBroker broker) {
        this.broker = broker;
    }

    public ContainerBroker broker() {
        return broker;
    }

    /** 背包里指定物品的总数量（主背包 36 格 + 副手），与现有资源管理器同口径 */
    public int countItem(Item item) {
        if (item == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) total += stack.getCount();
        }
        if (mc.player.getOffhandItem().is(item)) total += mc.player.getOffhandItem().getCount();
        return total;
    }

    /** 背包里是否存在满足筛选条件的物品 */
    public boolean hasDepositable(Predicate<ItemStack> filter) {
        if (filter == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && filter.test(stack)) return true;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        return !offhand.isEmpty() && filter.test(offhand);
    }
}
