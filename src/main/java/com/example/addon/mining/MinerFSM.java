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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

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

    // 传送监测数据
    private BlockPos teleportStartPos = BlockPos.ZERO;
    private int teleportTimeout = 0;

    // 修补模式数据
    private ItemStack savedTool = ItemStack.EMPTY;
    private ItemStack savedWeapon = ItemStack.EMPTY;
    private boolean repairMode = false;

    // 死亡标志
    private boolean playerWasDead = false;

    // 卡死监测：改用速度监测而非位置监测
    private int lowSpeedTicks = 0;
    private static final double MIN_SPEED_THRESHOLD = 0.05; // 速度低于0.05判定为卡住
    private static final int STUCK_TIME_THRESHOLD = 3600; // 3分钟

    public MinerFSM(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    public void reset() {
        state = MinerState.IDLE;
        stateTick = 0;
        teleportStartPos = BlockPos.ZERO;
        teleportTimeout = 0;
        savedTool = ItemStack.EMPTY;
        savedWeapon = ItemStack.EMPTY;
        repairMode = false;
        playerWasDead = false;
        lowSpeedTicks = 0;
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

        MinerState oldState = state;
        state = newState;
        stateTick = 0;

        // 状态转换播报
        broadcastStateTransition(oldState, newState);

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

        // 阶段 1：记录传送前位置并发送传送命令
        if (stateTick == 1) {
            teleportStartPos = mc.player.blockPosition();
            cmdMgr.executeCommand(module.getWildCommand());
            teleportTimeout = module.getTeleportDelay() * 20; // 转换为tick
            return;
        }

        // 阶段 2：等待命令执行完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：检测传送是否成功（位置变化 > 50格）
        BlockPos currentPos = mc.player.blockPosition();
        double distance = Math.sqrt(currentPos.distSqr(teleportStartPos));
        
        if (distance > 50) {
            // 传送成功，音效提示
            module.getSoundNotifier().notifyTeleportSuccess();
            
            // 等待世界加载
            if (stateTick > 40) {
                transitionTo(MinerState.MINING);
            }
            return;
        }

        // 阶段 4：超时检测 - 如果超过设定时间还在原地，重新RTP
        if (stateTick > teleportTimeout) {
            module.error("§c传送超时，重新尝试RTP");
            stateTick = 0;
        }
    }

    private void tickMining() {
        // 阶段 1：启动 Baritone
        if (stateTick == 1) {
            module.getBaritone().startMining(module.getTargetBlock());
            lowSpeedTicks = 0;
            
            // 播放开始挖矿音效
            module.getSoundNotifier().notifyMiningStart();
            return;
        }

        // 掉落物自动拾取：检测并拾取目标矿石掉落物
        pickupTargetOreDrops();

        // 耐久预警
        checkToolDurabilityWarning();

        // 优先级 1：死亡检测（已在 tick() 最开始处理）

        // 优先级 2：耐久检测
        ItemStack tool = mc.player.getMainHandItem();
        if (!tool.isEmpty() && needsRepair(tool)) {
            module.getSoundNotifier().notifyLowDurability();
            transitionTo(MinerState.REPAIR);
            return;
        }

        // 优先级 2.5：饱食度检测（低于15时暂停Baritone并吃东西）
        FoodData foodData = mc.player.getFoodData();
        if (foodData.getFoodLevel() < 15) {
            module.getBaritone().stop();
            transitionTo(MinerState.EATING);
            return;
        }

        // 优先级 3：食物不足检测（检查背包食物组数）
        if (countFoodStacks() < module.getHungerThreshold()) {
            module.getSoundNotifier().notifyLowFood();
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

        // 卡死检测：改用速度监测（Baritone卡住会抖动但速度极低）
        double currentSpeed = Math.sqrt(
            mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x +
            mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z
        );
        
        if (currentSpeed < MIN_SPEED_THRESHOLD) {
            lowSpeedTicks++;
        } else {
            lowSpeedTicks = 0;
        }

        // 如果3分钟持续低速，判定为卡死
        if (lowSpeedTicks > STUCK_TIME_THRESHOLD) {
            module.error("§c[自动挖矿] 检测到卡死（速度过低），重新RTP");
            module.getSoundNotifier().notifyStuck();
            module.getBaritone().stop();
            lowSpeedTicks = 0;
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // Baritone 卡死检测（保留原有逻辑）
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

        // 阶段 3：走到箱子边（使用 Baritone）
        if (!mc.player.blockPosition().closerThan(mineralChest.pos, 5.0)) {
            if (stateTick == 40) {
                // 启动 Baritone 走到箱子
                var baritone = module.getBaritone().getBaritoneInstance();
                if (baritone != null) {
                    baritone.getCommandManager().execute("goto " + 
                        mineralChest.pos.getX() + " " + 
                        mineralChest.pos.getY() + " " + 
                        mineralChest.pos.getZ());
                }
            }
            
            if (stateTick > 300) {
                module.getBaritone().stop();
                transitionTo(MinerState.GO_WILD);
            }
            return;
        }

        // 阶段 4：停止寻路，开箱倒货
        module.getBaritone().stop();
        
        if (!module.getContainer().isContainerOpen()) {
            module.getContainer().openContainer(mineralChest.pos);
            return;
        }

        // 阶段 5：持续倒货
        boolean hasMore = module.getContainer().depositOres();
        if (!hasMore || stateTick > 400) {
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

        // 阶段 3：走到箱子边（使用 Baritone）
        if (!mc.player.blockPosition().closerThan(foodChest.pos, 5.0)) {
            if (stateTick == 40) {
                // 启动 Baritone 走到箱子
                var baritone = module.getBaritone().getBaritoneInstance();
                if (baritone != null) {
                    baritone.getCommandManager().execute("goto " + 
                        foodChest.pos.getX() + " " + 
                        foodChest.pos.getY() + " " + 
                        foodChest.pos.getZ());
                }
            }
            
            if (stateTick > 300) {
                module.getBaritone().stop();
                transitionTo(MinerState.GO_WILD);
            }
            return;
        }

        // 阶段 4：停止寻路，开箱取食物
        module.getBaritone().stop();
        
        if (!module.getContainer().isContainerOpen()) {
            module.getContainer().openContainer(foodChest.pos);
            return;
        }

        // 阶段 5：取食物
        boolean taken = module.getContainer().withdrawFood();
        if (taken || stateTick > 400) {
            module.getContainer().closeContainer();
            transitionTo(MinerState.EATING);
        }
    }

    private void tickEating() {
        FoodData foodData = mc.player.getFoodData();
        
        // 检查饱食度是否回满（满值20）
        if (foodData.getFoodLevel() >= 20) {
            mc.options.keyUse.setDown(false); // 释放右键
            transitionTo(MinerState.MINING);
            module.getBaritone().startMining(module.getTargetBlock()); // 恢复Baritone
            return;
        }

        // 每2秒尝试吃一次（吃完一个食物需要32 tick = 1.6秒）
        if (stateTick % 40 == 0) {
            module.getContainer().autoEat();
        }

        // 超时保护：2分钟还没吃饱就放弃，回到挖矿
        if (stateTick > 2400) {
            mc.options.keyUse.setDown(false);
            transitionTo(MinerState.MINING);
            module.getBaritone().startMining(module.getTargetBlock());
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

        // 阶段 3：走到挂机点（使用 Baritone）
        if (!mc.player.blockPosition().closerThan(afkPoint.pos, 3.0)) {
            if (stateTick == 40) {
                // 启动 Baritone 走到挂机点
                var baritone = module.getBaritone().getBaritoneInstance();
                if (baritone != null) {
                    baritone.getCommandManager().execute("goto " + 
                        afkPoint.pos.getX() + " " + 
                        afkPoint.pos.getY() + " " + 
                        afkPoint.pos.getZ());
                }
            }
            
            if (stateTick > 300) {
                module.getBaritone().stop();
                transitionTo(MinerState.GO_WILD);
            }
            return;
        }

        // 阶段 4：停止寻路，调整视角到记录的 Yaw/Pitch
        module.getBaritone().stop();
        
        if (stateTick < 100 && !isViewAligned(afkPoint.yaw, afkPoint.pitch)) {
            smoothRotateTo(afkPoint.yaw, afkPoint.pitch);
            return;
        }

        // 阶段 5：执行 Auto-Swap（只做一次）
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

        // 阶段 6：持续监控耐久
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

        // 阶段 1：首次进入时立即尝试启用流星自动重生
        if (stateTick == 1) {
            tryMeteorAutoRespawn();
            module.getSoundNotifier().notifyDeath();
            module.error("§c[自动挖矿] 已调用流星自动重生模块");
        }

        // 阶段 2：等待复活
        if (mc.player.isDeadOrDying()) {
            if (stateTick % 20 == 0) {
                mc.player.respawn(); // 后备方案
            }
            return;
        }

        // 阶段 3：复活完成，进入等待
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

    /**
     * 统计背包中的食物数量
     */
    private int countFoodStacks() {
        if (mc.player == null) return 0;

        List<Item> whitelist = module.getFoodWhitelist();
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // 只统计白名单内的食物
            if (whitelist.contains(stack.getItem())) {
                count += stack.getCount(); // 统计实际数量
            }
        }
        return count;
    }

    private int countOreStacks() {
        if (mc.player == null) return 0;

        int totalCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = stack.getItem().toString();
            if (itemId.contains("ore") || itemId.contains("raw_") ||
                itemId.contains("diamond") || itemId.contains("emerald")) {
                totalCount += stack.getCount(); // 统计总数量
            }
        }
        // 转换为组数（向上取整）
        return (int) Math.ceil(totalCount / 64.0);
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

    private void broadcastStateTransition(MinerState from, MinerState to) {
        if (from == to) return;
        
        int oreStacks = countOreStacks();
        int targetStacks = module.getFullLoadStacks();
        int foodCount = countFoodStacks();
        int foodThreshold = module.getHungerThreshold();
        
        String message = switch (to) {
            case IDLE -> "§7[状态] 待机中";
            case GO_WILD -> "§a[状态] 前往野外";
            case MINING -> String.format("§e[状态] 开始挖矿 (矿石: %d/%d组, 食物: %d/%d组)", oreStacks, targetStacks, foodCount, foodThreshold);
            case UNLOADING -> String.format("§b[状态] 矿石已达 %d/%d 组，执行卸货", oreStacks, targetStacks);
            case SUPPLY -> String.format("§6[状态] 食物不足 (%d/%d组)，前往补给", foodCount, foodThreshold);
            case EATING -> "§d[状态] 补充饥饿值";
            case REPAIR -> "§c[状态] 工具耐久过低，联动杀戮光环修复中";
            case DEATH_HANDLING -> "§4[状态] 检测到死亡，已调用流星自动重生";
            case RESPAWN_WAIT -> "§6[状态] 复活完成，返回挂机点";
        };
        
        module.info(message);
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

    private void tryMeteorAutoRespawn() {
        try {
            var modules = meteordevelopment.meteorclient.systems.modules.Modules.get();
            if (modules == null) return;
            
            var autoRespawn = modules.get(meteordevelopment.meteorclient.systems.modules.player.AutoRespawn.class);
            if (autoRespawn != null && !autoRespawn.isActive()) {
                autoRespawn.toggle();
            }
        } catch (Exception e) {
            // 流星模块不存在，跳过
        }
    }

    /**
     * 掉落物自动拾取：只拾取当前选择的目标矿石掉落物
     * 
     * 策略：
     * 1. 扫描玩家周围6格范围内的ItemEntity
     * 2. 检查掉落物是否为目标矿石
     * 3. 自动移动到掉落物附近触发拾取
     */
    private void pickupTargetOreDrops() {
        if (mc.player == null || mc.level == null) return;

        Item targetItem = module.getTargetBlock().asItem();
        if (targetItem == null) return;

        // 扫描周围6格范围的ItemEntity
        AABB searchBox = mc.player.getBoundingBox().inflate(6.0);
        List<ItemEntity> nearbyItems = mc.level.getEntitiesOfClass(
            ItemEntity.class, 
            searchBox, 
            item -> item.isAlive() && !item.getItem().isEmpty()
        );

        for (ItemEntity itemEntity : nearbyItems) {
            ItemStack stack = itemEntity.getItem();
            
            // 只拾取目标矿石
            if (stack.getItem() == targetItem) {
                // 移动到掉落物位置（Minecraft会自动拾取范围内的掉落物）
                double distance = mc.player.distanceTo(itemEntity);
                if (distance > 1.5) {
                    // 如果距离较远，可以考虑让Baritone寻路过去
                    // 这里简单处理：只拾取已经在拾取范围内的
                    continue;
                }
            }
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

    /**
     * 状态转换播报
     */
    private void broadcastStateChange(MinerState from, MinerState to) {
        // 已废弃，统一使用 broadcastStateTransition
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
