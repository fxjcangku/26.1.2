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
    private BlockPos openingPos = null;
    private int openingCooldown = 0;
    private int actionCooldown = 0;
    private int foodCountBeforeWithdraw = -1;
    private static final int MAX_OPEN_ATTEMPTS = 5;

    public ContainerHelper(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    public void reset() {
        currentMenu = null;
        menuStateId = -1;
        stableStateTicks = 0;
        foodCountBeforeWithdraw = -1;
        actionCooldown = 0;
        trashDisposalCooldown = 0;
        openAttempts = 0;
        openingPos = null;
        openingCooldown = 0;
        foodCountBeforeWithdraw = -1;
        actionCooldown = 0;
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
        int dx = Math.abs(mc.player.blockPosition().getX() - pos.getX());
        int dy = Math.abs(mc.player.blockPosition().getY() - pos.getY());
        int dz = Math.abs(mc.player.blockPosition().getZ() - pos.getZ());
        // 切比雪夫邻域（含对角）：与 MinerFSM.isAdjacentTo 同判定，
        // Baritone 停在对角格时同样允许开箱（interact 包距离校验足够宽松）
        if (dx > 1 || dy > 1 || dz > 1 || (dx | dy | dz) == 0) {
            return;
        }
        if (openingCooldown > 0) {
            openingCooldown--;
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen<?> screen && screen.getMenu().containerId != 0) {
            return;
        }

        openAttempts++;
        if (openAttempts > MAX_OPEN_ATTEMPTS) {
            return;
        }
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return;
        }

        openingPos = pos;
        openingCooldown = 10;

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
        openingPos = null;
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
        if (mc.player == null || mc.gameMode == null) return false;
        if (!isContainerOpen()) {
            return mc.screen instanceof AbstractContainerScreen<?>;
        }
        if (actionCooldown > 0) {
            actionCooldown--;
            return true;
        }

        AbstractContainerMenu menu = currentMenu;
        if (menu == null) return false;

        Inventory inventory = mc.player.getInventory();

        // 扫描背包侧槽位，找到矿物后 Shift 点击（每5tick一格，直到全部目标矿转移完）
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (isAllowedOre(stack)) {
                InvUtils.shiftClick().slot(slot.index);
                actionCooldown = 5;
                return true;
            }
        }

        return false;
    }

    private boolean isAllowedOre(ItemStack stack) {
        String target = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(module.getTargetBlock()).getPath();
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String expected = switch (target) {
            case "lapis_ore", "deepslate_lapis_ore" -> "minecraft:lapis_lazuli";
            case "redstone_ore", "deepslate_redstone_ore" -> "minecraft:redstone";
            case "coal_ore", "deepslate_coal_ore" -> "minecraft:coal";
            case "diamond_ore", "deepslate_diamond_ore" -> "minecraft:diamond";
            case "emerald_ore", "deepslate_emerald_ore" -> "minecraft:emerald";
            case "gold_ore", "deepslate_gold_ore", "nether_gold_ore" -> "minecraft:raw_gold";
            case "iron_ore", "deepslate_iron_ore" -> "minecraft:raw_iron";
            case "copper_ore", "deepslate_copper_ore" -> "minecraft:raw_copper";
            case "nether_quartz_ore" -> "minecraft:quartz";
            case "ancient_debris" -> "minecraft:ancient_debris";
            default -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(module.getTargetBlock().asItem()).toString();
        };
        return expected.equals(itemId) || (target.endsWith("_ore") && itemId.equals("minecraft:" + target));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  补给操作
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 从食物箱提取食物（只拿白名单内的）
     * 
     * 拿满策略：循环 Shift 点击直到背包白名单食物达到「食物阈值」，
     * 或箱子里没有更多白名单食物（拿空即止）。
     * 每格点击间隔 5 tick，等待服务端到账后再拿下一格。
     * 
     * @return true=本次补给结束（已拿满或箱子拿空）；false=还在拿（继续调用）
     */
    public boolean withdrawFood() {
        if (!isContainerOpen() || mc.player == null || mc.gameMode == null) {
            return false;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return false;
        }

        int currentFoodCount = countWhitelistedFood();

        // 已拿满（达到食物阈值），结束补给
        if (currentFoodCount >= module.getHungerThreshold()) {
            return true;
        }

        // 上一格等待到账：数量增长才视为成功
        if (foodCountBeforeWithdraw >= 0) {
            if (currentFoodCount > foodCountBeforeWithdraw) {
                foodCountBeforeWithdraw = -1; // 到账，继续拿下一格
            } else {
                return false; // 物品还在服务器端飞行，等下一tick
            }
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
                foodCountBeforeWithdraw = currentFoodCount;
                InvUtils.shiftClick().slot(slot.index);
                actionCooldown = 5;
                return false;
            }
        }

        // 箱子里已没有白名单食物（拿空即止），结束补给
        return true;
    }

    private int countWhitelistedFood() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && module.getFoodWhitelist().contains(stack.getItem()) && stack.has(DataComponents.FOOD)) count += stack.getCount();
        }
        return count;
    }

    /**
     * 自动进食直到饥饿值回满
     * 改进：
     * 1. 只吃白名单里的食物
     * 2. 先检查热键栏有没有白名单食物，没有就从背包拿
     * 3. 持续按住右键吃东西，不会被Baritone打断
     */
    public void autoEat() {
        if (mc.player == null) return;

        FoodData foodData = mc.player.getFoodData();
        
        // 已经饱了就不吃
        if (foodData.getFoodLevel() >= 20) {
            mc.options.keyUse.setDown(false);
            return;
        }

        // 获取食物白名单
        var foodWhitelist = module.getFoodWhitelist();

        Inventory inventory = mc.player.getInventory();
        int bestHotbarSlot = -1;
        int bestHotbarNutrition = 0;

        // 第一步：在热键栏（0-8）找白名单中营养值最高的食物
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            // 只吃白名单里的食物
            if (!foodWhitelist.contains(stack.getItem())) continue;

            var foodComp = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            if (foodComp == null) continue;

            int nutrition = foodComp.nutrition();
            if (nutrition > bestHotbarNutrition) {
                bestHotbarNutrition = nutrition;
                bestHotbarSlot = i;
            }
        }

        // 第二步：如果热键栏没白名单食物，从背包（9-35）找并移动到热键栏
        if (bestHotbarSlot == -1) {
            int bestBackpackSlot = -1;
            int bestBackpackNutrition = 0;

            for (int i = 9; i < 36; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;

                // 只吃白名单里的食物
                if (!foodWhitelist.contains(stack.getItem())) continue;

                var foodComp = stack.get(net.minecraft.core.component.DataComponents.FOOD);
                if (foodComp == null) continue;

                int nutrition = foodComp.nutrition();
                if (nutrition > bestBackpackNutrition) {
                    bestBackpackNutrition = nutrition;
                    bestBackpackSlot = i;
                }
            }

            // 背包也没白名单食物，放弃
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
