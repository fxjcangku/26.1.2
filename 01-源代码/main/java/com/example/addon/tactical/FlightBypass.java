package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mixin.ClientLevelPredictionAccessor;
import com.example.addon.tactical.core.FlightPolicy;
import com.example.addon.tactical.core.TacticalCoordinator;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 飞行绕过模块（L2 移动执行器，2026-09-03 重构）。
 *
 * 职责边界（重构后）：
 * - 只负责「移动执行」：速度注入、真实跳跃、鞘翅起滑、烟花推进、垫脚放置；
 * - 不负责「决策」：每 tick 向 TacticalCoordinator（L1 唯一决策点）请求
 *   FlightDecision，只按决策执行，不允许自行修改模式（审计 P1-1 修复）；
 * - 不处理拉回包（冷却统计归协调器，本模块只读决策结果，审计 P0-5/P2-8 修复）；
 * - 不监听反作弊检测事件来做模式切换（模式降级由协调器降级链统一裁决）。
 *
 * 26.1.2 官方机制依据（重构核心变更）：
 * 旧实现的「发包 Y 下压 0.03125 + onGround 伪造」已删除——26.1.2 服务端
 * ServerGamePacketListenerImpl.handleMovePlayer 的浮空判定只认物理支撑
 * （verticalCollisionBelow / 脚下 0.55 格有方块）+ 合法飞行状态（abilities
 * 飞行权限 / 鞘翅 fallFlying / 悬浮药水 / 旁观），改包无法豁免，伪造无效。
 * 五个模式全部改为基于官方合法机制的移动注入：
 * 1. 发包飞行 —— 仅当服务端真正授予飞行能力（/fly、创造、旁观）时由协调器放行；
 * 2. 原版模拟 —— 落地即真实起跳，地面接触由原版物理重置浮空计时；
 * 3. 安全滑翔 —— 自动装备鞘翅 + 官方起伞命令（START_FALL_FLYING），
 *    fallFlying 豁免浮空判定且服务端速度容忍提升到 300 m/t；
 * 4. 烟花火箭 —— 滑翔中周期性使用烟花，服务端完全合法；
 * 5. 序列垫脚 —— 预测放置真实方块提供物理支撑，延迟拆除。
 *
 * @author yiyijia
 */
public class FlightBypass extends YiyiaddonModule {

