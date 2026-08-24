package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.farm.*;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动物流农场 - 商业级全自动农业系统
 * 
 * 核心能力：
 * · 爆破收割 - 无视角转动发包收割，支持 10 种作物
 * · 智能补种 - 副手种子自动播种，双作物/单作物/柱状物/蔓生物分别处理
 * · 物流自动化 - 卸货箱自动倒产物，补货箱自动取种子，种子安全库存截留
 * · Baritone 导航 - 自动往返农田与物流箱，失败降级原地干活
 * · 分帧扫描 - 512格/tick 预算，200x200 农场不掉帧
 * · 看门狗 - 每状态独立超时，卡死自动回待机
 * · 异常自愈 - 种子库空降级"只收不种"继续跑
 * · 时运防爆锁 - 剩余耐久低于阈值自动停止
 * · 防呆渲染 - 农田雷达/边界外框/水源辐射/容器字牌
 */
public final class AutoFarmMatrix extends YiyiaddonModule {

    private final FarmScanner scanner = new FarmScanner();
    private final ContainerBroker broker = new ContainerBroker();

    // ═══════════════════════════════════════════════════════════════════
    //  UI 配置面板（分组折叠）
    // ═══════════════════════════════════════════════════════════════════

    private final SettingGroup sgCrops = settings.createGroup("作物选择", false);
    private final SettingGroup sgLogistics = settings.createGroup("后勤设置", false);
    private final SettingGroup sgSafety = settings.createGroup("安全设置", false);
    private final SettingGroup sgRender = settings.createGroup("显示设置", false);

    // ─── 作物分类选择器 ───
    private final Setting<List<Block>> cropsDouble;
    private final Setting<List<Block>> cropsSingle;
    private final Setting<List<Block>> cropsPillar;
    private final Setting<List<Block>> cropsVine;

    // ─── 后勤配置 ───
    private final Setting<Integer> unloadThreshold;
    private final Setting<Integer> seedSafetyStock;
    private final Setting<Integer> bpt;
    private final Setting<Integer> reachDistance;
    private final Setting<Boolean> serpentinePatrol;

    // ─── 安全保护 ───
    private final Setting<Boolean> fortuneLock;
    private final Setting<Integer> fortuneLockThreshold;
    private final Setting<Boolean> antiTrample;

    // ─── 渲染辅助 ───
    private final Setting<Boolean> renderRadar;
    private final Setting<SettingColor> radarRipeColor;
    private final Setting<SettingColor> radarEmptyColor;
    private final Setting<ShapeMode> radarShapeMode;

    private final Setting<Boolean> renderBounds;
    private final Setting<SettingColor> boundsColor;
    private final Setting<ShapeMode> boundsShapeMode;

    private final Setting<Boolean> renderWaterRange;
    private final Setting<SettingColor> waterRangeColor;
    private final Setting<ShapeMode> waterRangeShapeMode;
    private final Setting<Integer> waterMaxSources;

    private final Setting<Boolean> renderLabels;

    // ─── 四锚点持久化（StringSetting 存序列化串） ───
    private final Setting<String> siteStart;
    private final Setting<String> siteEnd;
    private final Setting<String> siteDump;
    private final Setting<String> siteSupply;

    // ═══════════════════════════════════════════════════════════════════
    //  FSM 状态机
    // ═══════════════════════════════════════════════════════════════════

    private FarmState state = FarmState.STANDBY;
    private int stateTick;
    private int watchdogStrikes;
    private static final int MAX_WATCHDOG_STRIKES = 3;

    private String lastNotifiedState = "";

    /** 本轮作业的收割目标快照，进入 NUKE_FARMING 时从扫描器拷一份 */
    private List<BlockPos> workHarvest = List.of();
    /** 本轮作业的补种底盘快照 */
    private List<BlockPos> workPlant = List.of();

    /** 本轮已处理过的坐标，避免同一格反复发包 */
    private final Set<BlockPos> processedHarvest = new HashSet<>();
    private final Set<BlockPos> processedPlant = new HashSet<>();

    /**
     * 异常自愈降级标志：种子库空了就置位，只收不种继续跑。
     * 补货箱重新有种子后自动清零。
     */
    private boolean harvestOnly;

    /** 拾取等待的 tick 数，给服务端判定掉落物留出时间 */
    private static final int COLLECT_WAIT_TICKS = 40;

    /** 上次发送开容器包的 stateTick，用于限制重试频率 */
    private int lastOpenAttempt = -100;
    /** 开容器重试间隔 */
    private static final int OPEN_RETRY_INTERVAL = 10;

    /** 蛇形巡逻航点，按逐行折返顺序排列 */
    private List<BlockPos> patrolRoute = List.of();
    /** 当前正在前往的航点下标 */
    private int patrolIndex;

    /** 上次提示"等待成熟"时的扫描轮数，避免每轮都刷 */
    private int lastIdleNotifySweep;

    // ═══════════════════════════════════════════════════════════════════
    //  构造函数与设置初始化
    // ═══════════════════════════════════════════════════════════════════

