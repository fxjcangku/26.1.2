package com.example.addon.mining;

import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalTwoBlocks;
import com.example.addon.commands.WKCommand;
import com.example.addon.modules.AutoMinerModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private int teleportRetries = 0;
    private static final int MAX_TELEPORT_RETRIES = 3; // 传送失败最多自动重试 3 次

    // 种子模式采集数据
    private final Set<BlockPos> seedVisited = new HashSet<>(); // 本轮已挖/已跳过的预测位置
    private BlockPos seedTarget = null;                        // 当前锁定的预测矿
    private int seedBreakState = 0;                            // 0=未开始破坏 1=破坏推进中
    private int seedBreakTicks = 0;                            // 当前方块破坏耗时
    private Direction seedBreakFace = Direction.UP;            // 锁定后的破坏面（start/continue 必须一致，否则服务端重置破坏进度）
    private int seedPathRetries = 0;                           // 同一目标寻路重发次数

    // 修补模式数据
    private ItemStack savedTool = ItemStack.EMPTY;
    private ItemStack savedWeapon = ItemStack.EMPTY;
    private int savedToolSlot = -1;
    private int savedWeaponSlot = -1;
    private boolean repairMode = false;
    private boolean repairPathIssued = false;
    private int repairSwapAttempts = 0;
    private int repairSwapRequestedTick = -1;
    private boolean unloadingPathIssued = false;
    private boolean supplyPathIssued = false;
    private boolean killAuraWasOnBefore = false; // 进入修补前 KillAura 是否本来就开着（避免误关用户自己的 KA）
    private int supplyFailCount = 0;             // 补给空手连续计数（2 次箱空直接停机，防 SUPPLY↔MINING 死循环）

    // 潜影盒打包机换盒等待
    private boolean boxSwapWaiting = false;      // 关箱后等待打包机推盒+放新盒
    private int boxSwapTicks = 0;                // 换盒等待计时
    private int boxSwapCount = 0;                // 本轮卸货累计换盒次数
    private static final int BOX_SWAP_WAIT_TICKS = 40; // 换盒等待 2 秒（40 tick）
    private static final int MAX_BOX_SWAPS = 10;       // 换盒次数上限（防打包机坏了死循环）
    private int noContainerTicks = 0;                  // 标点位置无容器持续 tick
    private static final int NO_CONTAINER_TIMEOUT = 200; // 无容器 10 秒（200 tick）后停机

    // 死亡标志
    private boolean playerWasDead = false;

    // 卡死监测：改用速度监测而非位置监测
    private int lowSpeedTicks = 0;
    private int waterStuckTicks = 0;
    private boolean waterEscapeActive = false; // 水中脱困寻路是否进行中
    private int waterEscapeTicks = 0;          // 水中脱困寻路已持续时间
    private BlockPos lastPosSample = BlockPos.ZERO; // 原地抖动卡死的位置采样点
    private int noMoveTicks = 0;                    // 位移长时间不变的累计 tick
    private int stuckResetCount = 0;                // 连续原地抖动卡死次数（超过阈值才传送去野外）
    private static final double MIN_SPEED_THRESHOLD = 0.05; // 速度低于0.05判定为卡住
    private static final int STUCK_TIME_THRESHOLD = 3600;
    private static final int NO_MOVE_THRESHOLD = 3600; // 3分钟位移<2格判定原地抖动
    private static final int PATH_TIMEOUT_TICKS = 2400;

    // 自动捡取掉落物
    private ItemEntity pickupTarget = null;
    private int pickupTimeout = 0;

    public MinerFSM(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    public void reset() {
        state = MinerState.IDLE;
        stateTick = 0;
        teleportStartPos = BlockPos.ZERO;
        teleportTimeout = 0;
        teleportRetries = 0;
        seedVisited.clear();
        seedTarget = null;
        seedBreakState = 0;
        seedBreakTicks = 0;
        seedBreakFace = Direction.UP;
        seedPathRetries = 0;
        savedTool = ItemStack.EMPTY;
        savedWeapon = ItemStack.EMPTY;
        savedToolSlot = -1;
        savedWeaponSlot = -1;
        repairMode = false;
        repairPathIssued = false;
        repairSwapAttempts = 0;
        unloadingPathIssued = false;
        supplyPathIssued = false;
        killAuraWasOnBefore = false;
        supplyFailCount = 0;
        boxSwapWaiting = false;
        boxSwapTicks = 0;
        boxSwapCount = 0;
        noContainerTicks = 0;
        playerWasDead = false;
        lowSpeedTicks = 0;
        waterStuckTicks = 0;
        waterEscapeActive = false;
        waterEscapeTicks = 0;
        lastPosSample = BlockPos.ZERO;
        noMoveTicks = 0;
        stuckResetCount = 0;
        pickupTarget = null;
        pickupTimeout = 0;
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
        if (newState == MinerState.UNLOADING || newState == MinerState.SUPPLY || newState == MinerState.REPAIR || newState == MinerState.DEATH_HANDLING) {
            module.getContainer().closeContainer();
            module.getBaritone().stop();
        }

        // 物流寻路（卸货/补给/修补）用独立开关控制是否破坏方块；回到挖矿恢复全局破坏设置
        if (newState == MinerState.UNLOADING || newState == MinerState.SUPPLY || newState == MinerState.REPAIR) {
            module.getBaritone().updateSetting("allowBreak", module.isLogisticsBreakBlocks());
        }
        if (newState == MinerState.MINING) {
            module.getBaritone().updateSetting("allowBreak", module.getAllowBreak());
        }

        if (newState == MinerState.REPAIR) {
            repairPathIssued = false;
            repairSwapAttempts = 0;
            repairSwapRequestedTick = -1;
            repairMode = false;
            savedToolSlot = -1;
            savedWeaponSlot = -1;
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
            // 只有首次尝试需要记录起点，重试时起点不变（人还在原地）
            if (teleportRetries == 0) teleportStartPos = mc.player.blockPosition();
            cmdMgr.executeCommand(module.getWildCommand());
            teleportTimeout = module.getTeleportDelay() * 20; // 转换为tick
            return;
        }

        // 阶段 2：等待命令执行完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：检测传送是否成功（原地没动才算失败；RTP 插件可能只移几十格，同样算成功）
        BlockPos currentPos = mc.player.blockPosition();
        double distance = Math.sqrt(currentPos.distSqr(teleportStartPos));

        if (distance > 2) {
            teleportRetries = 0;
            module.getSoundNotifier().notifyTeleportSuccess();
            transitionTo(MinerState.MINING);
            return;
        }

        // 阶段 4：超时仍在原地 → 自动重发传送指令（最多 3 次），仍失败才停机
        if (stateTick > teleportTimeout) {
            if (teleportRetries < MAX_TELEPORT_RETRIES) {
                teleportRetries++;
                module.error("§e⚠ 传送未生效 §8▸ 自动重试 " + teleportRetries + "/" + MAX_TELEPORT_RETRIES);
                stateTick = 0; // 回到阶段 1 重新发指令
                return;
            }
            module.error("§c✗ 传送失败 §8▸ 已重试 " + MAX_TELEPORT_RETRIES + " 次，自动停止挖矿");
            if (module.isActive()) module.toggle();
        }
    }

    private void tickMining() {
        boolean seedMode = module.isSeedMiningEnabled();

        // 阶段 1：启动采集引擎（普通模式启动 Baritone mine；种子模式由自有循环驱动）
        if (stateTick == 1) {
            lowSpeedTicks = 0;
            seedTarget = null;
            seedBreakState = 0;
            seedBreakTicks = 0;
            seedVisited.clear();

            if (!seedMode) {
                module.getBaritone().startMining(module.getTargetBlocks());
            }

            // 播放开始挖矿音效
            module.getSoundNotifier().notifyMiningStart();
            return;
        }

        // 耐久预警
        checkToolDurabilityWarning();

        // 优先级 1：死亡检测（已在 tick() 最开始处理）

        // 优先级 2：耐久检测（镐子必备；镐/铲/斧/锄/剑任一工具低于阈值都触发修复）
        ItemStack tool = findMiningPickaxe();
        if (tool.isEmpty()) {
            module.getBaritone().stop();
            module.error("§c✗ 缺少镐子 §8▸ 自动挖矿已停止");
            if (module.isActive()) module.toggle();
            return;
        }
        if (findDamagedToolSlot() != -1) {
            ItemStack damaged = findDamagedTool();
            // 挂机修复依赖经验修补附魔（打怪掉经验修工具），没有则去修复点也白挂到超时，直接停机
            if (!hasMending(damaged)) {
                module.getBaritone().stop();
                module.error("§c✗ " + toolName(damaged) + "无经验修补附魔 §8▸ 无法自动修复，请换有经验修补的工具");
                if (module.isActive()) module.toggle();
                return;
            }
            module.getSoundNotifier().notifyLowDurability();
            transitionTo(MinerState.REPAIR);
            return;
        }

        // 优先级 2.5：饱食度检测（低于15时暂停进食；背包没白名单食物则直接去补给）
        FoodData foodData = mc.player.getFoodData();
        if (foodData.getFoodLevel() < 15) {
            module.getBaritone().stop();
            if (hasFoodToEat()) {
                transitionTo(MinerState.EATING);
            } else {
                // 没吃的还进进食状态会死循环（超时→MINING→又饿→又进食），必须转补给
                transitionTo(MinerState.SUPPLY);
            }
            return;
        }

        // 优先级 3：食物不足检测（检查背包食物组数）
        if (countFoodStacks() < module.getHungerThreshold()) {
            module.getSoundNotifier().notifyLowFood();
            transitionTo(MinerState.SUPPLY);
            return;
        }

        // 优先级 4：满载检测
        int oreStacks = countOreStacks();
        if (oreStacks >= module.getUnloadThreshold()) {
            transitionTo(MinerState.UNLOADING);
            return;
        }

        // 自动捡起目标矿掉落物（漏捡补偿；背包未满时才捡）
        if (tryPickupNearbyOre()) return;

        // 按模式推进采集
        if (seedMode) {
            tickSeedMining();
        } else {
            // 普通模式自愈：仅当 Baritone mine 进程意外退出时才重启。
            // 正常挖掘中并非时刻处于寻路状态（扫描/破坏时 isPathing 为 false），
            // 不能一见「没在寻路」就重启，否则每几秒重扫一遍矿、打断破坏进度。
            if (stateTick > 120 && stateTick % 60 == 0
                && !module.getBaritone().isPathing()
                && !module.getBaritone().isMiningActive()) {
                module.getBaritone().startMining(module.getTargetBlocks());
            }
        }

        // 卡死检测（两种模式共用）：速度监测，3 分钟持续低速判定卡死
        double currentSpeed = Math.sqrt(
            mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x +
            mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z
        );

        if (currentSpeed < MIN_SPEED_THRESHOLD) {
            lowSpeedTicks++;
        } else {
            lowSpeedTicks = 0;
        }

        if (lowSpeedTicks > STUCK_TIME_THRESHOLD) {
            module.error("§c✗ 检测到卡死（速度过低）§8▸ 重新前往野外");
            module.getSoundNotifier().notifyStuck();
            module.getBaritone().stop();
            lowSpeedTicks = 0;
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // 原地抖动卡死检测（每 10 秒采样位置）：Baritone 在点位附近原地抖动的通病，
        // 抖动时速度不为 0 抓不到，需按位移判断。连续 3 分钟位移<2格判定卡死。
        if (stateTick % 200 == 0) {
            BlockPos curPos = mc.player.blockPosition();
            if (!lastPosSample.equals(BlockPos.ZERO) && curPos.distSqr(lastPosSample) < 4.0) {
                noMoveTicks += 200;
            } else {
                noMoveTicks = 0;
                stuckResetCount = 0; // 玩家在正常移动，重置连续卡死计数
            }
            lastPosSample = curPos;
        }

        if (noMoveTicks > NO_MOVE_THRESHOLD) {
            module.getSoundNotifier().notifyStuck();
            module.getBaritone().stop();
            noMoveTicks = 0;
            stuckResetCount++;
            if (stuckResetCount >= 2) {
                // 连续两次抖动卡死，重置采掘目标也脱不了困，才传送去野外
                stuckResetCount = 0;
                module.error("§c✗ 原地抖动卡死（连续两次）§8▸ 重新前往野外");
                transitionTo(MinerState.GO_WILD);
            } else {
                // 先重置采掘目标脱困（换一个矿点），不急着传送
                module.info("§e⚠ 原地抖动卡死 §8▸ 重置采掘目标脱困");
                if (seedMode) {
                    if (seedTarget != null) {
                        seedVisited.add(seedTarget);
                        module.getOrePredictor().forgetOre(seedTarget);
                        seedTarget = null;
                        seedBreakState = 0;
                        seedBreakTicks = 0;
                    }
                } else {
                    module.getBaritone().startMining(module.getTargetBlocks());
                }
            }
            return;
        }

        // 水中卡死：先寻路到最近陆地脱困，避免直接 RTP（水中抖动会取消服务器传送读条）
        if (mc.player.isInWater()) {
            if (currentSpeed < MIN_SPEED_THRESHOLD) {
                waterStuckTicks++;
            } else {
                waterStuckTicks = 0;
                if (waterEscapeActive) waterEscapeActive = false;
            }

            if (waterStuckTicks > 600 && !waterEscapeActive) {
                module.getSoundNotifier().notifyStuck();
                module.getBaritone().stop();
                BlockPos land = findNearestLand();
                if (land != null) {
                    var baritone = module.getBaritone().getBaritoneInstance();
                    if (baritone != null) {
                        baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(land));
                        waterEscapeActive = true;
                        waterEscapeTicks = 0;
                        module.info("§e⚠ 水中卡死 §8▸ 寻路到最近陆地脱困");
                    } else {
                        waterStuckTicks = 0;
                        transitionTo(MinerState.GO_WILD);
                        return;
                    }
                } else {
                    module.error("§c✗ 水中卡死且周围无陆地 §8▸ 重新前往野外");
                    waterStuckTicks = 0;
                    transitionTo(MinerState.GO_WILD);
                    return;
                }
            }
        } else {
            waterStuckTicks = 0;
            waterEscapeActive = false;
        }

        // 水中脱困推进：已上岸则恢复挖矿；超时仍未脱困则 RTP 兜底
        if (waterEscapeActive) {
            waterEscapeTicks++;
            if (!mc.player.isInWater()) {
                waterEscapeActive = false;
                waterEscapeTicks = 0;
                module.getBaritone().stop();
                module.info("§a✓ 已脱离水域 §8▸ 继续挖矿");
                if (!seedMode) {
                    module.getBaritone().startMining(module.getTargetBlocks());
                }
                return;
            }
            if (waterEscapeTicks > 400) { // 20 秒仍未脱困，RTP 兜底
                waterEscapeActive = false;
                waterEscapeTicks = 0;
                module.getBaritone().stop();
                module.error("§c✗ 水中脱困超时 §8▸ 重新前往野外");
                transitionTo(MinerState.GO_WILD);
                return;
            }
            return; // 脱困寻路中，暂停其它挖矿逻辑
        }

        // Baritone 卡死检测（普通模式专用，种子模式由 seedPathRetries 兜底）
        if (!seedMode && stateTick > 6000 && stateTick % 1200 == 0) {
            if (module.getBaritone().isStuck()) {
                module.getBaritone().stop();
                transitionTo(MinerState.GO_WILD);
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  种子模式采集循环（只挖预测真矿，忽略假矿）
    //  流程：挑最近预测矿 → 寻路到位 → 视角锁定 → 原版进度合法破坏 → 下一块
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickSeedMining() {
        BaritoneExecutor baritone = module.getBaritone();

        // 无目标 → 挑选最近的一块实测矿
        if (seedTarget == null) {
            seedTarget = findNextSeedTarget();
            if (seedTarget == null) {
                // 新区块的实测扫描尚未完成 → 继续等待，不要误判「没矿」急着 RTP
                if (module.getOrePredictor().hasPendingScans()) return;
                // RTP 后区块分帧加载，若立即判空会在区块到位前误 RTP（传送到树上/高处尤其明显）。
                // 600 tick（30秒）内还有未加载区块就先等，超时则强制换区防卡死。
                if (stateTick < 600 && module.getOrePredictor().hasUnloadedChunksInRange(mc.player.blockPosition(), 64)) return;
                module.info("§e⚠ [种子挖矿] 附近实测矿点已挖完 §8▸ 重新前往野外换区域");
                seedVisited.clear();
                transitionTo(MinerState.GO_WILD);
                return;
            }
            ensureBestToolForSeedTarget();
            seedPathRetries = 0;
            if (!baritone.pathToOre(seedTarget)) {
                seedVisited.add(seedTarget);
                seedTarget = null;
            }
            return;
        }

        // 目标方块已不是目标矿（已挖完/预测偏差）→ 移出缓存并换下一块
        if (!isTargetOreAt(seedTarget)) {
            seedBreakState = 0;
            seedBreakTicks = 0;
            seedVisited.add(seedTarget);
            module.getOrePredictor().forgetOre(seedTarget);
            seedTarget = null;
            return;
        }

        // 未到位：等待寻路；寻路中断每 0.5 秒重发，同一目标发 6 次仍不到就放弃
        if (mc.player.blockPosition().distSqr(seedTarget) > 25.0) {
            if (!baritone.isCustomGoalActive() && stateTick % 10 == 0) {
                seedPathRetries++;
                if (seedPathRetries > 6) {
                    module.warning("§e⚠ [种子挖矿] 该预测矿无法到达 §8▸ 跳过");
                    seedVisited.add(seedTarget);
                    module.getOrePredictor().forgetOre(seedTarget);
                    seedTarget = null;
                    seedBreakState = 0;
                    seedBreakTicks = 0;
                } else {
                    baritone.pathToOre(seedTarget);
                }
            }
            return;
        }

        // 就位：停止寻路，锁定视角合法破坏
        if (baritone.isPathing()) baritone.stop();
        breakSeedBlock();
    }

    /** 挑选周围 64 格中最近的一块「预测且未挖且确为目标矿」的位置 */
    private BlockPos findNextSeedTarget() {
        if (mc.player == null) return null;

        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : module.getOrePredictor().getPredictedOresInRange(mc.player.blockPosition(), 64)) {
            if (seedVisited.contains(pos)) continue;
            if (!isTargetOreAt(pos)) {
                seedVisited.add(pos); // 预测位置无矿，标记避免反复检查
                module.getOrePredictor().forgetOre(pos); // 陈旧缓存同步剔除，渲染框即时消失
                continue;
            }
            double d = mc.player.blockPosition().distSqr(pos);
            if (d < best) {
                best = d;
                nearest = pos;
            }
        }
        return nearest;
    }

    /** 预测位置上的方块是否为当前目标矿（含深层变种家族匹配） */
    private boolean isTargetOreAt(BlockPos pos) {
        if (mc.level == null) return false;
        return module.isTargetFamily(mc.level.getBlockState(pos).getBlock());
    }

    /**
     * 螺旋搜索玩家周围最近的可站立陆地（非流体方块且上方为空气），用于水中脱困。
     * 只在水中卡死触发时调用一次，扫描半径 40 格、上下 2 格。
     */
    private BlockPos findNearestLand() {
        if (mc.player == null || mc.level == null) return null;
        BlockPos feet = mc.player.blockPosition();
        for (int r = 0; r <= 40; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // 切比雪夫环，只扫当前半径一圈
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = feet.offset(dx, dy, dz);
                        // 该格无流体、非空气、上方可站（空气且无流体）
                        if (mc.level.getFluidState(p).isEmpty()
                            && !mc.level.getBlockState(p).isAir()
                            && mc.level.getBlockState(p.above()).isAir()
                            && mc.level.getFluidState(p.above()).isEmpty()) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 就位后锁定视角，走原版破坏进度流程（start → continue → 服务端回包确认） */
    private void breakSeedBlock() {
        if (mc.gameMode == null || mc.player == null || mc.level == null || seedTarget == null) return;
        BlockPos pos = seedTarget;

        // 视角锁定方块中心（破坏判定只依赖位置与距离，本地转动即可）
        Vec3 eye = mc.player.getEyePosition();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz))));
        mc.player.setXRot((float) Math.toDegrees(-Math.atan2(dy, horiz)));

        if (seedBreakState == 0) {
            // 首次破坏：锁定 face 并缓存——后续 continue 必须用同一 face，
            // 否则服务端视为换面攻击，破坏进度被重置，380 tick 也挖不掉一块
            Direction[] ordered = Direction.orderedByNearest(mc.player);
            seedBreakFace = ordered.length > 0 ? ordered[0] : Direction.UP;
            mc.gameMode.startDestroyBlock(pos, seedBreakFace);
            seedBreakState = 1;
            seedBreakTicks = 0;
            return;
        }

        seedBreakTicks++;
        mc.gameMode.continueDestroyBlock(pos, seedBreakFace);

        // 服务端回包确认破坏完成（方块不再是目标矿），或超过 20 秒放弃这块
        if (!isTargetOreAt(pos) || seedBreakTicks > 400) {
            seedVisited.add(pos);
            module.getOrePredictor().forgetOre(pos);
            seedTarget = null;
            seedBreakState = 0;
            seedBreakTicks = 0;
        }
    }

    /** 种子模式破坏前切换工具：开关开启时按目标方块选最佳工具，关闭时固定镐子 */
    private void ensureBestToolForSeedTarget() {
        if (mc.player == null) return;
        // 开关关闭时保持原有行为：固定镐子
        if (!module.getAutoTool()) {
            ensurePickaxeInHand();
            return;
        }
        // 无目标或目标为空，回退镐子
        if (seedTarget == null || mc.level == null) {
            ensurePickaxeInHand();
            return;
        }
        BlockState state = mc.level.getBlockState(seedTarget);
        int bestSlot = findBestToolSlot(state);
        if (bestSlot == -1) {
            ensurePickaxeInHand();
            return;
        }
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (currentSlot == bestSlot) return;
        if (bestSlot < 9) {
            InvUtils.swap(bestSlot, false);
        } else {
            InvUtils.move().from(bestSlot).to(0);
            InvUtils.swap(0, false);
        }
    }

    /** 扫描快捷栏，返回挖掘指定方块最快的工具槽位（无快于空手的工具返回 -1） */
    private int findBestToolSlot(BlockState state) {
        int best = -1;
        double bestSpeed = 1.0; // 空手破坏速度基准
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }

    /** 种子模式固定切镐子（自动切换工具开关关闭时的回退行为） */
    private void ensurePickaxeInHand() {
        if (mc.player == null) return;
        if (isPickaxe(mc.player.getMainHandItem())) return;

        int slot = findMiningPickaxeSlot();
        if (slot == -2) {
            InvUtils.move().fromOffhand().to(0);
        } else if (slot >= 0) {
            if (slot < 9) {
                InvUtils.swap(slot, false);
            } else {
                InvUtils.move().from(slot).to(0);
                InvUtils.swap(0, false);
            }
        }
    }

    /** 扫描附近掉落的目标矿（精准采集捡原矿方块，时运捡掉落物），用于漏捡补偿 */
    private ItemEntity findNearbyOreDrop(double radius) {
        if (mc.level == null || mc.player == null) return null;
        Set<String> acceptIds = module.isSilkTouchMode()
            ? module.getTargetBlockIds()
            : Set.of(module.getTargetDropItemId());

        List<ItemEntity> items = mc.level.getEntitiesOfClass(
            ItemEntity.class, mc.player.getBoundingBox().inflate(radius), e -> true);
        ItemEntity nearest = null;
        double best = radius * radius;
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!acceptIds.contains(id)) continue;
            double d = item.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        return nearest;
    }

    /**
     * 自动捡起附近掉落的目标矿（漏捡补偿）。
     * 返回 true 表示正在捡取（暂停本帧挖矿逻辑），false 表示未在捡取。
     */
    private boolean tryPickupNearbyOre() {
        if (mc.player == null || mc.level == null) return false;
        // 背包已满时不捡，否则捡不起来反而卡住（掉落物一直存在→反复寻路→原地打转）
        if (countOreStacks() >= module.getUnloadThreshold()) return false;

        if (pickupTarget == null) {
            // 每 10 tick 扫一次，避免每帧全量扫实体
            if (stateTick % 10 != 0) return false;
            ItemEntity drop = findNearbyOreDrop(6.0);
            if (drop == null) return false;
            pickupTarget = drop;
            pickupTimeout = 0;
            module.getBaritone().stop();
            var baritone = module.getBaritone().getBaritoneInstance();
            if (baritone != null) {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(drop.blockPosition()));
            }
            return true;
        }

        // 已有捡取目标：消失/超时/太远 → 放弃并恢复挖矿
        pickupTimeout++;
        if (pickupTarget.isRemoved() || !pickupTarget.isAlive()
            || pickupTimeout > 600 || pickupTarget.distanceTo(mc.player) > 16) {
            pickupTarget = null;
            module.getBaritone().stop();
            if (!module.isSeedMiningEnabled()) {
                module.getBaritone().startMining(module.getTargetBlocks());
            }
            return false;
        }
        return true;
    }

    private void checkToolDurabilityWarning() {
        if (mc.player == null) return;

        // 找出耐久最低的可修复工具（镐/铲/斧/锄/剑，含副手），预警提示跟修复触发用同一套判定
        ItemStack lowest = ItemStack.EMPTY;
        int lowestRemaining = Integer.MAX_VALUE;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isRepairableTool(stack)) continue;
            Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
            Integer damage = stack.get(DataComponents.DAMAGE);
            if (maxDamage == null || damage == null) continue;
            int remaining = maxDamage - damage;
            if (remaining < lowestRemaining) {
                lowestRemaining = remaining;
                lowest = stack;
            }
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (isRepairableTool(offhand)) {
            Integer maxDamage = offhand.get(DataComponents.MAX_DAMAGE);
            Integer damage = offhand.get(DataComponents.DAMAGE);
            if (maxDamage != null && damage != null) {
                int remaining = maxDamage - damage;
                if (remaining < lowestRemaining) {
                    lowestRemaining = remaining;
                    lowest = offhand;
                }
            }
        }

        if (lowest.isEmpty()) return;

        int threshold = module.getDurabilityThreshold();
        // 耐久进入预警区（阈值 1.5 倍以内但未触发修复），每 30 秒提醒一次
        if (lowestRemaining <= threshold * 1.5 && lowestRemaining > threshold) {
            if (stateTick % 600 == 0) {
                module.info("§e⚠ " + toolName(lowest) + " 剩余耐久 " + lowestRemaining + " §8▸ 即将触发修复流程");
                module.getSoundNotifier().notifyLowDurability();
            }
        }
    }

    /** 工具中文名（用于耐久预警/修复播报，能识别是哪种工具） */
    private String toolName(ItemStack stack) {
        if (stack.isEmpty()) return "工具";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.endsWith("_pickaxe")) return "镐子";
        if (id.endsWith("_shovel")) return "铲子";
        if (id.endsWith("_axe")) return "斧头";
        if (id.endsWith("_hoe")) return "锄头";
        if (id.endsWith("_sword")) return "剑";
        return "工具";
    }

    /**
     * 是否已站到容器跟前（可开箱）
     * 
     * 切比雪夫距离 ≤ 1（含对角格）：Baritone GoalGetToBlock 的合法终点
     * 就是切比雪夫邻域，玩家斜着接近时可能停在对角格。若这里用曼哈顿=1，
     * Baritone 认为已到达不再移动，判定却永远不满足 → 开箱死锁。
     * 改成与 Goal 定义一致后，无论落在哪个邻格都能立即开箱。
     */
    private boolean isAdjacentTo(BlockPos target) {
        BlockPos player = mc.player.blockPosition();
        int dx = Math.abs(player.getX() - target.getX());
        int dy = Math.abs(player.getY() - target.getY());
        int dz = Math.abs(player.getZ() - target.getZ());
        return dx <= 1 && dy <= 1 && dz <= 1 && (dx | dy | dz) != 0;
    }

    /**
     * 智能识别矿物箱位置的潜影盒颜色，返回「§颜色代码 + 中文色名 + 潜影盒」，用于换盒公屏提示。
     * 16 色潜影盒都是独立方块变体，颜色编码在方块 ID 里（如 white_shulker_box / purple_shulker_box）。
     */
    private String shulkerColorLabel(BlockPos pos) {
        if (mc.level == null || pos == null) return "§7潜影盒";
        Block block = mc.level.getBlockState(pos).getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (id.equals("shulker_box")) return "§7默认潜影盒";
        if (!id.endsWith("_shulker_box")) return "§7容器";

        String key = id.substring(0, id.length() - "_shulker_box".length());
        String color = switch (key) {
            case "white" -> "§f白色";
            case "orange" -> "§6橙色";
            case "magenta" -> "§d品红";
            case "light_blue" -> "§b淡蓝";
            case "yellow" -> "§e黄色";
            case "lime" -> "§a黄绿";
            case "pink" -> "§d粉色";
            case "gray" -> "§8灰色";
            case "light_gray" -> "§7淡灰";
            case "cyan" -> "§b青色";
            case "purple" -> "§5紫色";
            case "blue" -> "§9蓝色";
            case "brown" -> "§6棕色";
            case "green" -> "§2绿色";
            case "red" -> "§c红色";
            case "black" -> "§0黑色";
            default -> "§7" + key;
        };
        return color + "潜影盒";
    }

    private void tickUnloading() {
        CommandManager cmdMgr = module.getCmdManager();
        WKCommand.WKData mineralChest = WKCommand.getMineralChest();

        if (stateTick == 1) {
            unloadingPathIssued = false;
            boxSwapCount = 0;
            noContainerTicks = 0;
        }

        if (mineralChest == null) {
            if (module.isActive()) module.toggle();
            return;
        }

        // 潜影盒打包机换盒等待：关箱后等 2 秒让打包机推盒+放新盒，再重走开箱流程
        if (boxSwapWaiting) {
            boxSwapTicks++;
            if (boxSwapTicks >= BOX_SWAP_WAIT_TICKS) {
                boxSwapWaiting = false;
                boxSwapTicks = 0;
                module.info("§7换盒完成 §8▸ 重新打开 " + shulkerColorLabel(mineralChest.pos) + " §7继续卸货...");
            } else {
                return; // 继续等打包机换盒
            }
        }

        // [维度限制已临时关闭] 目标不在当前维度时不再停机，便于测试状态机（后续按需恢复）
        // if (!mineralChest.inCurrentDimension()) {
        //     module.error("§c矿物箱在当前维度不存在（" + mineralChest.dimensionName() + "），自动停止");
        //     if (module.isActive()) module.toggle();
        //     return;
        // }

        // 阶段 1：传送到矿物箱
        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getUnloadCommand());
            return;
        }

        // 阶段 2：等待传送完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：走到箱子相邻的一格（Baritone；寻路中断每 0.5 秒自动重发，不再一断就干等超时）
        if (!isAdjacentTo(mineralChest.pos)) {
            if (!unloadingPathIssued || (!module.getBaritone().isCustomGoalActive() && stateTick % 10 == 0)) {
                unloadingPathIssued = true;
                // 发起/重发 Baritone 走到箱子
                var baritone = module.getBaritone().getBaritoneInstance();
                if (baritone == null) {
                    module.error("§cBaritone 未加载，无法寻路到矿物箱，自动停止");
                    if (module.isActive()) module.toggle();
                    return;
                }
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(mineralChest.pos));
            }
            
            if (stateTick > PATH_TIMEOUT_TICKS) {
                module.getBaritone().stop();
                if (module.isActive()) module.toggle();
                return;
            }
            return;
        }

        // 阶段 4：停止寻路，面向箱子后开箱（避免背对开箱）
        module.getBaritone().stop();
        faceBlock(mineralChest.pos);

        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            if (stateTick % 20 == 0) module.getContainer().closeContainer();
            return;
        }
        if (!module.getContainer().isContainerOpen()) {
            // 标点位置无容器（潜影盒被推走后未放新盒）→ 累计计时，超时停机提示
            if (!module.getContainer().isContainerAt(mineralChest.pos)) {
                noContainerTicks++;
                if (noContainerTicks > NO_CONTAINER_TIMEOUT) {
                    module.error("§c✗ 矿物箱位置已无容器 §8▸ 自动停止模块");
                    if (module.isActive()) module.toggle();
                    return;
                }
            } else {
                noContainerTicks = 0;
            }
            module.getContainer().openContainer(mineralChest.pos);
            return;
        }

        // 阶段 5：持续倒货，直到目标矿石全部转移
        module.getContainer().depositOres();

        // 背包目标矿放完 → 关箱走人（放完才 RTP）
        if (!module.getContainer().hasOreInInventory()) {
            module.getContainer().closeContainer();
            transitionTo(MinerState.GO_WILD);
            return;
        }

        // 箱子满但背包还有矿
        if (module.getContainer().isContainerFull()) {
            if (module.isShulkerPackerEnabled()) {
                // 打包机模式：关箱等打包机换盒，再重开新盒继续放
                boxSwapCount++;
                if (boxSwapCount > MAX_BOX_SWAPS) {
                    module.error("§c✗ 潜影盒换盒超过 " + MAX_BOX_SWAPS + " 次仍未放完 §8▸ 自动停止");
                    module.getContainer().closeContainer();
                    if (module.isActive()) module.toggle();
                    return;
                }
                module.getContainer().closeContainer();
                boxSwapWaiting = true;
                boxSwapTicks = 0;
                module.info("§e⚠ " + shulkerColorLabel(mineralChest.pos) + " §e已满 §8▸ 等打包机换盒后重开继续放");
                return;
            }
            // 普通箱子模式：箱子满了放不下 → 停止模块并提示，避免空塞后带矿 RTP 跑掉
            module.getContainer().closeContainer();
            module.error("§c✗ 矿物容器已满 §8▸ 无法继续卸货，自动停止模块");
            if (module.isActive()) module.toggle();
            return;
        }

        // 超时保护（非打包机模式兜底）
        if (!module.isShulkerPackerEnabled() && stateTick > 400) {
            module.getContainer().closeContainer();
            transitionTo(MinerState.GO_WILD);
        }
    }

    private void tickSupply() {
        CommandManager cmdMgr = module.getCmdManager();
        WKCommand.WKData foodChest = WKCommand.getFoodChest();
        if (stateTick == 1) {
            supplyPathIssued = false;
        }

        if (foodChest == null) {
            if (module.isActive()) module.toggle();
            return;
        }

        // [维度限制已临时关闭] 目标不在当前维度时不再停机，便于测试状态机（后续按需恢复）
        // if (!foodChest.inCurrentDimension()) {
        //     module.error("§c食物箱在当前维度不存在（" + foodChest.dimensionName() + "），自动停止");
        //     if (module.isActive()) module.toggle();
        //     return;
        // }

        // 阶段 1：传送到补给点
        if (stateTick == 1) {
            cmdMgr.executeCommand(module.getSupplyCommand());
            return;
        }

        // 阶段 2：等待传送完成
        if (cmdMgr.isCommandExecuting()) {
            return;
        }

        // 阶段 3：走到箱子相邻的一格（Baritone；寻路中断每 0.5 秒自动重发）
        if (!isAdjacentTo(foodChest.pos)) {
            if (!supplyPathIssued || (!module.getBaritone().isCustomGoalActive() && stateTick % 10 == 0)) {
                supplyPathIssued = true;
                var baritone = module.getBaritone().getBaritoneInstance();
                if (baritone == null) {
                    module.error("§cBaritone 未加载，无法寻路到食物箱，自动停止");
                    if (module.isActive()) module.toggle();
                    return;
                }
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(foodChest.pos));
            }

            if (stateTick > 1200) {
                module.getBaritone().stop();
                if (module.isActive()) module.toggle();
            }
            return;
        }

        // 阶段 4：停止寻路，面向箱子后开箱取食物
        module.getBaritone().stop();
        faceBlock(foodChest.pos);
        
        if (!module.getContainer().isContainerOpen()) {
            // 屏幕被其它界面占用时定期强制关闭，避免永远打不开箱子
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) return;
            if (mc.screen != null && stateTick % 20 == 0) module.getContainer().closeContainer();
            module.getContainer().openContainer(foodChest.pos);
            return;
        }

        // 阶段 5：取食物，结束后返回矿区重新前往野外，避免在食物箱原地挖矿
        boolean taken = module.getContainer().withdrawFood();
        if (taken || stateTick > 400) {
            module.getContainer().closeContainer();
            // 补给成功判定必须与触发条件一致（按白名单食物数量是否达标），
            // 不能用 hasFoodToEat()：背包哪怕只剩 1 块食物也会被判成功，
            // 导致「食物不足→补给→箱空→回来→又不足」无限 RTP 死循环。
            if (countFoodStacks() >= module.getHungerThreshold()) {
                supplyFailCount = 0;
                module.info("§a✓ 食物已补充 §8▸ 返回矿区");
                transitionTo(MinerState.GO_WILD);
            } else if (supplyFailCount++ >= 1) {
                // 连续 2 次补给空手：箱子没白名单食物，再循环也只是空转 RTP，停机让玩家补货
                module.error("§c✗ 补给箱连续 2 次无白名单食物 §8▸ 自动停止（请补充食物箱）");
                if (module.isActive()) module.toggle();
            } else {
                module.warning("§e⚠ 补给箱内无白名单食物 §8▸ 返回矿区继续挖（饥饿时仍会再试一次）");
                transitionTo(MinerState.GO_WILD);
            }
        }
    }

    private void tickEating() {
        FoodData foodData = mc.player.getFoodData();
        
        // 检查饱食度是否回满（满值20）
        if (foodData.getFoodLevel() >= 20) {
            mc.options.keyUse.setDown(false); // 释放右键
            module.info("§a✓ 饱食度已恢复 §8▸ 继续挖矿");
            module.getSoundNotifier().notifyMiningStart();
            transitionTo(MinerState.MINING);
            return;
        }

        // 食物耗尽（拿到手上的最后一块也吃完了）：别傻等 2 分钟超时，直接去补给
        if (!hasFoodToEat()) {
            mc.options.keyUse.setDown(false);
            module.info("§6⚠ 食物已吃完 §8▸ 前往补给点");
            transitionTo(MinerState.SUPPLY);
            return;
        }

        // 每 tick 都尝试进食：autoEat 内部幂等，未在进食时触发一次 useItem，
        // 已在使用中则仅保持按键。窗口失焦/开 GUI 时按键会被吞，靠 useItem 兜底。
        module.getContainer().autoEat();

        // 每 2 秒播报一次进食进度（避免刷屏）
        if (stateTick % 40 == 0) {
            ItemStack handItem = mc.player.getMainHandItem();
            String foodName = handItem.isEmpty() ? "食物" : handItem.getHoverName().getString();
            module.info("§e进食中 §8▸ " + foodName + " §7(饱食度: " + foodData.getFoodLevel() + "/20)");
        }

        // 超时保护：2分钟还没吃饱就放弃，回到挖矿
        if (stateTick > 2400) {
            mc.options.keyUse.setDown(false);
            module.warning("§c⚠ 进食超时 §8▸ 放弃等待");
            module.getSoundNotifier().notifyLowFood();
            transitionTo(MinerState.MINING);
        }
    }

    private void tickRepair() {
        WKCommand.WKData afkPoint = WKCommand.getAFKPoint();

        if (afkPoint == null) {
            if (module.isActive()) module.toggle();
            return;
        }

        // [维度限制已临时关闭] 目标不在当前维度时不再停机，便于测试状态机（后续按需恢复）
        // if (!afkPoint.inCurrentDimension()) {
        //     module.error("§c挂机修补点在当前维度不存在（" + afkPoint.dimensionName() + "），自动停止");
        //     if (module.isActive()) module.toggle();
        //     return;
        // }

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

        // 阶段 3：走到挂机点（Baritone；寻路中断每 0.5 秒自动重发）
        if (!mc.player.blockPosition().closerThan(afkPoint.pos, 3.0)) {
            if (!repairPathIssued || (!module.getBaritone().isCustomGoalActive() && stateTick % 10 == 0)) {
                repairPathIssued = true;
                var baritone = module.getBaritone().getBaritoneInstance();
                if (baritone == null) {
                    module.error("§cBaritone 未加载，无法寻路到挂机点，自动停止");
                    if (module.isActive()) module.toggle();
                    return;
                }
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalTwoBlocks(afkPoint.pos));
            }

            if (stateTick > 1200) {
                module.getBaritone().stop();
                if (module.isActive()) module.toggle();
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
            if (repairSwapRequestedTick == -1) {
                int toolSlot = findDamagedToolSlot();
                if (toolSlot == -1) {
                    // 没有需要修的工具了（可能已被其它机制修好），直接返回矿区
                    transitionTo(MinerState.GO_WILD);
                    return;
                }
                savedToolSlot = toolSlot;
                savedTool = toolSlot == -2 ? mc.player.getOffhandItem().copy()
                    : mc.player.getInventory().getItem(toolSlot).copy();
                savedWeaponSlot = findWeaponSlotInHotbar();
                repairSwapAttempts++;
                repairSwapRequestedTick = stateTick;
                if (toolSlot >= 0) InvUtils.move().from(toolSlot).toOffhand();
                if (toolSlot == -2) {
                    repairMode = true;
                    startKillAura();
                }
                return;
            }

            ItemStack offhandTool = mc.player.getOffhandItem();
            boolean toolMoved = ItemStack.isSameItemSameComponents(savedTool, offhandTool);
            if (toolMoved) {
                if (savedWeaponSlot >= 0) {
                    InvUtils.swap(savedWeaponSlot, false);
                }
                repairMode = true;
                startKillAura();
                return;
            }

            // 工具还没到副手：最多等 15 tick（服务端到账通常 1~2 tick），超时才重发，
            // 避免像旧逻辑那样每 2 tick 就重发一次、把正在移动的光标打乱反而更慢。
            if (stateTick - repairSwapRequestedTick > 15) {
                repairSwapRequestedTick = -1;
                if (repairSwapAttempts >= 3) {
                    if (module.isActive()) module.toggle();
                }
            }
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
            module.error("§c✗ 已调用流星自动重生模块");
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
        // 阈值不能超过工具最大耐久：否则满耐久（remaining == maxDamage）仍被判为
        // 「需修复」，修完回矿区又立刻触发修复，形成修复↔挖矿死循环。
        int effectiveThreshold = Math.min(module.getDurabilityThreshold(), maxDamage);
        return remaining < effectiveThreshold;
    }

    private boolean isFullyRepaired(ItemStack tool) {
        if (tool.isEmpty()) return true;
        Integer damage = tool.get(DataComponents.DAMAGE);
        return damage == null || damage <= 5; // 接近满耐久
    }

    private ItemStack findMiningPickaxe() {
        ItemStack mainHand = mc.player.getMainHandItem();
        if (isPickaxe(mainHand)) return mainHand;
        ItemStack offhand = mc.player.getOffhandItem();
        if (isPickaxe(offhand)) return offhand;
        int slot = findMiningPickaxeSlot();
        return slot < 0 ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
    }

    private int findMiningPickaxeSlot() {
        ItemStack offhand = mc.player.getOffhandItem();
        if (isPickaxe(offhand)) return -2;
        for (int i = 0; i < 36; i++) {
            if (isPickaxe(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pickaxe");
    }

    /** 是否为可修复的挖掘/战斗工具（镐/铲/斧/锄/剑），且有耐久属性 */
    private boolean isRepairableTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.get(DataComponents.MAX_DAMAGE) == null) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return id.endsWith("_pickaxe") || id.endsWith("_shovel") || id.endsWith("_axe")
            || id.endsWith("_hoe") || id.endsWith("_sword");
    }

    /** 工具是否带经验修补附魔（挂机修复靠打怪掉经验 + 经验修补，没有就修不了） */
    private boolean hasMending(ItemStack stack) {
        if (stack.isEmpty() || mc.level == null) return false;
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return false;
        try {
            var lookup = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var holder = lookup.get(Enchantments.MENDING).orElse(null);
            return holder != null && enchantments.getLevel(holder) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 找出当前耐久最低且低于阈值的工具槽位（含副手），返回 -1 无、-2 副手 */
    private int findDamagedToolSlot() {
        if (mc.player == null) return -1;

        // 副手工具优先（已就位，直接修）
        if (isRepairableTool(mc.player.getOffhandItem()) && needsRepair(mc.player.getOffhandItem())) {
            return -2;
        }

        int bestSlot = -1;
        int lowestRemaining = Integer.MAX_VALUE;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isRepairableTool(stack) || !needsRepair(stack)) continue;
            Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
            Integer damage = stack.get(DataComponents.DAMAGE);
            int remaining = maxDamage - damage;
            if (remaining < lowestRemaining) {
                lowestRemaining = remaining;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** 返回当前最该修的耐久最低工具（用于播报具体工具名） */
    private ItemStack findDamagedTool() {
        int slot = findDamagedToolSlot();
        if (slot == -2) return mc.player.getOffhandItem();
        if (slot >= 0) return mc.player.getInventory().getItem(slot);
        return ItemStack.EMPTY;
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

    /**
     * 背包里是否还有白名单内的可吃食物（有 FOOD 组件才算）
     */
    private boolean hasFoodToEat() {
        if (mc.player == null) return false;
        List<Item> whitelist = module.getFoodWhitelist();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && whitelist.contains(stack.getItem()) && stack.has(DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    private int countOreStacks() {
        if (mc.player == null) return 0;

        // 精准采集按原矿方块（含深层变种）计数；时运按掉落物计数（下界残骸掉落物=自身方块，两模式共用）
        Set<String> acceptIds = module.isSilkTouchMode()
            ? module.getTargetBlockIds()
            : Set.of(module.getTargetDropItemId());

        int totalCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (acceptIds.contains(itemId)) {
                totalCount += stack.getCount(); // 统计目标矿物数量
            }
        }
        // 转换为完整组数
        return totalCount / 64;
    }

    private ItemStack findWeaponInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.endsWith("_sword")) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    /** 返回热键栏里第一把剑的槽位下标，无则 -1（修补时用于把剑换到主手打怪修装备） */
    private int findWeaponSlotInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_sword")) {
                return i;
            }
        }
        return -1;
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
            case IDLE -> "§7待机中";
            case GO_WILD -> "§a✓ 前往野外";
            case MINING -> String.format("§a✓ 开始挖矿 §8▸ 矿石 %d/%d 组 · 食物 %d/%d 个", oreStacks, targetStacks, foodCount, foodThreshold);
            case UNLOADING -> String.format("§b开始卸货 §8▸ 矿石已达 %d/%d 组", oreStacks, targetStacks);
            case SUPPLY -> String.format("§6⚠ 前往补给 §8▸ 食物不足 %d/%d 个", foodCount, foodThreshold);
            case EATING -> "§d补充饥饿值";
            case REPAIR -> "§c⚠ " + toolName(findDamagedTool()) + "耐久过低 §8▸ 联动杀戮光环修复中";
            case DEATH_HANDLING -> "§c✗ 检测到死亡 §8▸ 已调用流星自动重生";
            case RESPAWN_WAIT -> "§6复活完成 §8▸ 返回挂机点";
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
        float smoothYaw = currentYaw + deltaYaw * 0.3f;
        float smoothPitch = currentPitch + deltaPitch * 0.3f;

        mc.player.setYRot(smoothYaw);
        mc.player.setXRot(smoothPitch);
    }

    /** 直接把视角转向某个方块（卸货/补给开箱前面向箱子，避免背对开箱） */
    private void faceBlock(BlockPos pos) {
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setXRot((float) Math.toDegrees(-Math.atan2(dy, horiz)));
    }

    private void startKillAura() {
        KillAura killAura = Modules.get().get(KillAura.class);
        boolean wasActive = killAura != null && killAura.isActive();
        if (killAura != null && !wasActive) {
            killAura.toggle();
            killAuraWasOnBefore = true; // 标记为「我们开的」，退出修补时才能关
        }
    }

    private void stopKillAura() {
        KillAura killAura = Modules.get().get(KillAura.class);
        // 只关我们自己开启的 KA；用户进入模块前就开着的 KA 保持原样（避免状态污染）
        if (killAura != null && killAura.isActive() && killAuraWasOnBefore) {
            killAura.toggle();
        }
        killAuraWasOnBefore = false;
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

    private void restoreHotbar() {
        if (mc.player == null) return;

        // 把修好的工具从副手放回原槽位。
        // 旧实现固定放回 0 号槽，若 0 号槽已被武器占据，会把武器顶进副手（bug：修完稿子副手变成别的东西）。
        if (!mc.player.getOffhandItem().isEmpty()) {
            if (savedToolSlot >= 0) {
                InvUtils.move().fromOffhand().to(savedToolSlot);
            } else if (savedToolSlot == -2) {
                // 工具原本就在副手，无需移动
            } else {
                InvUtils.move().fromOffhand().to(0);
            }
        }

        repairMode = false;
        savedTool = ItemStack.EMPTY;
        savedWeapon = ItemStack.EMPTY;
        savedToolSlot = -1;
        savedWeaponSlot = -1;
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
