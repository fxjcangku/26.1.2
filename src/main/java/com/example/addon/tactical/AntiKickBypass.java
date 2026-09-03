package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.tactical.core.TacticalCoordinator;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 发包防踢模块（L2 发包执行器，2026-09-03 重构）。
 *
 * 职责边界（重构后）：
 * - 只负责「发包侧执行」：伪装客户端、聊天排队、防挂机、挖掘/放置限速、
 *   视角抖动、网络延迟、拉回分析记录；
 * - 不处理拉回包本身：冷却登记与统计由 TacticalCoordinator 统一完成（优先级
 *   最低的最先执行），本模块只订阅 RubberBandDetectedEvent 做分析记录，
 *   不再回发确认包 —— 26.1.2 原版 ClientPacketListener.handleMovePlayer 已自动
 *   回发 AcceptTeleportation + 1 个 PosRot 确认包，再补发等于制造篡改面（审计 P1-1）；
 * - 不写任何全局状态：服务器卡顿/拉回冷却只读 TacticalCoordinator（审计 P0-5）。
 *
 * 历史死循环修复（审计 P1-2）：
 * 1. 网络延迟重发改用 Connection.send(packet, null, true) 三参重载——
 *    Meteor 的 PacketEvent.Send 只钩子 send(Packet, ChannelFutureListener) 双参重载，
 *    三参直达底层不触发事件，转发包不再被自己再次取消；
 * 2. 移动包（ServerboundMovePlayerPacket）不进延迟队列，避免与飞行模块的
 *    移动注入互相抖动；
 * 3. 聊天排队重发加转发标记，防止 sendChat 产出的包再次入队永发不出去。
 *
 * @author yiyijia
 */
