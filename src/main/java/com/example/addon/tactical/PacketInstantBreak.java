package com.example.addon.tactical;

import com.example.addon.core.SettingUiHelper;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mixin.ClientLevelPredictionAccessor;
import com.example.addon.modules.AutoMinerModule;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;
import meteordevelopment.meteorclient.systems.modules.world.Excavator;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import meteordevelopment.meteorclient.systems.modules.world.PacketMine;
import meteordevelopment.meteorclient.systems.modules.world.VeinMiner;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 发包秒破模块（独立完整实现）
 *
 * <p>用纯发包的方式瞬间破坏方块，不依赖原版挖掘动画。相比自动挖矿自带的「秒破」
 * （走 MultiPlayerGameMode.destroyBlock 客户端预测），本模块默认走「预测同步」
 * 方案——方块状态在服务端确认前不在客户端提前置空，从根本上消除「假方块 / 空气墙」。</p>
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>三种破坏方式：秒破（极速）/ 原版速度（防回滚）/ 自定义倍速</li>
 *   <li>两种目标模式：瞄准破坏（手动）/ 范围自动（Nuker 式）</li>
 *   <li>预测同步：服务端确认前客户端不提前置空方块，从根上消除假方块/空气墙</li>
 *   <li>进度 ESP：带百分比标签，颜色与框线样式可自选</li>
 *   <li>反作弊：混淆破坏进度、补发 ABORT、服务器卡顿自停、拉回冷却自停</li>
 *   <li>冲突联动：检测自动挖矿秒破 / Meteor 发包挖掘类功能，冲突则拒启并播报</li>
 * </ul>
 *
 * @author yiyijia
 */
