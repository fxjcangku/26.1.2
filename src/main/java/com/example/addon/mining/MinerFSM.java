package com.example.addon.mining;

import com.example.addon.commands.WKCommand;
import com.example.addon.modules.AutoMinerModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

/**
 * 挖矿状态机 - FSM 核心引擎
 * 
 * 状态转换流程：
 * IDLE → GO_WILD → MINING → [UNLOADING/SUPPLY/REPAIR] → GO_WILD → MINING ...
 * 
 * 死亡事件拦截：
 * ANY_STATE → DEATH_HANDLING → RESPAWN_WAIT → GO_WILD
 */
public final class MinerFSM {

    private final AutoMinerModule module;
    private final Minecraft mc;

    private MinerState state = MinerState.IDLE;
    private int stateTick = 0;

    // 修补模式数据
    private ItemStack savedTool = ItemStack.EMPTY;
    private ItemStack savedWeapon = ItemStack.EMPTY;
    private boolean repairMode = false;

    // 死亡标志
    private boolean playerWasDead = false;

    public MinerFSM(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    public void reset() {
        state = MinerState.IDLE;
        stateTick = 0;
        savedTool = ItemStack.EMPTY;
        savedWeapon = ItemStack.EMPTY;
        repairMode = false;
        playerWasDead = false;
    }

    public void tick() {
        if (mc.player == null || mc.level == null) return;

        // 死亡事件拦截（最高优先级）
        if (mc.player.isDeadOrDying() && !playerWasDead) {
            playerWasDead = true;
            transitionTo(MinerState.DEATH_HANDLING);
            return;
        }

        // 复活检测
        if (playerWasDead && !mc.player.isDeadOrDying()) {
            playerWasDead = false;
            transitionTo(MinerState.RESPAWN_WAIT);
        }

        stateTick++;

        switch (state) {
            case IDLE -> tickIdle();
            case GO_WILD -> tickGoWild();
            case MINING -> tickMining();
            case UNLOADING -> tickUnloading();
            case SUPPLY -> tickSupply();
            case EATING -> tickEating();
            case REPAIR -> tickRepair();
            case DEATH_HANDLING -> tickDeathHandling();
            case RESPAWN_WAIT -> tickRespawnWait();
        }
    }

    private void transitionTo(MinerState newState) {
        if (state == newState) return;

        // 状态退出清理
        onStateExit(state);

        state = newState;
        stateTick = 0;

        // 状态进入初始化
        onStateEnter(newState);
    }

    private void onStateEnter(MinerState newState) {
        if (newState == MinerState.MINING) {
            module.getBaritone().startMining(module.getTargetBlock());
        }
    }

    private void onStateExit(MinerState oldState) {
        if (oldState == MinerState.MINING) {
            module.getBaritone().stop();
        }
        if (oldState == MinerState.REPAIR) {
            stopKillAura();
            restoreHotbar();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  状态处理
    // ═══════════════════════════════════════════════════════════════════

    private void tickIdle() {
        // 启动时立即进入去野外
        transitionTo(MinerState.GO_WILD);
    }

    private void tickGoWild() {
        CommandManager cmdMgr = module.getCmdManager();

        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getWildCommand());
            return;
        }

        // 等待区块加载完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 加载完成，开始挖矿
        transitionTo(MinerState.MINING);
    }

    private void tickMining() {
        // 每次进入 MINING 状态时启动 Baritone
        if (stateTick == 1) {
            module.getBaritone().startMining(module.getTargetBlock());
        }

        // 耐久预警
        checkToolDurabilityWarning();

        // 优先级 1：死亡检测（已在 tick() 最开始处理）

        // 优先级 2：耐久检测
        ItemStack tool = mc.player.getMainHandItem();
        if (!tool.isEmpty() && needsRepair(tool)) {
            transitionTo(MinerState.REPAIR);
            return;
        }

        // 优先级 3：饥饿检测
        FoodData food = mc.player.getFoodData();
        if (food.getFoodLevel() < module.getHungerThreshold()) {
            transitionTo(MinerState.SUPPLY);
            return;
        }

        // 优先级 4：满载检测
        if (countOreStacks() >= module.getUnloadThreshold()) {
            transitionTo(MinerState.UNLOADING);
            return;
        }

        // Baritone 异常自愈
        if (!module.getBaritone().isPathing() && stateTick > 60) {
            if (stateTick % 60 == 0) {
                module.getBaritone().stop();
                module.getBaritone().startMining(module.getTargetBlock());
            }
        }

        // Baritone 卡死检测
        if (stateTick > 6000 && stateTick % 1200 == 0) {
            if (module.getBaritone().isStuck()) {
                module.getBaritone().stop();
                transitionTo(MinerState.GO_WILD);
            }
        }
    }

    private void checkToolDurabilityWarning() {
        ItemStack tool = mc.player.getMainHandItem();
        if (tool.isEmpty()) return;

        Integer maxDamage = tool.get(DataComponents.MAX_DAMAGE);
        Integer damage = tool.get(DataComponents.DAMAGE);
        if (maxDamage == null || damage == null) return;

        int remaining = maxDamage - damage;
        int threshold = module.getDurabilityThreshold();

        if (remaining <= threshold * 1.5 && remaining > threshold) {
            if (stateTick % 600 == 0) {
                // 耐久预警（静默）
            }
        }
    }

    private void tickUnloading() {
        CommandManager cmdMgr = module.getCmdManager();
        WKCommand.WKData mineralChest = WKCommand.getMineralChest();

        if (mineralChest == null) {
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // 阶段 1：传送到矿物箱
        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getUnloadCommand());
            return;
        }

        // 阶段 2：等待传送完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：走到箱子边
        if (!mc.player.blockPosition().closerThan(mineralChest.pos, 5.0)) {
            if (stateTick > 100) {
                transitionTo(MinerState.GO_WILD);
            }
            return;
        }

        // 阶段 4：开箱倒货
        if (!module.getContainer().isContainerOpen()) {
            module.getContainer().openContainer(mineralChest.pos);
            return;
        }

        // 阶段 5：持续倒货
        boolean hasMore = module.getContainer().depositOres();
        if (!hasMore || stateTick > 200) {
            module.getContainer().closeContainer();
            transitionTo(MinerState.GO_WILD);
        }
    }

