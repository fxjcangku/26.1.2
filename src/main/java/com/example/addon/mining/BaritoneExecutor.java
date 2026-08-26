package com.example.addon.mining;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import com.example.addon.modules.AutoMinerModule;
import com.example.addon.translations.BaritoneChatTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Baritone 寻路中间件
 * 
 * 功能：
 * · 封装 BaritoneAPI 的 #mine 调用
 * · 寻路异常处理（找不到目标、原地滞留）
 * · 地形防卡死心跳（3分钟位移<5格判定卡死）
 * · 种子挖矿过滤（只挖预测位置的矿石）
 */
public final class BaritoneExecutor {

    private final AutoMinerModule module;
    private final Minecraft mc;

    private boolean disabled = false;
    private BlockPos lastPos = BlockPos.ZERO;
    private int stuckTicks = 0;
    private int lastCheckTick = 0;

    private static final int STUCK_CHECK_INTERVAL = 3600; // 3分钟
    private static final int STUCK_DISTANCE_THRESHOLD = 5; // 5格

    public BaritoneExecutor(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    /**
     * 启动 Baritone 挖矿
     */
    public void startMining(Block target) {
        if (disabled) {
            module.error("Baritone 不可用，无法启动挖矿");
            return;
        }

        try {
            var baritone = getBaritone();
            if (baritone == null) {
                disabled = true;
                module.error("Baritone 未加载");
                return;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(target).toString();

            // 种子挖矿模式：使用自定义Goal只挖预测位置
            if (module.isSeedMiningEnabled()) {
                Set<BlockPos> predictedOres = module.getOrePredictor().getAllPredictedOres();
                
                if (predictedOres.isEmpty()) {
                    module.error("§c种子挖矿：未找到预测矿石位置");
                    return;
                }

                // 创建Goal列表：每个预测位置创建一个GoalTwoBlocks
                List<Goal> goals = predictedOres.stream()
                    .map(GoalTwoBlocks::new)
                    .collect(Collectors.toList());

                // 使用GoalComposite：Baritone会自动选择最近的目标
                Goal compositeGoal = new GoalComposite(goals.toArray(new Goal[0]));

                // 直接设置自定义寻路任务
                baritone.getCustomGoalProcess().setGoalAndPath(compositeGoal);

                module.info("§e[种子挖矿] 已锁定 " + predictedOres.size() + " 个预测位置");
                module.info("§aBaritone 已启动：" + BaritoneChatTranslations.translateBlockId(blockId));

            } else {
                // 普通挖矿模式：使用原生mine命令
                baritone.getCommandManager().execute("mine " + blockId);
                module.info("§aBaritone 已启动挖掘：" + BaritoneChatTranslations.translateBlockId(blockId));
            }

            // 重置卡死检测
            if (mc.player != null) {
                lastPos = mc.player.blockPosition();
            }
            stuckTicks = 0;
            lastCheckTick = 0;

        } catch (Throwable e) {
            disabled = true;
            module.error("Baritone 调用失败：" + e.getMessage());
        }
    }

    /**
     * 停止 Baritone
     */
    public void stop() {
        if (disabled) return;

        try {
            var baritone = getBaritone();
            if (baritone == null) return;

            baritone.getCommandManager().execute("stop");
            baritone.getPathingBehavior().cancelEverything();

        } catch (Throwable e) {
            disabled = true;
        }
    }

    /**
     * 检查玩家视线瞄准的方块是否在预测位置内
     * 用于种子挖矿模式的运行时过滤
     * 
     * @param targetPos 玩家瞄准的方块位置
     * @return true表示允许挖掘，false表示阻止挖掘
     */
    public boolean isAllowedToMine(BlockPos targetPos) {
        // 非种子挖矿模式，全部允许
        if (!module.isSeedMiningEnabled()) {
            return true;
        }

        // 种子挖矿模式，只允许挖预测位置的矿石
        return module.getOrePredictor().isPredictedOreAt(targetPos);
    }

    /**
     * 批量应用 Baritone 设置（模块启动时调用）
     * 
     * 根据 Baritone GitHub 源码，Settings 类的字段定义：
     * - allowBreak (Boolean)
     * - allowSprint (Boolean)
     * - allowPlace (Boolean)
     * - legitMine (Boolean) - 不存在，跳过
     * - maxFallHeightNoWater (Integer) - 不存在，使用其他参数
     */
    public void applySettings(boolean avoidLava, boolean mobAvoidance, int mobAvoidanceRadius,
                              boolean allowBreak, boolean allowPlace, int maxFallHeight,
                              boolean pauseMiningForFallingBlocks,
                              boolean allowInventory, boolean autoTool, boolean sprintAscends,
                              boolean allowParkour, boolean allowParkourPlace,
                              boolean allowDiagonalAscend, boolean allowDiagonalDescend,
                              boolean allowOnlyExposedOres,
                              int allowOnlyExposedOresDistance, int minYLevelWhileMining,
                              int maxYLevelWhileMining, int mineMaxOreLocationsCount,
                              boolean blacklistClosestOnFailure, boolean legitMine,
                              int legitMineYLevel, boolean legitMineIncludeDiagonals) {
        if (disabled) return;

        try {
            var settings = BaritoneAPI.getSettings();
            List<Block> blocksToAvoid = new ArrayList<>(settings.blocksToAvoid.value);
            if (avoidLava && !blocksToAvoid.contains(Blocks.LAVA)) blocksToAvoid.add(Blocks.LAVA);
            if (!avoidLava) blocksToAvoid.removeIf(block -> block == Blocks.LAVA);
            settings.blocksToAvoid.value = blocksToAvoid;
            
            // ✅ 确认存在的设置
            settings.allowSprint.value = true;           // 启用疾跑
            settings.allowBreak.value = allowBreak;      // 是否破坏方块
            settings.allowPlace.value = allowPlace;      // 是否放置方块
            settings.avoidance.value = mobAvoidance;
            settings.mobAvoidanceRadius.value = mobAvoidanceRadius;
            settings.maxFallHeightNoWater.value = maxFallHeight;
            settings.pauseMiningForFallingBlocks.value = pauseMiningForFallingBlocks;
            settings.allowInventory.value = allowInventory;
            settings.autoTool.value = autoTool;
            settings.sprintAscends.value = sprintAscends;
            settings.allowParkour.value = allowParkour;
            settings.allowParkourPlace.value = allowParkourPlace;
            settings.allowDiagonalAscend.value = allowDiagonalAscend;
            settings.allowDiagonalDescend.value = allowDiagonalDescend;
            settings.allowOnlyExposedOres.value = allowOnlyExposedOres;
            settings.allowOnlyExposedOresDistance.value = allowOnlyExposedOresDistance;
            settings.minYLevelWhileMining.value = minYLevelWhileMining;
            settings.maxYLevelWhileMining.value = maxYLevelWhileMining;
            settings.mineMaxOreLocationsCount.value = mineMaxOreLocationsCount;
            settings.blacklistClosestOnFailure.value = blacklistClosestOnFailure;
            settings.legitMine.value = legitMine;
            settings.legitMineYLevel.value = legitMineYLevel;
            settings.legitMineIncludeDiagonals.value = legitMineIncludeDiagonals;
            
            // 优先挖掘附近矿石的设置
            settings.mineGoalUpdateInterval.value = module.getMineGoalUpdateInterval();  // 使用配置值
            settings.blockReachDistance.value = 4.5f;    // 缩小交互距离，优先近处
            
        } catch (Throwable e) {
            disabled = true;
        }
    }

    /**
     * 动态更新单个设置
     */
    public void updateSetting(String key, Object value) {
        if (disabled) return;

        try {
            var settings = BaritoneAPI.getSettings();
            
            if (key.equals("allowBreak")) settings.allowBreak.value = (Boolean) value;
            else if (key.equals("allowPlace")) settings.allowPlace.value = (Boolean) value;
            else if (key.equals("avoidLava")) {
                List<Block> blocksToAvoid = new ArrayList<>(settings.blocksToAvoid.value);
                if ((Boolean) value && !blocksToAvoid.contains(Blocks.LAVA)) blocksToAvoid.add(Blocks.LAVA);
                if (!(Boolean) value) blocksToAvoid.removeIf(block -> block == Blocks.LAVA);
                settings.blocksToAvoid.value = blocksToAvoid;
            }
            else if (key.equals("avoidance")) settings.avoidance.value = (Boolean) value;
            else if (key.equals("mobAvoidanceRadius")) settings.mobAvoidanceRadius.value = (Integer) value;
            else if (key.equals("maxFallHeightNoWater")) settings.maxFallHeightNoWater.value = (Integer) value;
            else if (key.equals("pauseMiningForFallingBlocks")) settings.pauseMiningForFallingBlocks.value = (Boolean) value;
            else if (key.equals("allowInventory")) settings.allowInventory.value = (Boolean) value;
            else if (key.equals("autoTool")) settings.autoTool.value = (Boolean) value;
            else if (key.equals("sprintAscends")) settings.sprintAscends.value = (Boolean) value;
            else if (key.equals("allowParkour")) settings.allowParkour.value = (Boolean) value;
            else if (key.equals("allowParkourPlace")) settings.allowParkourPlace.value = (Boolean) value;
            else if (key.equals("allowDiagonalAscend")) settings.allowDiagonalAscend.value = (Boolean) value;
            else if (key.equals("allowDiagonalDescend")) settings.allowDiagonalDescend.value = (Boolean) value;
            else if (key.equals("allowOnlyExposedOres")) settings.allowOnlyExposedOres.value = (Boolean) value;
            else if (key.equals("allowOnlyExposedOresDistance")) settings.allowOnlyExposedOresDistance.value = (Integer) value;
            else if (key.equals("minYLevelWhileMining")) settings.minYLevelWhileMining.value = (Integer) value;
            else if (key.equals("maxYLevelWhileMining")) settings.maxYLevelWhileMining.value = (Integer) value;
            else if (key.equals("mineMaxOreLocationsCount")) settings.mineMaxOreLocationsCount.value = (Integer) value;
            else if (key.equals("blacklistClosestOnFailure")) settings.blacklistClosestOnFailure.value = (Boolean) value;
            else if (key.equals("legitMine")) settings.legitMine.value = (Boolean) value;
            else if (key.equals("legitMineYLevel")) settings.legitMineYLevel.value = (Integer) value;
            else if (key.equals("legitMineIncludeDiagonals")) settings.legitMineIncludeDiagonals.value = (Boolean) value;
            else if (key.equals("mineGoalUpdateInterval")) settings.mineGoalUpdateInterval.value = (Integer) value;
            
        } catch (Throwable e) {
            disabled = true;
        }
    }

    /**
     * 更新搭路方块白名单
     */
    public void updatePlaceBlocks(List<Block> blocks) {
        if (disabled) return;

        try {
            var settings = BaritoneAPI.getSettings();
            settings.acceptableThrowawayItems.value = new ArrayList<>(
                blocks.stream()
                    .map(net.minecraft.world.level.block.Block::asItem)
                    .filter(item -> item != net.minecraft.world.item.Items.AIR)
                    .toList()
            );
        } catch (Throwable e) {
            disabled = true;
        }
    }

    /**
     * 检测 Baritone 是否卡死（原地滞留超过3分钟且位移<5格）
     */
    public boolean isStuck() {
        if (mc.player == null || disabled) return false;

        try {
            var baritone = getBaritone();
            if (baritone == null) return false;

            // 只有在寻路状态下才检测卡死
            if (!baritone.getPathingBehavior().isPathing()) {
                return false;
            }

            stuckTicks++;

            // 每3分钟检测一次
            if (stuckTicks - lastCheckTick < STUCK_CHECK_INTERVAL) {
                return false;
            }

            BlockPos currentPos = mc.player.blockPosition();
            double distance = Math.sqrt(currentPos.distSqr(lastPos));

            lastCheckTick = stuckTicks;
            lastPos = currentPos;

            // 3分钟内位移小于5格，判定卡死
            return distance < STUCK_DISTANCE_THRESHOLD;

        } catch (Throwable e) {
            disabled = true;
            return false;
        }
    }

    /**
     * Baritone 是否正在寻路
     */
    public boolean isPathing() {
        if (disabled) return false;

        try {
            var baritone = getBaritone();
            if (baritone == null) return false;
            return baritone.getPathingBehavior().isPathing();
        } catch (Throwable e) {
            disabled = true;
            return false;
        }
    }

    /**
     * 获取 Baritone 实例（供状态机调用）
     */
    public IBaritone getBaritoneInstance() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable e) {
            disabled = true;
            return null;
        }
    }

    /**
     * 获取 Baritone 实例（内部使用）
     */
    private IBaritone getBaritone() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable e) {
            disabled = true;
            return null;
        }
    }
}
