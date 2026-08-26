package com.example.addon.mining;

import com.example.addon.modules.AutoMinerModule;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 容器交互助手
 * 
 * 功能：
 * · 静默垃圾丢弃器（分频发包规避反作弊）
 * · 极速卸货流（高速 SlotClick 转移矿物）
 * · 食物提取与自动进食
 */
public final class ContainerHelper {

    private final AutoMinerModule module;
    private final Minecraft mc;

    private int trashDisposalCooldown = 0;
    private static final int TRASH_DISPOSAL_INTERVAL = 5; // 每5 tick丢一次垃圾

    private AbstractContainerMenu currentMenu = null;
    private int menuStateId = -1;
    private int stableStateTicks = 0;
    private static final int STABLE_REQUIRED = 3;

    private int openAttempts = 0;
    private static final int MAX_OPEN_ATTEMPTS = 5;

    public ContainerHelper(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    public void reset() {
        currentMenu = null;
        menuStateId = -1;
        stableStateTicks = 0;
        trashDisposalCooldown = 0;
        openAttempts = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  垃圾丢弃
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 静默垃圾丢弃器（每 tick 调用）
     * 
     * 分频发包规避反作弊：每5 tick丢一个物品
     * 
     * @param trashList 垃圾方块名单
     * @param placeBlocks Baritone搭路方块白名单（排除在丢弃外）
     */
    public void tickTrashDisposal(List<Block> trashList, List<Block> placeBlocks) {
        if (mc.player == null || trashList.isEmpty()) return;

        trashDisposalCooldown--;
        if (trashDisposalCooldown > 0) return;

        // 扫描背包找垃圾
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            Block block = Block.byItem(stack.getItem());
            
            // 排除搭路方块：如果在搭路白名单中，跳过丢弃
            if (placeBlocks.contains(block)) continue;
            
            if (trashList.contains(block)) {
                dropStack(i);
                trashDisposalCooldown = TRASH_DISPOSAL_INTERVAL;
                return;
            }
        }
    }

    /**
     * 丢弃指定槽位的物品（发送丢弃数据包）
     */
    private void dropStack(int slot) {
        if (mc.player == null || mc.gameMode == null) return;

        try {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) return;
            
            // 调用 InvUtils.drop() 正确丢弃物品
            InvUtils.drop().slot(slot);
        } catch (Exception e) {
            // 静默失败
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  容器交互
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 打开容器（发送交互包）
     */
    public void openContainer(BlockPos pos) {
        if (mc.player == null || mc.level == null) return;

        openAttempts++;
        if (openAttempts > MAX_OPEN_ATTEMPTS) {
            openAttempts = 0;
            return;
        }

        // 校验是否为容器
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return;
        }

        // 构造命中结果
        Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);

        // 发送交互包
        if (mc.gameMode != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        }
    }

