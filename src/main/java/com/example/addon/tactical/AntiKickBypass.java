package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 发包防踢模块（完整实现）
 * 
 * 功能：
 * 1. Masa伪装（Brand改vanilla + 白名单拦截Mod频道 + NBT限频 + 假潜行拦截）
 * 2. 聊天队列（1500ms间隔 + 全角空格混淆）
 * 3. 拉回断流（暂停 + 确认包 + 静止包）
 * 4. 活跃欺骗（高频发配方书包）
 * 
 * @author yiyijia
 */
public class AntiKickBypass extends YiyiaddonModule {

    private final SettingGroup sgMasa = settings.createGroup("Masa伪装");
    private final SettingGroup sgChat = settings.createGroup("聊天队列");
    private final SettingGroup sgAntiAfk = settings.createGroup("防挂机");
    private final SettingGroup sgThrottle = settings.createGroup("过载断流");
    private final SettingGroup sgPacketLoss = settings.createGroup("丢包伪装");

    // Masa伪装设置
    private final Setting<Boolean> fakeBrand = sgMasa.add(new BoolSetting.Builder()
        .name("伪装Brand")
        .description("将客户端Brand改为vanilla")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockModChannels = sgMasa.add(new BoolSetting.Builder()
        .name("拦截Mod频道")
        .description("白名单拦截所有非minecraft:命名空间的CustomPayload包")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> limitNbtQueries = sgMasa.add(new BoolSetting.Builder()
        .name("限制NBT查询")
        .description("限制QueryBlockNbtC2SPacket频率（每秒最多2个）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockFakeSneak = sgMasa.add(new BoolSetting.Builder()
        .name("拦截假潜行")
        .description("检测潜行时速度>0.3则拦截（Tweakeroo指纹）")
        .defaultValue(true)
        .build()
    );

    // 聊天队列设置
    private final Setting<Boolean> enableChatQueue = sgChat.add(new BoolSetting.Builder()
        .name("启用聊天队列")
        .description("高频聊天送入队列，按1500ms间隔发送")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chatInterval = sgChat.add(new IntSetting.Builder()
        .name("发送间隔（ms）")
        .description("队列中两条消息的发送间隔")
        .defaultValue(1500)
        .min(1000)
        .max(3000)
        .sliderMax(3000)
        .visible(() -> enableChatQueue.get())
        .build()
    );

    private final Setting<Boolean> obfuscateChat = sgChat.add(new BoolSetting.Builder()
        .name("混淆字符")
        .description("在消息末尾随机插入全角空格（\u3000）")
        .defaultValue(true)
        .visible(() -> enableChatQueue.get())
        .build()
    );

    // 防挂机设置
    private final Setting<Boolean> antiAfk = sgAntiAfk.add(new BoolSetting.Builder()
        .name("防挂机检测")
        .description("每10秒发送配方书假包清零挂机判定")
        .defaultValue(true)
        .build()
    );

    // ── 过载断流：挖掘与交互限频 ──
    private final Setting<Boolean> limitDigging = sgThrottle.add(new BoolSetting.Builder()
        .name("挖掘限频")
        .description("限制每秒破坏方块包数量，超出的排队顺延，防止挖太快被踢")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxDigPerSecond = sgThrottle.add(new IntSetting.Builder()
        .name("每秒挖掘上限")
        .description("原版极限约 5 个/秒，设太高等于没限")
        .defaultValue(8)
        .min(2)
        .max(20)
        .sliderRange(2, 20)
        .visible(limitDigging::get)
        .build()
    );

    private final Setting<Boolean> limitInteract = sgThrottle.add(new BoolSetting.Builder()
        .name("交互限频")
        .description("限制每秒方块放置与右键包数量，超出部分直接拦截，防止交互太快被踢")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxInteractPerSecond = sgThrottle.add(new IntSetting.Builder()
        .name("每秒交互上限")
        .description("原版右键极限约 4 个/秒")
        .defaultValue(8)
        .min(2)
        .max(20)
        .sliderRange(2, 20)
        .visible(limitInteract::get)
        .build()
    );

    private final Setting<Boolean> throttleOnDownload = sgThrottle.add(new BoolSetting.Builder()
        .name("下载期间降速")
        .description("资源包下载中把上限压到一半，模拟真实下载卡顿")
        .defaultValue(true)
        .build()
    );

    // ── 丢包：主动丢弃部分出站包 ──
    private final Setting<Integer> moveDropRate = sgPacketLoss.add(new IntSetting.Builder()
        .name("移动包丢包率")
        .description("按百分比随机丢弃移动包，伪造网络抖动。超过 20% 容易触发拉回，谨慎调高")
        .defaultValue(0)
        .min(0)
        .max(60)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> interactDropRate = sgPacketLoss.add(new IntSetting.Builder()
        .name("交互包丢包率")
        .description("按百分比随机丢弃方块交互包。丢弃会使该次挖掘/放置无效，仅用于压低发包密度")
        .defaultValue(0)
        .min(0)
        .max(60)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Boolean> keepCriticalPackets = sgPacketLoss.add(new BoolSetting.Builder()
        .name("保护关键包")
        .description("丢包时不碰传送确认包与带序号的交互包，避免坐标错乱与方块回滚")
        .defaultValue(true)
        .build()
    );

    // 内部状态
    private final Queue<String> chatQueue = new LinkedList<>();
    private long lastChatSendTime = 0;
    private final AtomicInteger nbtQueriesThisSecond = new AtomicInteger(0);
    private long lastNbtResetTime = System.currentTimeMillis();
    private final Random random = new Random();

    // 拉回包冷却状态
    private boolean rubberBandHandling = false;
    private int rubberBandCooldownTicks = 0;

    // 防挂机计数器
    private int antiAfkTicker = 0;

    // 过载断流：滑动窗口计数（每秒重置）
    private int digThisSecond = 0;
    private int interactThisSecond = 0;
    private long lastThrottleResetTime = System.currentTimeMillis();

    // 统计：本次会话被拦截/丢弃的包数，用于面板回显
    private int throttledCount = 0;
    private int droppedCount = 0;

    public AntiKickBypass() {
        super(CATEGORY_TACTICAL, "发包防踢", "全方位发包拦截，Masa伪装，聊天队列，拉回断流。");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Masa伪装 - 拦截发送的包
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;

        Packet<?> packet = event.packet;

        // 1. Brand伪装：拦截并改写为vanilla
        if (fakeBrand.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
            CustomPacketPayload payload = customPayload.payload();
            if (payload instanceof BrandPayload) {
                // 改写为vanilla
                event.setCancelled(true);
                event.connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
                return;
            }
        }

        // 2. Mod频道拦截：只放行minecraft:命名空间
        if (blockModChannels.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
            CustomPacketPayload payload = customPayload.payload();
            String namespace = payload.type().id().getNamespace();
            if (!namespace.equals("minecraft")) {
                event.setCancelled(true);
                return;
            }
        }

        // 3. NBT查询限频：每秒最多2个
        if (limitNbtQueries.get() && packet instanceof ServerboundBlockEntityTagQueryPacket) {
            // 每秒重置计数器
            long now = System.currentTimeMillis();
            if (now - lastNbtResetTime > 1000) {
                nbtQueriesThisSecond.set(0);
                lastNbtResetTime = now;
            }

            // 检查是否超过限制
            if (nbtQueriesThisSecond.incrementAndGet() > 2) {
                event.setCancelled(true);
                return;
            }
        }

        // 4. 假潜行拦截：26.1.2 的潜行状态在 PlayerInput 包的 shift 位里
        //    Tweakeroo 的假潜行会出现「shift=true 但水平速度仍是正常行走速度」的组合，
        //    这里把 shift 位抹掉重发，服务端只会看到一次普通移动。
        if (blockFakeSneak.get() && packet instanceof ServerboundPlayerInputPacket inputPacket) {
            Input input = inputPacket.input();
            if (input.shift() && mc.player != null && mc.player.getDeltaMovement().horizontalDistance() > 0.3) {
                event.setCancelled(true);
                event.connection.send(new ServerboundPlayerInputPacket(new Input(
                    input.forward(), input.backward(), input.left(), input.right(),
                    input.jump(), false, input.sprint()
                )));
                return;
            }
        }

        // 5. 过载断流：挖掘与交互限频，超额直接拦截
        if (applyThrottle(event, packet)) return;

        // 6. 丢包伪装：按概率丢弃出站包，制造网络抖动的表象
        if (applyPacketLoss(event, packet)) return;

        // 7. 聊天队列：拦截高频聊天并送入队列
        if (enableChatQueue.get() && packet instanceof ServerboundChatPacket chatPacket) {
            event.setCancelled(true);
            String message = chatPacket.message();

            // 混淆：随机插入全角空格
            if (obfuscateChat.get()) {
                int insertCount = random.nextInt(3) + 1; // 1-3个全角空格
                StringBuilder sb = new StringBuilder(message);
                for (int i = 0; i < insertCount; i++) {
                    sb.append("\u3000"); // 全角空格
                }
                message = sb.toString();
            }

            chatQueue.offer(message);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  过载断流 - 挖掘与交互限频
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 挖掘与交互限频。
     *
     * 服务端的「超速」判定看的是单位时间内的动作包密度，所以这里按秒开窗计数，
     * 超额的包直接拦掉。不做排队重发：sequence 在发包前已取号，延后补发会造成
     * 序号乱序，服务端会回滚方块，比被踢更难排查。
     *
     * @return true 表示已拦截，调用方应停止后续处理
     */
    private boolean applyThrottle(PacketEvent.Send event, Packet<?> packet) {
        long now = System.currentTimeMillis();
        if (now - lastThrottleResetTime >= 1000) {
            digThisSecond = 0;
            interactThisSecond = 0;
            lastThrottleResetTime = now;
        }

        // 资源包下载期间压到一半，配合服务器检测模块模拟下载卡顿
        boolean downloading = throttleOnDownload.get() && TacticalFSM.isDownloadingResourcePack();

        if (limitDigging.get() && packet instanceof ServerboundPlayerActionPacket action) {
            // 只对真正的破坏动作计数，丢弃物品等动作走的是同一个包但不算挖掘
            ServerboundPlayerActionPacket.Action type = action.getAction();
            if (type == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || type == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {

                int limit = downloading ? Math.max(1, maxDigPerSecond.get() / 2) : maxDigPerSecond.get();
                if (++digThisSecond > limit) {
                    event.cancel();
                    throttledCount++;
                    return true;
                }
            }
        }

        if (limitInteract.get()
            && (packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundUseItemPacket)) {

            int limit = downloading ? Math.max(1, maxInteractPerSecond.get() / 2) : maxInteractPerSecond.get();
            if (++interactThisSecond > limit) {
                event.setCancelled(true);
                throttledCount++;
                return true;
            }
        }

        return false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  丢包伪装 - 按概率丢弃出站包
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 按概率丢弃出站包，让服务端看到的发包节奏带上网络抖动的痕迹。
     *
     * 移动包丢弃是安全的：原版客户端本身就允许丢包，服务端靠下一个包的坐标续算。
     * 但丢弃率过高会让服务端认为坐标跳变，反而触发拉回，所以上限压在 60%。
     *
     * 带 sequence 的交互包在开启「保护关键包」时不参与丢弃，原因同 applyThrottle：
     * 取过号的包被丢会留下序号空洞，服务端回滚方块。
     *
     * @return true 表示已丢弃，调用方应停止后续处理
     */
    private boolean applyPacketLoss(PacketEvent.Send event, Packet<?> packet) {
        // 拉回处理期间不丢包：此刻正需要确认包与静止包准确送达
        if (rubberBandHandling) return false;

        if (moveDropRate.get() > 0 && packet instanceof ServerboundMovePlayerPacket) {
            if (random.nextInt(100) < moveDropRate.get()) {
                event.setCancelled(true);
                droppedCount++;
                return true;
            }
        }

        if (interactDropRate.get() > 0
            && (packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundPlayerActionPacket)) {

            // 关键包保护：这些包都携带 sequence，丢弃会导致预测序号空洞
            if (keepCriticalPackets.get()) return false;

            if (random.nextInt(100) < interactDropRate.get()) {
                event.setCancelled(true);
                droppedCount++;
                return true;
            }
        }

        return false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  拉回断流 - 监听拉回事件并执行断流
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onRubberBand(TacticalFSM.RubberBandEvent event) {
        if (!isActive() || mc.player == null) return;

        // 1. 立即发送确认包
        mc.player.connection.send(new ServerboundAcceptTeleportationPacket(event.getTeleportId()));

        // 2. 设置冷却状态（2秒 = 40 ticks）
        rubberBandHandling = true;
        rubberBandCooldownTicks = 40;
        TacticalFSM.setRubberBandCooldown(true);

        // 3. 发送3个静止状态的移动包（模拟"玩家愣住了"）
        for (int i = 0; i < 3; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                event.getX(), event.getY(), event.getZ(),
                mc.player.getYRot(), mc.player.getXRot(),
                true,  // onGround
                false  // horizontalCollision
            ));
        }

        notify("§e拉回包已处理（发送确认包 + 静止包 + 2秒冷却）");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  资源包下载期间降低发包速率
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onResourcePackDownloading(TacticalFSM.ResourcePackDownloadingEvent event) {
        if (!isActive()) return;

        if (event.isDownloading) {
            notify("§e资源包下载中，已降低发包速率");
            // 这里可以实现发包限速逻辑（简化版不实现）
        } else {
            notify("§a资源包下载完成，已恢复正常发包速率");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Tick处理：聊天队列 + 拉回冷却 + 防挂机
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        // 1. 处理拉回包冷却
        if (rubberBandHandling) {
            rubberBandCooldownTicks--;
            if (rubberBandCooldownTicks <= 0) {
                rubberBandHandling = false;
                TacticalFSM.setRubberBandCooldown(false);
            }
        }

        // 2. 处理聊天队列（按间隔发送）
        if (enableChatQueue.get() && !chatQueue.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastChatSendTime >= chatInterval.get()) {
                String message = chatQueue.poll();
                if (message != null) {
                    // 重新构造聊天包并发送（简化版：直接用原生API）
                    mc.player.connection.sendChat(message);
                    lastChatSendTime = now;
                }
            }
        }

        // 3. 防挂机：每10秒（200 ticks）发送配方书假包
        if (antiAfk.get()) {
            antiAfkTicker++;
            if (antiAfkTicker >= 200) {
                antiAfkTicker = 0;
                sendAntiAfkPacket();
            }
        }
    }

    /**
     * 发送防挂机假包（配方书设置包）
     */
    private void sendAntiAfkPacket() {
        if (mc.player == null || mc.player.connection == null) return;

        // 发送配方书设置变更包（服务器会认为客户端在操作）
        mc.player.connection.send(new ServerboundRecipeBookChangeSettingsPacket(
            RecipeBookType.CRAFTING,
            false,  // isOpen
            false   // isFiltering
        ));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UI 面板
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l发包防踢 · 功能说明" },
            new String[]{
                "§e§l▌ Masa伪装",
                "§f  · §aBrand伪装§r: 将客户端标识改为vanilla",
                "§f  · §a拦截Mod频道§r: 白名单拦截非minecraft:频道",
                "§f  · §aNBT限频§r: 每秒最多2个查询包（防Litematica指纹）",
                "§f  · §a假潜行拦截§r: 速度>0.3时拦截潜行包（防Tweakeroo指纹）"
            },
            new String[]{
                "§a§l▌ 聊天队列",
                "§f  · 高频聊天自动送入队列",
                "§f  · 按 " + chatInterval.get() + "ms 间隔发送",
                "§f  · 末尾随机插入全角空格混淆（绕过复读机检测）",
                "§f  · 当前队列长度: §e" + chatQueue.size()
            },
            new String[]{
                "§b§l▌ 拉回断流",
                "§f  · 收到拉回包立即发送确认包",
                "§f  · 发送3个静止状态的移动包（模拟愣住）",
                "§f  · 2秒冷却期间暂停飞行模块"
            },
            new String[]{
                "§d§l▌ 防挂机",
                "§f  · 每10秒发送配方书假包",
                "§f  · 清零服务器挂机判定计时器"
            },
            new String[]{
                "§6§l▌ 过载断流",
                "§f  · 挖掘上限 §e" + maxDigPerSecond.get() + "§f 个/秒，交互上限 §e" + maxInteractPerSecond.get() + "§f 个/秒",
                "§f  · 超额的包直接拦掉，不排队补发",
                "§f    （补发会造成 sequence 空洞，服务端会回滚方块）",
                "§f  · 资源包下载期间上限自动压到一半",
                "§f  · 本次已拦截: §e" + throttledCount + "§f 个"
            },
            new String[]{
                "§9§l▌ 丢包伪装",
                "§f  · 移动包丢包率 §e" + moveDropRate.get() + "%§f，交互包 §e" + interactDropRate.get() + "%",
                "§f  · 移动包可安全丢弃，服务端靠下一个包续算坐标",
                "§f  · 丢包率超过 20% 容易触发拉回，建议保守",
                "§f  · 本次已丢弃: §e" + droppedCount + "§f 个"
            },
            new String[]{
                "§c§l▌ 联动状态",
                "§f  · 拉回包冷却: " + (rubberBandHandling ? "§c是" : "§a否"),
                "§f  · 资源包下载: " + (TacticalFSM.isDownloadingResourcePack() ? "§c是" : "§a否")
            },
            new String[]{
                "§c§l▌ 注意",
                "§f  · 开启「保护关键包」时交互包不参与丢弃",
                "§f    关掉它会导致挖掘/放置失效，仅在压发包密度时用",
                "§f  · 挖掘上限调到 15 以上基本等于没限"
            }
        );
    }
}