public class AntiKickBypass extends YiyiaddonModule {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  设置分组
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final SettingGroup sg1 = settings.createGroup("伪装客户端");
    private final SettingGroup sg2 = settings.createGroup("聊天排队");
    private final SettingGroup sg3 = settings.createGroup("防挂机");
    private final SettingGroup sg4 = settings.createGroup("限制发包");
    private final SettingGroup sg6 = settings.createGroup("拉回分析");
    private final SettingGroup sg7 = settings.createGroup("模拟真人");

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  伪装客户端 - 让服务器认为你是原版玩家
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> fakeBrand = sg1.add(new BoolSetting.Builder()
        .name("改客户端名字")
        .description("服务器问你用什么客户端时回答 vanilla（原版）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockModChannels = sg1.add(new BoolSetting.Builder()
        .name("拦截 Mod 通信")
        .description("26.1.2 官方已移除模组列表握手，服务器只能靠 fabric 自建频道注册识别 litematica/tweakeroo 等模组；本开关拦截 register/unregister 本体与全部非原版命名空间 payload 频道，让频道侦察零收获")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockFakeSneak = sg1.add(new BoolSetting.Builder()
        .name("拦截假潜行")
        .description("潜行标记与移动速度矛盾时摘掉潜行标记（Tweakeroo 假潜行特征）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockFakeSprint = sg1.add(new BoolSetting.Builder()
        .name("拦截假疾跑")
        .description("疾跑标记与移动方向矛盾（后退/无前进仍疾跑）时摘掉疾跑标记")
        .defaultValue(true)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  聊天排队 - 防止发消息太快被踢
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> enableChatQueue = sg2.add(new BoolSetting.Builder()
        .name("开启聊天排队")
        .description("消息由本模块排队慢发，防止刷屏被踢")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chatInterval = sg2.add(new IntSetting.Builder()
        .name("每条消息间隔（毫秒）")
        .description("两条消息之间等多久")
        .defaultValue(1500)
        .min(1000)
        .max(3000)
        .noSlider()
        .visible(() -> enableChatQueue.get())
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  防挂机 - 假装你在玩游戏
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> antiAfk = sg3.add(new BoolSetting.Builder()
        .name("防挂机检测")
        .description("每 5 秒发一个微小转身包刷新服务端活跃时间戳")
        .defaultValue(true)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  限制发包 - 防止挖太快/放太快被踢
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> limitDigging = sg4.add(new BoolSetting.Builder()
        .name("限制挖掘速度")
        .description("挖方块太快会被踢，这个功能帮你限速")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxDigPerSecond = sg4.add(new IntSetting.Builder()
        .name("每秒最多挖几个")
        .description("原版最快 5 个/秒，调太高等于没限制")
        .defaultValue(8)
        .min(2)
        .max(20)
        .noSlider()
        .visible(limitDigging::get)
        .build()
    );

    private final Setting<Boolean> limitInteract = sg4.add(new BoolSetting.Builder()
        .name("限制放置速度")
        .description("放方块/右键太快会被踢，这个功能帮你限速")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxInteractPerSecond = sg4.add(new IntSetting.Builder()
        .name("每秒最多放几个")
        .description("原版最快 4 个/秒")
        .defaultValue(8)
        .min(2)
        .max(20)
        .noSlider()
        .visible(limitInteract::get)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  拉回分析 - 记录什么操作容易被拉回
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> enableAnalysis = sg6.add(new BoolSetting.Builder()
        .name("开启拉回分析")
        .description("记录你每次被拉回时在做什么，累积到阈值后输出分析报告")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> analysisThreshold = sg6.add(new IntSetting.Builder()
        .name("累积几次后分析")
        .description("被拉回多少次后给你一份分析报告")
        .defaultValue(10)
        .min(5)
        .max(50)
        .noSlider()
        .visible(() -> enableAnalysis.get())
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模拟真人 - 让你的操作看起来像真人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> enableViewShake = sg7.add(new BoolSetting.Builder()
        .name("视角抖动")
        .description("移动时视角轻微抖动，模拟手抖")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> shakeIntensity = sg7.add(new DoubleSetting.Builder()
        .name("抖动幅度")
        .description("抖多厉害，2° 刚好，太大会被识别成机器人")
        .defaultValue(2.0)
        .min(0.5)
        .max(5.0)
        .noSlider()
        .visible(() -> enableViewShake.get())
        .build()
    );

    private final Setting<Boolean> enableNetworkDelay = sg7.add(new BoolSetting.Builder()
        .name("网络延迟")
        .description("发包时随机延迟 20-80 毫秒，模拟网络卡顿（移动包不延迟，避免与飞行注入互相抖动）")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minDelay = sg7.add(new IntSetting.Builder()
        .name("最小延迟（毫秒）")
        .description("延迟下限")
        .defaultValue(20)
        .min(0)
        .max(100)
        .noSlider()
        .visible(() -> enableNetworkDelay.get())
        .build()
    );

    private final Setting<Integer> maxDelay = sg7.add(new IntSetting.Builder()
        .name("最大延迟（毫秒）")
        .description("延迟上限，不要超过 100 毫秒，会卡")
        .defaultValue(80)
        .min(0)
        .max(200)
        .noSlider()
        .visible(() -> enableNetworkDelay.get())
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部数据
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // 聊天队列（仅主线程访问）
    private final Queue<String> chatQueue = new LinkedList<>();
    private long lastChatSendTime = 0;

    // 聊天重发标记：sendChat 产出的包经过发包事件时凭此放行，防止再次入队死循环
    private boolean forwardingChat = false;

    // 防挂机计数器
    private int antiAfkTicker = 0;

    // 限制发包计数器
    private final AtomicInteger digThisSecond = new AtomicInteger(0);
    private final AtomicInteger interactThisSecond = new AtomicInteger(0);
    private long lastThrottleResetTime = System.currentTimeMillis();

    // 动态限速倍率：协调器确认高风险反作弊时收紧（1.0=正常，越小越严格），运行时打折不改用户设置
    private volatile double throttleFactor = 1.0;

    // 拉回播报节流：拉回可能连发，5 秒只报一次，避免刷屏
    private long lastRubberBandNotice = 0L;

    // 本次会话起始时刻（进服时登记，用于离服时计算存活时长；
    // 会话重置事件不碰它，避免 GameLeft 顺序问题导致误判「存活不到 1 分钟」）
    private long sessionStartAt = 0L;

    // 进服伪装统计（会话级）：品牌替换与 Mod 频道拦截次数，离服汇总时一并播报，
    // 给玩家「伪装层确实在工作」的可验证证据
    private int sessionBrandSpoofs = 0;
    private int sessionChannelBlocks = 0;

    // 拉回分析记录
    private static class RubberBandRecord {
        final boolean flying;
        final boolean digging;
        final boolean placing;
        final double speed;

        RubberBandRecord(boolean flying, boolean digging, boolean placing, double speed) {
            this.flying = flying;
            this.digging = digging;
            this.placing = placing;
            this.speed = speed;
        }
    }
    private final List<RubberBandRecord> rubberBandHistory = Collections.synchronizedList(new ArrayList<>());
    private boolean currentlyDigging = false;
    private boolean currentlyPlacing = false;

    // 视角抖动
    private int shakeTickCounter = 0;
    private int nextShakeAt = 5;
    private Vec3 lastPosition = Vec3.ZERO;

    // 网络延迟：转发包记录原始 Connection，重发走三参 send 直达底层绕过发包事件
    private final PriorityQueue<DelayedPacket> delayQueue = new PriorityQueue<>(
        Comparator.comparingLong(p -> p.sendAt)
    );
    private static class DelayedPacket {
        final Packet<?> packet;
        final Connection connection;
        final long sendAt;

        DelayedPacket(Packet<?> packet, Connection connection, long sendAt) {
            this.packet = packet;
            this.connection = connection;
            this.sendAt = sendAt;
        }
    }
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "yiyiaddon-NetworkDelay");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> delayTask;

    private final Random random = new Random();

    public AntiKickBypass() {
        super(CATEGORY_TACTICAL, "发包防踢", "6 合 1 防踢系统：伪装+排队+防挂机+限速+拉回分析+真人模拟。点击按钮查看说明。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            WButton helpBtn = theme.button("§e查看使用说明");
            helpBtn.action = () -> mc.setScreen(new com.example.addon.ui.HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();
        }, new String[0]);
    }

    private String[] buildHelpContent() {
        return com.example.addon.ui.HelpScreen.buildHelpContent(
            new com.example.addon.ui.HelpScreen.HelpSection("功能概览",
                "§8├─ §f伪装客户端 §8- §7改Brand、拦截Mod频道、摘假潜行/假疾跑",
                "§8├─ §f聊天排队 §8- §7自动排队防刷屏",
                "§8├─ §f防挂机 §8- §7假装在操作",
                "§8├─ §f限制发包 §8- §7防止挖太快/放太快被踢",
                "§8├─ §f拉回分析 §8- §7记录什么操作容易被拉回",
                "§8└─ §f模拟真人 §8- §7视角抖动、网络延迟"
            ),

            new com.example.addon.ui.HelpScreen.HelpSection("与协调器的联动（重构后）",
                "§a[1] §f拉回冷却/统计 §8- §7统一由协调器登记，本模块只做分析",
                "§a[2] §f高风险反作弊 §8- §7协调器确认后限速自动收紧至 50%",
                "§a[3] §f服务器卡顿 §8- §7只读协调器状态，挖掘/放置即时停发",
                "§a[4] §f不再回发拉回确认包 §8- §726.1.2 原版客户端已自动确认"
            ),

            new com.example.addon.ui.HelpScreen.HelpSection("伪装 masa 全家桶 / 投影类模组（26.1.2 实测机制）",
                "§a[1] §f官方已移除模组列表握手 §8- §726.1.2 服务器读不到 fabric 模组列表本身",
                "§a[2] §f频道注册走 fabric 自建 register §8- §7Mod通信开关全拦非原版频道",
                "§a[3] §fBrand 伪装 vanilla §8- §7查客户端名字只会得到原版",
                "§a[4] §f假潜行 / 假疾跑 §8- §7摘除 Tweakeroo 行为特征",
                "§c⚠ §f进服前必须已开启本模块 §8- §7频道注册发生在进服瞬间的配置阶段"
            ),

            new com.example.addon.ui.HelpScreen.HelpSection("注意事项",
                "§c⚠ §f单人世界自动禁用，多人世界自动启用",
                "§c⚠ §f拉回分析会记录大量数据，调试完记得关闭",
                "§c⚠ §f模拟真人功能会影响操作手感，按需开启",
                "§c⚠ §f全拦非原版频道会导致 fabric 服务器认不出你装了 fabric，生电服需要 fabric 频道功能时请暂时关闭 Mod通信开关"
            )
        );
    }

    @Override
    public void onActivate() {
        // 单人世界自动关闭
        if (mc.hasSingleplayerServer()) {
            chatFeedback = false;
            toggle();
            chatFeedback = true;
            warning("§c单人世界无需防踢");
            return;
        }

        resetSessionState();

        if (enableNetworkDelay.get()) {
            delayTask = scheduler.scheduleAtFixedRate(this::processDelayQueue, 0, 5, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onDeactivate() {
        if (delayTask != null) {
            delayTask.cancel(false);
            delayTask = null;
        }
        synchronized (delayQueue) {
            delayQueue.clear();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  生命周期：进服登记会话起点；会话重置清私有状态（不碰 sessionStartAt）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;
        sessionStartAt = System.currentTimeMillis();
        // 会话级伪装统计独立归零（不依赖 SessionResetEvent 的发布顺序，避免
        // 协调器与模块同为普通优先级时事件先后不确定导致的脏计数）
        sessionBrandSpoofs = 0;
        sessionChannelBlocks = 0;
        announceDisguiseReady();
    }

    /** 进服伪装自检：单条多行块，播报四个伪装组件的实际开关状态（每会话一次） */
    private void announceDisguiseReady() {
        notify(
            "进服伪装自检：" +
                "\n§8├─ §f客户端名 §8▸ " + (fakeBrand.get() ? highlightText("vanilla") : "§c未伪装") +
                "\n§8├─ §fMod通信 §8▸ " + (blockModChannels.get() ? highlightText("拦截中") : "§c未拦截") +
                "\n§8├─ §f假潜行 §8▸ " + (blockFakeSneak.get() ? highlightText("拦截中") : "§c未拦截") +
                "\n§8└─ §f假疾跑 §8▸ " + (blockFakeSprint.get() ? highlightText("拦截中") : "§c未拦截")
        );
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;

        // 用进服时刻算存活时长，不看任何会被重置的计时器
        long sessionDuration = System.currentTimeMillis() - sessionStartAt;
        if (sessionStartAt > 0 && sessionDuration < 60_000 && enableAnalysis.get()) {
            notifyError("§c存活不到 1 分钟，可能被踢了");
        }
        // 离服汇总：本会话伪装层实际拦截量。被踢/秒退时玩家最需要这个证据
        if (sessionBrandSpoofs > 0 || sessionChannelBlocks > 0) {
            notify(
                "本会话伪装统计：" +
                    "\n§8├─ §f品牌替换 §8▸ " + highlightNumber(String.valueOf(sessionBrandSpoofs)) + " 次" +
                    "\n§8└─ §fMod频道拦截 §8▸ " + highlightNumber(String.valueOf(sessionChannelBlocks)) + " 个"
            );
        }
        sessionStartAt = 0L;
        sessionBrandSpoofs = 0;
        sessionChannelBlocks = 0;
    }

    @EventHandler
    private void onSessionReset(TacticalCoordinator.SessionResetEvent event) {
        resetSessionState();
    }

    private void resetSessionState() {
        chatQueue.clear();
        forwardingChat = false;
        lastChatSendTime = 0;
        antiAfkTicker = 0;
        digThisSecond.set(0);
        interactThisSecond.set(0);
        lastThrottleResetTime = System.currentTimeMillis();
        throttleFactor = 1.0;
        lastRubberBandNotice = 0L;
        rubberBandHistory.clear();
        currentlyDigging = false;
        currentlyPlacing = false;
        sessionBrandSpoofs = 0;
        sessionChannelBlocks = 0;
        shakeTickCounter = 0;
        nextShakeAt = 3 + random.nextInt(6);
        lastPosition = mc.player != null ? mc.player.position() : Vec3.ZERO;
        synchronized (delayQueue) {
            delayQueue.clear();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  发包拦截 - 伪装+聊天+限速+网络延迟
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler(priority = -100)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;
        Packet<?> packet = event.packet;

        // ① 伪装客户端
        // Brand 是服务端主动查询的，伪造包必须替换；替换用 sendSilently 直达底层，
        // 不走发包事件，避免「拦截→重发→再拦截」的递归
        if (fakeBrand.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
            CustomPacketPayload payload = customPayload.payload();
            if (payload instanceof BrandPayload brandPayload) {
                if (!"vanilla".equals(brandPayload.brand())) {
                    event.cancel();
                    event.sendSilently(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
                    sessionBrandSpoofs++;
                }
                return;
            }
        }

        // 拦截 mod 频道：非 minecraft 命名空间直接暴露 Mod 列表；minecraft:register /
        // unregister 的正文就是 mod 频道列表，一并拦掉，否则伪造客户端形同虚设
        if (blockModChannels.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
            CustomPacketPayload payload = customPayload.payload();
            String namespace = payload.type().id().getNamespace();
            String id = payload.type().id().toString();
            if (!namespace.equals("minecraft")
                || id.equals("minecraft:register")
                || id.equals("minecraft:unregister")) {
                event.setCancelled(true);
                sessionChannelBlocks++;
                return;
            }
        }

        if (packet instanceof ServerboundPlayerInputPacket inputPacket) {
            Input input = inputPacket.input();
            boolean newShift = input.shift();
            boolean newSprint = input.sprint();
            boolean modified = false;

            // 假潜行：shift 标记与移动速度矛盾（潜行应慢，高速说明假潜行）。
            // 不能取消整包——Input 包携带全部按键，重写包体只摘掉 shift 标记
            if (blockFakeSneak.get() && input.shift() && mc.player != null) {
                double speed = mc.player.getDeltaMovement().horizontalDistance();
                boolean onIce = mc.level.getBlockState(mc.player.blockPosition().below()).getBlock()
                    instanceof net.minecraft.world.level.block.IceBlock;
                if (speed > 0.16 && !onIce && !mc.player.isSprinting()) {
                    newShift = false;
                    modified = true;
                }
            }

            // 假疾跑：sprint 标记与移动方向矛盾。sprint=true 却无前进输入或正在后退，
            // 即 Tweakeroo 假疾跑的特征，摘掉 sprint
            if (blockFakeSprint.get() && input.sprint() && (!input.forward() || input.backward())) {
                newSprint = false;
                modified = true;
            }

            if (modified) {
                event.packet = new ServerboundPlayerInputPacket(new Input(
                    input.forward(), input.backward(), input.left(), input.right(),
                    input.jump(), newShift, newSprint
                ));
                return;
            }
        }

        // ② 聊天排队（重发中的聊天包凭转发标记放行，防止死循环）
        if (enableChatQueue.get() && packet instanceof ServerboundChatPacket chatPacket && !forwardingChat) {
            event.cancel();
            chatQueue.offer(chatPacket.message());
            return;
        }

        // ④ 限制发包
        if (applyThrottle(event, packet)) return;

        // ⑦ 网络延迟：转发包记住原始连接，重发走三参 send（不触发发包事件）
        if (enableNetworkDelay.get() && shouldDelay(packet)) {
            event.cancel();
            enqueueDelayed(packet, event.connection);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  拉回分析（状态订阅：冷却与统计已由协调器完成，不做任何发包响应）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onRubberBand(TacticalCoordinator.RubberBandDetectedEvent event) {
        if (!isActive()) return;

        // 26.1.2 原版 ClientPacketListener.handleMovePlayer 已自动回发
        // AcceptTeleportation + 1 个 PosRot 确认包，这里只记分析数据，不再发包

        if (mc.player == null) return;

        if (enableAnalysis.get()) {
            boolean flying = !mc.player.onGround() && mc.player.getDeltaMovement().y > -0.08;
            double speed = mc.player.getDeltaMovement().horizontalDistance();

            rubberBandHistory.add(new RubberBandRecord(flying, currentlyDigging, currentlyPlacing, speed));
            currentlyDigging = false;
            currentlyPlacing = false;

            if (rubberBandHistory.size() >= analysisThreshold.get()) {
                analyzeRubberBands();
                rubberBandHistory.clear();
            }
        }

        // 播报节流：拉回可能连发，5 秒只报一次
        long now = System.currentTimeMillis();
        if (now - lastRubberBandNotice >= 5000) {
            lastRubberBandNotice = now;
            notify("§e⚠ 被拉回 §8▸ 冷却与统计已由协调器登记");
        }
    }

    /**
     * 反作弊检测联动：协调器确认高风险反作弊时动态收紧发包限速（阈值打五折），
     * 规避高频挖掘/放置包被判定为自动化而踢出。运行时打折不改用户设置。
     */
    @EventHandler
    private void onAntiCheatDetected(TacticalCoordinator.AntiCheatDetectedEvent event) {
        if (!isActive() || !TacticalCoordinator.hasAdvancedAntiCheat()) return;

        if (throttleFactor > 0.5) {
            throttleFactor = 0.5;
            notify("检测到 " + event.antiCheatName + "，发包限速收紧到 50%");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Tick 处理 - 聊天+防挂机+视角抖动
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        // ② 聊天排队
        if (enableChatQueue.get() && !chatQueue.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastChatSendTime >= chatInterval.get()) {
                String message = chatQueue.poll();
                if (message != null) {
                    // 转发标记：sendChat 产出的聊天包在发包事件里放行，不再次入队
                    forwardingChat = true;
                    try {
                        mc.player.connection.sendChat(message);
                    } finally {
                        forwardingChat = false;
                    }
                    lastChatSendTime = now;
                }
            }
        }

        // ③ 防挂机：每 5 秒发一个微小视角旋转包刷新服务端活跃时间戳。
        // RecipeBook 设置包不刷新该时间戳，任何移动/转身才会
        if (antiAfk.get()) {
            antiAfkTicker++;
            if (antiAfkTicker >= 100) {
                antiAfkTicker = 0;
                float yaw = mc.player.getYRot() + (random.nextFloat() - 0.5f);
                mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    yaw, mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
            }
        }

        // ⑦ 视角抖动
        if (enableViewShake.get()) {
            handleViewShake();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  限制发包实现
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private boolean applyThrottle(PacketEvent.Send event, Packet<?> packet) {
        long now = System.currentTimeMillis();
        if (now - lastThrottleResetTime >= 1000) {
            digThisSecond.set(0);
            interactThisSecond.set(0);
            lastThrottleResetTime = now;
        }

        // 服务器卡顿 / 拉回冷却（只读协调器）：直接丢弃挖掘与放置包，避免顶风作案
        boolean stressed = TacticalCoordinator.isServerLagging() || TacticalCoordinator.isRubberBandCooldown();

        if (limitDigging.get() && packet instanceof ServerboundPlayerActionPacket action) {
            ServerboundPlayerActionPacket.Action type = action.getAction();
            if (type == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || type == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {

                currentlyDigging = true;
                // 动态限速：高风险反作弊时按 throttleFactor 收紧阈值（运行时打折）
                int effectiveMax = (int) Math.max(2, maxDigPerSecond.get() * throttleFactor);
                if (stressed || digThisSecond.get() >= effectiveMax) {
                    event.cancel();
                    return true;
                }
                digThisSecond.incrementAndGet();
            }
        }

        if (limitInteract.get() && (packet instanceof ServerboundUseItemOnPacket
            || packet instanceof ServerboundUseItemPacket)) {

            currentlyPlacing = true;
            int effectiveMax = (int) Math.max(2, maxInteractPerSecond.get() * throttleFactor);
            if (stressed || interactThisSecond.get() >= effectiveMax) {
                event.setCancelled(true);
                return true;
            }
            interactThisSecond.incrementAndGet();
        }

        return false;
    }

    private void analyzeRubberBands() {
        int total = rubberBandHistory.size();
        int flyingCount = 0, diggingCount = 0, placingCount = 0, speedCount = 0;

        for (RubberBandRecord r : rubberBandHistory) {
            if (r.flying) flyingCount++;
            if (r.digging) diggingCount++;
            if (r.placing) placingCount++;
            if (r.speed > 0.3) speedCount++;
        }

        // 报告合并为单条多行块，正文「标签 §8▸ 值」对齐，禁止逐条刷屏
        StringBuilder sb = new StringBuilder("§e§l拉回分析报告 §8（").append(total).append(" 次）§r\n");
        sb.append("§8├─ §f飞行时被拉 §8▸ ").append(highlightNumber(String.valueOf(flyingCount)))
            .append(" 次 §8（").append(percent(flyingCount, total)).append("%）\n");
        sb.append("§8├─ §f挖掘时被拉 §8▸ ").append(highlightNumber(String.valueOf(diggingCount)))
            .append(" 次 §8（").append(percent(diggingCount, total)).append("%）\n");
        sb.append("§8├─ §f放置时被拉 §8▸ ").append(highlightNumber(String.valueOf(placingCount)))
            .append(" 次 §8（").append(percent(placingCount, total)).append("%）\n");
        sb.append("§8└─ §f高速时被拉 §8▸ ").append(highlightNumber(String.valueOf(speedCount)))
            .append(" 次 §8（").append(percent(speedCount, total)).append("%）");

        // 建议行独立于统计树之外（用 ▸ 而非 └─），避免出现双 └─ 破坏树形结构
        if (flyingCount > total * 0.5) sb.append("\n§8▸ §a建议 §8▸ 把「飞行绕过」切换到安全滑翔或原版模拟");
        else if (diggingCount > total * 0.4) sb.append("\n§8▸ §a建议 §8▸ 降低「每秒最多挖几个」的值");
        else if (placingCount > total * 0.4) sb.append("\n§8▸ §a建议 §8▸ 降低「每秒最多放几个」的值");

        notify(sb.toString());
    }

    private int percent(int part, int total) {
        return total == 0 ? 0 : (int) ((double) part / total * 100);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  视角抖动实现
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void handleViewShake() {
        Vec3 currentPos = mc.player.position();
        boolean isMoving = currentPos.distanceTo(lastPosition) > 0.01;
        lastPosition = currentPos;

        if (!isMoving) return;

        shakeTickCounter++;
        if (shakeTickCounter >= nextShakeAt) {
            shakeTickCounter = 0;
            nextShakeAt = 3 + random.nextInt(6);

            float intensity = shakeIntensity.get().floatValue();
            float deltaYaw = (random.nextFloat() - 0.5f) * 2 * intensity;
            float deltaPitch = (random.nextFloat() - 0.5f) * 2 * intensity;

            float newYaw = mc.player.getYRot() + deltaYaw;
            float newPitch = Math.max(-90, Math.min(90, mc.player.getXRot() + deltaPitch));

            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                newYaw, newPitch, mc.player.onGround(), mc.player.horizontalCollision
            ));
            mc.player.setYRot(newYaw);
            mc.player.setXRot(newPitch);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  网络延迟实现（移动包不进队列，重发走三参 send 绕过发包事件防死循环）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private boolean shouldDelay(Packet<?> packet) {
        return !(packet instanceof ServerboundKeepAlivePacket
            || packet instanceof ServerboundAcceptTeleportationPacket
            || packet instanceof ServerboundMovePlayerPacket);
    }

    private void enqueueDelayed(Packet<?> packet, Connection connection) {
        int delay = minDelay.get() + random.nextInt(maxDelay.get() - minDelay.get() + 1);
        long sendAt = System.currentTimeMillis() + delay;
        synchronized (delayQueue) {
            delayQueue.offer(new DelayedPacket(packet, connection, sendAt));
        }
    }

    private void processDelayQueue() {
        if (mc.player == null || mc.player.connection == null) return;
        long now = System.currentTimeMillis();
        List<DelayedPacket> toSend = new ArrayList<>();

        synchronized (delayQueue) {
            while (!delayQueue.isEmpty() && delayQueue.peek().sendAt <= now) {
                toSend.add(delayQueue.poll());
            }
        }

        if (!toSend.isEmpty()) {
            mc.execute(() -> {
                for (DelayedPacket d : toSend) {
                    // 三参 send(packet, listener, flush) 直达底层：
                    // Meteor 发包事件只钩子双参重载，这里不会再次触发 onPacketSend
                    if (d.connection.isConnected()) {
                        d.connection.send(d.packet, null, true);
                    }
                }
            });
        }
    }
}