    private final SettingGroup sgMode = settings.createGroup("模式选择");
    private final SettingGroup sgTweaks = settings.createGroup("参数调整");

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模式选择
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<FlightPolicy.FlightMode> mode = sgMode.add(new EnumSetting.Builder<FlightPolicy.FlightMode>()
        .name("飞行模式")
        .description("选择期望执行的绕过模式，最终是否执行由战术协调器按检测结果与降级档位裁决")
        .defaultValue(FlightPolicy.FlightMode.PACKET_FLY)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  参数调整
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Integer> glideSpeed = sgTweaks.add(new IntSetting.Builder()
        .name("滑翔速度（0.01格/tick）")
        .description("安全滑翔模式：Y 轴速度档位，1 = 0.01格/tick。整数档位保证加减按钮可用（Meteor 双精度控件步长硬编码 1）")
        .defaultValue(3)
        .min(1)
        .max(30)
        .noSlider()
        .visible(() -> mode.get() == FlightPolicy.FlightMode.SAFE_GLIDE)
        .build()
    );

    private final Setting<Integer> vanillaJumpInterval = sgTweaks.add(new IntSetting.Builder()
        .name("跳跃间隔（tick）")
        .description("原版模拟模式：每 N tick 在落地瞬间触发一次真实起跳，每一跳都由地面接触重置浮空计时")
        .defaultValue(3)
        .min(1)
        .max(10)
        .noSlider()
        .visible(() -> mode.get() == FlightPolicy.FlightMode.VANILLA_MIMIC)
        .build()
    );

    private final Setting<Integer> scaffoldDelay = sgTweaks.add(new IntSetting.Builder()
        .name("垫脚延迟（ms）")
        .description("序列垫脚模式：放置后延迟 N 毫秒再破坏")
        .defaultValue(100)
        .min(80)
        .max(200)
        .noSlider()
        .visible(() -> mode.get() == FlightPolicy.FlightMode.SEQUENCE_SCAFFOLD)
        .build()
    );

    private final Setting<Boolean> adaptiveSlowdown = sgTweaks.add(new BoolSetting.Builder()
        .name("自适应降级")
        .description("连续被拉回后由协调器沿降级链逐档降级，脱离危险窗口后逐档恢复")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rubberBandThreshold = sgTweaks.add(new IntSetting.Builder()
        .name("拉回降级阈值")
        .description("滑动窗口内连续被拉回 N 次触发降级一档")
        .defaultValue(3)
        .min(2)
        .max(10)
        .noSlider()
        .visible(adaptiveSlowdown::get)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部执行状态（全部为私有态，会话重置时统一清零）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** tick 计数：驱动跳跃间隔与烟花周期 */
    private int tickCounter = 0;

    /** 垫脚已放置计数：每 5 次留一块不拆，模拟手动失误 */
    private int scaffoldCounter = 0;

    /** 垫脚延迟拆除登记（主线程 tick 驱动，预测处理器非线程安全） */
    private BlockPos pendingDestroyPos = null;
    private long pendingDestroyAt = 0L;

    /** 空中开伞请求去重：一次离地只发一条 START_FALL_FLYING */
    private boolean glideDeployRequested = false;

    /** 缺鞘翅提示节流（5 秒一次，防每 tick 刷屏） */
    private long lastGlideHintAt = 0L;

    private final Random random = new Random();

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  决策播报去重锁（状态播报规范：状态变化才播，防每 tick 刷屏）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private FlightPolicy.FlightReason lastNotifiedReason = null;
    private FlightPolicy.FlightMode lastNotifiedMode = null;

    /** 经历过拒绝/降级后恢复放行，补一条恢复播报 */
    private boolean wasBlocked = false;

    public FlightBypass() {
        super(CATEGORY_TACTICAL, "飞行绕过", "五种基于 26.1.2 官方机制的飞行模式，由战术协调器统一决策。点击按钮查看说明。");
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
            new com.example.addon.ui.HelpScreen.HelpSection("飞行模式（26.1.2 官方机制依据）",
                "§8├─ §e发包飞行 §8- §7需服务端授予飞行能力（/fly/创造/旁观）",
                "§8│   §7协调器校验 abilities 后放行，未授权自动拒绝",
                "§8│   §7零注入原版飞行：速度由服务端权威计算，无法客户端加速",
                "§8│",
                "§8├─ §e原版模拟 §8- §7落地即真实起跳",
                "§8│   §7地面接触由原版物理重置浮空计时，全服合法",
                "§8│",
                "§8├─ §e安全滑翔 §8- §7自动换鞘翅 + 官方起伞",
                "§8│   §7fallFlying 豁免浮空判定，速度容忍 300 m/t",
                "§8│",
                "§8├─ §e烟花火箭 §8- §7滑翔中周期性使用烟花推进",
                "§8│   §7服务端完全合法，需背包有烟花与鞘翅",
                "§8│",
                "§8└─ §e序列垫脚 §8- §7真实放置方块提供物理支撑",
                "§8    §7延迟拆除并周期性留痕，需主手方块"
            ),

            new com.example.addon.ui.HelpScreen.HelpSection("协调器统一决策",
                "§a[1] §f拉回冷却期 §8- §7全模式统一暂停 2 秒",
                "§a[2] §f连续拉回 §8- §7沿降级链逐档降级",
                "§8    §f发包飞行/烟花火箭 → 安全滑翔 → 原版模拟",
                "§a[3] §f脱离危险窗口 §8- §7每 10 秒恢复一档",
                "§a[4] §f高风险反作弊 §8- §7发包飞行直接拒绝"
            ),

            new com.example.addon.ui.HelpScreen.HelpSection("注意事项",
                "§c⚠ §f发包飞行需要服务器开 /fly 或创造/旁观权限",
                "§c⚠ §f发包飞行水平速度由服务端规则决定，想更快请用烟花火箭模式",
                "§c⚠ §f安全滑翔与烟花火箭需要背包里有鞘翅",
                "§c⚠ §f序列垫脚需要主手持有可放置方块",
                "§c⚠ §f报警恢复全程由协调器裁决，模块不自行切换模式"
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
            warning("§c单人世界无需飞行绕过");
            return;
        }

        resetLocalState();
    }

    @Override
    public void onDeactivate() {
        // 关闭时先把欠的方块拆掉，避免脚下留下痕迹
        if (pendingDestroyPos != null) {
            destroyScaffoldBlock(pendingDestroyPos);
            pendingDestroyPos = null;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  会话重置（进服/离服）：清本模块私有执行状态，
    //  全局状态由协调器自行清零，本模块不碰任何共享字段
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onSessionReset(TacticalCoordinator.SessionResetEvent event) {
        resetLocalState();
    }

    private void resetLocalState() {
        tickCounter = 0;
        scaffoldCounter = 0;
        pendingDestroyPos = null;
        pendingDestroyAt = 0L;
        glideDeployRequested = false;
        lastGlideHintAt = 0L;
        lastNotifiedReason = null;
        lastNotifiedMode = null;
        wasBlocked = false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  主循环：请求决策 → 播报状态变化 → 按决策执行
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null) return;

        tickCounter++;

        // 垫脚拆除到点就执行：必须在决策门槛之前处理，否则方块残留
        if (pendingDestroyPos != null && System.currentTimeMillis() >= pendingDestroyAt) {
            destroyScaffoldBlock(pendingDestroyPos);
            pendingDestroyPos = null;
        }

        // 唯一决策入口：协调器按「冷却 → 降级档权衡 → 准入」顺序产出本 tick 决策
        FlightPolicy.FlightDecision decision = TacticalCoordinator.evaluateFlight(
            mode.get(), adaptiveSlowdown.get(), rubberBandThreshold.get());

        // 状态播报（带去重锁，只在状态变化时输出）
        broadcastDecision(decision);

        // 拒绝/冷却：本 tick 不注入任何移动
        if (!decision.granted()) return;

        switch (decision.mode()) {
            case PACKET_FLY -> handlePacketFly();
            case VANILLA_MIMIC -> handleVanillaMimic();
            case SAFE_GLIDE -> handleSafeGlide();
            case FIREWORK_BOOST -> handleFireworkBoost();
            case SEQUENCE_SCAFFOLD -> handleSequenceScaffold();
        }
    }

    /**
     * 决策状态播报（去重锁：同原因同模式只播一次）。
     *
     * 冷却暂停静默不播（FlightPolicy 约定），拒绝与降级播 ✗/⚠，
     * 恢复放行补一条 §a✓ 恢复播报。
     */
    private void broadcastDecision(FlightPolicy.FlightDecision d) {
        if (d.reason() == lastNotifiedReason && d.mode() == lastNotifiedMode) return;
        lastNotifiedReason = d.reason();
        lastNotifiedMode = d.mode();

        switch (d.reason()) {
            case DEGRADED -> {
                wasBlocked = true;
                notify("§e⚠ 连续拉回触发降级 §8▸ " + highlightFunction(d.mode().displayName));
            }
            case NO_FLY_ABILITY -> {
                wasBlocked = true;
                notify("§c✗ 服务器未授予飞行能力 §8▸ " + highlightFunction("发包飞行") + " 不可执行，请改用"
                    + highlightFunction("原版模拟 / 安全滑翔 / 序列垫脚"));
            }
            case HIGH_RISK_AC -> {
                wasBlocked = true;
                notify("§c✗ 命中高风险反作弊 §8▸ " + highlightFunction("发包飞行") + " 已被协调器拒绝");
            }
            case GRANTED -> {
                if (wasBlocked) {
                    wasBlocked = false;
                    notify("§a✓ 已恢复执行 §8▸ " + highlightFunction(d.mode().displayName));
                }
            }
            case COOLDOWN -> {
                // 拉回冷却暂停：静默，防刷屏
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模式 1：发包飞行 —— 零注入，纯原版飞行（服务端速度权威）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 26.1.2 官方机制（LivingEntity.travelInAir + Player.travel 飞行分支）：
     * 玩家 abilities.flying 时，服务端以移动包的输入轴（xa/za）为唯一依据，
     * 乘服务端 getFlyingSpeed() 属性权威计算水平速度，客户端 setDeltaMovement
     * 的水平分量完全不参与；垂直方向服务端也只对自己内部的 Y 速度做 0.6 衰减。
     * 因此客户端注入任何速度都只改本地预测、与服务端轨迹漂移（触发距离校验），
     * 还无法加速。本模式不注入任何移动：悬停/上升/下降/水平全由原版飞行输入完成，
     * 这就是服务端规则内发包飞行的最高合法速度。
     */
    private void handlePacketFly() {
        // 零注入：原版飞行本身即最快合法形态，速度由服务端规则决定
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模式 2：原版模拟 —— 落地即真实起跳，地面接触重置浮空计时
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void handleVanillaMimic() {
        // 真实跳跃弧线：每一跳都由地面接触发起，服务端看到的完全是原版物理
        if (tickCounter % vanillaJumpInterval.get() != 0) return;
        if (!mc.player.onGround()) return;
        mc.player.jumpFromGround();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模式 3：安全滑翔 —— 自动装备鞘翅 + 官方起伞命令
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void handleSafeGlide() {
        if (!prepareGlide()) return;

        // 档位换算：1 档 = 0.01 格/tick
        double speed = glideSpeed.get() / 100.0;
        double vy;
        if (mc.options.keyJump.isDown()) {
            vy = speed;                       // 上升：缓爬升
        } else if (mc.options.keyShift.isDown()) {
            vy = -speed * 3.0;                // 下降：快速脱离危险高度
        } else {
            vy = -speed * 0.5;                // 无输入：微降，外观贴近自然滑翔
        }

        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motion.x, vy, motion.z);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模式 4：烟花火箭 —— 滑翔中周期性使用烟花推进（服务端完全合法）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void handleFireworkBoost() {
        // 起滑流程与安全滑翔共享（补鞘翅 → 起跳 → 开伞）
        prepareGlide();

        if (tickCounter % 10 != 0) return;
        if (!mc.player.isFallFlying() || mc.player.onGround()) return;

        // 判定哪只手真的握着烟花，服务端会校验手上物品
        InteractionHand hand;
        if (mc.player.getOffhandItem().getItem() == Items.FIREWORK_ROCKET) {
            hand = InteractionHand.OFF_HAND;
        } else if (mc.player.getMainHandItem().getItem() == Items.FIREWORK_ROCKET) {
            hand = InteractionHand.MAIN_HAND;
        } else {
            return;
        }

        // 鞘翅还有耐久才允许推进，否则服务端拒绝
        ItemStack elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (elytra.isEmpty() || elytra.getDamageValue() >= elytra.getMaxDamage()) return;

        ClientLevel level = mc.level;
        if (level == null) return;

        // 物品使用包携带预测序号，需经预测处理器取号
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
     * 共享起滑流程：补鞘翅 → 地面真实起跳 → 空中官方起伞。
     *
     * 起伞走 26.1.2 官方 LocalPlayer 同款命令包
     * （ServerboundPlayerCommandPacket.START_FALL_FLYING），
     * 服务端校验鞘翅与条件后置 fallFlying，豁免浮空判定。
     *
     * @return 是否已进入滑翔状态（fallFlying）
     */
    private boolean prepareGlide() {
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            ensureElytraEquipped();
            return false;
        }

        if (mc.player.onGround()) {
            // 地面：真实起跳离地，下一 tick 空中开伞
            mc.player.jumpFromGround();
            glideDeployRequested = false;
            return false;
        }

        if (!mc.player.isFallFlying()) {
            // 空中：发官方起伞命令（一次离地只发一条）
            if (!glideDeployRequested) {
                mc.player.connection.send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                glideDeployRequested = true;
            }
            return false;
        }

        glideDeployRequested = false;
        return true;
    }

    /**
     * 背包没穿鞘翅时自动补装到胸甲槽（Meteor 槽位点击路径，服务端同步），
     * 包里没有则节流提示。
     */
    private void ensureElytraEquipped() {
        FindItemResult elytra = InvUtils.find(stack -> stack.getItem() == Items.ELYTRA, 0, 35);
        if (!elytra.found()) {
            long now = System.currentTimeMillis();
            if (now - lastGlideHintAt >= 5000) {
                lastGlideHintAt = now;
                notify("§e⚠ 背包没有鞘翅 §8▸ 安全滑翔/烟花火箭需要 " + highlightText("鞘翅"));
            }
            return;
        }
        InvUtils.move().from(elytra.slot()).toArmor(2);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模式 5：序列垫脚 —— 预测放置真实方块提供物理支撑
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
        // 支撑面必须是实心方块，否则放置包没有依附面，服务端会直接丢弃
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

        // 登记延迟拆除，由主线程 tick 驱动（预测处理器不是线程安全的）
        pendingDestroyPos = belowPos;
        pendingDestroyAt = System.currentTimeMillis() + scaffoldDelay.get() + random.nextInt(40);
    }

    /**
     * 发送带合法 sequence 的方块放置包。
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
     * 拆除垫脚方块，START 与 STOP 各取一次号，不能复用同一个 sequence。
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
}