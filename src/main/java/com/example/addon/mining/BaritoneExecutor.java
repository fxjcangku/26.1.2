package com.example.addon.mining;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.example.addon.modules.AutoMinerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * Baritone 寻路中间件
 * 
 * 功能：
 * · 封装 BaritoneAPI 的 #mine 调用
 * · 寻路异常处理（找不到目标、原地滞留）
 * · 地形防卡死心跳（3分钟位移<5格判定卡死）
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

            // 获取方块的 Registry ID
            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(target).toString();

            // 调用 Baritone 的 mine 命令
            baritone.getCommandManager().execute("mine " + blockId);

            module.info("§aBaritone 已启动挖掘：" + blockId);

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
     * 批量应用 Baritone 设置（模块启动时调用）
     * 
     * 根据 Baritone GitHub 源码，Settings 类的字段定义：
     * - allowBreak (Boolean)
     * - allowSprint (Boolean)
     * - allowPlace (Boolean)
     * - legitMine (Boolean) - 不存在，跳过
     * - maxFallHeightNoWater (Integer) - 不存在，使用其他参数
     */
    public void applySettings(boolean avoidLava, boolean mobAvoidance, 
                              boolean allowBreak, boolean allowPlace,
                              int maxFallHeight, int miningRange, boolean legitMine) {
        if (disabled) return;

        try {
            var settings = BaritoneAPI.getSettings();
            
            // ✅ 确认存在的设置
            settings.allowSprint.value = true;           // 启用疾跑
            settings.allowBreak.value = allowBreak;      // 是否破坏方块
            settings.allowPlace.value = allowPlace;      // 是否放置方块
            
            // ❌ 这些设置在Settings.java中不存在，跳过
            // - avoidLava (不存在)
            // - mobAvoidance (不存在)
            // - maxFallHeightNoWater (不存在)
            // - legitMine (不存在)
            // - legitMineYLevel (不存在)
            
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
            
            // 只更新确认存在的设置
            if (key.equals("allowBreak")) settings.allowBreak.value = (Boolean) value;
            else if (key.equals("allowPlace")) settings.allowPlace.value = (Boolean) value;
            
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