public class PacketInstantBreak extends YiyiaddonModule {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  设置分组（按人因工程排序：先决定挖什么 → 怎么挖 → 怎么防 → 怎么看）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final SettingGroup sgTarget = settings.createGroup("目标选择");
    private final SettingGroup sgPacket = settings.createGroup("发包参数");
    private final SettingGroup sgAnticheat = settings.createGroup("防同步与反作弊");
    private final SettingGroup sgRender = settings.createGroup("进度显示");

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  目标选择
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("目标模式")
        .description("瞄准破坏：按住左键瞄准目标方块发包挖掘；范围自动：自动扫描周围方块批量发包挖掘")
        .defaultValue(TargetMode.AIM)
        .onChanged(m -> SettingUiHelper.reloadScreen())
        .build());
    { SettingUiHelper.currentValueLine(sgTarget, "当前目标模式", targetMode); }

    private final Setting<BreakMode> breakMode = sgTarget.add(new EnumSetting.Builder<BreakMode>()
        .name("破坏方式")
        .description("秒破：START+STOP 立即发送，服务端不校验挖掘速度时可用；原版速度：按真实工具速度推进，任何服务器都稳；自定义倍速：按倍率加速推进")
        .defaultValue(BreakMode.INSTANT)
        .onChanged(m -> SettingUiHelper.reloadScreen())
        .build());
    { SettingUiHelper.currentValueLine(sgTarget, "当前破坏方式", breakMode); }

    private final Setting<Double> speedMultiplier = sgTarget.add(new DoubleSetting.Builder()
        .name("速度倍率")
        .description("自定义倍速模式下，把真实挖掘速度乘以此倍率（1=原版，5=五倍速）")
        .defaultValue(5.0)
        .min(1.0)
        .max(20.0)
        .noSlider()
        .visible(() -> breakMode.get() == BreakMode.BOOST)
        .build());

    private final Setting<Integer> range = sgTarget.add(new IntSetting.Builder()
        .name("扫描半径")
        .description("范围自动模式下，以玩家为中心扫描的半径（格）")
        .defaultValue(4)
        .min(1)
        .max(6)
        .noSlider()
        .visible(() -> targetMode.get() == TargetMode.RANGE)
        .build());

    private final Setting<List<Block>> targetBlocks = sgTarget.add(new BlockListSetting.Builder()
        .name("目标方块")
        .description("范围自动模式下只挖这些方块；留空则挖所有可破坏方块")
        .defaultValue()
        .build());

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  发包参数
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Integer> delay = sgPacket.add(new IntSetting.Builder()
        .name("挖掘间隔（tick）")
        .description("每开始挖一个新方块前的等待 tick 数。数值越大对服务器压力越小，可有效避免挖太快卡死")
        .defaultValue(1)
        .min(0)
        .max(20)
        .noSlider()
        .build());

    private final Setting<Boolean> rotate = sgPacket.add(new BoolSetting.Builder()
        .name("转向发包")
        .description("挖掘时先发送视角转向包对准方块，服务端视角校验更宽松的服需要")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoSwitch = sgPacket.add(new BoolSetting.Builder()
        .name("自动换工具")
        .description("方块就绪时自动切换到最快工具，挖完自动切回")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> swing = sgPacket.add(new BoolSetting.Builder()
        .name("挥动手臂")
        .description("发包挖掘时同步挥动手臂动画，看起来更像真人在挖")
        .defaultValue(true)
        .build());

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  防同步与反作弊
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> obscureProgress = sgAnticheat.add(new BoolSetting.Builder()
        .name("混淆破坏进度")
        .description("挖掘时额外刷 ABORT 包，掩盖方块破坏进度，避免其他玩家看到挖掘裂纹")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> bypassAnticheat = sgAnticheat.add(new BoolSetting.Builder()
        .name("绕过反作弊")
        .description("仅强反作弊服务器（Grim 等）开启：秒破后额外补发 ABORT 包混淆破坏时序，绕过 fastbreak 检测")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> respectLag = sgAnticheat.add(new BoolSetting.Builder()
        .name("服务器卡顿自停")
        .description("检测到服务器 TPS 过低时暂停发包，避免雪上加霜被踢")
        .defaultValue(true)
        .build());

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  进度显示
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("显示进度")
        .description("是否渲染正在发包挖掘的方块与进度")
        .defaultValue(true)
        .build());

    private final Setting<EspStyle> espStyle = sgRender.add(new EnumSetting.Builder<EspStyle>()
        .name("框线样式")
        .description("进度框的渲染样式：仅线条 / 仅面 / 线+面")
        .defaultValue(EspStyle.BOTH)
        .onChanged(s -> SettingUiHelper.reloadScreen())
        .visible(render::get)
        .build());
    { SettingUiHelper.currentValueLine(sgRender, "当前框线样式", espStyle, render::get); }

    private final Setting<Boolean> shrinkProgress = sgRender.add(new BoolSetting.Builder()
        .name("进度收缩")
        .description("方块框随破坏进度向中心收缩，直观体现挖掘进度（0%满格 → 100%缩到中心）")
        .defaultValue(true)
        .visible(render::get)
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("未完成方块面色")
        .description("正在挖掘中方块的面颜色")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .visible(() -> render.get() && espStyle.get().sides())
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("未完成方块线色")
        .description("正在挖掘中方块的线颜色")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .visible(() -> render.get() && espStyle.get().lines())
        .build());

    private final Setting<SettingColor> readySideColor = sgRender.add(new ColorSetting.Builder()
        .name("可破坏方块面色")
        .description("进度已满、即将破坏方块的面颜色")
        .defaultValue(new SettingColor(0, 204, 0, 10))
        .visible(() -> render.get() && espStyle.get().sides())
        .build());

    private final Setting<SettingColor> readyLineColor = sgRender.add(new ColorSetting.Builder()
        .name("可破坏方块线色")
        .description("进度已满、即将破坏方块的线颜色")
        .defaultValue(new SettingColor(0, 204, 0, 255))
        .visible(() -> render.get() && espStyle.get().lines())
        .build());

    private final Setting<Boolean> showPercent = sgRender.add(new BoolSetting.Builder()
        .name("显示百分比")
        .description("在方块上方显示挖掘进度百分比标签")
        .defaultValue(true)
        .visible(render::get)
        .build());

    private final Setting<SettingColor> progressColor = sgRender.add(new ColorSetting.Builder()
        .name("百分比颜色")
        .description("进度百分比标签的文字颜色")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> render.get() && showPercent.get())
        .build());

    private final Setting<LabelStyle> labelStyle = sgRender.add(new EnumSetting.Builder<LabelStyle>()
        .name("标签内容")
        .description("百分比标签展示的内容：仅百分比 / 百分比+方块名 / 百分比+剩余tick")
        .defaultValue(LabelStyle.PERCENT)
        .onChanged(s -> SettingUiHelper.reloadScreen())
        .visible(() -> render.get() && showPercent.get())
        .build());
    { SettingUiHelper.currentValueLine(sgRender, "当前标签内容", labelStyle, () -> render.get() && showPercent.get()); }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部状态
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 正在发包挖掘的方块队列 */
    private final List<MineBlock> blocks = new ArrayList<>();

    /** 自动换工具后是否需要切回原槽位 */
    private boolean shouldUpdateSlot = false;

    /** 当前 tick 是否已经发过一个 START（每个 tick 只允许新开一个方块，防瞬间洪泛） */
    private boolean startedThisTick = false;

    /** 冲突巡检计数（每 20 tick 查一次，避免高频遍历模块列表） */
    private int conflictCheckTick = 0;

    public PacketInstantBreak() {
        super(CATEGORY_TACTICAL, "发包秒破", "纯发包瞬间破坏方块，防假方块/空气墙，带进度ESP与反作弊联动。点击按钮查看说明。");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  说明面板
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
            new com.example.addon.ui.HelpScreen.HelpSection("破坏方式",
                "§8├─ §e秒破（极速） §8- §7START+STOP 立即发送，秒破任何可破坏方块",
                "§8│   §7依赖服务端不校验挖掘速度，适合普通服务器",
                "§8│",
                "§8├─ §e原版速度 §8- §7按真实工具速度推进，任何服务器都稳",
                "§8│   §7不会产生假方块/空气墙，适合强反作弊服务器",
                "§8│",
                "§8└─ §e自定义倍速 §8- §7按倍率加速，速度与稳定取平衡"
            ),
            new com.example.addon.ui.HelpScreen.HelpSection("同步方式（防假方块）",
                "§8└─ §e预测同步 §8- §7服务端确认前客户端不提前置空方块",
                "§8    §7彻底避免「空气墙」与「假方块」，方块服务端确认后才消失"
            ),
            new com.example.addon.ui.HelpScreen.HelpSection("目标模式",
                "§8├─ §e瞄准破坏 §8- §7按住左键瞄准目标方块发包挖掘",
                "§8└─ §e范围自动 §8- §7自动扫描周围方块批量发包（Nuker式）",
                "§8    §7配合「目标方块」白名单精准挖指定方块"
            ),
            new com.example.addon.ui.HelpScreen.HelpSection("冲突联动",
                "§c[!] §f开启本模块时会自动检测以下冲突：",
                "§8  ├─ §7自动挖矿 · 快速破坏（秒破）",
                "§8  ├─ §7Meteor packet-mine（发包挖掘）",
                "§8  ├─ §7Meteor speed-mine（加速挖掘）",
                "§8  ├─ §7Meteor nuker（自动挖掘）",
                "§8  ├─ §7Meteor excavator（区域挖掘）",
                "§8  ├─ §7Meteor vein-miner（矿脉挖掘）",
                "§8  ├─ §7Meteor infinity-miner（无限挖掘）",
                "§8  └─ §7Meteor instant-rebreak（即时重挖）",
                "§c[!] §f检测到冲突会拒启并提示，关闭冲突功能后才能启动"
            ),
            new com.example.addon.ui.HelpScreen.HelpSection("注意事项",
                "§c⚠ §f秒破模式在强反作弊服务器可能无效，换原版速度",
                "§c⚠ §f挖掘间隔调大能显著缓解「挖太快卡死」",
                "§c⚠ §f单人世界自动禁用，仅在多人服务器生效"
            )
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  启动 / 关闭
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void onActivate() {
        // 单人世界自动关闭
        if (mc.hasSingleplayerServer()) {
            chatFeedback = false;
            toggle();
            chatFeedback = true;
            warning("§c单人世界无需发包秒破");
            return;
        }

        // 冲突检测：自动挖矿秒破 / Meteor 发包挖掘类功能开启时拒启
        List<String> conflicts = collectConflicts();
        if (!conflicts.isEmpty()) {
            chatFeedback = false;
            mc.execute(() -> {
                if (isActive()) toggle();
                chatFeedback = true;
            });
            notifyError("你已开启以下自带功能，请关闭后再打开发包秒破：");
            for (String c : conflicts) {
                notify("§c  §6· §f" + c);
            }
            return;
        }

        // 重置内部状态
        blocks.clear();
        shouldUpdateSlot = false;
        startedThisTick = false;

        reportStartupInfo();
    }

    @Override
    public void onDeactivate() {
        blocks.clear();

        // 自动换工具没切回时补一刀切回
        if (shouldUpdateSlot && mc.player != null) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
            shouldUpdateSlot = false;
        }
    }

    /** 启动报告：合并成一条多行消息块，只带一次模块前缀 */
    private void reportStartupInfo() {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 发包秒破 · 启动报告");
        report.append("\n§7破坏方式　§8▸ ").append(highlightFunction(breakMode.get().displayName)).append("§r");
        report.append("\n§7目标模式　§8▸ ").append(highlightFunction(targetMode.get().displayName)).append("§r");
        if (targetMode.get() == TargetMode.RANGE) {
            report.append("\n§7扫描半径　§8▸ ").append(highlightNumber(range.get() + " 格")).append("§r");
        }
        report.append("\n§7挖掘间隔　§8▸ ").append(highlightNumber(delay.get() + " tick")).append("§r");
        notify(report.toString());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  冲突检测
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 收集当前与发包秒破冲突的功能清单。
     *
     * 冲突来源两类：
     * 1. 本项目自动挖矿的「快速破坏（秒破）」——与发包秒破同走秒破语义，会双重发包；
     * 2. Meteor 自带的发包挖掘类模块（packet-mine / speed-mine / nuker / excavator /
     *    vein-miner / infinity-miner / instant-rebreak）。
     */
    private List<String> collectConflicts() {
        List<String> conflicts = new ArrayList<>();

        AutoMinerModule miner = Modules.get().get(AutoMinerModule.class);
        if (miner != null && miner.isActive() && miner.getFastBreak()) {
            conflicts.add("自动挖矿 · 快速破坏（秒破）");
        }

        PacketMine packetMine = Modules.get().get(PacketMine.class);
        if (packetMine != null && packetMine.isActive()) {
            conflicts.add("Meteor packet-mine（发包挖掘）");
        }

        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        if (speedMine != null && speedMine.isActive()) {
            conflicts.add("Meteor speed-mine（加速挖掘）");
        }

        Nuker nuker = Modules.get().get(Nuker.class);
        if (nuker != null && nuker.isActive()) {
            conflicts.add("Meteor nuker（自动挖掘）");
        }

        Excavator excavator = Modules.get().get(Excavator.class);
        if (excavator != null && excavator.isActive()) {
            conflicts.add("Meteor excavator（区域挖掘）");
        }

        VeinMiner veinMiner = Modules.get().get(VeinMiner.class);
        if (veinMiner != null && veinMiner.isActive()) {
            conflicts.add("Meteor vein-miner（矿脉挖掘）");
        }

        InfinityMiner infinityMiner = Modules.get().get(InfinityMiner.class);
        if (infinityMiner != null && infinityMiner.isActive()) {
            conflicts.add("Meteor infinity-miner（无限挖掘）");
        }

        InstantRebreak instantRebreak = Modules.get().get(InstantRebreak.class);
        if (instantRebreak != null && instantRebreak.isActive()) {
            conflicts.add("Meteor instant-rebreak（即时重挖）");
        }

        return conflicts;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  监听反作弊检测（联动服务器检测模块）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onAntiCheatDetected(TacticalFSM.AntiCheatDetectedEvent event) {
        if (!isActive()) return;

        // 检测到 Matrix/Grim 时，秒破模式降级为原版速度，规避 fastbreak 检测
        if (event.antiCheatName.contains("Grim") || event.antiCheatName.contains("Matrix")) {
            if (breakMode.get() == BreakMode.INSTANT) {
                breakMode.set(BreakMode.VANILLA);
                notify("检测到 " + event.antiCheatName + "，已自动切换到原版速度");
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  瞄准破坏：拦截开始破坏事件
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!isActive() || targetMode.get() != TargetMode.AIM) return;
        if (!BlockUtils.canBreak(event.blockPos)) return;

        // 取消原版破坏，改为发包挖掘
        event.cancel();
        if (!isMiningBlock(event.blockPos)) {
            blocks.add(new MineBlock().set(event.blockPos, event.direction));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  主循环
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null) return;

        // 冲突巡检：运行中检测到冲突功能开启，自动停机
        if (++conflictCheckTick >= 20) {
            conflictCheckTick = 0;
            if (!collectConflicts().isEmpty()) {
                chatFeedback = false;
                mc.execute(() -> {
                    if (isActive()) toggle();
                    chatFeedback = true;
                });
                notifyError("你已开启冲突的自带功能，已自动停止发包秒破");
                return;
            }
        }

        // 服务器卡顿 / 拉回冷却时暂停发包，避免顶风作案
        if (respectLag.get() && (TacticalFSM.isServerLagging() || TacticalFSM.isRubberBandCooldown())) {
            return;
        }

        startedThisTick = false;

        // 范围自动模式：扫描周围目标方块
        if (targetMode.get() == TargetMode.RANGE) {
            scanRange();
        }

        // 处理所有方块：推进破坏进度、发送 START/STOP
        blocks.removeIf(MineBlock::shouldRemove);

        // 自动换工具：切回原槽位
        if (shouldUpdateSlot) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
            shouldUpdateSlot = false;
        }

        for (MineBlock block : blocks) {
            block.mine();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  渲染：3D 框线 + 2D 百分比标签
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isActive() || !render.get()) return;

        for (MineBlock block : blocks) {
            block.render(event);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isActive() || !render.get() || !showPercent.get()) return;
        if (mc.player == null || mc.gameRenderer == null) return;

        for (MineBlock block : blocks) {
            block.renderLabel(event);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  工具方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private boolean isMiningBlock(BlockPos pos) {
        for (MineBlock block : blocks) {
            if (block.pos.equals(pos)) return true;
        }
        return false;
    }

    /** 范围自动模式：扫描以玩家为中心的立方体，匹配目标方块则加入挖掘队列 */
    private void scanRange() {
        if (blocks.size() >= 16) return;

        List<Block> targets = targetBlocks.get();
        int r = range.get();
        BlockPos center = mc.player.blockPosition();

        for (int dx = -r; dx <= r && blocks.size() < 16; dx++) {
            for (int dy = -r; dy <= r && blocks.size() < 16; dy++) {
                for (int dz = -r; dz <= r && blocks.size() < 16; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (isMiningBlock(pos)) continue;

                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir() || !BlockUtils.canBreak(pos, state)) continue;

                    // 白名单非空时只挖白名单内方块
                    if (!targets.isEmpty() && !targets.contains(state.getBlock())) continue;

                    blocks.add(new MineBlock().set(pos, BlockUtils.getDirection(pos)));
                }
            }
        }
    }

    /** 计算该方块当前的发包挖掘进度（0~1，超出则视为就绪） */
    private double computeProgress(MineBlock block) {
        if (!block.mining) return 0;
        if (breakMode.get() == BreakMode.INSTANT) return 1; // 秒破：立即就绪

        FindItemResult fir = InvUtils.findFastestTool(block.state);
        int slot = fir.found() ? fir.slot() : mc.player.getInventory().getSelectedSlot();
        double delta = BlockUtils.getBreakDelta(slot, block.state);
        double mult = breakMode.get() == BreakMode.BOOST ? speedMultiplier.get() : 1.0;
        return delta * mult * (mc.player.tickCount - block.startTick + 1);
    }

    /** 发送 START_DESTROY_BLOCK：经预测处理器取号并登记服务端已知状态（不预测破坏，防空气墙） */
    private void sendStartPacket(MineBlock block) {
        BlockStatePredictionHandler handler =
            ((ClientLevelPredictionAccessor) (Object) mc.level).yiyiaddon$getPredictionHandler();
        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(block.pos, mc.level.getBlockState(block.pos), mc.player);
            int sequence = predicting.currentSequence();
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, block.pos, block.dir, sequence));
        }
    }

    /** 发送 STOP_DESTROY_BLOCK：无需 sequence；客户端不置空，方块等服务端确认后消失（防假方块） */
    private void sendStopPacket(MineBlock block) {
        mc.player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, block.pos, block.dir));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  挖掘方块数据
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 单个发包挖掘中的方块 */
    private class MineBlock {
        BlockPos pos;
        BlockState state;
        Block block;
        Direction dir;

        int timer;       // 发送 START 前的等待 tick
        int startTick;   // START 发送时的玩家 tick
        boolean mining;  // 是否已发送 START
        boolean stopped; // 是否已发送 STOP

        MineBlock set(BlockPos pos, Direction dir) {
            this.pos = pos;
            this.dir = dir != null ? dir : Direction.UP;
            this.state = mc.level.getBlockState(pos);
            this.block = state.getBlock();
            this.timer = delay.get();
            this.mining = false;
            this.stopped = false;
            return this;
        }

        /** 是否应移除：方块已变化 / 超时未确认 / 超出作用距离 */
        boolean shouldRemove() {
            boolean broken = mc.level.getBlockState(pos).getBlock() != block;
            boolean timeout = mining && (mc.player.tickCount - startTick > 40);
            boolean distance = Utils.distance(
                mc.player.getEyePosition().x, mc.player.getEyePosition().y, mc.player.getEyePosition().z,
                pos.getX() + dir.getStepX(), pos.getY() + dir.getStepY(), pos.getZ() + dir.getStepZ()
            ) > Math.max(mc.player.blockInteractionRange(), range.get());
            return broken || timeout || distance;
        }

        /** 推进挖掘：发 START，就绪后发 STOP */
        void mine() {
            if (timer > 0) {
                timer--;
                return;
            }

            // 每个 tick 只新开一个方块，避免瞬间洪泛打崩服务器
            if (!mining && startedThisTick) return;

            if (!mining) {
                sendStart();
                mining = true;
                startedThisTick = true;
                startTick = mc.player.tickCount;
            }

            if (mining && !stopped && computeProgress(this) >= 1) {
                sendStop();
                stopped = true;
                if (obscureProgress.get()) {
                    mc.player.connection.send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, dir));
                }
            }
        }

        private void sendStart() {
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, () -> sendStartPacket(this));
            } else {
                sendStartPacket(this);
            }
        }

        private void sendStop() {
            // 自动换工具：就绪后切到最快工具再发 STOP
            if (autoSwitch.get()) {
                FindItemResult fir = InvUtils.findFastestTool(state);
                if (fir.found() && mc.player.getInventory().getSelectedSlot() != fir.slot()) {
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(fir.slot()));
                    shouldUpdateSlot = true;
                }
            }

            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, () -> sendStopPacket(this));
            } else {
                sendStopPacket(this);
            }

            // 绕过反作弊：额外补发 ABORT 混淆破坏时序
            if (bypassAnticheat.get()) {
                mc.player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos.above(), dir));
            }

            if (swing.get()) {
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }

        /** 渲染 3D 框线 */
        void render(Render3DEvent event) {
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
            if (shape.isEmpty()) return;

            double x1 = pos.getX() + shape.min(Direction.Axis.X);
            double y1 = pos.getY() + shape.min(Direction.Axis.Y);
            double z1 = pos.getZ() + shape.min(Direction.Axis.Z);
            double x2 = pos.getX() + shape.max(Direction.Axis.X);
            double y2 = pos.getY() + shape.max(Direction.Axis.Y);
            double z2 = pos.getZ() + shape.max(Direction.Axis.Z);

            boolean ready = computeProgress(this) >= 1;

            // 进度收缩：方块框随破坏进度向中心收缩，直观体现挖掘进度
            if (shrinkProgress.get() && mining && !ready) {
                double progress = Math.min(1, computeProgress(this));
                double remain = 1.0 - progress; // 剩余比例（1=满格，0=缩到中心）
                double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, cz = (z1 + z2) / 2;
                x1 = cx - (cx - x1) * remain;
                x2 = cx + (x2 - cx) * remain;
                y1 = cy - (cy - y1) * remain;
                y2 = cy + (y2 - cy) * remain;
                z1 = cz - (cz - z1) * remain;
                z2 = cz + (z2 - cz) * remain;
            }

            Color side = ready ? readySideColor.get() : sideColor.get();
            Color line = ready ? readyLineColor.get() : lineColor.get();
            event.renderer.box(x1, y1, z1, x2, y2, z2, side, line, espStyle.get().shapeMode, 0);
        }

        /** 渲染 2D 百分比标签 */
        void renderLabel(Render2DEvent event) {
            double progress = Math.min(1, computeProgress(this));
            int percent = (int) (progress * 100);

            StringBuilder label = new StringBuilder();
            label.append(percent).append('%');
            if (labelStyle.get() == LabelStyle.BLOCK) {
                label.append(' ').append(block.getName().getString());
            } else if (labelStyle.get() == LabelStyle.TICK) {
                int remain = (int) Math.ceil(Math.max(0, 1 - progress) / Math.max(0.0001, BlockUtils.getBreakDelta(
                    mc.player.getInventory().getSelectedSlot(), state)));
                label.append(" §7(剩 ").append(remain).append("t)");
            }

            Vector3d pos3d = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(pos3d, 1.0)) return;

            NametagUtils.begin(pos3d);
            TextRenderer.get().begin(1.0, false, true);
            double w = TextRenderer.get().getWidth(label.toString());
            TextRenderer.get().render(label.toString(), -w / 2, 0, progressColor.get(), true);
            TextRenderer.get().end();
            NametagUtils.end();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  枚举定义
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 破坏方式 */
    public enum BreakMode {
        INSTANT("秒破（极速）"),
        VANILLA("原版速度"),
        BOOST("自定义倍速");

        public final String displayName;

        BreakMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** 目标模式 */
    public enum TargetMode {
        AIM("瞄准破坏"),
        RANGE("范围自动");

        public final String displayName;

        TargetMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** 框线样式（映射到 Meteor ShapeMode） */
    public enum EspStyle {
        LINES("仅线条", ShapeMode.Lines),
        SIDES("仅面", ShapeMode.Sides),
        BOTH("线+面", ShapeMode.Both);

        public final String displayName;
        public final ShapeMode shapeMode;

        EspStyle(String displayName, ShapeMode shapeMode) {
            this.displayName = displayName;
            this.shapeMode = shapeMode;
        }

        public boolean lines() {
            return this == LINES || this == BOTH;
        }

        public boolean sides() {
            return this == SIDES || this == BOTH;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** 百分比标签内容 */
    public enum LabelStyle {
        PERCENT("仅百分比"),
        BLOCK("百分比+方块名"),
        TICK("百分比+剩余tick");

        public final String displayName;

        LabelStyle(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
