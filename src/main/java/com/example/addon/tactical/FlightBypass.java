package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mixin.ClientLevelPredictionAccessor;
import com.example.addon.mixin.LocalPlayerAccessor;
import com.example.addon.mixin.ServerboundMovePlayerPacketAccessor;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 飞行绕过模块（完整实现）
 * 
 * 5种飞行模式：
 * 1. 发包飞行 - 真正发包级飞行，本地 velocity + 浮空检测绕过 + onGround 伪造
 * 2. 原版模拟 - 高频跳跃伪装
 * 3. 安全滑翔 - 微下降规避重力检测
 * 4. 烟花火箭 - 模拟鞘翅加速（发送烟花使用包）
 * 5. 序列垫脚 - 预测方块放置（80-120ms随机延迟 + 每5次留一次）
 * 
 * @author yiyijia
 */
public class FlightBypass extends YiyiaddonModule {

    private final SettingGroup sgMode = settings.createGroup("模式选择");
    private final SettingGroup sgTweaks = settings.createGroup("参数调整");

    // 模式选择
    private final Setting<FlightMode> mode = sgMode.add(new EnumSetting.Builder<FlightMode>()
        .name("飞行模式")
        .description("选择绕过策略")
        .defaultValue(FlightMode.PACKET_FLY)
        .onChanged(m -> {
            if (TacticalFSM.hasAdvancedAntiCheat() && (m == FlightMode.VANILLA_MIMIC || m == FlightMode.FIREWORK_BOOST)) {
                notify("检测到高级反作弊，建议切换到发包飞行、安全滑翔或序列垫脚");
            }
        })
        .build()
    );

    // 参数调整
    private final Setting<Double> packetFlySpeed = sgTweaks.add(new DoubleSetting.Builder()
        .name("发包飞行速度")
        .description("发包飞行模式：上升/下降的 Y 轴速度，值越大升得越快")
        .defaultValue(0.3)
        .min(0.1)
        .max(1.0)
        .noSlider()
        .visible(() -> mode.get() == FlightMode.PACKET_FLY)
        .build()
    );

    private final Setting<Integer> antiKickInterval = sgTweaks.add(new IntSetting.Builder()
        .name("浮空重置间隔（tick）")
        .description("发包飞行模式：每隔N个tick把发包Y轴下压0.03130，重置服务端浮空计时（服务端80tick判定）")
        .defaultValue(20)
        .min(5)
        .max(60)
        .noSlider()
        .visible(() -> mode.get() == FlightMode.PACKET_FLY)
        .build()
    );

    private final Setting<Boolean> spoofOnGround = sgTweaks.add(new BoolSetting.Builder()
        .name("伪造落地标志")
        .description("发包飞行模式：把移动包的onGround强制设为true，绕过依赖落地标志的检测")
        .defaultValue(true)
        .visible(() -> mode.get() == FlightMode.PACKET_FLY)
        .build()
    );

    private final Setting<Double> vanillaJumpInterval = sgTweaks.add(new DoubleSetting.Builder()
        .name("跳跃间隔（tick）")
        .description("原版模拟模式：每N个tick发送一次onGround=true")
        .defaultValue(3.0)
        .min(1.0)
        .max(10.0)
        .noSlider()
        .visible(() -> mode.get() == FlightMode.VANILLA_MIMIC)
        .build()
    );

    private final Setting<Double> glideSpeed = sgTweaks.add(new DoubleSetting.Builder()
        .name("滑翔速度")
        .description("安全滑翔模式：每tick的Y轴速度（上升/下降共用此幅度）")
        .defaultValue(0.03)
        .min(0.01)
        .max(0.1)
        .noSlider()
        .visible(() -> mode.get() == FlightMode.SAFE_GLIDE)
        .build()
    );

    private final Setting<Integer> scaffoldDelay = sgTweaks.add(new IntSetting.Builder()
        .name("垫脚延迟（ms）")
        .description("序列垫脚模式：放置后延迟N毫秒再破坏")
        .defaultValue(100)
        .min(80)
        .max(200)
        .noSlider()
        .visible(() -> mode.get() == FlightMode.SEQUENCE_SCAFFOLD)
        .build()
    );

