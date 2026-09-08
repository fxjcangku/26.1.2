package com.example.addon.tactical.core;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 战术系统 L1 协调器（单例，常驻）。
 *
 * 这是三个战术模块的唯一决策与状态层，替代旧 TacticalFSM（旧实现只是静态字段容器
 * + 零订阅死事件，冷却双写、生命周期挂在 ServerDetector 开关上，全部推翻）。
 *
 * 职责（全部由本类单点承担）：
 * 1. 全局状态表唯一写者：检测结果、TPS/卡顿、拉回冷却、拉回统计、会话代次；
 * 2. 反作弊检测报告入口：L0 观测层只能通过 reportDetection 写入，带会话代次校验；
 * 3. 拉回响应唯一入口：收到 ClientboundPlayerPositionPacket 后统一记冷却 + 统计
 *    + 发通知事件，不额外发包（26.1.2 原版 ClientPacketListener 已自动回
 *    AcceptTeleportation + 1 个确认包，重复发包反而制造篡改面）；
 * 4. 飞行模式策略：每 tick 对执行器请求的模式做准入/降级裁决（唯一决策点）；
 * 5. 生命周期协议：GameJoined / GameLeft 无条件全量重置（不依赖任何模块开关），
 *    重置完成后发 SessionResetEvent 通知各模块清自己的私有状态。
 *
 * 事件层只做「通知」不做「状态」：AntiCheatDetectedEvent / RubberBandDetectedEvent
 * / SessionResetEvent 的发布都发生在状态已更新之后，订阅者只读状态做本地反应。
 *
 * @author yiyijia
 */
public final class TacticalCoordinator {

    private static final Minecraft mc = Minecraft.getInstance();

    /** 拉回冷却时长：收到拉回包后 2 秒内暂停所有绕过类动作 */
    private static final long RUBBER_BAND_COOLDOWN_MS = 2000L;

    /** 连续拉回计数的滑动窗口：超过该时长无拉回视为脱离危险，计数清零 */
    private static final long RUBBER_BAND_WINDOW_MS = 10_000L;

    /** TPS 采样周期（tick）：20 tick 约 1 秒采一次，避免高频读 TickRate */
    private static final int TPS_SAMPLE_INTERVAL = 20;

    /** 卡顿判定阈值：TPS 低于该值判定服务器卡顿 */
    private static final double LAGGING_TPS_THRESHOLD = 18.0;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  检测状态（唯一写者：reportDetection / beginSession）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 检测到的服务器核心显示名 */
    private static volatile String detectedServerCore = "未知";

    /** 检测到的反作弊显示名 */
    private static volatile String detectedAntiCheat = "未知";

    /** 是否命中高风险反作弊（按 ServerFingerprints.isHighRisk 名单统一判定） */
    private static volatile boolean highRiskAntiCheat = false;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  运行态状态（唯一写者：本类内部）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 拉回冷却截止时间戳（毫秒），0 表示不在冷却；读时自检到期，不会死锁 */
    private static volatile long rubberBandCooldownUntil = 0L;

    /** 服务器卡顿状态 */
    private static volatile boolean serverLagging = false;

    /** 最近一次采样的服务器 TPS */
    private static volatile double currentTps = 20.0;

    /** TPS 采样内部计数 */
    private static int tpsSampleTick = 0;

    /** 本次会话累计拉回次数（供检测层判断「拉回频繁」） */
    private static volatile int rubberBandTotal = 0;

    /** 滑动窗口内连续拉回次数（供飞行降级与限速收紧） */
    private static volatile int consecutiveRubberBands = 0;

    /** 最近一次拉回的时间戳 */
    private static volatile long lastRubberBandAt = 0L;

    /** 会话代次：GameJoined/GameLeft 各加一，用于丢弃跨服迟到的检测结果 */
    private static volatile long sessionId = 0L;

    /** 飞行降级档位（0~2）：连续拉回触发升档，脱离危险窗口自动降档恢复 */
    private static volatile int degradeLevel = 0;

