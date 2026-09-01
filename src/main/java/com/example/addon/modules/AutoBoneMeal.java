package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.tactical.TacticalFSM;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 自动骨粉（26.1.2 官方映射）
 * - Nuker 风格：每 Tick 可催熟多个目标
 * - BlockListSetting：带图标的方块选择器，自动过滤非 BonemealableBlock 方块
 * - 准星模式支持"未对准时"行为配置
 */
public class AutoBoneMeal extends YiyiaddonModule {

    // ================================================================
    //  枚举
    // ================================================================

    public enum TriggerMode {
        范围自动扫描,
        准星精准指向
    }

    // ================================================================
    //  设置组
    // ================================================================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("目标方块");
    private final SettingGroup sgBypass  = settings.createGroup("防作弊绕过");
    private final SettingGroup sgEsp     = settings.createGroup("ESP渲染");

    // ---- 一、基础参数 ----

    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>()
        .name("触发模式")
        .description("范围自动扫描：自动搜索周围所有目标；准星精准指向：仅对准星看着的方块生效。")
        .defaultValue(TriggerMode.范围自动扫描)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("作用半径")
        .description("范围扫描的最大半径（格）。")
        .defaultValue(4).min(1).max(8).noSlider()
        .visible(() -> triggerMode.get() == TriggerMode.范围自动扫描)
        .build()
    );

    private final Setting<Boolean> crosshairHint = sgGeneral.add(new BoolSetting.Builder()
        .name("准星提示")
        .description("准星对着不在目标列表的可催熟方块时，在聊天框提示方块名称。")
        .defaultValue(true)
        .visible(() -> triggerMode.get() == TriggerMode.准星精准指向)
        .build()
    );

    // ---- 二、目标方块（按类分组）----

    private final Setting<List<Block>> targetCrops = sgTargets.add(new BlockListSetting.Builder()
        .name("农作物")
        .description("小麦/胡萝卜/马铃薯/甜菜根/瓜茎/火把花/瓶子草/可可豆/甜浆果丛/洞穴藤蔓")
        .defaultValue(
            Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
            Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP,
            Blocks.MELON_STEM, Blocks.PUMPKIN_STEM,
            Blocks.COCOA, Blocks.SWEET_BERRY_BUSH, Blocks.CAVE_VINES
        )
        .filter(block -> block instanceof BonemealableBlock)
        .build()
    );

    private final Setting<List<Block>> targetSaplings = sgTargets.add(new BlockListSetting.Builder()
        .name("树苗")
        .description("橡树/云杉/白桦/丛林/金合欢/深色橡树/樱花/红树胎生苗/苍白橡树")
        .defaultValue(
            Blocks.OAK_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.BIRCH_SAPLING,
            Blocks.JUNGLE_SAPLING, Blocks.ACACIA_SAPLING, Blocks.DARK_OAK_SAPLING,
            Blocks.CHERRY_SAPLING, Blocks.MANGROVE_PROPAGULE, Blocks.PALE_OAK_SAPLING
        )
        .filter(block -> block instanceof BonemealableBlock)
        .build()
    );

    private final Setting<List<Block>> targetFlowers = sgTargets.add(new BlockListSetting.Builder()
        .name("花卉")
        .description("大型双格花（向日葵/丁香/玫瑰丛/牡丹）+ 粉红花瓣/野花/杜鹃花丛")
        .defaultValue(
            Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
            Blocks.PINK_PETALS, Blocks.WILDFLOWERS,
            Blocks.FLOWERING_AZALEA, Blocks.AZALEA
        )
        .filter(block -> block instanceof BonemealableBlock)
        .build()
    );

    private final Setting<List<Block>> targetMushrooms = sgTargets.add(new BlockListSetting.Builder()
        .name("蘑菇 / 菌类")
        .description("棕色蘑菇/红色蘑菇 + 下界绯红菌菇/诡异菌菇")
        .defaultValue(
            Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
            Blocks.CRIMSON_FUNGUS, Blocks.WARPED_FUNGUS
        )
        .filter(block -> block instanceof BonemealableBlock)
        .build()
    );

    private final Setting<List<Block>> targetAquaticNether = sgTargets.add(new BlockListSetting.Builder()
        .name("水下 / 下界")
        .description("海带 + 扭曲藤蔓/垂泪藤蔓 + 苔藓块/发光地衣/小型垂泪叶")
        .defaultValue(
            Blocks.KELP,
            Blocks.TWISTING_VINES, Blocks.WEEPING_VINES,
            Blocks.MOSS_BLOCK, Blocks.GLOW_LICHEN, Blocks.SMALL_DRIPLEAF
        )
        .filter(block -> block instanceof BonemealableBlock)
        .build()
    );

    // ---- 三、防作弊绕过 ----

    private final Setting<Integer> tickDelay = sgBypass.add(new IntSetting.Builder()
        .name("动作节流（Tick）")
        .description("每隔多少 Tick 执行一轮催熟，0=每帧最暴力，建议 0~2。")
        .defaultValue(0).min(0).noSlider()
        .build()
    );

    private final Setting<Integer> maxPerTick = sgBypass.add(new IntSetting.Builder()
        .name("每轮最大催熟数")
        .description("每轮（节流周期）最多同时催熟多少个方块。0=不限制（最暴力）。")
        .defaultValue(0).min(0).noSlider()
        .build()
    );

    private final Setting<Boolean> rotateSilent = sgBypass.add(new BoolSetting.Builder()
        .name("视角静默同步")
        .description("发包瞬间附加 LookAndOnGround 包，令服务端认为准星正对目标，防止判定隔墙点击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> offhandFirst = sgBypass.add(new BoolSetting.Builder()
        .name("副手优先")
        .description("优先检测副手是否持有骨粉，再检测主手。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swingHand = sgBypass.add(new BoolSetting.Builder()
        .name("摆动手臂")
        .description("催熟成功后播放挥手动画（关闭可减少服务端行为特征）。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> respectLag = sgBypass.add(new BoolSetting.Builder()
        .name("服务器卡顿自停")
        .description("检测到服务器 TPS 过低或被拉回时暂停催熟发包，避免雪上加霜被踢。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoThrottle = sgBypass.add(new BoolSetting.Builder()
        .name("反作弊自动降速")
        .description("检测到 Grim/Matrix 等高强度反作弊时自动提高动作节流，规避右键连点检测。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> checkOcclusion = sgGeneral.add(new BoolSetting.Builder()
        .name("遮挡射线检测")
        .description("开启后跳过视线被方块遮挡的目标（更严格，防服务端隔墙判定）；关闭后大范围也能催熟，适合密集农场。")
        .defaultValue(true)
        .visible(() -> triggerMode.get() == TriggerMode.范围自动扫描)
        .build()
    );

    private final Setting<Boolean> noTargetHint = sgGeneral.add(new BoolSetting.Builder()
        .name("无目标提示")
        .description("范围扫描/准星模式下，附近/准星处没有可催熟目标时聊天栏提示一次。")
        .defaultValue(false)
        .build()
    );

    // ---- 四、ESP渲染 ----

    private final Setting<Boolean> espEnabled = sgEsp.add(new BoolSetting.Builder()
        .name("启用ESP")
        .description("对范围内所有候选目标方块绘制半透明边界框。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgEsp.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("渲染方式：填充+轮廓 / 仅轮廓 / 仅填充。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgEsp.add(new ColorSetting.Builder()
        .name("线框颜色")
        .defaultValue(new SettingColor(0, 255, 100, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgEsp.add(new ColorSetting.Builder()
        .name("填充颜色")
        .defaultValue(new SettingColor(0, 255, 100, 45))
        .build()
    );

    // ================================================================
    //  内部状态
    // ================================================================

    private int tickCounter = 0;
    /** 当前帧扫描到的所有候选目标，供 ESP 渲染和催熟使用 */
    private final List<BlockPos> candidates = new ArrayList<>();
    /** 待发送队列：范围扫描收集到的目标按节流分帧发出 */
    private final Deque<BlockPos> sendQueue = new ArrayDeque<>();
    /** 上次提示的方块，防止每帧刷屏 */
    private Block lastHintedBlock = null;
    /** 是否因骨粉耗尽而自动暂停 */
    private boolean pausedNoBoneMeal = false;
    /** 上一帧是否有候选目标（用于无目标提示防刷屏） */
    private boolean hadTargetsLastTick = true;

    public AutoBoneMeal() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动骨粉",
            "自动催熟农作物，补骨粉，ESP高亮，视角静默同步。详细参考下面使用说明。");
    }

    // ── getWidget：配置页说明面板 ──────────────────────────────────────────────
    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l自动骨粉 · 使用说明" },
            new String[]{
                "§e§l▌ 准备",
                "§f  1. 主手/副手携带骨粉，或背包备足",
                "§f  2. 在「目标方块」中勾选需要催熟的作物",
                "§f  3. 选择触发模式后开启模块"
            },
            new String[]{
                "§a§l▌ 触发模式",
                "§f  范围扫描 — 自动搜索周围可催熟方块，每Tick同时催熟多个",
                "§f  准星指向 — 仅对准星正对的方块生效，准星离开则暂停"
            },
            new String[]{
                "§b§l▌ 防作弊",
                "§f  节流发包：每隔 N Tick 发一次，防频率检测",
                "§f  视角同步：发包附加视角包，防隔墙判定"
            },
            new String[]{
                "§d§l▌ 背包补给",
                "§f  快捷栏耗尽 → 自动从背包补入；背包也空 → 提示并暂停"
            },
            new String[]{
                "§c§l▌ 准星提示",
                "§f  准星模式下，对着未登记的可催熟方块时聊天栏提示一次"
            }
        );
    }

    @Override
    public void onActivate() {
        // 世界就绪判断：selfCheck 会访问 mc.level，必须先确认已进入世界
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            notifyError("必须在进入世界后才能启动模块。");
            mc.execute(this::toggle);
            return;
        }

        // 开机自检：缺项一次列全，配好一项下次就少一条
        if (!reportSelfCheck(selfCheck())) return;

        tickCounter        = 0;
        candidates.clear();
        sendQueue.clear();
        lastHintedBlock    = null;
        pausedNoBoneMeal   = false;
        hadTargetsLastTick = true;

        reportStartupInfo();
    }

    /**
     * 开机自检，收集全部缺项。
     *
     * 收集全部而不是遇到第一个就返回，这样用户一次就能看到还差什么，
     * 配好一项下次启动就少一条，不用反复开关模块试错。
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();

        // 目标方块检测：五个分类至少勾选一种可催熟方块
        if (targetCrops.get().isEmpty()
            && targetSaplings.get().isEmpty()
            && targetFlowers.get().isEmpty()
            && targetMushrooms.get().isEmpty()
            && targetAquaticNether.get().isEmpty()) {
            missing.add("§a目标方块§f·未勾选任何可催熟方块");
        }

        return missing;
    }

    /**
     * 启动播报：合并为一条多行消息块，只带一次模块前缀。
     *
     * 只报会影响本次结果的关键项（触发模式、目标方块、作用半径、节流），
     * 正文统一「标签 §8▸ 值」，与自动农场/自动挖矿的启动报告风格一致。
     */
    private void reportStartupInfo() {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 自动骨粉 · 启动报告");

        // 触发模式
        report.append("\n§7触发模式　§8▸ ")
            .append(highlightFunction(triggerMode.get() == TriggerMode.范围自动扫描 ? "范围自动扫描" : "准星精准指向"))
            .append("§r");

        // 目标方块总数
        int count = targetCrops.get().size() + targetSaplings.get().size()
            + targetFlowers.get().size() + targetMushrooms.get().size()
            + targetAquaticNether.get().size();
        report.append("\n§7目标方块　§8▸ ").append(highlightNumber(count + " 种")).append("§r");

        // 作用半径（仅范围模式）
        if (triggerMode.get() == TriggerMode.范围自动扫描) {
            report.append("\n§7作用半径　§8▸ ").append(highlightNumber(range.get() + " 格")).append("§r");
        }

        // 动作节流
        report.append("\n§7动作节流　§8▸ ").append(highlightNumber(tickDelay.get() + " Tick")).append("§r");

        // 视角静默同步（开=绿、关=红）
        report.append("\n§7视角同步　§8▸ ")
            .append(rotateSilent.get() ? "§a§l已开启" : "§c§l已关闭")
            .append("§r");

        notify(report.toString());
    }

    @Override
    public void onDeactivate() {
        candidates.clear();
        pausedNoBoneMeal = false;
    }

    // ================================================================
    //  Tick 主逻辑
    // ================================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null
                || mc.getConnection() == null) return;

        // 服务器卡顿 / 拉回冷却时暂停催熟，避免顶风作案被踢
        if (respectLag.get() && (TacticalFSM.isServerLagging() || TacticalFSM.isRubberBandCooldown())) {
            return;
        }

        candidates.clear();

        // 节流
        if (++tickCounter <= tickDelay.get()) return;
        tickCounter = 0;

        // ① 先收集候选目标（每轮节流周期重新扫描一次）
        collectTargets();

        // ② 范围模式：把新目标补入发送队列（按距离排序），去重已在队列中的
        if (triggerMode.get() == TriggerMode.范围自动扫描 && !candidates.isEmpty()) {
            candidates.sort(Comparator.comparingDouble(
                pos -> mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
            ));
            for (BlockPos pos : candidates) {
                if (!sendQueue.contains(pos)) sendQueue.addLast(pos);
            }
        }

        // ③ 无目标且队列也空时提示
        boolean hasWork = !candidates.isEmpty() || !sendQueue.isEmpty();
        if (!hasWork) {
            if (noTargetHint.get() && hadTargetsLastTick) {
                String hint = (triggerMode.get() == TriggerMode.准星精准指向)
                    ? "§7准星处没有可催熟的目标"
                    : "§7附近没有可催熟的目标";
                notify(hint);
            }
            hadTargetsLastTick = false;
            return;
        }
        hadTargetsLastTick = true;

        // ④ 有目标后才找/换骨粉槽位，避免无目标时锁快捷栏
        InteractionHand hand = findBoneMealHand();
        if (hand == null) {
            if (!pausedNoBoneMeal) {
                pausedNoBoneMeal = true;
                notify("§c✗ 骨粉耗尽 §8▸ 已自动暂停");
            }
            return;
        }
        if (pausedNoBoneMeal) {
            pausedNoBoneMeal = false;
            notify("§a✓ 检测到骨粉 §8▸ 恢复工作");
        }

        // ⑤ 26.1.2 无 PacketThrottle 联动，直接按 maxPerTick 设置限制本轮催熟数
        //    maxPerTick=0 表示不限制
        int cap = maxPerTick.get();

        if (triggerMode.get() == TriggerMode.范围自动扫描) {
            int sent = 0;
            while (!sendQueue.isEmpty() && (cap == 0 || sent < cap)) {
                BlockPos target = sendQueue.pollFirst();
                if (rotateSilent.get()) sendSilentRotation(Vec3.atCenterOf(target));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
                InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
                if (result.consumesAction() && swingHand.get()) mc.player.swing(hand);
                sent++;
            }
        } else {
            // 准星模式：只催熟一个
            if (!candidates.isEmpty()) {
                BlockPos target = candidates.get(0);
                if (rotateSilent.get()) sendSilentRotation(Vec3.atCenterOf(target));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
                InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
                if (result.consumesAction() && swingHand.get()) mc.player.swing(hand);
            }
        }
    }

    // ================================================================
    //  候选目标收集
    // ================================================================

    private void collectTargets() {
        switch (triggerMode.get()) {
            case 准星精准指向 -> {
                BlockPos crosshair = getCrosshairTarget();
                if (crosshair != null) candidates.add(crosshair);
                // 准星没对准 → candidates 为空，直接不动作
            }
            case 范围自动扫描 -> scanRange();
        }
    }

    /** 准星命中的方块，若不在目标列表或已成熟则返回 null */
    private BlockPos getCrosshairTarget() {
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return null;
        if (bhr.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = bhr.getBlockPos();
        var state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        // 准星提示：对着可催熟但不在目标列表的方块时，聊天框提示一次
        if (crosshairHint.get()
                && !isTargeted(block)
                && block instanceof BonemealableBlock
                && block != lastHintedBlock) {
            lastHintedBlock = block;
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath()
                .replace("_", " ");
            notify("§e准星对着 §f" + name + " §e不在目标列表，可前往设置添加");
        }

        if (!isTargeted(block)) return null;
        if (!isFertilizable(pos, state)) return null;

        // 离开后重置，下次换别的方块还能再提示
        lastHintedBlock = null;
        return pos;
    }

    /** 范围扫描：收集半径内所有合法目标 */
    private void scanRange() {
        int r = range.get();
        double rangeSq = range.get() * range.get();
        BlockPos center = mc.player.blockPosition();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    Vec3 posCenter = Vec3.atCenterOf(pos);
                    if (mc.player.getEyePosition().distanceToSqr(posCenter) > rangeSq) continue;
                    var state = mc.level.getBlockState(pos);
                    if (!isTargeted(state.getBlock())) continue;
                    if (!isFertilizable(pos, state)) continue;
                    if (checkOcclusion.get() && isOccluded(posCenter)) continue;
                    candidates.add(pos);
                }
            }
        }
    }

    // ================================================================
    //  合法性校验
    // ================================================================

    /**
     * 调用 MC 原生 BonemealableBlock#isValidBonemealTarget：
     * 内部自动判断 Age 是否已满（成熟则返回 false），不浪费骨粉。
     */
    private boolean isTargeted(Block block) {
        return targetCrops.get().contains(block)
            || targetSaplings.get().contains(block)
            || targetFlowers.get().contains(block)
            || targetMushrooms.get().contains(block)
            || targetAquaticNether.get().contains(block);
    }

    private boolean isFertilizable(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BonemealableBlock fert)) return false;
        return fert.isValidBonemealTarget(mc.level, pos, state);
    }

    /** 视线遮挡射线检测：眼睛 → 目标中心，中途撞到实体方块则跳过 */
    private boolean isOccluded(Vec3 targetCenter) {
        Vec3 eye = mc.player.getEyePosition();
        HitResult hit = mc.level.clip(new ClipContext(
            eye, targetCenter,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        ));
        if (hit.getType() == HitResult.Type.MISS) return false;
        if (hit instanceof BlockHitResult bhr) {
            return eye.distanceToSqr(bhr.getLocation()) < eye.distanceToSqr(targetCenter) - 0.1;
        }
        return false;
    }

    // ================================================================
    //  防作弊辅助
    // ================================================================

    /**
     * 查找骨粉持有手。
     * 快捷栏没有时，自动从背包找一格骨粉换到当前选中格。
     * 背包也没有时返回 null，触发自动暂停。
     */
    private InteractionHand findBoneMealHand() {
        // 副手优先
        if (offhandFirst.get() && mc.player.getOffhandItem().is(Items.BONE_MEAL)) return InteractionHand.OFF_HAND;
        // 快捷栏（含主手）
        FindItemResult hotbar = InvUtils.findInHotbar(Items.BONE_MEAL);
        if (hotbar.found()) {
            InvUtils.swap(hotbar.slot(), false);
            return InteractionHand.MAIN_HAND;
        }
        // 背包（slot 0~35 全搜，排除快捷栏已搜过的 0~8）
        FindItemResult inv = InvUtils.find(Items.BONE_MEAL);
        if (inv.found() && inv.slot() > 8) {
            // 用 move() 把背包格换到当前快捷栏选中格
            InvUtils.move().from(inv.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    /**
     * 静默视角同步：发 Rot 包附带目标 yaw/pitch，
     * 客户端视角不变，服务端认为玩家正看着目标，防隔墙交互检测。
     */
    private void sendSilentRotation(Vec3 target) {
        Vec3 eye  = mc.player.getEyePosition();
        Vec3 diff = target.subtract(eye);
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horizDist));
        float yaw   = (float)  Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
        mc.getConnection().send(
            new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision)
        );
    }

    /**
     * 反作弊检测联动：检测到 Grim/Matrix 时自动抬高动作节流，
     * 规避高频右键连点被服务端判定为自动化交互而踢出。
     */
    @EventHandler
    private void onAntiCheatDetected(TacticalFSM.AntiCheatDetectedEvent event) {
        if (!isActive() || !autoThrottle.get()) return;
        if (!event.antiCheatName.contains("Grim") && !event.antiCheatName.contains("Matrix")) return;

        if (tickDelay.get() < 3) {
            tickDelay.set(3);
            notify("检测到 " + event.antiCheatName + "，已自动提高动作节流到 3 Tick");
        }
    }

    // ================================================================
    //  3D ESP 渲染（渲染全部候选目标）
    // ================================================================

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!espEnabled.get() || candidates.isEmpty()) return;
        for (BlockPos p : candidates) {
            event.renderer.box(p, fillColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    // ================================================================
    //  HUD 状态栏
    // ================================================================

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;
        boolean hasBoneMeal = mc.player.getOffhandItem().is(Items.BONE_MEAL)
                           || mc.player.getMainHandItem().is(Items.BONE_MEAL);
        if (!hasBoneMeal) return "§c无骨粉";
        if (!candidates.isEmpty()) return "§a" + candidates.size() + " 块";
        return triggerMode.get() == TriggerMode.准星精准指向 ? "§7等待准星" : "§7扫描中";
    }
}
