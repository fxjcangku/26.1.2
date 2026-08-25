package com.example.addon.tactical;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战术三枢纽 - 全局状态机
 * 
 * 负责三模块之间的事件传递与状态同步
 * 
 * @author yiyijia
 */
public class TacticalFSM {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  全局状态存储
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 检测到的服务器核心类型 */
    private static volatile String detectedServerCore = "未知";

    /** 检测到的反作弊类型 */
    private static volatile String detectedAntiCheat = "未知";

    /** 是否检测到高级反作弊（Matrix/GrimAC） */
    private static volatile boolean hasAdvancedAntiCheat = false;

    /** 资源包下载状态 */
    private static volatile boolean isDownloadingResourcePack = false;

    /** 拉回包冷却状态 */
    private static volatile boolean rubberBandCooldown = false;

    /** 服务器卡顿状态 */
    private static volatile boolean serverLagging = false;
    private static volatile double currentTps = 20.0;

    /** 正在下载的资源包记录 */
    private static final ConcurrentHashMap<UUID, ResourcePackDownload> downloadingPacks = new ConcurrentHashMap<>();

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  状态读取方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static String getDetectedServerCore() {
        return detectedServerCore;
    }

    public static String getDetectedAntiCheat() {
        return detectedAntiCheat;
    }

    public static boolean hasAdvancedAntiCheat() {
        return hasAdvancedAntiCheat;
    }

    public static boolean isDownloadingResourcePack() {
        return isDownloadingResourcePack;
    }

    public static boolean isRubberBandCooldown() {
        return rubberBandCooldown;
    }

    public static boolean isServerLagging() {
        return serverLagging;
    }

    public static double getCurrentTps() {
        return currentTps;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  状态更新方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 更新服务器核心检测结果 */
    public static void setDetectedServerCore(String core) {
        detectedServerCore = core;
    }

    /** 更新反作弊检测结果 */
    public static void setDetectedAntiCheat(String antiCheat) {
        detectedAntiCheat = antiCheat;
        hasAdvancedAntiCheat = antiCheat.contains("Matrix") || antiCheat.contains("Grim");
    }

    /** 设置资源包下载状态 */
    public static void setDownloadingResourcePack(boolean downloading) {
        isDownloadingResourcePack = downloading;
    }

    /** 设置拉回包冷却状态 */
    public static void setRubberBandCooldown(boolean cooldown) {
        rubberBandCooldown = cooldown;
    }

    /** 设置服务器卡顿状态 */
    public static void setServerLagging(boolean lagging, double tps) {
        serverLagging = lagging;
        currentTps = tps;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  事件发布方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 发布反作弊检测事件
     * 飞行绕过模块会监听此事件并切换到安全模式
     */
    public static void publishAntiCheatDetected(String antiCheatName) {
        setDetectedAntiCheat(antiCheatName);
        MeteorClient.EVENT_BUS.post(AntiCheatDetectedEvent.get(antiCheatName));
    }

    /**
     * 发布拉回包事件
     * 发包防踢模块会监听此事件并执行断流
     */
    public static void publishRubberBand(ClientboundPlayerPositionPacket packet) {
        setRubberBandCooldown(true);
        MeteorClient.EVENT_BUS.post(RubberBandEvent.get(packet));
    }

    /**
     * 发布资源包下载开始事件
     * 发包防踢模块会监听此事件并降低发包速率
     */
    public static void publishResourcePackDownloadStart(UUID packId) {
        setDownloadingResourcePack(true);
        downloadingPacks.put(packId, new ResourcePackDownload(packId, System.currentTimeMillis()));
        MeteorClient.EVENT_BUS.post(ResourcePackDownloadingEvent.get(packId, true));
    }

    /**
     * 发布资源包下载完成事件
     * 发包防踢模块会监听此事件并恢复正常发包速率
     */
    public static void publishResourcePackDownloadComplete(UUID packId, boolean success) {
        downloadingPacks.remove(packId);
        if (downloadingPacks.isEmpty()) {
            setDownloadingResourcePack(false);
        }
        MeteorClient.EVENT_BUS.post(ResourcePackDownloadingEvent.get(packId, false));
    }

    /**
     * 发布服务器卡顿事件
     * 网络层伪装模块会监听此事件并降低发包频率
     */
    public static void publishServerLagging(double tps) {
        setServerLagging(tps < 18.0, tps);
        MeteorClient.EVENT_BUS.post(ServerLaggingEvent.get(tps));
    }

    /** 重置所有状态（离开服务器时调用） */
    public static void reset() {
        detectedServerCore = "未知";
        detectedAntiCheat = "未知";
        hasAdvancedAntiCheat = false;
        isDownloadingResourcePack = false;
        rubberBandCooldown = false;
        serverLagging = false;
        currentTps = 20.0;
        downloadingPacks.clear();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  自定义事件类
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 反作弊检测事件 */
    public static class AntiCheatDetectedEvent {
        private static final AntiCheatDetectedEvent INSTANCE = new AntiCheatDetectedEvent();
        public String antiCheatName;

        private AntiCheatDetectedEvent() {}

        public static AntiCheatDetectedEvent get(String name) {
            INSTANCE.antiCheatName = name;
            return INSTANCE;
        }
    }

    /** 拉回包事件 */
    public static class RubberBandEvent {
        private static final RubberBandEvent INSTANCE = new RubberBandEvent();
        public ClientboundPlayerPositionPacket packet;

        private RubberBandEvent() {}

        public static RubberBandEvent get(ClientboundPlayerPositionPacket pkt) {
            INSTANCE.packet = pkt;
            return INSTANCE;
        }

        public int getTeleportId() {
            return packet.id();
        }

        public double getX() {
            return packet.change().position().x;
        }

        public double getY() {
            return packet.change().position().y;
        }

        public double getZ() {
            return packet.change().position().z;
        }
    }

    /** 资源包下载事件 */
    public static class ResourcePackDownloadingEvent {
        private static final ResourcePackDownloadingEvent INSTANCE = new ResourcePackDownloadingEvent();
        public UUID packId;
        public boolean isDownloading;

        private ResourcePackDownloadingEvent() {}

        public static ResourcePackDownloadingEvent get(UUID id, boolean downloading) {
            INSTANCE.packId = id;
            INSTANCE.isDownloading = downloading;
            return INSTANCE;
        }
    }

    /** 资源包下载记录 */
    private static class ResourcePackDownload {
        final UUID id;
        final long startTime;

        ResourcePackDownload(UUID id, long startTime) {
            this.id = id;
            this.startTime = startTime;
        }
    }

    /** 服务器卡顿事件 */
    public static class ServerLaggingEvent {
        private static final ServerLaggingEvent INSTANCE = new ServerLaggingEvent();
        public double tps;

        private ServerLaggingEvent() {}

        public static ServerLaggingEvent get(double currentTps) {
            INSTANCE.tps = currentTps;
            return INSTANCE;
        }
    }
}