    private void tickSupply() {
        CommandManager cmdMgr = module.getCmdManager();
        WKCommand.WKData foodChest = WKCommand.getFoodChest();

        if (foodChest == null) {
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // 阶段 1：传送到补给点
        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getSupplyCommand());
            return;
        }

        // 阶段 2：等待传送完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：走到箱子边
        if (!mc.player.blockPosition().closerThan(foodChest.pos, 5.0)) {
            if (stateTick > 100) {
                transitionTo(MinerState.GO_WILD);
            }
            return;
        }

        // 阶段 4：开箱取食物
        if (!module.getContainer().isContainerOpen()) {
            module.getContainer().openContainer(foodChest.pos);
            return;
        }

        // 阶段 5：取食物
        boolean taken = module.getContainer().withdrawFood();
        if (taken || stateTick > 200) {
            module.getContainer().closeContainer();
            transitionTo(MinerState.EATING);
        }
    }

    private void tickEating() {
        FoodData food = mc.player.getFoodData();

        // 已吃饱
        if (food.getFoodLevel() >= 20) {
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // 每秒尝试进食
        if (stateTick % 20 == 0) {
            module.getContainer().autoEat();
        }

        // 超时保护
        if (stateTick > 2400) {
            transitionTo(MinerState.GO_WILD);
        }
    }

    private void tickRepair() {
        WKCommand.WKData afkPoint = WKCommand.getAFKPoint();

        if (afkPoint == null) {
            transitionTo(MinerState.GO_WILD);
            return;
        }

        CommandManager cmdMgr = module.getCmdManager();

        // 阶段 1：传送到挂机点
        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getAFKCommand());
            return;
        }

        // 阶段 2：等待传送完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：调整视角到记录的 Yaw/Pitch
        if (stateTick < 40 && !isViewAligned(afkPoint.yaw, afkPoint.pitch)) {
            smoothRotateTo(afkPoint.yaw, afkPoint.pitch);
            return;
        }

        // 阶段 4：执行 Auto-Swap（只做一次）
        if (!repairMode) {
            savedTool = mc.player.getMainHandItem().copy();
            savedWeapon = findWeaponInHotbar();

            // 工具切副手
            InvUtils.move().from(0).toOffhand();

            // 武器切主手
            if (!savedWeapon.isEmpty()) {
                int weaponSlot = findSlotInHotbar(savedWeapon);
                if (weaponSlot != -1) {
                    InvUtils.swap(weaponSlot, false);
                }
            }

            repairMode = true;
            startKillAura();
            return;
        }

        // 阶段 5：持续监控耐久
        ItemStack currentTool = mc.player.getOffhandItem();
        if (currentTool.isEmpty() || isFullyRepaired(currentTool)) {
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // 超时保护：10分钟没修满
        if (stateTick > 12000) {
            stopKillAura();
            if (module.isActive()) module.toggle();
        }
    }

    private boolean isViewAligned(float targetYaw, float targetPitch) {
        if (mc.player == null) return false;
        
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float deltaYaw = Math.abs(targetYaw - currentYaw);
        float deltaPitch = Math.abs(targetPitch - currentPitch);

        if (deltaYaw > 180) deltaYaw = 360 - deltaYaw;

        return deltaYaw < 5.0f && deltaPitch < 5.0f;
    }

    private void tickDeathHandling() {
        if (mc.player == null) return;

        // 阶段 1：等待复活
        if (mc.player.isDeadOrDying()) {
            if (stateTick % 20 == 0) {
                // 每秒尝试复活
                mc.player.respawn();
            }
            return;
        }

        // 阶段 2：复活完成，进入等待
        transitionTo(MinerState.RESPAWN_WAIT);
    }

    private void tickRespawnWait() {
        CommandManager cmdMgr = module.getCmdManager();

        // 等待复活完成（玩家不再是死亡状态）
        if (mc.player.isDeadOrDying()) {
            return;
        }

        // 阶段 1：执行死亡重返指令
        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getRespawnCommand());
            return;
        }

        // 阶段 2：等待传送完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：返回野外恢复挖矿
        transitionTo(MinerState.GO_WILD);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════════

    private boolean needsRepair(ItemStack tool) {
        if (tool.isEmpty()) return false;
        Integer maxDamage = tool.get(DataComponents.MAX_DAMAGE);
        Integer damage = tool.get(DataComponents.DAMAGE);
        if (maxDamage == null || damage == null) return false;
        int remaining = maxDamage - damage;
        return remaining < module.getDurabilityThreshold();
    }

    private boolean isFullyRepaired(ItemStack tool) {
        if (tool.isEmpty()) return true;
        Integer damage = tool.get(DataComponents.DAMAGE);
        return damage == null || damage <= 5; // 接近满耐久
    }

    private int countOreStacks() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = stack.getItem().toString();
            if (itemId.contains("ore") || itemId.contains("raw_") ||
                itemId.contains("diamond") || itemId.contains("emerald")) {
                count += (stack.getCount() + 63) / 64;
            }
        }
        return count;
    }

    private ItemStack findWeaponInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            String id = stack.getItem().toString();
            if (id.contains("sword") || id.contains("axe")) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private int findSlotInHotbar(ItemStack target) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                return i;
            }
        }
        return -1;
    }

    private void smoothRotateTo(float targetYaw, float targetPitch) {
        if (mc.player == null) return;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float deltaYaw = targetYaw - currentYaw;
        float deltaPitch = targetPitch - currentPitch;

        // 归一化角度到 [-180, 180]
        while (deltaYaw > 180) deltaYaw -= 360;
        while (deltaYaw < -180) deltaYaw += 360;

        // 平滑插值
        float smoothYaw = currentYaw + deltaYaw * 0.1f;
        float smoothPitch = currentPitch + deltaPitch * 0.1f;

        mc.player.setYRot(smoothYaw);
        mc.player.setXRot(smoothPitch);
    }

    private void startKillAura() {
        KillAura killAura = Modules.get().get(KillAura.class);
        if (killAura != null && !killAura.isActive()) {
            killAura.toggle();
        }
    }

    private void stopKillAura() {
        KillAura killAura = Modules.get().get(KillAura.class);
        if (killAura != null && killAura.isActive()) {
            killAura.toggle();
        }
    }

    private void restoreHotbar() {
        if (mc.player == null) return;

        // 工具换回主手（从副手取回到第一个空槽）
        InvUtils.move().fromOffhand().to(0);

        repairMode = false;
        savedTool = ItemStack.EMPTY;
        savedWeapon = ItemStack.EMPTY;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  状态枚举
    // ═══════════════════════════════════════════════════════════════════

    public enum MinerState {
        IDLE("待机"),
        GO_WILD("前往野外"),
        MINING("采掘中"),
        UNLOADING("卸货中"),
        SUPPLY("补给中"),
        EATING("进食中"),
        REPAIR("修补中"),
        DEATH_HANDLING("死亡处理"),
        RESPAWN_WAIT("复活等待");

        private final String cn;

        MinerState(String cn) {
            this.cn = cn;
        }

        public String cn() {
            return cn;
        }
    }
}