    /** 最近一次降级/恢复档位调整的时间戳（恢复每窗口只调一档，防止一 tick 全量恢复） */
    private static volatile long lastDegradeAdjustAt = 0L;

    static {
        // 本类静态挂载后即注册到 Meteor 事件总线，生命周期不依赖任何模块开关
        MeteorClient.EVENT_BUS.subscribe(TacticalCoordinator.class);
    }

    private TacticalCoordinator() {
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  状态读取（模块只读，禁止直接改字段）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static String getDetectedServerCore() {
        return detectedServerCore;
    }

    public static String getDetectedAntiCheat() {
        return detectedAntiCheat;
    }

    /** 是否命中高风险反作弊（旧 API hasAdvancedAntiCheat 的语义对齐版） */
    public static boolean hasAdvancedAntiCheat() {
        return highRiskAntiCheat;
    }

    /** 是否命中高风险反作弊名单 */
    public static boolean isHighRiskAntiCheat() {
        return highRiskAntiCheat;
    }

    /** 是否处于拉回冷却（调用即检查到期，到期自动解除，不会死锁） */
    public static boolean isRubberBandCooldown() {
        if (rubberBandCooldownUntil == 0L) return false;
        if (System.currentTimeMillis() >= rubberBandCooldownUntil) {
            rubberBandCooldownUntil = 0L;
            return false;
        }
        return true;
    }

    public static boolean isServerLagging() {
        return serverLagging;
    }

    public static double getCurrentTps() {
        return currentTps;
    }

    /** 本次会话累计拉回次数（检测层判断反作弊激进程度用） */
    public static int getRubberBandTotal() {
        return rubberBandTotal;
    }

    /** 当前会话代次（观测层调度异步任务时取走，上报时校验防串服） */
    public static long currentSession() {
        return sessionId;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  L0 观测层写入入口（唯一合法的检测写入通道）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 上报服务器检测结果（仅供 L0 观测层 ServerDetector 调用）。
     *
     * @param reportSessionId 调度检测时取走的会话代次；与当前不一致说明结果
     *                        来自上一个服务器，直接丢弃，防止跨服污染（P0-5）
     * @param core            服务器核心显示名（可为 null 表示不更新）
     * @param antiCheat       反作弊显示名（可为 null 表示不更新）
     */
    public static void reportDetection(long reportSessionId, String core, String antiCheat) {
        if (reportSessionId != sessionId) return;

        if (core != null) detectedServerCore = core;
        if (antiCheat == null) return;

        detectedAntiCheat = antiCheat;
        highRiskAntiCheat = ServerFingerprints.isHighRisk(antiCheat);

        // 状态先落地再发通知事件：订阅者只做本地反应，不反向写状态
        if (!"未检测".equals(antiCheat) && !"未发现".equals(antiCheat)) {
            MeteorClient.EVENT_BUS.post(AntiCheatDetectedEvent.get(antiCheat));
        }
    }

    /**
     * 飞行执行请求准入裁决（唯一决策点）。
     *
     * 顺序：拉回冷却（全局暂停）→ 降级档位恢复/升级 → 按档位沿降级链走靶 →
     * 发包飞行准入（高风险/无飞行权限拒绝）。执行器每 tick 以此为准，
     * 不允许绕过协调器自行换模式。
     *
     * 降级档位是会话级持久状态（degradeLevel）：连续拉回达到阈值升一档（封顶 2），
     * 超过危险窗口无拉回则每窗口降一档恢复，避免旧实现「一次性换模式、永不回头」。
     *
     * @param requested        用户设置里选定的模式
     * @param adaptiveSlowdown 自适应降速开关（执行器传入用户偏好）
     * @param degradeThreshold 连续拉回降级阈值（执行器传入用户偏好）
     * @return 本 tick 的执行决策
     */
    public static FlightPolicy.FlightDecision evaluateFlight(FlightPolicy.FlightMode requested,
                                                             boolean adaptiveSlowdown,
                                                             int degradeThreshold) {
        // 拉回冷却期统一暂停全部模式：此时继续注入移动等于顶风作案
        if (isRubberBandCooldown()) {
            return new FlightPolicy.FlightDecision(requested, FlightPolicy.FlightReason.COOLDOWN);
        }

        long now = System.currentTimeMillis();

        // 恢复：脱离危险窗口（10 秒无拉回）每窗口只降一档，避免刚脱险就全量放开
        if (degradeLevel > 0
            && now - lastRubberBandAt > RUBBER_BAND_WINDOW_MS
            && now - lastDegradeAdjustAt > RUBBER_BAND_WINDOW_MS) {
            degradeLevel--;
            lastDegradeAdjustAt = now;
        }

        // 降级：滑动窗口内连续拉回达到阈值，升一档（封顶 2 档封顶）
        if (adaptiveSlowdown && consecutiveRubberBands >= degradeThreshold && degradeLevel < 2) {
            degradeLevel++;
            lastDegradeAdjustAt = now;
            consecutiveRubberBands = 0;
        }

        // 按档位沿降级链走向目标模式（降级链：发包飞行/烟花火箭 → 安全滑翔 → 原版模拟）
        FlightPolicy.FlightMode target = requested;
        for (int i = 0; i < degradeLevel; i++) {
            FlightPolicy.FlightMode next = degradeStep(target);
            if (next == target) break;
            target = next;
        }

        if (target != requested) {
            return new FlightPolicy.FlightDecision(target, FlightPolicy.FlightReason.DEGRADED);
        }

        // 发包飞行准入：26.1.2 官方 ServerGamePacketListenerImpl.handleMovePlayer
        // 的浮空判定只认物理支撑 + allowFlight/mayfly/鞘翅/悬浮等合法状态
        // （sources.jar L1137-1145），发包无法豁免。只有服务端真正授予飞行能力
        // （/fly、创造、旁观）才允许执行；未授权时不再停摆，改为沿降级链
        // 自动落到可执行模式（安全滑翔/原版模拟），执行器拿到 NO_FLY_ABILITY
        // 决策时执行降级目标模式
        if (requested == FlightPolicy.FlightMode.PACKET_FLY) {
            if (highRiskAntiCheat) {
                return new FlightPolicy.FlightDecision(requested, FlightPolicy.FlightReason.HIGH_RISK_AC);
            }
            Player player = mc.player;
            if (player == null || (!player.getAbilities().flying && !player.getAbilities().mayfly)) {
                return new FlightPolicy.FlightDecision(degradeStep(requested), FlightPolicy.FlightReason.NO_FLY_ABILITY);
            }
        }

        return new FlightPolicy.FlightDecision(requested, FlightPolicy.FlightReason.GRANTED);
    }

    /**
     * 降级链单步：朝「不依赖任何前置条件」的方向退一档。
     * 发包飞行/烟花火箭 → 安全滑翔（鞘翅）→ 原版模拟（仅需地面，最终档）。
     *
     * 降级目标必须有可执行资产：鞘翅档缺鞘翅时直接跨到原版模拟，
     * 防止降级决策产出执行器无法落地的模式（决策层对资产可用性负责）。
     */
    private static FlightPolicy.FlightMode degradeStep(FlightPolicy.FlightMode mode) {
        FlightPolicy.FlightMode target = switch (mode) {
            case PACKET_FLY, FIREWORK_BOOST -> FlightPolicy.FlightMode.SAFE_GLIDE;
            case SAFE_GLIDE, SEQUENCE_SCAFFOLD -> FlightPolicy.FlightMode.VANILLA_MIMIC;
            case VANILLA_MIMIC -> FlightPolicy.FlightMode.VANILLA_MIMIC;
        };

        if (target == FlightPolicy.FlightMode.SAFE_GLIDE && !hasElytra()) {
            return FlightPolicy.FlightMode.VANILLA_MIMIC;
        }
        return target;
    }

    /** 背包/护甲槽里是否有鞘翅（安全滑翔档的资产校验） */
    private static boolean hasElytra() {
        Player player = mc.player;
        if (player == null) return false;

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == Items.ELYTRA) return true;
        }
        return false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  生命周期协议（唯一入口，不依赖任何模块开关）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 全量重置所有全局状态（进服/离服统一走这里，供外部兜底调用） */
    public static void reset() {
        beginSession();
    }

    @EventHandler
    private static void onGameJoined(GameJoinedEvent event) {
        beginSession();
    }

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        beginSession();
    }