    private final Setting<Boolean> adaptiveSlowdown = sgTweaks.add(new BoolSetting.Builder()
        .name("自适应降速")
        .description("连续被拉回多次后自动降级到更保守的飞行模式，避免持续触发反作弊被封")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rubberBandThreshold = sgTweaks.add(new IntSetting.Builder()
        .name("拉回降级阈值")
        .description("连续被拉回N次后自动降级到安全滑翔")
        .defaultValue(3)
        .min(2)
        .max(10)
        .noSlider()
        .visible(adaptiveSlowdown::get)
        .build()
    );

    // 内部状态
    private int tickCounter = 0;
    private int scaffoldCounter = 0;
    private final Random random = new Random();

    // 发包飞行状态：浮空重置计数 + 上一个发包的 Y（用于浮空检测绕过）
    private int packetFlyTick = 0;
    private double lastPacketY = Double.MAX_VALUE;

    // 自适应降速状态：连续拉回计数 + 最近一次拉回时间（用于自动降级与恢复）
    private int consecutiveRubberBands = 0;
    private long lastRubberBandTime = 0L;

    // 拉回包播报节流：拉回是高频事件，频繁提示会刷屏，5 秒只报一次
    private long lastRubberBandNotice = 0L;

    // 垫脚延迟拆除登记（主线程 tick 驱动，避免子线程碰预测处理器）
    private BlockPos pendingDestroyPos = null;
    private long pendingDestroyAt = 0L;

    public FlightBypass() {
        super(CATEGORY_TACTICAL, "飞行绕过", "五种飞行模式绕过GrimAC/Matrix/Vulcan高级反作弊。点击按钮查看说明。");
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
            new com.example.addon.ui.HelpScreen.HelpSection("飞行模式",
                "§8├─ §e发包飞行 §8- §7真正发包级飞行（推荐）",
                "§8│   §7本地velocity + 浮空检测绕过 + onGround伪造",
                "§8│   §7唯一能骗过服务端重力校验的模式",
                "§8│",
                "§8├─ §e原版模拟 §8- §7高频跳跃伪装",
                "§8│   §7适用于低级反作弊，检测宽松的服务器",
                "§8│",
                "§8├─ §e安全滑翔 §8- §7微下降规避重力检测",
                "§8│   §7适用于高级反作弊，如GrimAC/Matrix",
                "§8│",
                "§8├─ §e烟花火箭 §8- §7模拟鞘翅加速",
                "§8│   §7发送烟花使用包，需要装备鞘翅",
                "§8│",
                "§8└─ §e序列垫脚 §8- §7预测方块放置",
                "§8    §780-120ms随机延迟，每5次留一次痕迹"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("参数调整",
                "§6▸ §f飞行速度 §8- §e0.3 §7(发包飞行模式)",
                "§6▸ §f浮空重置间隔 §8- §e20 tick §7(发包飞行模式)",
                "§6▸ §f伪造落地标志 §8- §e开 §7(发包飞行模式)",
                "§6▸ §f跳跃间隔 §8- §e3 tick §7(原版模拟模式)",
                "§6▸ §f滑翔速度 §8- §e0.03 §7(安全滑翔模式)",
                "§6▸ §f放置延迟 §8- §e80-120ms §7(序列垫脚模式)"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("自动适配",
                "§a[1] §f加入服务器时自动检测反作弊类型",
                "§a[2] §f检测到GrimAC/Matrix时自动切换安全模式",
                "§a[3] §f被拉回时自动断流联动（配合发包防踢）"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("注意事项",
                "§c⚠ §f发包飞行对Vulcan/Matrix等高强度反作弊仍有风险，自行评估",
                "§c⚠ §f原版模拟和烟花火箭对高级反作弊无效",
                "§c⚠ §f安全滑翔会持续下降，需要间歇性上升补偿",
                "§c⚠ §f序列垫脚需要背包里有方块（圆石/泥土等）",
                "§c⚠ §f被拉回时会自动触发断流联动（需开启发包防踢）"
            )
        );
    }

    @Override
    public void onDeactivate() {
        // 关闭时先把欠的方块拆掉，否则脚下会留下痕迹
        if (pendingDestroyPos != null) {
            destroyScaffoldBlock(pendingDestroyPos);
            pendingDestroyPos = null;
        }
        // 恢复位置重发间隔：发包飞行会把 positionReminder 压低强制高频发包，
        // 关闭后必须复位，否则残留会导致持续高频发包被服务端判异常
        if (mc.player != null) {
            ((LocalPlayerAccessor) (Object) mc.player).yiyiaddon$setPositionReminder(0);
        }
    }

    @Override
    public void onActivate() {
        // 单人世界自动关闭
        if (mc.hasSingleplayerServer()) {
            chatFeedback = false; // 禁用开关消息
            toggle(); // 关闭模块
            chatFeedback = true; // 恢复开关消息
            warning("§c单人世界无需飞行绕过");
            return;
        }
        
        tickCounter = 0;
        scaffoldCounter = 0;
        pendingDestroyPos = null;
        packetFlyTick = 0;
        lastPacketY = Double.MAX_VALUE;
        consecutiveRubberBands = 0;
        lastRubberBandTime = 0L;

        // 检测到高级反作弊时自动切换安全模式
        if (TacticalFSM.hasAdvancedAntiCheat()) {
            if (mode.get() == FlightMode.VANILLA_MIMIC || mode.get() == FlightMode.FIREWORK_BOOST) {
                mode.set(FlightMode.SAFE_GLIDE);
                notify("检测到高级反作弊，已自动切换到安全滑翔");
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  监听反作弊检测事件（自动切换安全模式）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onAntiCheatDetected(TacticalFSM.AntiCheatDetectedEvent event) {
        if (!isActive()) return;

        // 检测到 Matrix/GrimAC 时强制切换到安全模式
        if (event.antiCheatName.contains("Grim") || event.antiCheatName.contains("Matrix")) {
            if (mode.get() == FlightMode.VANILLA_MIMIC || mode.get() == FlightMode.FIREWORK_BOOST) {
                mode.set(FlightMode.SAFE_GLIDE);
                notify("检测到 " + event.antiCheatName + "，已强制切换到安全滑翔");
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  监听拉回包（触发断流联动）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!isActive()) return;

        // 收到拉回包时发布事件（AntiKickBypass 会监听并处理），播报节流防刷屏
        if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
            TacticalFSM.publishRubberBand(packet);
            long now = System.currentTimeMillis();

            // 自适应降速：统计连续拉回次数，达到阈值自动降级到更保守的模式。
            // 反作弊拉回=它已经判定你移动非法，此时继续顶风只会累积 flag 量，
            // 主动降级能及时止损；一段时间无拉回则重置计数，避免一次误拉回就永久降级
            if (adaptiveSlowdown.get()) {
                // 超过 10 秒无拉回视为脱离危险，重置连续计数
                if (now - lastRubberBandTime > 10_000) {
                    consecutiveRubberBands = 0;
                }
                lastRubberBandTime = now;
                consecutiveRubberBands++;

                if (consecutiveRubberBands >= rubberBandThreshold.get()
                    && mode.get() == FlightMode.PACKET_FLY) {
                    consecutiveRubberBands = 0;
                    mode.set(FlightMode.SAFE_GLIDE);
                    notify("§e⚠ 连续被拉回 " + rubberBandThreshold.get() + " 次 §8▸ §f已自动降级到安全滑翔");
                }
            }

            if (now - lastRubberBandNotice >= 5000) {
                lastRubberBandNotice = now;
                notify("§e⚠ 收到拉回包 §8▸ §f已联动防踢断流");
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  飞行核心逻辑
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        // 垫脚拆除到点就执行，冷却检查之前处理，避免方块残留
        if (pendingDestroyPos != null && System.currentTimeMillis() >= pendingDestroyAt) {
            destroyScaffoldBlock(pendingDestroyPos);
            pendingDestroyPos = null;
        }

        // 拉回包冷却期间暂停飞行
        if (TacticalFSM.isRubberBandCooldown()) {
            return;
        }

        tickCounter++;

        switch (mode.get()) {
            case PACKET_FLY:
                handlePacketFly();
                break;
            case VANILLA_MIMIC:
                handleVanillaMimic();
                break;
            case SAFE_GLIDE:
                handleSafeGlide();
                break;
            case FIREWORK_BOOST:
                handleFireworkBoost();
                break;
            case SEQUENCE_SCAFFOLD:
                handleSequenceScaffold();
                break;
        }
    }

    /**
     * 模式 0: 发包飞行 - 真正发包级飞行
     *
     * 这是唯一能骗过服务端权威重力校验的飞行方式，原理分两层：
     * 1. 本地层：用 velocity 让客户端玩家真的飞起来（渲染同步）；
     * 2. 发包层：拦截即将发出的移动包，周期性把 Y 坐标下压 0.03130 + 伪造 onGround，
     *    绕过服务端 ServerGamePacketListener#handleMovePlayer 的浮空检测。
     *
     * 浮空检测：服务端连续 80 tick（约 4 秒）发现玩家 Y 不变或上升就判定非法飞行，
     * 所以必须在 80 tick 内至少让服务端看到一次「下降 >= 0.03125」的合法落地轨迹。
     */
    private void handlePacketFly() {
        // 本地垂直速度控制：跳跃上升、潜行下降、无输入悬停
        Vec3 vel = mc.player.getDeltaMovement();
        double vy = 0;
        if (mc.options.keyJump.isDown()) {
            vy = packetFlySpeed.get();
        } else if (mc.options.keyShift.isDown()) {
            vy = -packetFlySpeed.get();
        }
        mc.player.setDeltaMovement(vel.x, vy, vel.z);

        // 压低位置重发间隔，强制客户端每 tick 重发移动包，
        // 保证即使水平静止（只上下飞）也能持续触发发包伪造
        ((LocalPlayerAccessor) (Object) mc.player).yiyiaddon$setPositionReminder(1);
    }

    /**
     * 发包飞行：拦截即将发送的移动包，做浮空检测绕过 + onGround 伪造。
     *
     * 这是「超过 Meteor」的关键——Meteor 的 Flight 只在 anti-kick 模式下改 Y，
     * 我这里额外叠加 onGround 伪造 + 与 TacticalFSM 拉回断流联动。
     */
    @EventHandler
    private void onSendMovePacket(PacketEvent.Send event) {
        if (!isActive() || mode.get() != FlightMode.PACKET_FLY) return;
        if (!(event.packet instanceof ServerboundMovePlayerPacket packet)) return;

        // 只处理带位置的包（Pos / PosRot），Rot / StatusOnly 不含 Y 直接跳过
        double currentY = packet.getY(Double.MAX_VALUE);
        if (currentY == Double.MAX_VALUE) return;

        ServerboundMovePlayerPacketAccessor accessor = (ServerboundMovePlayerPacketAccessor) packet;

        // onGround 伪造：让服务端认为玩家落地，绕过依赖落地标志的检测
        if (spoofOnGround.get()) {
            accessor.yiyiaddon$setOnGround(true);
        }

        // 浮空检测绕过：每隔 N tick 把发包 Y 下压 0.03130（大于服务端 0.03125 阈值），
        // 制造「正在下降」的合法轨迹，重置服务端浮空计时
        packetFlyTick++;
        if (packetFlyTick >= antiKickInterval.get() && lastPacketY != Double.MAX_VALUE) {
            packetFlyTick = 0;
            accessor.yiyiaddon$setY(lastPacketY - 0.03130);
        } else {
            lastPacketY = currentY;
        }
    }

    /**
     * 模式 1: 原版模拟 - 高频跳跃伪装
     * 每N个tick发送一次onGround=true，伪装成"高频跳跃"
     */
    private void handleVanillaMimic() {
        // 关键修复：实际控制Y轴速度让玩家飞起来
        if (mc.options.keyJump.isDown()) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x, 0.5, motion.z);
        } else if (mc.options.keyShift.isDown()) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x, -0.5, motion.z);
        } else {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(motion.x, 0, motion.z);
        }
        
        // 高频伪造 onGround 骗过反作弊
        int interval = (int) vanillaJumpInterval.get().doubleValue();
        if (tickCounter % interval == 0) {
            Vec3 pos = mc.player.position();
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                pos.x, pos.y, pos.z,
                mc.player.getYRot(), mc.player.getXRot(),
                true,
                false
            ));
        }
    }

    /**
     * 模式 2: 安全滑翔 - 微速巡航规避重力/飞行检测
     *
     * 真实起飞逻辑：按住跳跃键以「滑翔速度」上升、潜行键快速下降、
     * 无输入时保持微下降伪装。全部用一个小的 Y 速度驱动，
     * 避免原版模拟那种一瞬 +0.5 的大速度被运动预测类反作弊一眼看穿。
     */
    private void handleSafeGlide() {
        double speed = glideSpeed.get();
        Vec3 motion = mc.player.getDeltaMovement();

        double vy;
        if (mc.options.keyJump.isDown()) {
            vy = speed;                       // 上升：缓慢爬升，伪装缓降曲线
        } else if (mc.options.keyShift.isDown()) {
            vy = -speed * 3.0;                // 下降：快速脱离危险高度
        } else {
            vy = -speed;                      // 无输入：维持微下降外观
        }

        mc.player.setDeltaMovement(motion.x, vy, motion.z);
    }

    /**
     * 模式 3: 烟花火箭 - 模拟鞘翅加速
     * 每10个tick发送一次"使用烟花"包，服务器会认为是合法的鞘翅推进
     */
    private void handleFireworkBoost() {
        if (tickCounter % 10 != 0) return;

        // 判定哪只手真的握着烟花，服务端会校验手上物品，伪造无效
        InteractionHand hand;
        if (mc.player.getOffhandItem().getItem() == Items.FIREWORK_ROCKET) {
            hand = InteractionHand.OFF_HAND;
        } else if (mc.player.getMainHandItem().getItem() == Items.FIREWORK_ROCKET) {
            hand = InteractionHand.MAIN_HAND;
        } else {
            return;
        }

        // 必须处于滑翔状态且鞘翅有耐久，否则服务端会拒绝
        if (!mc.player.isFallFlying() || mc.player.onGround()) return;
        
        ItemStack elytra = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (elytra.isEmpty() || elytra.getDamageValue() >= elytra.getMaxDamage()) return;

        ClientLevel level = mc.level;
        if (level == null) return;

        // 物品使用包同样携带预测序号，需经预测处理器取号
        BlockStatePredictionHandler handler =
            ((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler();

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            mc.player.connection.send(new ServerboundUseItemPacket(
                hand,
                predicting.currentSequence(),
                mc.player.getYRot(),
                mc.player.getXRot()
            ));
        }
    }

    /**
     * 模式 4: 序列垫脚 - 预测方块放置
     *
     * 在玩家脚下放置方块创造"合法实地"刷新掉落判定，随后拆除。
     * 拆除延迟 80-120ms 并周期性保留方块，避免"放置后瞬间破坏"的行为特征。
     */
    private void handleSequenceScaffold() {
        if (tickCounter % 5 != 0) return;

        // 主手必须是方块物品，否则放置包会被服务端直接丢弃
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem)) return;

        BlockPos belowPos = mc.player.blockPosition().below();
        BlockState belowState = mc.level.getBlockState(belowPos);
        
        // 检查目标位置：必须是空气且无流体，且碰撞箱为空
        if (!belowState.isAir() || !belowState.getFluidState().isEmpty()) return;
        if (!belowState.getCollisionShape(mc.level, belowPos).isEmpty()) return;

        // 放置目标格的下方那一格作为支撑面，向上放置到脚下。
        // 支撑面必须是实心方块（非空气且碰撞箱非空），
        // 否则放置包没有依附面，服务端会直接丢弃（此前判定写反导致本模式永远放不出方块）
        BlockPos supportPos = belowPos.below();
        BlockState supportState = mc.level.getBlockState(supportPos);
        if (supportState.isAir() || supportState.getCollisionShape(mc.level, supportPos).isEmpty()) return;

        BlockHitResult hitResult = new BlockHitResult(
            new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5),
            Direction.UP,
            supportPos,
            false
        );

        if (!sendPredictedPlace(belowPos, hitResult)) return;

        scaffoldCounter++;

        // 每 5 次保留一次方块不拆，模拟手动操作的失误
        if (scaffoldCounter % 5 == 0) return;

        // 登记延迟拆除，由主线程 tick 驱动（预测处理器不是线程安全的，不能丢子线程）
        pendingDestroyPos = belowPos;
        pendingDestroyAt = System.currentTimeMillis() + scaffoldDelay.get() + random.nextInt(40);
    }

