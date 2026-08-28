package com.example.addon.mining;

import com.example.addon.modules.AutoMinerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
    private int maxWaitTicks = 600; // 动态设置，默认30秒

    // 区块加载检测
    private BlockPos lastPlayerPos = BlockPos.ZERO;
    private int chunksLoadedCount = 0;
    private static final int CHUNKS_LOADED_REQUIRED = 5;

    // 虚空坠落检测
    private double lastY = 0;
    private int rapidFallTicks = 0;
    private static final double RAPID_FALL_THRESHOLD = 2.0; // 每tick下降超过2格判定为快速坠落

    // GUI等待与自动点击
    private boolean waitingForGui = false;
    private int guiWaitTicks = 0;
    private static final int GUI_MAX_WAIT_TICKS = 100; // 5秒超时

    public CommandManager(AutoMinerModule module) {
        this.module = module;
        this.mc = Minecraft.getInstance();
    }

    // #region debug-point A:E:init
    void debugReport(String hypothesisId, String location, String data) {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:7777/event");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                String body = "{\"sessionId\":\"automine-igui-click\",\"runId\":\"verify-fix\",\"hypothesisId\":\"" + hypothesisId + "\",\"location\":\"" + location + "\",\"msg\":\"[DEBUG] automine IGUI trace\",\"data\":\"" + data.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"ts\":" + System.currentTimeMillis() + "}";
                connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }, "automine-debug").start();
    }
    // #endregion

    public void reset() {
        executing = false;
        executeTick = 0;
        lastPlayerPos = BlockPos.ZERO;
        chunksLoadedCount = 0;
        lastY = 0;
        rapidFallTicks = 0;
        waitingForGui = false;
        guiWaitTicks = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  指令执行
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 执行聊天指令（如 /rtp, /home kuang）
     * 
     * 发送后进入阻塞状态，直到传送完成并满足安全条件
     * 自动处理斜杠前缀，无论输入 rtp 或 /rtp 都能正确执行
     */
    public void executeCommand(String command) {
        if (mc.player == null || command.isEmpty()) {
            return;
        }

        // 自动补充斜杠：如果命令不以/开头，自动添加
        String cmd = command.trim();
        if (!cmd.startsWith("/")) {
            cmd = "/" + cmd;
        }
        
        // 移除多余的斜杠（如果有人输入 //rtp）
        while (cmd.startsWith("//")) {
            cmd = cmd.substring(1);
        }
        
        // 去掉前缀/后发送
        mc.player.connection.sendCommand(cmd.substring(1));
        debugReport("A", "CommandManager.executeCommand:115", "command=" + cmd + ", guiEnabled=" + module.isRtpGuiEnabled() + ", keyword=" + module.getRtpGuiKeyword());

        // 从模块获取传送等待时长（秒转tick）
        maxWaitTicks = module.getTeleportDelay() * 20;

        executing = true;
        executeTick = 0;
        lastPlayerPos = mc.player.blockPosition();
        lastY = mc.player.getY();
        chunksLoadedCount = 0;
        rapidFallTicks = 0;
        
        // 如果启用RTP GUI自动点击，进入GUI等待状态
        if (module.isRtpGuiEnabled()) {
            waitingForGui = true;
            guiWaitTicks = 0;
        }
    }

    /**
     * 指令是否正在执行中（用于阻塞状态机）
     */
    public boolean isCommandExecuting() {
        if (!executing) return false;

        executeTick++;

        // 优先处理GUI自动点击
        if (waitingForGui) {
            return handleGuiAutoClick();
        }

        // 超时保护（使用动态设置的等待时长）
        if (executeTick > maxWaitTicks) {
            module.error("§c[自动挖矿] 传送超时，重新RTP");
            executing = false;
            // 标记需要重新传送
            module.requestRetryTeleport();
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

    // ═══════════════════════════════════════════════════════════════════
    //  GUI自动点击
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 处理RTP GUI自动点击
     * 
     * 策略：
     * · 等待GUI打开（检测mc.screen不为null）
     * · 遍历所有按钮，查找文本包含关键词的按钮
     * · 纯文本匹配：移除所有颜色代码（§x）和空格后进行比对
     * · 找到后模拟点击并关闭GUI
     * 
     * @return true=继续阻塞，false=GUI处理完毕
     */
    private boolean handleGuiAutoClick() {
        guiWaitTicks++;

        // 超时保护
        if (guiWaitTicks > GUI_MAX_WAIT_TICKS) {
            waitingForGui = false;
            return true; // 继续等待传送完成
        }

        Screen currentScreen = mc.screen;
        
        // 等待GUI出现
        if (currentScreen == null) {
            return true;
        }

        String keyword = module.getRtpGuiKeyword();
        if (keyword == null || keyword.isEmpty()) {
            waitingForGui = false;
            return true;
        }

        // 标准化关键词（移除颜色和空格）
        String normalizedKeyword = stripFormatting(keyword);
        debugReport("A", "CommandManager.handleGuiAutoClick:284", "keywordRaw=" + keyword + ", keywordNormalized=" + normalizedKeyword + ", children=" + currentScreen.children().size());

        if (!(currentScreen instanceof AbstractContainerScreen<?> containerScreen)) {
            return true;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        if (menu == null || mc.player == null || mc.gameMode == null) {
            return true;
        }

        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemText = stripFormatting(stack.getHoverName().getString());
            if (!itemText.contains(normalizedKeyword)) continue;

            debugReport("C", "CommandManager.handleGuiAutoClick:310", "MATCH slot=" + slot.index + ", text=" + itemText + ", menu=" + menu.getClass().getName());
            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
            waitingForGui = false;
            return true;
        }

        // 未找到匹配槽位，继续等待
        return true;
    }

    /**
     * 移除Minecraft颜色代码（§x）和所有空格
     */
    private String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "").replaceAll("\\s+", "");
    }
}
