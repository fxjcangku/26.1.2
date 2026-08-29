package com.example.addon.villager.navigation;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 村民导航服务
 * 
 * 封装 Baritone 寻路，提供：
 * · 工作站寻路（计算正前方站位）
 * · 容器寻路（绿宝石箱/卸货箱）
 * · 到达判定
 * · 卡死检测
 * · 停止导航
 * 
 * 参考：BaritoneExecutor 的封装模式
 */
public final class VillagerNavigationService {

    private final Minecraft mc;
    private boolean disabled = false;
    
    // 卡死检测
    private BlockPos lastPos = BlockPos.ZERO;
    private int stuckTicks = 0;
    private int lastCheckTick = 0;
    private static final int STUCK_CHECK_INTERVAL = 3600; // 3分钟
    private static final int STUCK_DISTANCE_THRESHOLD = 5; // 5格

    public VillagerNavigationService() {
        this.mc = Minecraft.getInstance();
    }

    /**
     * 寻路到工作站附近站位
     * 
     * 核心逻辑：不直接走向村民，而是走向工作站正前方 1 格可站立位置
     * 
     * @param workstation 工作站坐标
     * @return true 表示成功启动寻路
     */
    public boolean pathToWorkstation(BlockPos workstation) {
        if (disabled || mc.player == null) {
            return false;
        }

        try {
            IBaritone baritone = getBaritone();
            if (baritone == null) {
                disabled = true;
                return false;
            }

            // 计算工作站正前方站位（优先北面，依次尝试其他方向）
            BlockPos standingPos = findStandingPosition(workstation);
            if (standingPos == null) {
                return false;
            }

            // 创建目标并开始寻路
            Goal goal = new GoalBlock(standingPos);
            baritone.getCustomGoalProcess().setGoalAndPath(goal);

            // 重置卡死检测
            if (mc.player != null) {
                lastPos = mc.player.blockPosition();
            }
            stuckTicks = 0;
            lastCheckTick = 0;

            return true;

        } catch (Throwable e) {
            disabled = true;
            return false;
        }
    }

    /**
     * 寻路到容器（绿宝石箱/卸货箱）
     * 
     * @param container 容器坐标
     * @return true 表示成功启动寻路
     */
    public boolean pathToContainer(BlockPos container) {
        if (disabled || mc.player == null) {
            return false;
        }

        try {
            IBaritone baritone = getBaritone();
            if (baritone == null) {
                disabled = true;
                return false;
            }

            // 计算容器前方站位
            BlockPos standingPos = findStandingPosition(container);
            if (standingPos == null) {
                standingPos = container; // 退化方案：直接走向容器
            }

            Goal goal = new GoalBlock(standingPos);
            baritone.getCustomGoalProcess().setGoalAndPath(goal);

            // 重置卡死检测
            if (mc.player != null) {
                lastPos = mc.player.blockPosition();
            }
            stuckTicks = 0;
            lastCheckTick = 0;

            return true;

        } catch (Throwable e) {
            disabled = true;
            return false;
        }
    }

    /**
     * 计算目标方块前方的可站立位置
     * 
     * 优先级：北 > 南 > 西 > 东
     * 
     * @param target 目标方块坐标
     * @return 站位坐标，失败返回 null
     */
    private BlockPos findStandingPosition(BlockPos target) {
        if (mc.level == null) return null;

        // 按优先级尝试四个方向
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

        for (Direction dir : directions) {
            BlockPos candidate = target.relative(dir);
            
            if (isStandable(candidate)) {
                return candidate;
            }
        }

        // 所有方向都不可站立，返回 null
        return null;
    }

    /**
     * 判断位置是否可站立
     * 
     * 条件：
     * 1. 脚下方块可站立（固体方块）
     * 2. 脚部空间可通过
     * 3. 头部空间可通过
     */
    private boolean isStandable(BlockPos pos) {
        if (mc.level == null) return false;

        // 脚下必须是完整碰撞方块（1.21.5+ 替代 blocksMotion()）
        BlockPos below = pos.below();
        BlockState belowState = mc.level.getBlockState(below);
        if (!belowState.isCollisionShapeFullBlock(mc.level, below)) {
            return false;
        }

        // 脚部和头部必须可通过
        BlockState feetState = mc.level.getBlockState(pos);
        BlockState headState = mc.level.getBlockState(pos.above());

        return !feetState.isCollisionShapeFullBlock(mc.level, pos)
            && !headState.isCollisionShapeFullBlock(mc.level, pos.above());
    }

    /**
     * 判断是否已到达目标
     * 
     * @param target 目标坐标
     * @param range 到达判定距离（格）
     * @return true 表示已到达
     */
    public boolean hasArrived(BlockPos target, double range) {
        if (mc.player == null) return false;

        BlockPos playerPos = mc.player.blockPosition();
        return playerPos.distSqr(target) <= range * range;
    }

    /**
     * 判断 Baritone 是否正在寻路
     */
    public boolean isPathing() {
        if (disabled) return false;

        try {
            IBaritone baritone = getBaritone();
            if (baritone == null) return false;
            return baritone.getPathingBehavior().isPathing();
        } catch (Throwable e) {
            disabled = true;
            return false;
        }
    }

    /**
     * 停止导航
     */
    public void stop() {
        if (disabled) return;

        try {
            IBaritone baritone = getBaritone();
            if (baritone == null) return;

            baritone.getCustomGoalProcess().onLostControl();
            baritone.getPathingBehavior().cancelEverything();
            resetStuckDetection();

        } catch (Throwable e) {
            disabled = true;
        }
    }

    /**
     * 检测是否卡死（3分钟内位移 < 5格）
     */
    public boolean isStuck() {
        if (mc.player == null || disabled) return false;

        try {
            IBaritone baritone = getBaritone();
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
     * 重置卡死检测
     */
    public void resetStuckDetection() {
        if (mc.player != null) {
            lastPos = mc.player.blockPosition();
        }
        stuckTicks = 0;
        lastCheckTick = 0;
    }

    /**
     * Baritone 是否可用
     */
    public boolean isAvailable() {
        return !disabled && getBaritone() != null;
    }

    /**
     * 获取 Baritone 实例
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