    /**
     * 发送带合法 sequence 的方块放置包。
     *
     * 26.1.2 服务端会校验每个方块交互包的预测序号，必须经
     * BlockStatePredictionHandler 取号并登记原状态，序号错乱会被回滚。
     */
    private boolean sendPredictedPlace(BlockPos target, BlockHitResult hitResult) {
        ClientLevel level = mc.level;
        if (level == null) return false;

        BlockState original = level.getBlockState(target);
        BlockStatePredictionHandler handler =
            ((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler();

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(target, original, mc.player);
            int sequence = predicting.currentSequence();
            mc.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, sequence));
        }

        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * 拆除垫脚方块，同样需要独立取号。
     * START 与 STOP 各取一次号，不能复用同一个 sequence。
     */
    private void destroyScaffoldBlock(BlockPos pos) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        BlockState original = level.getBlockState(pos);
        if (original.isAir()) return;

        BlockStatePredictionHandler handler =
            ((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler();

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(pos, original, mc.player);
            int sequence = predicting.currentSequence();
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, sequence));
        }

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(pos, level.getBlockState(pos), mc.player);
            int sequence = predicting.currentSequence();
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, sequence));
        }

        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UI 面板
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 飞行模式枚举 */
    public enum FlightMode {
        PACKET_FLY("发包飞行"),
        VANILLA_MIMIC("原版模拟"),
        SAFE_GLIDE("安全滑翔"),
        FIREWORK_BOOST("烟花火箭"),
        SEQUENCE_SCAFFOLD("序列垫脚");

        public final String displayName;

        FlightMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
