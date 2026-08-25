package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 终极防踢模块（全功能整合版）
 * 
 * 包含 7 大功能：
 * 1. 伪装客户端 - 改 Brand、拦截 Mod 频道
 * 2. 聊天排队 - 自动排队防刷屏
 * 3. 防挂机 - 假装在操作
 * 4. 限制发包 - 防止挖太快/放太快被踢
 * 5. 拉回处理 - 被拉回时自动断流
 * 6. 拉回分析 - 记录什么操作容易被拉回
 * 7. 模拟真人 - 视角抖动、网络延迟
 * 
 * @author yiyijia
 */
public class AntiKickBypass extends YiyiaddonModule {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  设置分组
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final SettingGroup sg1 = settings.createGroup("① 伪装客户端");
    private final SettingGroup sg2 = settings.createGroup("② 聊天排队");
    private final SettingGroup sg3 = settings.createGroup("③ 防挂机");
    private final SettingGroup sg4 = settings.createGroup("④ 限制发包");
    private final SettingGroup sg5 = settings.createGroup("⑤ 拉回处理");
    private final SettingGroup sg6 = settings.createGroup("⑥ 拉回分析");
    private final SettingGroup sg7 = settings.createGroup("⑦ 模拟真人");

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ① 伪装客户端 - 让服务器认为你是原版玩家
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> fakeBrand = sg1.add(new BoolSetting.Builder()
        .name("改客户端名字")
        .description("服务器问你用什么客户端时回答 vanilla（原版），不让它知道你用外挂")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockModChannels = sg1.add(new BoolSetting.Builder()
        .name("拦截 Mod 通信")
        .description("阻止你的 Mod 列表发给服务器，避免被检测到")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockFakeSneak = sg1.add(new BoolSetting.Builder()
        .name("拦截假潜行")
        .description("你在用 Tweakeroo 假潜行时，服务器会发现你潜行但跑很快，这个功能会帮你拦掉")
        .defaultValue(true)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ② 聊天排队 - 防止发消息太快被踢
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> enableChatQueue = sg2.add(new BoolSetting.Builder()
        .name("开启聊天排队")
        .description("你发消息太快时自动排队，慢慢发，防止被踢")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chatInterval = sg2.add(new IntSetting.Builder()
        .name("每条消息间隔（毫秒）")
        .description("两条消息之间等多久，1500 毫秒 = 1.5 秒")
        .defaultValue(1500)
        .min(1000)
        .max(3000)
        .sliderMax(3000)
        .visible(() -> enableChatQueue.get())
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ③ 防挂机 - 假装你在玩游戏
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> antiAfk = sg3.add(new BoolSetting.Builder()
        .name("防挂机检测")
        .description("每 10 秒假装你在翻配方书，让服务器以为你在玩")
        .defaultValue(true)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ④ 限制发包 - 防止挖太快/放太快被踢
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
        .sliderRange(2, 20)
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
        .sliderRange(2, 20)
        .visible(limitInteract::get)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ⑤ 拉回处理 - 被拉回时自动处理
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // 无设置项，自动运行

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ⑥ 拉回分析 - 记录什么操作容易被拉回
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> enableAnalysis = sg6.add(new BoolSetting.Builder()
        .name("开启拉回分析")
        .description("记录你每次被拉回时在做什么，累积 10 次后告诉你哪个操作最容易被拉回")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> analysisThreshold = sg6.add(new IntSetting.Builder()
        .name("累积几次后分析")
        .description("被拉回多少次后给你一份分析报告")
        .defaultValue(10)
        .min(5)
        .max(50)
        .sliderRange(5, 50)
        .visible(() -> enableAnalysis.get())
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ⑦ 模拟真人 - 让你的操作看起来像真人
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> enableViewShake = sg7.add(new BoolSetting.Builder()
        .name("视角抖动")
        .description("移动时视角会轻微抖动，模拟手抖，看起来更像真人")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> shakeIntensity = sg7.add(new DoubleSetting.Builder()
        .name("抖动幅度")
        .description("抖多厉害，2° 刚好，太大会被识别成机器人")
        .defaultValue(2.0)
        .min(0.5)
        .max(5.0)
        .sliderMax(5.0)
        .visible(() -> enableViewShake.get())
        .build()
    );

    private final Setting<Boolean> enableNetworkDelay = sg7.add(new BoolSetting.Builder()
        .name("网络延迟")
        .description("发包时随机延迟 20-80 毫秒，模拟网络卡顿")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minDelay = sg7.add(new IntSetting.Builder()
        .name("最小延迟（毫秒）")
        .description("延迟下限")
        .defaultValue(20)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(() -> enableNetworkDelay.get())
        .build()
    );

    private final Setting<Integer> maxDelay = sg7.add(new IntSetting.Builder()
        .name("最大延迟（毫秒）")
        .description("延迟上限，不要超过 100 毫秒，会卡")
        .defaultValue(80)
        .min(0)
        .max(200)
        .sliderRange(0, 200)
        .visible(() -> enableNetworkDelay.get())
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部数据
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // 聊天队列
    private final Queue<String> chatQueue = new LinkedList<>();
    private long lastChatSendTime = 0;

    // 防挂机计数器
    private int antiAfkTicker = 0;

    // 限制发包计数器
    private final AtomicInteger digThisSecond = new AtomicInteger(0);
    private final AtomicInteger interactThisSecond = new AtomicInteger(0);
    private long lastThrottleResetTime = System.currentTimeMillis();
    private final AtomicInteger throttledCount = new AtomicInteger(0);

    // 拉回处理状态
    private boolean rubberBandHandling = false;
    private int rubberBandCooldownTicks = 0;

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

    // 网络延迟
    private final PriorityQueue<DelayedPacket> delayQueue = new PriorityQueue<>(
        Comparator.comparingLong(p -> p.sendAt)
    );
    private static class DelayedPacket {
        final Packet<?> packet;
        final long sendAt;
        DelayedPacket(Packet<?> packet, long sendAt) {
            this.packet = packet;
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
        super(CATEGORY_TACTICAL, "anti-kick-bypass", "7 合 1 防踢系统：伪装+排队+防挂机+限速+拉回处理+分析+真人模拟。");
    }

    @Override
    public void onActivate() {
        lastChatSendTime = 0;
        antiAfkTicker = 0;
        digThisSecond.set(0);
        interactThisSecond.set(0);
        throttledCount.set(0);
        rubberBandHandling = false;
        rubberBandCooldownTicks = 0;
        shakeTickCounter = 0;
        nextShakeAt = 3 + random.nextInt(6);
        lastPosition = mc.player != null ? mc.player.position() : Vec3.ZERO;

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
    //  发包拦截 - 伪装+聊天+限速+网络延迟
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler(priority = -100)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;
        Packet<?> packet = event.packet;

        // ① 伪装客户端
        if (fakeBrand.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
            CustomPacketPayload payload = customPayload.payload();
            if (payload instanceof BrandPayload) {
                event.setCancelled(true);
                event.connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
                return;
            }
        }

        if (blockModChannels.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
            CustomPacketPayload payload = customPayload.payload();
            String namespace = payload.type().id().getNamespace();
            if (!namespace.equals("minecraft")) {
                event.setCancelled(true);
                return;
            }
        }

        if (blockFakeSneak.get() && packet instanceof ServerboundPlayerInputPacket inputPacket) {
            Input input = inputPacket.input();
            if (input.shift() && mc.player != null) {
                double speed = mc.player.getDeltaMovement().horizontalDistance();
                boolean onIce = mc.level.getBlockState(mc.player.blockPosition().below()).getBlock() 
                    instanceof net.minecraft.world.level.block.IceBlock;
                if (speed > 0.16 && !onIce && !mc.player.isSprinting()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // ② 聊天排队
        if (enableChatQueue.get() && packet instanceof ServerboundChatPacket chatPacket) {
            event.setCancelled(true);
            chatQueue.offer(chatPacket.message());
            return;
        }

        // ④ 限制发包
        if (applyThrottle(event, packet)) return;

        // ⑦ 网络延迟
        if (enableNetworkDelay.get() && shouldDelay(packet)) {
            event.setCancelled(true);
            enqueueDelayed(packet);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  收包监听 - 拉回处理
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!isActive()) return;

        if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
            handleRubberBand(packet);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;

        long sessionDuration = System.currentTimeMillis() - lastThrottleResetTime;
        if (sessionDuration < 60_000 && enableAnalysis.get()) {
            notifyError("§c存活不到 1 分钟，可能被踢了");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Tick 处理 - 聊天+防挂机+拉回冷却+视角抖动
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        // 拉回冷却倒计时
        if (rubberBandHandling) {
            rubberBandCooldownTicks--;
            if (rubberBandCooldownTicks <= 0) {
                rubberBandHandling = false;
                TacticalFSM.setRubberBandCooldown(false);
            }
        }

        // ② 聊天排队
        if (enableChatQueue.get() && !chatQueue.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastChatSendTime >= chatInterval.get()) {
                String message = chatQueue.poll();
                if (message != null) {
                    mc.player.connection.sendChat(message);
                    lastChatSendTime = now;
                }
            }
        }

        // ③ 防挂机
        if (antiAfk.get()) {
            antiAfkTicker++;
            if (antiAfkTicker >= 200) {
                antiAfkTicker = 0;
                mc.player.connection.send(new ServerboundRecipeBookChangeSettingsPacket(
                    RecipeBookType.CRAFTING, false, false
                ));
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

        if (limitDigging.get() && packet instanceof ServerboundPlayerActionPacket action) {
            ServerboundPlayerActionPacket.Action type = action.getAction();
            if (type == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || type == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                
                currentlyDigging = true;
                if (digThisSecond.get() >= maxDigPerSecond.get()) {
                    event.cancel();
                    throttledCount.incrementAndGet();
                    return true;
                }
                digThisSecond.incrementAndGet();
            }
        }

        if (limitInteract.get() && (packet instanceof ServerboundUseItemOnPacket 
            || packet instanceof ServerboundUseItemPacket)) {
            
            currentlyPlacing = true;
            if (interactThisSecond.get() >= maxInteractPerSecond.get()) {
                event.setCancelled(true);
                throttledCount.incrementAndGet();
                return true;
            }
            interactThisSecond.incrementAndGet();
        }

        return false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  拉回处理实现
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void handleRubberBand(ClientboundPlayerPositionPacket packet) {
        // ⑤ 拉回处理
        mc.player.connection.send(new ServerboundAcceptTeleportationPacket(packet.id()));
        rubberBandHandling = true;
        rubberBandCooldownTicks = 40;
        TacticalFSM.setRubberBandCooldown(true);

        for (int i = 0; i < 3; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                packet.change().position().x,
                packet.change().position().y,
                packet.change().position().z,
                mc.player.getYRot(),
                mc.player.getXRot(),
                true, false
            ));
        }

        // ⑥ 拉回分析
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

        notify("§e被拉回！已自动处理（发确认包 + 静止包 + 2 秒冷却）");
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

        notify("§e§l拉回分析报告（" + total + " 次）");
        notify("§f飞行时被拉：§c" + flyingCount + "§f 次（" + percent(flyingCount, total) + "%）");
        notify("§f挖掘时被拉：§c" + diggingCount + "§f 次（" + percent(diggingCount, total) + "%）");
        notify("§f放置时被拉：§c" + placingCount + "§f 次（" + percent(placingCount, total) + "%）");
        notify("§f高速时被拉：§c" + speedCount + "§f 次（" + percent(speedCount, total) + "%）");

        if (flyingCount > total * 0.5) notify("§a建议：去「飞行绕过」模块切换到安全滑翔");
        if (diggingCount > total * 0.4) notify("§a建议：降低「每秒最多挖几个」的值");
        if (placingCount > total * 0.4) notify("§a建议：降低「每秒最多放几个」的值");
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
    //  网络延迟实现
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private boolean shouldDelay(Packet<?> packet) {
        return !(packet instanceof ServerboundKeepAlivePacket 
            || packet instanceof ServerboundAcceptTeleportationPacket);
    }

    private void enqueueDelayed(Packet<?> packet) {
        int delay = minDelay.get() + random.nextInt(maxDelay.get() - minDelay.get() + 1);
        long sendAt = System.currentTimeMillis() + delay;
        synchronized (delayQueue) {
            delayQueue.offer(new DelayedPacket(packet, sendAt));
        }
    }

    private void processDelayQueue() {
        if (mc.player == null || mc.player.connection == null) return;
        long now = System.currentTimeMillis();
        List<Packet<?>> toSend = new ArrayList<>();

        synchronized (delayQueue) {
            while (!delayQueue.isEmpty() && delayQueue.peek().sendAt <= now) {
                toSend.add(delayQueue.poll().packet);
            }
        }

        if (!toSend.isEmpty()) {
            mc.execute(() -> {
                if (mc.player != null && mc.player.connection != null) {
                    for (Packet<?> p : toSend) {
                        mc.player.connection.send(p);
                    }
                }
            });
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UI 界面
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            table -> {
                WButton clearData = theme.button("清空拉回分析数据");
                clearData.action = () -> {
                    rubberBandHistory.clear();
                    notify("§a已清空");
                };
                table.add(clearData).expandX();
                table.row();
            },
            new String[]{ "§l终极防踢 · 使用说明" },
            new String[]{
                "§e§l▌ 快速开始",
                "§f  1. 打开这个模块（终极防踢）",
                "§f  2. 打开「服务器检测」模块",
                "§f  3. 打开「飞行绕过」模块",
                "§f  4. 进服务器，自动运行"
            },
            new String[]{
                "§a§l▌ 7 大功能",
                "§f  ① 伪装客户端 - 让服务器以为你是原版玩家",
                "§f  ② 聊天排队 - 发消息太快时自动排队",
                "§f  ③ 防挂机 - 假装你在操作",
                "§f  ④ 限制发包 - 防止挖/放太快被踢",
                "§f  ⑤ 拉回处理 - 被拉回时自动发确认包",
                "§f  ⑥ 拉回分析 - 记录什么操作容易被拉回",
                "§f  ⑦ 模拟真人 - 视角抖动 + 网络延迟"
            },
            new String[]{
                "§b§l▌ 当前状态",
                "§f  · 聊天队列：§e" + chatQueue.size() + "§f 条排队中",
                "§f  · 本次拦截：§e" + throttledCount.get() + "§f 个包",
                "§f  · 拉回记录：§e" + rubberBandHistory.size() + "§f 次",
                "§f  · 延迟队列：§e" + delayQueue.size() + "§f 个包"
            },
            new String[]{
                "§c§l▌ 推荐设置",
                "§f  · 挖掘上限：§e8§f 个/秒（原版 5，留点余量）",
                "§f  · 放置上限：§e8§f 个/秒（原版 4，留点余量）",
                "§f  · 视角抖动：§e2°§f（太大会被识别）",
                "§f  · 网络延迟：§e默认关闭§f（开了会卡，慎用）"
            },
            new String[]{
                "§d§l▌ 注意事项",
                "§f  · 「网络延迟」会让你操作变卡，不建议开",
                "§f  · 被拉回 10 次后会自动给你分析报告",
                "§f  · 如果一直被拉回，去「飞行绕过」换模式",
                "§f  · 高级反作弊服务器建议全部开启"
            }
        );
    }
}