    /**
     * 关闭容器
     *
     * 只在当前 Screen 确实是容器界面时才关：player.closeContainer() 会无条件关掉
     * 当前打开的任意 Screen，若在 Meteor GUI 打开时调用会把面板一起关掉。
     * 注意 containerMenu 判空没用——玩家自身背包菜单始终非 null。
     */
    public void closeContainer() {
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?>) {
            mc.player.closeContainer();
        }
        openAttempts = 0;
        currentMenu = null;
        menuStateId = -1;
        stableStateTicks = 0;
    }

    /**
     * 容器是否已打开
     */
    public boolean isContainerOpen() {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null || menu.containerId == 0) {
            return false;
        }

        // 等待 stateId 稳定
        if (currentMenu != menu) {
            currentMenu = menu;
            menuStateId = menu.getStateId();
            stableStateTicks = 0;
            return false;
        }

        if (menu.getStateId() != menuStateId) {
            menuStateId = menu.getStateId();
            stableStateTicks = 0;
            return false;
        }

        stableStateTicks++;
        return stableStateTicks >= STABLE_REQUIRED;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  卸货操作
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 卸货：把背包里的矿物 Shift 点进箱子
     * 
     * 优化策略：等待GUI稳定后按顺序快速放入
     * 
     * @return 是否还有矿物需要继续转移
     */
    public boolean depositOres() {
        if (!isContainerOpen() || mc.player == null || mc.gameMode == null) {
            return false;
        }

        AbstractContainerMenu menu = currentMenu;
        if (menu == null) return false;

        Inventory inventory = mc.player.getInventory();
        boolean foundAny = false;

        // 扫描背包侧槽位，找到矿物后 Shift 点击
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemId = stack.getItem().toString();
            if (itemId.contains("ore") || itemId.contains("raw_") || 
                itemId.contains("diamond") || itemId.contains("emerald") ||
                itemId.contains("coal") || itemId.contains("redstone") ||
                itemId.contains("lapis") || itemId.contains("quartz")) {
                
                // 使用 Shift+左键快速移动物品
                InvUtils.shiftClick().slot(slot.index);
                foundAny = true;
                
                // 每次只传输一个槽位，避免服务器延迟导致丢失
                return true;
            }
        }

        return foundAny;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  补给操作
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 从食物箱提取食物（只拿白名单内的，默认拿1组）
     * 
     * 优化策略：等待GUI稳定后快速提取
     * 
     * @return 是否成功提取
     */
    public boolean withdrawFood() {
        if (!isContainerOpen() || mc.player == null || mc.gameMode == null) {
            return false;
        }

        AbstractContainerMenu menu = currentMenu;
        if (menu == null) return false;

        Inventory inventory = mc.player.getInventory();
        List<Item> whitelist = module.getFoodWhitelist();

        // 扫描箱子侧的槽位
        for (Slot slot : menu.slots) {
            if (slot.container == inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            // 判断是否为食物且在白名单内
            var foodComp = stack.get(DataComponents.FOOD);
            if (foodComp != null && whitelist.contains(stack.getItem())) {
                // 使用 Shift+左键快速移动食物
                InvUtils.shiftClick().slot(slot.index);
                return true;
            }
        }

        return false;
    }

    /**
     * 自动进食直到饥饿值回满
     * 改进：
     * 1. 先检查热键栏有没有食物，没有就从背包拿
     * 2. 持续按住右键吃东西，不会被Baritone打断
     */
    public void autoEat() {
        if (mc.player == null) return;

        FoodData foodData = mc.player.getFoodData();
        
        // 已经饱了就不吃
        if (foodData.getFoodLevel() >= 20) {
            mc.options.keyUse.setDown(false);
            return;
        }

        Inventory inventory = mc.player.getInventory();
        int bestHotbarSlot = -1;
        int bestHotbarNutrition = 0;

        // 第一步：在热键栏（0-8）找最高营养值的食物
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            var foodComp = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            if (foodComp == null) continue;

            int nutrition = foodComp.nutrition();
            if (nutrition > bestHotbarNutrition) {
                bestHotbarNutrition = nutrition;
                bestHotbarSlot = i;
            }
        }

        // 第二步：如果热键栏没食物，从背包（9-35）找并移动到热键栏
        if (bestHotbarSlot == -1) {
            int bestBackpackSlot = -1;
            int bestBackpackNutrition = 0;

            for (int i = 9; i < 36; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;

                var foodComp = stack.get(net.minecraft.core.component.DataComponents.FOOD);
                if (foodComp == null) continue;

                int nutrition = foodComp.nutrition();
                if (nutrition > bestBackpackNutrition) {
                    bestBackpackNutrition = nutrition;
                    bestBackpackSlot = i;
                }
            }

            // 背包也没食物，放弃
            if (bestBackpackSlot == -1) {
                mc.options.keyUse.setDown(false);
                return;
            }

            // 找一个空的热键栏槽位（优先8号位）
            int emptyHotbarSlot = -1;
            for (int i = 8; i >= 0; i--) {
                if (inventory.getItem(i).isEmpty()) {
                    emptyHotbarSlot = i;
                    break;
                }
            }

            // 如果热键栏没空位，用8号位
            if (emptyHotbarSlot == -1) {
                emptyHotbarSlot = 8;
            }

            // 从背包移动食物到热键栏
            InvUtils.move().from(bestBackpackSlot).to(emptyHotbarSlot);
            bestHotbarSlot = emptyHotbarSlot;
        }

        // 第三步：切换到食物槽
        InvUtils.swap(bestHotbarSlot, false);
        
        // 持续按住右键吃东西（需要按住32 tick才能吃完）
        mc.options.keyUse.setDown(true);
    }
}
