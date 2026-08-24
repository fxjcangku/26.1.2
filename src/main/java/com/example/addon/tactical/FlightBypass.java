package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mixin.ClientLevelPredictionAccessor;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
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
 * 4种飞行模式：
 * 1. 原版模拟 - 高频跳跃伪装
 * 2. 安全滑翔 - 微下降规避重力检测
 * 3. 烟花火箭 - 模拟鞘翅加速（发送烟花使用包）
 * 4. 序列垫脚 - 预测方块放置（80-120ms随机延迟 + 每5次留一次）
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
        .defaultValue(FlightMode.VANILLA_MIMIC)
        .onChanged(m -> {
            if (TacticalFSM.hasAdvancedAntiCheat() && (m == FlightMode.VANILLA_MIMIC || m == FlightMode.FIREWORK_BOOST)) {
                notify("检测到高级反作弊，建议切换到安全滑翔或序列垫脚");
            }
        })
        .build()
    );

    // 参数调整
    private final Setting<Double> vanillaJumpInterval = sgTweaks.add(new DoubleSetting.Builder()
        .name("跳跃间隔（tick）")
        .description("原版模拟模式：每N个tick发送一次onGround=true")
        .defaultValue(3.0)
        .min(1.0)
        .max(10.0)
        .sliderMax(10.0)
        .visible(() -> mode.get() == FlightMode.VANILLA_MIMIC)
        .build()
    );

    private final Setting<Double> glideSpeed = sgTweaks.add(new DoubleSetting.Builder()
        .name("下降速度")
        .description("安全滑翔模式：每tick下降的Y轴距离")
        .defaultValue(0.03)
        .min(0.01)
        .max(0.1)
        .sliderMax(0.1)
        .visible(() -> mode.get() == FlightMode.SAFE_GLIDE)
        .build()
    );

    private final Setting<Integer> scaffoldDelay = sgTweaks.add(new IntSetting.Builder()
        .name("垫脚延迟（ms）")
        .description("序列垫脚模式：放置后延迟N毫秒再破坏")
        .defaultValue(100)
        .min(80)
        .max(200)
        .sliderMax(200)
        .visible(() -> mode.get() == FlightMode.SEQUENCE_SCAFFOLD)
        .build()
    );

    // 内部状态
    private int tickCounter = 0;
    private int scaffoldCounter = 0;
    private final Random random = new Random();

    // 垫脚延迟拆除登记（主线程 tick 驱动，避免子线程碰预测处理器）
    private BlockPos pendingDestroyPos = null;
    private long pendingDestroyAt = 0L;

    public FlightBypass() {
        super(CATEGORY_TACTICAL, "飞行绕过", "四种模式绕过 GrimAC/Matrix 顶级反作弊。");
    }

    @Override
    public void onDeactivate() {
        // 关闭时先把欠的方块拆掉，否则脚下会留下痕迹
        if (pendingDestroyPos != null) {
            destroyScaffoldBlock(pendingDestroyPos);
            pendingDestroyPos = null;
        }
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        scaffoldCounter = 0;
        pendingDestroyPos = null;

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

        // 收到拉回包时发布事件（AntiKickBypass 会监听并处理）
        if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
            TacticalFSM.publishRubberBand(packet);
            notify("§c收到拉回包，已触发防踢断流");
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
     * 模式 1: 原版模拟 - 高频跳跃伪装
     * 每N个tick发送一次onGround=true，伪装成"高频跳跃"
     */
    private void handleVanillaMimic() {
        int interval = (int) vanillaJumpInterval.get().doubleValue();
        if (tickCounter % interval == 0) {
            // 发送带onGround=true的移动包
            Vec3 pos = mc.player.position();
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                pos.x, pos.y, pos.z,
                mc.player.getYRot(), mc.player.getXRot(),
                true,  // onGround = true
                false  // horizontalCollision = false
            ));
        }
    }

    /**
     * 模式 2: 安全滑翔 - 微下降规避重力检测
     * 每tick让Y轴下降0.03，规避"连续上升"检测
     */
    private void handleSafeGlide() {
        double speed = glideSpeed.get();
        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motion.x, -speed, motion.z);
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

        // 必须处于滑翔状态，否则烟花不会产生推进，只是白白消耗
        if (!mc.player.isFallFlying()) return;

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
        if (!mc.level.getBlockState(belowPos).isAir()) return;

        // 放置目标格的下方那一格作为命中面，向上放置
        BlockPos supportPos = belowPos.below();
        if (mc.level.getBlockState(supportPos).isAir()) return;

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

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l飞行绕过 · 功能说明" },
            new String[]{
                "§e§l▌ 当前模式",
                "§f  " + mode.get().displayName
            },
            new String[]{
                "§a§l▌ 模式说明",
                "§f  1. §e原版模拟§r - 高频跳跃伪装（适合低级反作弊）",
                "§f  2. §e安全滑翔§r - 微下降规避重力检测（推荐）",
                "§f  3. §e烟花火箭§r - 模拟鞘翅加速（需要副手有烟花）",
                "§f  4. §e序列垫脚§r - 预测方块放置（最安全但慢）"
            },
            new String[]{
                "§b§l▌ 智能联动",
                "§f  · 检测到 Matrix/GrimAC 时自动切换安全模式",
                "§f  · 收到拉回包时触发防踢模块断流",
                "§f  · 拉回包冷却期间自动暂停飞行"
            },
            new String[]{
                "§c§l▌ 注意事项",
                "§f  · 序列垫脚模式需要主手持有方块",
                "§f  · 烟花模式需要副手持有烟花火箭",
                "§f  · 在高级反作弊服务器建议使用安全滑翔"
            }
        );
    }

    /** 飞行模式枚举 */
    public enum FlightMode {
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
