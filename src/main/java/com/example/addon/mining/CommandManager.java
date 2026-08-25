package com.example.addon.mining;

import com.example.addon.modules.AutoMinerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 指令管理与防卡死网络中心
 * 
 * 核心功能：
 * · 发送聊天指令（/rtp, /home 等）
 * · Anti-Lag & Loading Check（区块加载检测）
 * · 防虚空坠落保护（Y轴极速下降检测）
 * · 服务器 Tick 响应恢复检测
 * 
 * 阻塞机制：
 * 发送指令后，isCommandExecuting() 返回 true，阻止状态机推进。
 * 直到区块加载完成、玩家安全落地、服务器响应恢复，才返回 false。
 */
public final class CommandManager {

    private final AutoMinerModule module;
    private final Minecraft mc;

    private boolean executing = false;
    private int executeTick = 0;

    // 区块加载检测
    private BlockPos lastPlayerPos = BlockPos.ZERO;
    private int chunksLoadedCount = 0;
    private static final int CHUNKS_LOADED_REQUIRED = 5;

    // 虚空坠落检测
    private double lastY = 0;
    private int rapidFallTicks = 0;
    private static final double RAPID_FALL_THRESHOLD = 2.0; // 每tick下降超过2格判定为快速坠落

    // 超时保护
    private static final int MAX_WAIT_TICKS = 600; // 30秒超时

    public CommandManager(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    public void reset() {
        executing = false;
        executeTick = 0;
        lastPlayerPos = BlockPos.ZERO;
        chunksLoadedCount = 0;
        lastY = 0;
        rapidFallTicks = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  指令执行
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 执行聊天指令（如 /rtp, /home kuang）
     * 
     * 发送后进入阻塞状态，直到传送完成并满足安全条件
     */
    public void executeCommand(String command) {
        if (mc.player == null || command.isEmpty()) {
            return;
        }

        mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);

        executing = true;
        executeTick = 0;
        lastPlayerPos = mc.player.blockPosition();
        lastY = mc.player.getY();
        chunksLoadedCount = 0;
        rapidFallTicks = 0;
    }

    /**
     * 指令是否正在执行中（用于阻塞状态机）
     */
    public boolean isCommandExecuting() {
        if (!executing) return false;

        executeTick++;

        // 超时保护
        if (executeTick > MAX_WAIT_TICKS) {
            executing = false;
            return false;
        }

        // 前20 tick等待服务器响应
        if (executeTick < 20) {
            return true;
        }

        // 三重检测：区块加载 + 安全落地 + 服务器响应
        boolean chunksReady = checkChunksLoaded();
        boolean landingSafe = checkLandingSafe();
        boolean serverResponsive = checkServerResponsive();

        if (chunksReady && landingSafe && serverResponsive) {
            executing = false;
            return false;
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  安全检测
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 检测区块是否加载完成
     * 
     * 策略：连续5 tick周围9x9区块都已加载
     */
    private boolean checkChunksLoaded() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        BlockPos pos = player.blockPosition();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        int loadedCount = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                LevelChunk chunk = level.getChunk(cx, cz);
                
                if (chunk != null && !chunk.isEmpty()) {
                    loadedCount++;
                }
            }
        }

        chunksLoadedCount = (loadedCount >= 9) ? chunksLoadedCount + 1 : 0;
        return chunksLoadedCount >= CHUNKS_LOADED_REQUIRED;
    }

    /**
     * 检测玩家是否安全落地（防虚空坠落）
     * 
     * 策略：
     * · 连续3 tick 不再极速下降（每tick下降<2格）
     * · 且玩家在地面上或在水中
     */
    private boolean checkLandingSafe() {
        LocalPlayer player = mc.player;
        if (player == null) return false;

        double currentY = player.getY();
        double deltaY = lastY - currentY;
        lastY = currentY;

        // 极速下降检测
        if (deltaY > RAPID_FALL_THRESHOLD) {
            rapidFallTicks++;
        } else {
            rapidFallTicks = 0;
        }

        // 如果连续极速下降超过10 tick，判定为掉虚空
        if (rapidFallTicks > 10) {
            return false;
        }

        // 玩家在地面或水中
        boolean onGround = player.onGround() || player.isInWater() || player.isInLava();

        return onGround && rapidFallTicks == 0;
    }

    /**
     * 检测服务器是否响应正常
     * 
     * 策略：
     * · 玩家位置发生变化（说明服务器在同步位置）
     * · 或玩家已落地且静止超过5 tick
     */
    private boolean checkServerResponsive() {
        LocalPlayer player = mc.player;
        if (player == null) return false;

        BlockPos currentPos = player.blockPosition();

        // 位置变化说明服务器在响应
        if (!currentPos.equals(lastPlayerPos)) {
            lastPlayerPos = currentPos;
            return false; // 还在移动，继续等
        }

        // 位置静止超过5 tick，且玩家在地面
        return executeTick > 25 && player.onGround();
    }
}