    public AutoFarmMatrix() {
        super(AddonTemplate.CATEGORY, "自动物流农场",
            "爆破收割补种，Baritone 导航，箱子物流自动化，分帧扫描不掉帧。详细参考下面使用说明。");

        // ─── 作物分类选择器 ───
        cropsDouble = sgCrops.add(new BlockListSetting.Builder()
            .name("双作物")
            .description("种子与产物分离：小麦、甜菜")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.TWIN;
            })
            .build());

        cropsSingle = sgCrops.add(new BlockListSetting.Builder()
            .name("单作物")
            .description("产物即种子：土豆、胡萝卜、地狱疣")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.SINGLE;
            })
            .build());

        cropsPillar = sgCrops.add(new BlockListSetting.Builder()
            .name("柱状物")
            .description("Y+1 切割：竹子、甘蔗、仙人掌")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.PILLAR;
            })
            .build());

        cropsVine = sgCrops.add(new BlockListSetting.Builder()
            .name("蔓生物")
            .description("只砍果实：南瓜、西瓜")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.VINE;
            })
            .build());

        // ─── 后勤配置 ───
        unloadThreshold = sgLogistics.add(new IntSetting.Builder()
            .name("卸货阈值")
            .description("背包白名单物品满多少组时触发卸货")
            .defaultValue(20)
            .min(1)
            .sliderMax(36)
            .build());

        seedSafetyStock = sgLogistics.add(new IntSetting.Builder()
            .name("种子安全库存")
            .description("卸货时截留多少组种子留作补种")
            .defaultValue(3)
            .min(0)
            .sliderMax(10)
            .build());

        bpt = sgLogistics.add(new IntSetting.Builder()
            .name("发包速率(BPT)")
            .description("每 tick 最多发送多少个破坏/播种包，过高可能被服务端拦截")
            .defaultValue(10)
            .min(1)
            .sliderMax(30)
            .build());

        reachDistance = sgLogistics.add(new IntSetting.Builder()
            .name("收割距离")
            .description("能操作多远的方块。原版上限约 4.5 格，调高属于超距，服务端可能拒绝或判违规")
            .defaultValue(4)
            .min(1)
            .sliderRange(1, 8)
            .build());

        serpentinePatrol = sgLogistics.add(new BoolSetting.Builder()
            .name("蛇形巡逻")
            .description("靠 Baritone 沿农田逐行折返走位，把整片田都覆盖到。关闭则只收站着够得到的")
            .defaultValue(true)
            .build());

        // ─── 安全保护 ───
        fortuneLock = sgSafety.add(new BoolSetting.Builder()
            .name("时运防爆锁")
            .description("主手工具剩余耐久低于阈值时停止破坏，防止工具爆掉")
            .defaultValue(true)
            .build());

        fortuneLockThreshold = sgSafety.add(new IntSetting.Builder()
            .name("时运锁阈值")
            .description("剩余耐久低于此值时触发")
            .defaultValue(10)
            .min(1)
            .sliderMax(100)
            .visible(() -> fortuneLock.get())
            .build());

        antiTrample = sgSafety.add(new BoolSetting.Builder()
            .name("防踩踏")
            .description("农田范围内拦截跳跃键，避免踩坏耕地（不保证 100% 有效）")
            .defaultValue(true)
            .build());

        // ─── 渲染辅助 ───
        renderRadar = sgRender.add(new BoolSetting.Builder()
            .name("农田雷达")
            .description("成熟作物绿框，待补种空地黄框")
            .defaultValue(true)
            .build());

        radarRipeColor = sgRender.add(new ColorSetting.Builder()
            .name("成熟框颜色")
            .description("可收割作物的边框颜色")
            .defaultValue(new SettingColor(0, 255, 0, 75))
            .visible(() -> renderRadar.get())
            .build());

        radarEmptyColor = sgRender.add(new ColorSetting.Builder()
            .name("空地框颜色")
            .description("待补种底盘的边框颜色")
            .defaultValue(new SettingColor(255, 255, 0, 75))
            .visible(() -> renderRadar.get())
            .build());

        radarShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("雷达形状")
            .description("框的渲染模式")
            .defaultValue(ShapeMode.Both)
            .visible(() -> renderRadar.get())
            .build());

        renderBounds = sgRender.add(new BoolSetting.Builder()
            .name("边界外框")
            .description("农场范围的大外接盒")
            .defaultValue(true)
            .build());

        boundsColor = sgRender.add(new ColorSetting.Builder()
            .name("边界框颜色")
            .description("农场边界的颜色")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> renderBounds.get())
            .build());

        boundsShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("边界形状")
            .description("边界框的渲染模式")
            .defaultValue(ShapeMode.Lines)
            .visible(() -> renderBounds.get())
            .build());

        renderWaterRange = sgRender.add(new BoolSetting.Builder()
            .name("水源辐射范围")
            .description("显示一桶水能滋润多少格耕地（9x9x2 层）")
            .defaultValue(false)
            .build());

        waterRangeColor = sgRender.add(new ColorSetting.Builder()
            .name("水源框颜色")
            .description("水源辐射范围的颜色")
            .defaultValue(new SettingColor(0, 150, 255, 40))
            .visible(() -> renderWaterRange.get())
            .build());

        waterRangeShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("水源形状")
            .description("水源辐射框的渲染模式")
            .defaultValue(ShapeMode.Sides)
            .visible(() -> renderWaterRange.get())
            .build());

        waterMaxSources = sgRender.add(new IntSetting.Builder()
            .name("水源渲染上限")
            .description("最多渲染多少个水源，防止大农场渲染卡顿")
            .defaultValue(50)
            .min(1)
            .sliderMax(200)
            .visible(() -> renderWaterRange.get())
            .build());

        renderLabels = sgRender.add(new BoolSetting.Builder()
            .name("容器字牌")
            .description("卸货箱与补货箱头顶显示防呆标签")
            .defaultValue(true)
            .build());

        // ─── 四锚点（隐藏设置，由指令管理） ───
        siteStart = settings.getDefaultGroup().add(new StringSetting.Builder()
            .name("_anchor_start")
            .description("内部使用：农田起点")
            .defaultValue(FarmSite.UNBOUND)
            .visible(() -> false)
            .build());

        siteEnd = settings.getDefaultGroup().add(new StringSetting.Builder()
            .name("_anchor_end")
            .description("内部使用：农田终点")
            .defaultValue(FarmSite.UNBOUND)
            .visible(() -> false)
            .build());

        siteDump = settings.getDefaultGroup().add(new StringSetting.Builder()
            .name("_anchor_dump")
            .description("内部使用：卸货总仓")
            .defaultValue(FarmSite.UNBOUND)
            .visible(() -> false)
            .build());

        siteSupply = settings.getDefaultGroup().add(new StringSetting.Builder()
            .name("_anchor_supply")
            .description("内部使用：种子补货箱")
            .defaultValue(FarmSite.UNBOUND)
            .visible(() -> false)
            .build());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  模块生命周期
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        // 不加单人世界守卫：本模块走的是标准 C2S 交互包，
        // 单人世界的内置服务端一样能正常处理，方便在本地开创造测试

        // 开机四重自检
        String error = selfCheck();
        if (error != null) {
            notifyError("自检失败：" + error);
            if (isActive()) toggle();
            return;
        }

        // 重置状态机
        state = FarmState.STANDBY;
        stateTick = 0;
        watchdogStrikes = 0;
        lastNotifiedState = "";

        // 应用作物勾选
        scanner.setEnabledCrops(getEnabledCrops());
        FarmSite start = FarmSite.parse(siteStart.get());
        FarmSite end = FarmSite.parse(siteEnd.get());
        if (start != null && end != null) {
            scanner.setBounds(start.pos(), end.pos());
        }
        rebuildPatrolRoute();

        if (serpentinePatrol.get() && !FarmNav.available()) {
            notifyError("Baritone 不可用，蛇形巡逻已跳过，只收站着够得到的");
        }

        notify("§a已启动，当前状态：" + state.cn());
    }

    @Override
    public void onDeactivate() {
        FarmNav.cancel();
        scanner.reset();
        broker.reset();
        ContainerBroker.closeContainer();
        patrolRoute = List.of();
        patrolIndex = 0;
        lastIdleNotifySweep = 0;
    }

    /**
     * 开机四重自检，任一失败返回错误原因
     */
    private String selfCheck() {
        // 1. 至少勾选一种作物
        if (getEnabledCrops().isEmpty()) {
            return "未勾选任何作物，请在「作物图鉴」里至少勾选一种";
        }

        // 2. 四锚点全部绑定
        FarmSite start = site(SiteType.START);
        FarmSite end = site(SiteType.END);
        FarmSite dump = site(SiteType.DUMP);
        FarmSite supply = site(SiteType.SUPPLY);

        if (start == null) return "起点未绑定";
        if (end == null) return "终点未绑定";
        if (dump == null) return "卸货箱未绑定";
        if (supply == null) return "补货箱未绑定";

        // 3. 四锚点都在当前维度
        if (!start.inCurrentDimension()) return "起点不在当前维度";
        if (!end.inCurrentDimension()) return "终点不在当前维度";
        if (!dump.inCurrentDimension()) return "卸货箱不在当前维度";
        if (!supply.inCurrentDimension()) return "补货箱不在当前维度";

        // 4. 农田范围有效（体积 > 0）
        scanner.setBounds(start.pos(), end.pos());
        if (scanner.volume() == 0) {
            return "农田范围无效（起点终点重合）";
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  锚点管理（供指令调用）
    // ═══════════════════════════════════════════════════════════════════

    public FarmSite site(SiteType type) {
        String raw = switch (type) {
            case START -> siteStart.get();
            case END -> siteEnd.get();
            case DUMP -> siteDump.get();
            case SUPPLY -> siteSupply.get();
        };
        return FarmSite.parse(raw);
    }

    public void bindSite(SiteType type, FarmSite site) {
        String serialized = site.serialize();
        switch (type) {
            case START -> siteStart.set(serialized);
            case END -> siteEnd.set(serialized);
            case DUMP -> siteDump.set(serialized);
            case SUPPLY -> siteSupply.set(serialized);
        }

        // 起点或终点变更时立刻更新扫描器
        if (type == SiteType.START || type == SiteType.END) {
            FarmSite start = site(SiteType.START);
            FarmSite end = site(SiteType.END);
            if (start != null && end != null) {
                scanner.setBounds(start.pos(), end.pos());
            }
        }
    }

    public void clearSite(SiteType type) {
        switch (type) {
            case START -> siteStart.set(FarmSite.UNBOUND);
            case END -> siteEnd.set(FarmSite.UNBOUND);
            case DUMP -> siteDump.set(FarmSite.UNBOUND);
            case SUPPLY -> siteSupply.set(FarmSite.UNBOUND);
        }
    }

    public void clearAllSites() {
        siteStart.set(FarmSite.UNBOUND);
        siteEnd.set(FarmSite.UNBOUND);
        siteDump.set(FarmSite.UNBOUND);
        siteSupply.set(FarmSite.UNBOUND);
        scanner.reset();
        patrolRoute = List.of();
        patrolIndex = 0;
        lastIdleNotifySweep = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  事件处理
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // 防踩踏
        if (antiTrample.get() && scanner.contains(mc.player.blockPosition())) {
            if (mc.options.keyJump.isDown()) {
                mc.options.keyJump.setDown(false);
            }
        }

        // 分帧扫描
        scanner.tick();

        // 容器同步观测
        broker.tick();

        // 状态机推进
        tickStateMachine();

        // 看门狗。待机状态等作物成熟不算超时，其他状态卡住才是真超时
        if (state != FarmState.STANDBY) {
            stateTick++;
            if (stateTick > state.watchdogTicks()) {
                watchdogStrikes++;
                if (watchdogStrikes >= MAX_WATCHDOG_STRIKES) {
                    notifyError("看门狗检测到连续 " + MAX_WATCHDOG_STRIKES + " 次超时，模块已停机");
                    if (isActive()) toggle();
                    return;
                }
                notifyError("状态 " + state.cn() + " 超时，回退待机（第 " + watchdogStrikes + " 次）");
                transitionTo(FarmState.STANDBY);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!scanner.bounded()) return;

        // 农田雷达
        if (renderRadar.get()) {
            FarmRenderer.renderRadar(event,
                scanner.harvestQueue(), scanner.plantQueue(),
                radarRipeColor.get(), radarRipeColor.get(),
                radarEmptyColor.get(), radarEmptyColor.get(),
                radarShapeMode.get());
        }

        // 边界外框
        if (renderBounds.get()) {
            FarmRenderer.renderBounds(event, scanner.min(), scanner.max(),
                boundsColor.get(), boundsColor.get(), boundsShapeMode.get());
        }

        // 水源辐射范围
        if (renderWaterRange.get()) {
            List<BlockPos> sources = FarmRenderer.collectWaterSources(
                scanner.min(), scanner.max(), waterMaxSources.get());
            FarmRenderer.renderWaterRange(event, sources,
                waterRangeColor.get(), waterRangeColor.get(), waterRangeShapeMode.get());
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderLabels.get()) return;

        FarmSite dump = site(SiteType.DUMP);
        FarmSite supply = site(SiteType.SUPPLY);

        if (dump != null && dump.inCurrentDimension()) {
            FarmRenderer.renderLabel(event, dump.pos(), "[📥 卸货总仓]",
                new SettingColor(255, 215, 0));
        }

        if (supply != null && supply.inCurrentDimension()) {
            FarmRenderer.renderLabel(event, supply.pos(), "[📦 种子库]",
                new SettingColor(100, 150, 255));
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // 退出服务器时关闭模块，防止下次进入时在错误维度运行
        if (isActive()) toggle();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FSM 状态机
    // ═══════════════════════════════════════════════════════════════════

    private void tickStateMachine() {
        switch (state) {
            case STANDBY -> tickStandby();
            case NUKE_FARMING -> tickNukeFarming();
            case COLLECTING -> tickCollecting();
            case JUDGMENT -> tickJudgment();
            case UNLOADING -> tickUnloading();
            case RESTOCKING -> tickRestocking();
        }
    }

    private void transitionTo(FarmState newState) {
        if (state == newState) return;

        state = newState;
        stateTick = 0;
        // 开箱节流的基准也要跟着归零。否则第二次进卸货时 stateTick 从 0 起算，
        // 会被上一轮遗留的 lastOpenAttempt 判成"刚刚才试过"，白等十几 tick 才肯开箱
        lastOpenAttempt = -OPEN_RETRY_INTERVAL;

        // 只播报物流动作。收割 → 拾取 → 决策 → 待机 是每几秒就走一遍的常规循环，
        // 逐条播报等于刷屏，一分钟能顶几百条消息
        if (newState == FarmState.UNLOADING || newState == FarmState.RESTOCKING) {
            if (!lastNotifiedState.equals(newState.cn())) {
                notify("切换状态：" + newState.cn());
                lastNotifiedState = newState.cn();
            }
        } else if (newState == FarmState.NUKE_FARMING) {
            // 从物流状态回到干活，把播报锁放开，下次再去箱子时才会重新提示
            lastNotifiedState = "";
        }

        // 取消导航
        if (newState != FarmState.UNLOADING && newState != FarmState.RESTOCKING) {
            FarmNav.cancel();
        }
    }

    private void tickStandby() {
        // 等待扫描器至少完成一整轮，拿到稳定快照
        if (scanner.completedSweeps() == 0) return;

        List<BlockPos> harvest = scanner.harvestQueue();
        List<BlockPos> plant = scanner.plantQueue();
        if (harvest.isEmpty() && plant.isEmpty()) {
            // 收完一整轮，发现农田没菜了才提示。每 10 轮提示一次避免刷屏
            if (scanner.completedSweeps() - lastIdleNotifySweep >= 10) {
                notify("§7等待作物成熟中...（已扫描 " + scanner.completedSweeps() + " 轮，暂无可收割目标）");
                lastIdleNotifySweep = scanner.completedSweeps();
            }
            return;
        }

        // 拷一份作业快照。扫描器的快照随时会被下一轮整体替换，
        // 直接引用会导致作业进行到一半目标列表突然变形
        workHarvest = harvest;
        workPlant = plant;
        processedHarvest.clear();
        processedPlant.clear();

        // 每轮作业都从航线头开始重走，否则第二轮会以为已经巡完
        patrolIndex = 0;

        transitionTo(FarmState.NUKE_FARMING);
    }

    private void tickNukeFarming() {
        // 时运防爆锁：主手工具快爆了切空手继续收，别拿钻石镐换几颗小麦
        if (fortuneLock.get()) {
            ItemStack tool = mc.player.getMainHandItem();
            if (!tool.isEmpty()
                && FarmPacketOps.getRemainingDurability(tool) < fortuneLockThreshold.get()) {
                // 找热键栏第一个空格切过去，没空格就切到最后一格（通常是火把/食物）
                int emptySlot = mc.player.getInventory().getFreeSlot();
                if (emptySlot >= 0 && emptySlot < 9) {
                    InvUtils.swap(emptySlot, false);
                } else {
                    mc.player.getInventory().setSelectedSlot(8);
                }
                notify("§e主手工具耐久不足 " + fortuneLockThreshold.get() + "，已切空手继续收割");
            }
        }

        // 破坏与播种分开计数：两者共用一个 BPT 会互相饿死，
        // 收割快的时候播种永远排不上号
        int breakBudget = bpt.get();
        int plantBudget = bpt.get();

        // ── 收割 ──
        for (BlockPos pos : workHarvest) {
            if (breakBudget <= 0) break;
            if (processedHarvest.contains(pos)) continue;
            if (!inReach(pos)) continue;  // 够不着就跳过，走近了下一 tick 会收

            // 二次确认：快照生成到现在可能已经被别人收了
            BlockState state = mc.level.getBlockState(pos);
            CropProfile profile = CropProfile.byBlock(state.getBlock());
            if (profile == null || !profile.isHarvestable(state, mc.level, pos)) {
                processedHarvest.add(pos);
                continue;
            }

            if (FarmPacketOps.breakBlock(pos, Direction.UP)) {
                processedHarvest.add(pos);
                breakBudget--;
            }
        }

        // ── 补种 ──
        if (!harvestOnly) {
            for (BlockPos soil : workPlant) {
                if (plantBudget <= 0) break;
                if (processedPlant.contains(soil)) continue;
                if (!inReach(soil)) continue;

                CropProfile profile = plantableAt(soil);
                if (profile == null) {
                    processedPlant.add(soil);
                    continue;
                }

                InteractionHand hand = prepareSeed(profile.seed());
                if (hand == null) {
                    // 手上没种子了，降级只收不种，本轮剩下的补种目标全部放弃
                    harvestOnly = true;
                    notifyError("背包已无种子，降级为只收不种");
                    break;
                }

                if (FarmPacketOps.useOnBlock(hand, soil)) {
                    processedPlant.add(soil);
                    plantBudget--;
                }
            }
        }

        // 当前位置够得到的全处理完了，才走下一个航点
        int reachableHarvest = reachableCount(workHarvest);
        int reachablePlant = reachableCount(workPlant);
        boolean currentDone = reachableHarvest == 0 
            && (harvestOnly || reachablePlant == 0);
        
        if (currentDone) {
            // 蛇形巡逻：推进到下一个航点
            if (serpentinePatrol.get() && FarmNav.available()) {
                advancePatrol();
            }
        }

        // 全部目标处理完（包括够不着的也算）才收工
        // 这样可以确保 Baritone 走完整条航线，不会遗漏任何作物
        if (processedHarvest.size() >= workHarvest.size()
            && (harvestOnly || processedPlant.size() >= workPlant.size())) {
            transitionTo(FarmState.COLLECTING);
        }
    }

    /**
     * 推进蛇形巡逻。
     *
     * 改成每 tick 都调用，让 Baritone 一直保持移动状态。
     * 到站后自动指向下一个航点，实现边走边收。
     */
    private void advancePatrol() {
        if (patrolRoute.isEmpty()) return;
        if (patrolIndex >= patrolRoute.size()) return;

        BlockPos waypoint = patrolRoute.get(patrolIndex);

        // 到站了就指向下一个航点
        if (mc.player.blockPosition().distSqr(waypoint) <= 4) {
            patrolIndex++;
            if (patrolIndex >= patrolRoute.size()) return;  // 航线走完
            waypoint = patrolRoute.get(patrolIndex);
        }

        // 让 Baritone 持续走向当前航点
        if (!FarmNav.pathing()) {
            FarmNav.goTo(waypoint, 1);
        }
    }

    /**
     * 按农田范围生成蛇形航线：沿 X 走一行，Z 跨一步，下一行反向走回来。
     *
     * 航点间距取收割距离的两倍再减一，保证相邻两站的作用范围有重叠，不留漏收的缝。
     * 航点高度统一取 min.y + 1，也就是站在底盘上表面。
     */
    private void rebuildPatrolRoute() {
        if (!scanner.bounded()) {
            patrolRoute = List.of();
            patrolIndex = 0;
            return;
        }

        BlockPos min = scanner.min();
        BlockPos max = scanner.max();
        int step = Math.max(1, reachDistance.get() * 2 - 1);
        int y = min.getY() + 1;

        List<BlockPos> route = new ArrayList<>();
        boolean reverse = false;
        for (int z = min.getZ(); z <= max.getZ(); z += step) {
            if (reverse) {
                for (int x = max.getX(); x >= min.getX(); x -= step) route.add(new BlockPos(x, y, z));
            } else {
                for (int x = min.getX(); x <= max.getX(); x += step) route.add(new BlockPos(x, y, z));
            }
            reverse = !reverse;
        }

        patrolRoute = List.copyOf(route);
        patrolIndex = 0;
    }

    private void tickCollecting() {
        // 走到农田中心，增大拾取范围覆盖掉落物
        if (stateTick == 0 && serpentinePatrol.get() && FarmNav.available() && scanner.bounded()) {
            BlockPos center = new BlockPos(
                (scanner.min().getX() + scanner.max().getX()) / 2,
                scanner.min().getY() + 1,
                (scanner.min().getZ() + scanner.max().getZ()) / 2
            );
            FarmNav.goTo(center, 2);
        }

        // 等待掉落物飞回来或被磁力吸引到
        if (stateTick < COLLECT_WAIT_TICKS) return;
        
        FarmNav.cancel();
        transitionTo(FarmState.JUDGMENT);
    }

    private void tickJudgment() {
        // 背包满了强制去卸货，不管产物够不够阈值。
        // 否则拾取不了新掉落物，东西烂地上；swap 种子也会失败，误降级只收不种
        int freeSlots = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) freeSlots++;
        }
        if (freeSlots <= 2) {
            transitionTo(FarmState.UNLOADING);
            return;
        }

        // 背包快满就去卸货。按总组数算，设 20 就是"白名单物品总共 20 组"
        if (countWhitelistStacks() >= unloadThreshold.get()) {
            transitionTo(FarmState.UNLOADING);
            return;
        }

        // 只收不种降级状态下，种子不足才值得跑一趟补货箱
        if (harvestOnly || countSeeds() < seedSafetyStock.get() * 64) {
            if (site(SiteType.SUPPLY) != null) {
                transitionTo(FarmState.RESTOCKING);
                return;
            }
        }

        transitionTo(FarmState.STANDBY);
    }

    private void tickUnloading() {
        FarmSite dump = site(SiteType.DUMP);
        if (dump == null) {
            notifyError("卸货箱未绑定，跳过卸货");
            transitionTo(FarmState.STANDBY);
            return;
        }

        // 走到箱子边上。Baritone 不可用时只能指望玩家自己就站在旁边
        if (!mc.player.isWithinBlockInteractionRange(dump.pos(), 0.0)) {
            if (!FarmNav.pathing()) FarmNav.goTo(dump.pos(), 2);
            return;
        }
        FarmNav.cancel();

        // 容器没开就开，开了没同步完就等
        if (ContainerBroker.openMenu() == null) {
            tryOpenContainer(dump.pos());
            return;
        }
        if (!broker.isReady()) return;

        // 倒货：白名单内、且不在截留清单里的物品
        Set<Item> whitelist = lootWhitelist();
        Set<Item> retain = retainItems();
        boolean moved = false;
        for (int i = 0; i < bpt.get(); i++) {
            if (!broker.depositOne(stack -> whitelist.contains(stack.getItem())
                && !retain.contains(stack.getItem()))) {
                break;
            }
            moved = true;
        }

        // 一个都倒不动：要么倒完了，要么箱子满了。
        // 按用户实际配置（漏斗 + 打包机 + 红石检测）箱子不会满，所以不停机，直接回去干活
        if (!moved) {
            ContainerBroker.closeContainer();
            broker.reset();
            transitionTo(FarmState.STANDBY);
        }
    }

    private void tickRestocking() {
        FarmSite supply = site(SiteType.SUPPLY);
        if (supply == null) {
            transitionTo(FarmState.STANDBY);
            return;
        }

        if (!mc.player.isWithinBlockInteractionRange(supply.pos(), 0.0)) {
            if (!FarmNav.pathing()) FarmNav.goTo(supply.pos(), 2);
            return;
        }
        FarmNav.cancel();

        if (ContainerBroker.openMenu() == null) {
            tryOpenContainer(supply.pos());
            return;
        }
        if (!broker.isReady()) return;

        // 取种子：只取当前启用作物真正需要的那几种
        boolean moved = false;
        for (CropProfile profile : getEnabledCrops()) {
            if (!profile.needsSeed()) continue;
            if (countItem(profile.seed()) >= seedSafetyStock.get() * 64) continue;

            if (broker.withdrawOne(profile.seed())) {
                moved = true;
                break;
            }
        }

        if (!moved) {
            ContainerBroker.closeContainer();
            broker.reset();

            // 补到货就解除降级，补货箱也空了就维持只收不种继续跑
            if (countSeeds() > 0) {
                if (harvestOnly) notify("§a种子已补充，恢复收割播种");
                harvestOnly = false;
            } else {
                harvestOnly = true;
            }
            transitionTo(FarmState.STANDBY);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════════

    private Set<CropProfile> getEnabledCrops() {
        Set<CropProfile> enabled = EnumSet.noneOf(CropProfile.class);
        
        // 从方块列表转换成作物图鉴
        for (Block block : cropsDouble.get()) {
            CropProfile profile = CropProfile.byBlock(block);
            if (profile != null) enabled.add(profile);
        }
        for (Block block : cropsSingle.get()) {
            CropProfile profile = CropProfile.byBlock(block);
            if (profile != null) enabled.add(profile);
        }
        for (Block block : cropsPillar.get()) {
            CropProfile profile = CropProfile.byBlock(block);
            if (profile != null) enabled.add(profile);
        }
        for (Block block : cropsVine.get()) {
            CropProfile profile = CropProfile.byBlock(block);
            if (profile != null) enabled.add(profile);
        }
        
        return enabled;
    }

    /** 该底盘坐标当前能种哪种作物，种不了返回 null */
    private CropProfile plantableAt(BlockPos soil) {
        for (CropProfile profile : getEnabledCrops()) {
            if (profile.isPlantable(mc.level, soil)) return profile;
        }
        return null;
    }

    /**
     * 把种子准备到手上，返回该用哪只手发包。
     *
     * 优先用副手：副手播种不会打断主手工具的挖掘节奏，也不用来回 swap 热键栏。
     * 副手没有就在热键栏找一格切过去，热键栏也没有才算真没种子。
     */
    private InteractionHand prepareSeed(Item seed) {
        if (seed == null) return null;

        if (mc.player.getOffhandItem().is(seed)) return InteractionHand.OFF_HAND;
        if (mc.player.getMainHandItem().is(seed)) return InteractionHand.MAIN_HAND;

        FindItemResult result = InvUtils.findInHotbar(seed);
        if (result.found() && result.isHotbar()) {
            InvUtils.swap(result.slot(), false);
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    /**
     * 该坐标是否在配置的收割距离内。
     *
     * 不用原版 isWithinBlockInteractionRange：那个锁死在 blockInteractionRange()（约 4.5 格），
     * 没法给玩家开放调节。这里改成方块中心到眼睛的直线距离自己算。
     */
    private boolean inReach(BlockPos pos) {
        double limit = reachDistance.get();
        return mc.player.getEyePosition().distanceToSqr(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= limit * limit;
    }

    /**
     * 列表里有多少格是当前站位够得着的。
     *
     * 作业完成判定不能拿整个列表长度比，够不着的格子永远处理不掉，
     * 会把状态机死死卡在 NUKE_FARMING 直到看门狗超时。
     */
    private int reachableCount(List<BlockPos> list) {
        int count = 0;
        for (BlockPos pos : list) {
            if (inReach(pos)) count++;
        }
        return count;
    }

    /** 当前启用作物的全部卸货白名单物品 */
    private Set<Item> lootWhitelist() {
        Set<Item> items = new HashSet<>();
        for (CropProfile profile : getEnabledCrops()) {
            items.addAll(profile.lootWhitelist());
        }
        return items;
    }

    /** 卸货时需要截留的种子物品（单作物的产物即种子，必须留够补种量） */
    private Set<Item> retainItems() {
        Set<Item> items = new HashSet<>();
        for (CropProfile profile : getEnabledCrops()) {
            Item retain = profile.retainItem();
            if (retain != null) items.add(retain);
        }
        return items;
    }

    /** 背包里白名单物品的总组数（向上取整） */
    private int countWhitelistStacks() {
        Set<Item> whitelist = lootWhitelist();
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && whitelist.contains(stack.getItem())) {
                total += stack.getCount();
            }
        }
        // 副手也算
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty() && whitelist.contains(offhand.getItem())) {
            total += offhand.getCount();
        }
        // 转组数：127 个 → 2 组（向上取整）
        return (total + 63) / 64;
    }

    /** 背包里指定物品的总数量 */
    private int countItem(Item item) {
        if (item == null) return 0;
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) total += stack.getCount();
        }
        total += mc.player.getOffhandItem().is(item) ? mc.player.getOffhandItem().getCount() : 0;
        return total;
    }

    /** 背包里当前启用作物所需种子的总数量 */
    private int countSeeds() {
        int total = 0;
        for (CropProfile profile : getEnabledCrops()) {
            if (profile.needsSeed()) total += countItem(profile.seed());
        }
        return total;
    }

    /**
     * 发开容器包，带重试节流。
     *
     * 服务端可能因为区块未加载、方块被拆等原因不回应，
     * 每 tick 硬发会瞬间刷出几十个交互包，直接踩到反作弊上。
     */
    private void tryOpenContainer(BlockPos pos) {
        if (stateTick - lastOpenAttempt < OPEN_RETRY_INTERVAL) return;
        lastOpenAttempt = stateTick;
        broker.reset();
        FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, pos, Direction.UP);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  使用说明面板
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{
                "§l自动物流农场 · 使用说明"
            },
            new String[]{
                "§e§l▌ 准备工作",
                "§f  1. 建好农田（耕地或对应底盘），规划好起点和终点坐标",
                "§f  2. 放置卸货箱（接漏斗走物流）和补货箱（装种子）",
                "§f  3. 在配置页面勾选要种的作物（双作物/单作物/柱状物/蔓生物）",
                "§f  4. 用指令绑定四个锚点（起点、终点、卸货箱、补货箱）",
                "§f  5. 主手拿时运工具（可选），背包准备好种子"
            },
            new String[]{
                "§6§l▌ 指令系统",
                "§f  · " + highlightCommand(".nongchang set 起点") + " — 准星对准农田一角，绑定起点",
                "§f  · " + highlightCommand(".nongchang set 终点") + " — 准星对准对角，绑定终点",
                "§f  · " + highlightCommand(".nongchang set 卸货箱") + " — 准星对准箱子，绑定卸货箱",
                "§f  · " + highlightCommand(".nongchang set 补货箱") + " — 准星对准箱子，绑定补货箱",
                "§f  · " + highlightCommand(".nongchang status") + " — 查看四个锚点的坐标和维度",
                "§f  · " + highlightCommand(".nongchang remove 起点") + " — 解绑单个锚点",
                "§f  · " + highlightCommand(".nongchang clear") + " — 一键清空所有锚点",
                "§f  · 支持中英文：" + highlightText("起点/start") + "、" + highlightText("终点/end") + "、" + highlightText("卸货箱/dump") + "、" + highlightText("补货箱/supply")
            },
            new String[]{
                "§a§l▌ 状态机运作流程",
                "§f  1. " + highlightText("待机") + " — 扫描农田（512格/tick），发现成熟作物或空地进入收割",
                "§f  2. " + highlightText("收割播种") + " — 蛇形巡逻走位，每个航点收完种完才走下一站",
                "§f     · 收割和补种各自独立BPT预算（默认10格/tick）",
                "§f     · 够不着的跳过，走近了下一tick自然收到",
                "§f     · 时运工具耐久不足自动切空手继续收",
                "§f     · 种子不足降级只收不种，补货后自动恢复",
                "§f  3. " + highlightText("拾取掉落") + " — 走到农田中心，等待40 tick让掉落物飞回来",
                "§f  4. " + highlightText("状态决策") + " — 背包空格≤2强制卸货 → 产物≥阈值卸货 → 种子不足补货 → 回待机",
                "§f  5. " + highlightText("卸货") + " — Baritone走到箱子边，倒白名单产物（按安全库存截留种子）",
                "§f  6. " + highlightText("补货") + " — 从补货箱取当前启用作物的种子，取够了解除降级"
            },
            new String[]{
                "§b§l▌ 参数建议",
                "§f  · " + highlightText("卸货阈值") + "：默认20组，产物达到这个数量就去卸货",
                "§f  · " + highlightText("种子安全库存") + "：默认3组，单作物会截留这么多不倒进卸货箱",
                "§f  · " + highlightText("BPT限速") + "：默认10，每tick收割/播种的格子数，太高可能被反作弊拦截",
                "§f  · " + highlightText("收割距离") + "：默认4格，调到5以上属于超距，服务端可能拒绝",
                "§f  · " + highlightText("时运防爆阈值") + "：默认5，耐久低于这个值自动切空手"
            },
            new String[]{
                "§d§l▌ 作物分类",
                "§f  · " + highlightText("双作物") + "（小麦/甜菜）：种子与产物分离，种子自动留作补种",
                "§f  · " + highlightText("单作物") + "（土豆/胡萝卜/地狱疣）：产物即种子，截留安全库存",
                "§f  · " + highlightText("柱状物") + "（竹子/甘蔗/仙人掌）：Y+1切割保留根部，无补种",
                "§f  · " + highlightText("蔓生物") + "（南瓜/西瓜）：只砍果实，茎自动再生，无补种",
                "§f  · " + highlightText("毒马铃薯") + "：进卸货白名单，全部倒进卸货箱，不截留"
            },
            new String[]{
                "§d§l▌ 蛇形巡逻",
                "§f  · 开关：" + highlightText("蛇形巡逻") + "（需要Baritone支持）",
                "§f  · 效果：自动沿Z轴折返走位，覆盖整片农田，无需手动走位",
                "§f  · 航点间距 = 收割距离×2-1，确保相邻两站覆盖范围重叠",
                "§f  · Baritone不可用时提示降级，只收站着够得到的作物"
            },
            new String[]{
                "§c§l▌ 注意事项",
                "§f  · 单人世界可以正常开启，走的是标准交互包，适合本地建小田试参数",
                "§f  · " + highlightText("四个锚点必须在同一维度") + "，跨维度会导致发包发到空气上",
                "§f  · 双作物（小麦/甜菜）需要分开的补货箱和卸货箱",
                "§f  · 单作物（土豆/胡萝卜）可以补货箱和卸货箱绑同一个，自动循环",
                "§f  · " + highlightText("同时勾选多种作物会补错种") + "（底盘相同的作物无法区分）",
                "§f  · 建议单作物管理，或者手动分层（奇偶Y高度隔离，详见开发文档）",
                "§f  · 潜影盒被打包机推掉后，" + highlightText("原位放个空盒即可，无需重设点位"),
                "§f  · 看门狗：待机不计时，其他状态超时回待机，连续3次直接停机报警"
            },
            new String[]{
                "§5§l▌ 渲染辅助",
                "§f  · " + highlightText("农田雷达") + "：成熟作物绿框，待补种空地黄框",
                "§f  · " + highlightText("农场边界外框") + "：起点到终点的立方体边界，颜色可配",
                "§f  · " + highlightText("水源辐射范围") + "：显示每桶水滋润多少格耕地（9×9×2理论上限162格）",
                "§f  · " + highlightText("防呆字牌") + "：卸货箱金色 [卸货总仓]，补货箱蓝色 [种子库]"
            }
        );
    }
}