    private static void beginSession() {
        sessionId++;
        detectedServerCore = "未知";
        detectedAntiCheat = "未知";
        highRiskAntiCheat = false;
        rubberBandCooldownUntil = 0L;
        serverLagging = false;
        currentTps = 20.0;
        tpsSampleTick = 0;
        rubberBandTotal = 0;
        consecutiveRubberBands = 0;
        lastRubberBandAt = 0L;
        degradeLevel = 0;
        lastDegradeAdjustAt = 0L;

        // 全局状态清零后再通知模块清私有状态，保证模块读到的已是新会话状态
        MeteorClient.EVENT_BUS.post(SessionResetEvent.get());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  拉回响应唯一入口（只记状态，不补发包）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 收到服务器位置纠正包时的统一处理。
     *
     * 为什么不再补发确认包：26.1.2 原版 ClientPacketListener.handleMovePlayer
     * 已自动回发 AcceptTeleportation + 1 个 PosRot 确认包；旧 AntiKickBypass
     * 额外补发的 3 个静止包既冗余，又会被飞行模块的发包拦截器二次篡改（审计
     * P1-1），故重构后接收链路上只有本协调器这一家处理器。
     *
     * 优先级 -1000 保证先于所有模块的收包监听执行，模块只读冷却结果。
     */
    @EventHandler(priority = -1000)
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPlayerPositionPacket)) return;

        long now = System.currentTimeMillis();
        rubberBandCooldownUntil = now + RUBBER_BAND_COOLDOWN_MS;
        rubberBandTotal++;

        // 滑动窗口连续计数：超过 10 秒无拉回视为脱离危险，计数清零
        if (now - lastRubberBandAt > RUBBER_BAND_WINDOW_MS) {
            consecutiveRubberBands = 0;
        }
        lastRubberBandAt = now;
        consecutiveRubberBands++;

        MeteorClient.EVENT_BUS.post(RubberBandDetectedEvent.get());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  TPS 采样（常驻，不再依赖 ServerDetector 开关）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        if (mc.level == null) return;

        if (++tpsSampleTick < TPS_SAMPLE_INTERVAL) return;
        tpsSampleTick = 0;

        float tps = TickRate.INSTANCE.getTickRate();
        // tps <= 0 表示未进服或数据未就绪，不能据此判卡顿
        if (tps <= 0) return;

        currentTps = tps;
        serverLagging = tps < LAGGING_TPS_THRESHOLD;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  通知事件（只做通知，不做状态）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 反作弊检测完成（状态已写入，订阅者做本地反应如收紧限速） */
    public static class AntiCheatDetectedEvent {
        private static final AntiCheatDetectedEvent INSTANCE = new AntiCheatDetectedEvent();

        /** 检测到的反作弊显示名 */
        public String antiCheatName;

        private AntiCheatDetectedEvent() {
        }

        public static AntiCheatDetectedEvent get(String name) {
            INSTANCE.antiCheatName = name;
            return INSTANCE;
        }
    }

    /** 收到一次拉回（冷却与统计已更新，订阅者做本地记录/分析） */
    public static class RubberBandDetectedEvent {
        private static final RubberBandDetectedEvent INSTANCE = new RubberBandDetectedEvent();

        private RubberBandDetectedEvent() {
        }

        public static RubberBandDetectedEvent get() {
            return INSTANCE;
        }
    }

    /** 会话重置完成（进服/离服均触发，模块清私有状态用） */
    public static class SessionResetEvent {
        private static final SessionResetEvent INSTANCE = new SessionResetEvent();

        private SessionResetEvent() {
        }

        public static SessionResetEvent get() {
            return INSTANCE;
        }
    }
}