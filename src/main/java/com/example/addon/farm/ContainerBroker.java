package com.example.addon.farm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 容器同步层：所有箱子/潜影盒操作都强依赖 containerMenu 的同步状态，
 * 禁止在 menu 未就绪时抢跑发包，否则服务端会判定为幽灵物品并回滚。
 *
 * 26.1.2 的容器交互 API 已经从 clickSlot + SlotActionType 换成了
 * MultiPlayerGameMode#handleContainerInput(containerId, slot, button, ContainerInput, player)，
 * 同步字段也从 getRevision() 改成了 getStateId()。
 *
 * 就绪判定采取三重校验：
 * 1. 当前 Screen 必须是容器界面，且 menu 非空
 * 2. containerId 必须有效（0 是玩家自身背包，说明箱子没开成功）
 * 3. stateId 必须连续若干 tick 保持稳定，说明服务端已经把初始物品同步完
 */
public final class ContainerBroker {

    /** stateId 需要连续稳定多少 tick 才认为容器同步完成 */
    private static final int STABLE_TICKS_REQUIRED = 3;

    private int lastStateId = Integer.MIN_VALUE;
    private int stableTicks;

    /** 每次开新容器前调用，清空上一次的同步观测数据 */
    public void reset() {
        lastStateId = Integer.MIN_VALUE;
        stableTicks = 0;
    }

    /**
     * 每 tick 调用一次，推进 stateId 稳定性观测。
     * 必须在读写容器之前调用，否则 isReady() 永远不会为真。
     */
    public void tick() {
        AbstractContainerMenu menu = openMenu();
        if (menu == null) {
            reset();
            return;
        }

        int stateId = menu.getStateId();
        if (stateId == lastStateId) {
            stableTicks++;
        } else {
            lastStateId = stateId;
            stableTicks = 0;
        }
    }

    /** 容器是否已完成同步，可以安全发起槽位操作 */
    public boolean isReady() {
        return openMenu() != null && stableTicks >= STABLE_TICKS_REQUIRED;
    }

    /**
     * 取出当前打开的容器 menu。
     * 只认容器界面，玩家自身背包（containerId == 0）不算，返回 null。
     */
    public static AbstractContainerMenu openMenu() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return null;

        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null || menu.containerId == 0) return null;
        return menu;
    }

    /**
     * 容器（箱子侧）的槽位数量。
     * menu.slots 前半部分是箱子槽位，后 36 格是玩家背包，靠 Slot.container 区分。
     */
    public static int chestSlotCount(AbstractContainerMenu menu) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;

        Inventory inventory = player.getInventory();
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) count++;
        }
        return count;
    }

    /** 箱子侧是否还有空位可以接收物品 */
    public static boolean hasChestSpace(AbstractContainerMenu menu, ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        Inventory inventory = player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == inventory) continue;

            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) return true;
            // 同种物品且未满堆叠，也算有空位
            if (ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把玩家背包侧符合条件的物品 shift 点进箱子。
     * 一次只处理一个槽位，由调用方按 BPT 节流分批执行。
     *
     * @param filter 物品筛选条件
     * @return 是否实际发出了一次操作
     */
    public boolean depositOne(Predicate<ItemStack> filter) {
        if (!isReady()) return false;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return false;

        AbstractContainerMenu menu = openMenu();
        if (menu == null) return false;

        Inventory inventory = player.getInventory();
        for (Slot slot : menu.slots) {
            // 只处理玩家背包侧的槽位
            if (slot.container != inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !filter.test(stack)) continue;
            if (!hasChestSpace(menu, stack)) return false;

            quickMove(menu, slot.index);
            return true;
        }
        return false;
    }

    /**
     * 从箱子里取出指定物品到玩家背包。
     * 一次只处理一个槽位，由调用方按 BPT 节流分批执行。
     *
     * @param item 要提取的物品
     * @return 是否实际发出了一次操作
     */
    public boolean withdrawOne(Item item) {
        if (!isReady()) return false;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return false;

        AbstractContainerMenu menu = openMenu();
        if (menu == null) return false;

        // 背包没有空位时不发包，避免物品被服务端塞回箱子造成来回抖动
        if (player.getInventory().getFreeSlot() == -1) return false;

        Inventory inventory = player.getInventory();
        for (Slot slot : menu.slots) {
            // 只处理箱子侧的槽位
            if (slot.container == inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(item)) continue;

            quickMove(menu, slot.index);
            return true;
        }
        return false;
    }

    /** 统计箱子侧指定物品的总数 */
    public static int countInChest(AbstractContainerMenu menu, Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;

        Inventory inventory = player.getInventory();
        int total = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /**
     * 发送 shift 点击（快速移动）。
     * button 参数在 QUICK_MOVE 下代表左右键，固定传 0。
     */
    private static void quickMove(AbstractContainerMenu menu, int slotIndex) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        mc.gameMode.handleContainerInput(menu.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, player);
    }

    /**
     * 关闭当前容器界面。
     *
     * 必须先确认当前 Screen 真的是容器界面：player.closeContainer() 会无条件
     * 关掉当前打开的任意 Screen，若在 Meteor GUI 打开时调用（例如点击模块开关
     * 触发 onDeactivate），会把 Meteor 面板一起关掉。
     */
    public static void closeContainer() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        // 不是容器界面就不关，避免误伤 Meteor GUI
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
        player.closeContainer();
    }
}
