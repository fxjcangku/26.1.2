package com.example.addon.tactical;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// ╔════════════════════════════════════════════════════════════════════╗
// ║                    战术枢纽 - 全局状态机                          ║
// ║               三大模块联动的事件总线与状态管理                     ║
// ╚════════════════════════════════════════════════════════════════════╝
//
// 【核心功能】
// 1. 事件发布订阅系统 - 模块间解耦通信
// 2. 全局状态共享 - 反作弊类型、拉回标记、资源包下载状态
// 3. 线程安全保证 - ConcurrentHashMap + CopyOnWriteArrayList
//
// 【联动逻辑】
// [服务器检测] 识别反作弊 → 发布 AntiCheatDetectedEvent
//     ↓
// [飞行绕过] 监听事件 → 强制切换到安全模式
//
// [飞行绕过] 收到拉回包 → 发布 RubberBandEvent
//     ↓
// [发包防踢] 监听事件 → 暂停移动包 + 发确认包
//
// [服务器检测] 下载资源包 → 发布 ResourcePackDownloadingEvent
//     ↓
// [发包防踢] 监听事件 → 降低发包速率
//
// ════════════════════════════════════════════════════════════════════

/**
 * 战术枢纽全局状态机
 * 
 * 负责三大模块（飞行绕过、发包防踢、服务器检测）的事件联动
 * 
 * @author yiyijia
 */
public class TacticalFSM {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  全局状态存储
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 当前服务器的反作弊类型 */
    private static volatile AntiCheatType detectedAntiCheat = AntiCheatType.UNKNOWN;

    /** 当前服务器核心类型 */
    private static volatile ServerCoreType detectedServerCore = ServerCoreType.UNKNOWN;

    /** 是否正在被拉回 */
    private static volatile boolean isRubberBanding = false;

    /** 是否正在下载资源包 */
    private static volatile boolean isDownloadingResourcePack = false;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  事件总线
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * 订阅事件
     */
    @SuppressWarnings("unchecked")
    public static <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 发布事件
     */
    @SuppressWarnings("unchecked")
    public static <T> void publish(T event) {
        CopyOnWriteArrayList<Consumer<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Consumer<?> listener : list) {
                try {
                    ((Consumer<T>) listener).accept(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  状态访问器
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static AntiCheatType getDetectedAntiCheat() {
        return detectedAntiCheat;
    }

    public static void setDetectedAntiCheat(AntiCheatType type) {
        detectedAntiCheat = type;
    }

    public static ServerCoreType getDetectedServerCore() {
        return detectedServerCore;
    }

    public static void setDetectedServerCore(ServerCoreType type) {
        detectedServerCore = type;
    }

    public static boolean isRubberBanding() {
        return isRubberBanding;
    }

    public static void setRubberBanding(boolean value) {
        isRubberBanding = value;
    }

    public static boolean isDownloadingResourcePack() {
        return isDownloadingResourcePack;
    }

    public static void setDownloadingResourcePack(boolean value) {
        isDownloadingResourcePack = value;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  事件定义
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 服务器核心检测事件
     */
    public static class ServerCoreDetectedEvent {
        public final ServerCoreType coreType;
        public final String version;

        public ServerCoreDetectedEvent(ServerCoreType coreType, String version) {
            this.coreType = coreType;
            this.version = version;
        }
    }

    /**
     * 反作弊检测事件
     */
    public static class AntiCheatDetectedEvent {
        public final AntiCheatType type;
        public final String version;

        public AntiCheatDetectedEvent(AntiCheatType type, String version) {
            this.type = type;
            this.version = version;
        }
    }

    /**
     * 拉回包事件
     */
    public static class RubberBandEvent {
        public final double x, y, z;
        public final float yaw, pitch;
        public final int teleportId;

        public RubberBandEvent(double x, double y, double z, float yaw, float pitch, int teleportId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.teleportId = teleportId;
        }
    }

    /**
     * 资源包下载事件
     */
    public static class ResourcePackDownloadingEvent {
        public final String url;
        public final String hash;
        public final boolean started; // true=开始下载, false=下载完成

        public ResourcePackDownloadingEvent(String url, String hash, boolean started) {
            this.url = url;
            this.hash = hash;
            this.started = started;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  服务器核心类型枚举
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public enum ServerCoreType {
        UNKNOWN("未知"),
        VANILLA("原版"),
        
        // Paper 系列
        PAPER("Paper"),
        PURPUR("Purpur"),
        PUFFERFISH("Pufferfish"),
        AIRPLANE("Airplane"),
        
        // 中国特供版
        LEAVES("Leaves"),
        GALE("Gale"),
        SAKURA("Sakura"),
        PLAZMA("Plazma"),
        PEARL("Pearl"),
        
        // Spigot 系列
        SPIGOT("Spigot"),
        CRAFTBUKKIT("CraftBukkit"),
        BUKKIT("Bukkit"),
        
        // Mod 端
        FORGE("Forge"),
        FABRIC("Fabric"),
        NEOFORGE("NeoForge"),
        QUILT("Quilt"),
        
        // 混合端
        ARCLIGHT("Arclight"),
        MOHIST("Mohist"),
        MAGMA("Magma"),
        CATSERVER("CatServer"),
        BANNER("Banner"),
        
        // 代理端
        BUNGEECORD("BungeeCord"),
        WATERFALL("Waterfall"),
        VELOCITY("Velocity");

        public final String displayName;

        ServerCoreType(String displayName) {
            this.displayName = displayName;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  反作弊类型枚举
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public enum AntiCheatType {
        UNKNOWN("未知"),
        
        // 国际主流反作弊
        GRIM_AC("GrimAC"),
        MATRIX("Matrix"),
        VULCAN("Vulcan"),
        POLAR("Polar"),
        SPARTAN("Spartan"),
        NEGATIVITY("Negativity"),
        THEMIS("Themis"),
        REFLEX("Reflex"),
        VERUS("Verus"),
        INTAVE("Intave"),
        HAWK("Hawk"),
        SUNRISE("Sunrise"),
        KAURI("Kauri"),
        
        // 中国特色反作弊
        ANTIMC("AntiMC"),
        VULCANCH("VulcanCH"),
        MATRIX_CN("MatrixCN"),
        SHADOW("Shadow"),
        SPARROW("Sparrow"),
        HORIZON("Horizon"),
        PHANTOM("Phantom"),
        
        // 开源/社区反作弊
        NEGATIVEX("NegativeX"),
        AAC("AAC"),
        ANTICHEAT("AntiCheat"),
        NOCHEATPLUS("NoCheatPlus"),
        
        // 企业级反作弊
        GUARDIANAI("GuardianAI"),
        FIREWALL("Firewall"),
        SENTINEL("Sentinel"),
        
        // 无反作弊
        VANILLA("无反作弊");

        public final String displayName;

        AntiCheatType(String displayName) {
            this.displayName = displayName;
        }
    }
}
