package com.example.addon.mining;

import com.example.addon.farm.FarmPacketOps;
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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;

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
    private int eatMoveCooldown = 0;
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
        eatMoveCooldown = 0;
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
            // 玩家背包(containerId=0)的 InventoryMenu 槽位映射：快捷栏 0-8 → 36-44，主背包 9-35 → 9-35。
            // 之前直接传 inventory 下标导致快捷栏丢到错误的槽位（0-8 对应合成/盔甲区），快捷栏垃圾永远丢不掉。
            int menuSlot = slot < 9 ? 36 + slot : slot;
            // button=1 + THROW = 丢弃整组（等价 Ctrl+Q），与 Meteor InvUtils.drop() 同语义
            mc.gameMode.handleContainerInput(0, menuSlot, 1, ContainerInput.THROW, mc.player);
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
            // 打开失败多次后加长冷却再重试，而不是永久放弃（挂后台/网络抖动时可能连续失败，
            // 永久放弃会导致玩家站在箱子前傻等，切回窗口才能继续）。
            openingCooldown = 30;
            openAttempts = 0;
            return;
        }
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return;
        }

        openingPos = pos;
        openingCooldown = 10;

        // 直接发包开箱（带 sequence 预测处理），不依赖 mc.gameMode.useItemOn：
        // 鼠标切出窗口/窗口失焦时 useItemOn 的交互会被吞，导致箱子打不开。
        FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, pos, Direction.UP);
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
     * 发送 Shift 快速移动包。
     * 26.1.2 已把旧 clickSlot + SlotActionType 换成
     * MultiPlayerGameMode#handleContainerInput(containerId, slot, button, ContainerInput, player)。
     * Meteor 的 InvUtils.shiftClick 仍走旧 API，在 26.1.2 下发包无效（物品不被移动）。
     */
    private void quickMove(AbstractContainerMenu menu, int slotIndex) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(menu.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, mc.player);
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
        if (stableStateTicks >= STABLE_REQUIRED) {
            openAttempts = 0; // 容器成功稳定打开，重置开箱尝试计数
            return true;
        }
        return false;
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

        // 一次性把所有目标矿 Shift 点进箱子（不再一格一格等冷却），服务端按序处理即可
        int moved = 0;
        StringBuilder 未匹配 = new StringBuilder();
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (isAllowedOre(stack)) {
                module.debugEvent("C", "卸货放入", "槽位=" + slot.index + " 物品=" + itemIdOf(stack) + " 目标=" + targetItemId());
                quickMove(menu, slot.index);
                moved++;
            } else if (未匹配.length() < 240) {
                // 采集「玩家背包里但被判定非目标矿」的物品，用于定位「打开箱却不放矿」的根因
                未匹配.append(itemIdOf(stack)).append(',');
            }
        }

        if (未匹配.length() > 0) {
            module.debugEvent("C", "卸货未匹配", "目标=" + targetItemId() + " 背包=" + 未匹配);
        }

        if (moved > 0) {
            actionCooldown = 1; // 下一 tick 再补扫一次，防止有遗漏
            return true;
        }
        return false;
    }

    /** 物品完整 ID（含 minecraft: 前缀），埋点用 */
    private String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 目标矿物对应掉落物 ID，埋点用 */
    private String targetItemId() {
        Block target = module.getTargetBlock();
        if (target == null) return "无";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target.asItem()).toString();
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
                quickMove(menu, slot.index);
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
     * 自动进食直到饥饿值回满。
     *
     * 修正要点：
     * 1. 从背包拿食物到热键栏这一步「只移动、不进食」，等物品到账后再吃，避免空手按住右键一直放方块。
     * 2. 用 gameMode.useItem 直接触发进食，不依赖 keyUse 按键状态（窗口失焦/开 GUI 时按键会被吞）。
     * 3. keyUse.setDown(true) 仅用于防止游戏循环主动 releaseUsingItem，保持持续进食。
     */
    public void autoEat() {
        if (mc.player == null) return;

        FoodData foodData = mc.player.getFoodData();
        if (foodData.getFoodLevel() >= 20) {
            mc.options.keyUse.setDown(false);
            return;
        }

        // 正在等待「背包→热键栏」的移动到账，期间不发重复包，也不按住右键
        if (eatMoveCooldown > 0) {
            eatMoveCooldown--;
            mc.options.keyUse.setDown(false);
            return;
        }

        var foodWhitelist = module.getFoodWhitelist();
        Inventory inventory = mc.player.getInventory();

        // 第一步：热键栏找白名单中营养值最高的食物
        int bestHotbarSlot = -1;
        int bestHotbarNutrition = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !foodWhitelist.contains(stack.getItem())) continue;
            var foodComp = stack.get(DataComponents.FOOD);
            if (foodComp == null) continue;
            if (foodComp.nutrition() > bestHotbarNutrition) {
                bestHotbarNutrition = foodComp.nutrition();
                bestHotbarSlot = i;
            }
        }

        // 第二步：热键栏没有，从背包拿一个到热键栏（只移动，不进食）
        if (bestHotbarSlot == -1) {
            int bestBackpackSlot = -1;
            int bestBackpackNutrition = 0;
            for (int i = 9; i < 36; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !foodWhitelist.contains(stack.getItem())) continue;
                var foodComp = stack.get(DataComponents.FOOD);
                if (foodComp == null) continue;
                if (foodComp.nutrition() > bestBackpackNutrition) {
                    bestBackpackNutrition = foodComp.nutrition();
                    bestBackpackSlot = i;
                }
            }

            if (bestBackpackSlot == -1) {
                mc.options.keyUse.setDown(false);
                return;
            }

            // 找一个空热键栏槽位（优先8号位）
            int emptyHotbarSlot = -1;
            for (int i = 8; i >= 0; i--) {
                if (inventory.getItem(i).isEmpty()) {
                    emptyHotbarSlot = i;
                    break;
                }
            }
            if (emptyHotbarSlot == -1) emptyHotbarSlot = 8;

            InvUtils.move().from(bestBackpackSlot).to(emptyHotbarSlot);
            eatMoveCooldown = 5; // 等 5 tick 到账
            mc.options.keyUse.setDown(false);
            return;
        }

        // 第三步：切到食物槽，直接触发进食
        InvUtils.swap(bestHotbarSlot, false);
        mc.options.keyUse.setDown(true);
        if (!mc.player.isUsingItem()) {
            // 直接 useItem 触发进食（食物使用与准星/方块无关，窗口失焦也能吃到）
            if (mc.gameMode != null) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
}
