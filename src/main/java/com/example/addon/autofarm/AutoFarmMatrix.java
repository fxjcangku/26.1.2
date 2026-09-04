package com.example.addon.autofarm;

import com.example.addon.autofarm.command.NongChangCommand;
import com.example.addon.autofarm.controller.FarmController;
import com.example.addon.autofarm.controller.FarmDecision;
import com.example.addon.autofarm.controller.FarmObserver;
import com.example.addon.autofarm.controller.FarmSelfCheck;
import com.example.addon.autofarm.controller.FarmVerifier;
import com.example.addon.autofarm.model.CropProfile;
import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.model.FarmState;
import com.example.addon.autofarm.model.HarvestMode;
import com.example.addon.autofarm.model.PlantMode;
import com.example.addon.autofarm.model.SiteType;
import com.example.addon.autofarm.render.FarmRenderer;
import com.example.addon.autofarm.resource.FarmResourceManager;
import com.example.addon.autofarm.scan.FarmScanner;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.HarvestTask;
import com.example.addon.autofarm.task.PlantTask;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.farm.ContainerBroker;
import com.example.addon.ui.HelpScreen;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 自动农场 - 全自动农业系统（重写版）。
 *
 * 核心流程：Observe → Decide → Act → Verify → Replan。
 * 熟一颗收一颗，收割后验证、补种、拾取，物流任务（卸货/补货/毒马铃薯）独占，
 * 单作物箱装单物品作物与柱状/果实产物、种子补货箱装双物品作物种子、多作物箱装双物品成熟掉落物，
 * 多作物箱与种子补货箱同套双作物判定，毒马铃薯独立处理。
 */
public final class AutoFarmMatrix extends YiyiaddonModule {

    // ── 服务实例 ──
    private final FarmScanner scanner = new FarmScanner();
    private final ContainerBroker broker = new ContainerBroker();
    private final FarmResourceManager resources = new FarmResourceManager();
    private final FarmObserver observer = new FarmObserver();
    private final FarmVerifier verifier = new FarmVerifier();
    private final FarmSelfCheck selfCheck = new FarmSelfCheck();
    private final FarmDecision decision;
    private final FarmController controller;

    // ═══════════════════════════════════════════════════════════════════
    //  UI 配置面板（分组折叠）
    // ═══════════════════════════════════════════════════════════════════

    private final SettingGroup sgCrops = settings.createGroup("作物选择", true);
    private final SettingGroup sgPerSeed = settings.createGroup("逐作物独立配置", true);
    private final SettingGroup sgLogistics = settings.createGroup("运行参数", false);
    private final SettingGroup sgSafety = settings.createGroup("保护与锄地", false);
    private final SettingGroup sgRender = settings.createGroup("渲染显示", false);

    // ─── 作物分类选择器 ───
    private final Setting<List<Block>> cropsDouble;
    private final Setting<List<Block>> cropsSingle;
    private final Setting<List<Block>> cropsPillar;
    private final Setting<List<Block>> cropsVine;

    // ─── 单种子独立配置（跟随「作物选择」启用作物联动显示） ───
    private final Map<CropProfile, Setting<Integer>> perCropUnload = new HashMap<>();
    private final Map<CropProfile, Setting<Integer>> perCropRestock = new HashMap<>();

    // ─── 后勤配置 ───
    private final Setting<HarvestMode> harvestMode;
    private final Setting<Integer> batchCount;
    private final Setting<PlantMode> plantMode;
    private final Setting<Integer> plantBatchCount;
    private final Setting<Integer> unloadThreshold;
    private final Setting<Integer> poisonUnloadThreshold;
    private final Setting<Integer> bpt;
    private final Setting<Integer> reachDistance;

    // ─── 安全保护 ───
    private final Setting<Boolean> antiTrample;
    private final Setting<Boolean> autoTill;

    // ─── 渲染辅助 ───
    private final Setting<Boolean> renderBounds;
    private final Setting<SettingColor> boundsColor;
    private final Setting<Boolean> renderTarget;
    private final Setting<SettingColor> targetColor;
    private final Setting<Boolean> renderLabels;

    // ─── 六点位持久化（StringSetting 存序列化串） ───
    private final Setting<String> siteStart;
    private final Setting<String> siteEnd;
    private final Setting<String> siteSingle;
    private final Setting<String> siteMulti;
    private final Setting<String> siteSeed;
    private final Setting<String> sitePoison;

    /** 物流状态播报去重锁 */
    private String lastNotifiedState = "";

    /** 批量收割进度播报去重锁 */
    private String lastBatchProgress = "";

    /** 启动前记录的「失焦暂停」原值，关闭模块时还原（后台挂机时鼠标失焦不应暂停游戏） */
    private Boolean prevPauseOnLostFocus = null;

    public AutoFarmMatrix() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动农场",
            "熟一颗收一颗，自动补种拾取，单作物箱/种子补货箱/多作物箱与杂物箱物流自动化。点击按钮查看说明。");

        // ─── 作物分类选择器 ───
        cropsDouble = sgCrops.add(new BlockListSetting.Builder()
            .name("双作物")
            .description("种子与产物分离：小麦、甜菜根")
            .defaultValue(List.of())
            .filter(this::isDoubleCrop)
            .build());

        cropsSingle = sgCrops.add(new BlockListSetting.Builder()
            .name("单作物")
            .description("产物即种子：胡萝卜、马铃薯、下界疣")
            .defaultValue(List.of())
            .filter(this::isSingleCrop)
            .build());

        cropsPillar = sgCrops.add(new BlockListSetting.Builder()
            .name("柱状物")
            .description("切根部上方：竹子、甘蔗、仙人掌")
            .defaultValue(List.of())
            .filter(this::isPillar)
            .build());

        cropsVine = sgCrops.add(new BlockListSetting.Builder()
            .name("果实")
            .description("只砍果实：南瓜、西瓜")
            .defaultValue(List.of())
            .filter(this::isFruit)
            .build());

        // ─── 单种子独立配置页：跟随「作物选择」联动，启用几种作物就显示几种配置 ───
        // 双物品作物成熟掉落物卸入多作物箱（判别与种子补货箱同套判定），其余产物卸入单作物箱；
        // 三种作物共用多作物箱时，各作物的卸货数量互不影响、各按各的阈值卸货
        for (CropProfile profile : CropProfile.values()) {
            // 杂物作物（仙人掌花）走杂物卸货全局阈值，不在此创建独立卸货/补货配置
            if (profile.junk()) continue;

            String unloadDesc = profile.needsReplant() && profile.plantItem() != profile.harvestItem()
                ? "该作物成熟掉落物超过此组数才卸入 §d多作物箱§r（种子的盈余仍卸入种子补货箱）"
                : "该作物产物超过此组数才卸入 §6单作物箱";

            Setting<Integer> unload = sgPerSeed.add(new IntSetting.Builder()
                .name(profile.displayName() + "-卸货数量")
                .description(unloadDesc)
                .defaultValue(FarmResourceManager.DEFAULT_UNLOAD_GROUPS)
                .min(1).max(36).noSlider()
                .visible(() -> getEnabledCrops().contains(profile))
                .build());
            perCropUnload.put(profile, unload);

            // 补货种子数量仅对需要补种的作物有意义，柱状物/果实不显示
            if (profile.needsReplant()) {
                Setting<Integer> restock = sgPerSeed.add(new IntSetting.Builder()
                    .name(profile.displayName() + "-补货种子数量")
                    .description("该作物种植材料低于多少组时自动去作物箱补货，卸货时始终保留这批材料")
                    .defaultValue(FarmResourceManager.DEFAULT_RESTOCK_GROUPS)
                    .min(1).max(10).noSlider()
                    .visible(() -> getEnabledCrops().contains(profile))
                    .build());
                perCropRestock.put(profile, restock);
            }
        }

        // ─── 后勤配置 ───
        harvestMode = sgLogistics.add(new EnumSetting.Builder<HarvestMode>()
            .name("收割模式")
            .description("单个收割：每次只处理一个成熟目标；批量收割：一次锁定多个目标后严格串行执行")
            .defaultValue(HarvestMode.SINGLE)
            .onChanged(this::onHarvestModeChanged)
            .build());

        batchCount = sgLogistics.add(new IntSetting.Builder()
            .name("批量收割数量")
            .description("批量模式下一次最多锁定的成熟目标数（1~32），仅在批量收割模式下生效")
            .defaultValue(8).min(1).max(32).noSlider()
            .visible(() -> harvestMode.get() == HarvestMode.BATCH)
            .build());

        plantMode = sgLogistics.add(new EnumSetting.Builder<PlantMode>()
            .name("补种模式")
            .description("顺序优先：按作物枚举顺序种满一种再种下一种；均匀轮转：启用作物轮流种保持均衡；就近跟随：空耕地种回周围已有作物的同类，保持混种分区")
            .defaultValue(PlantMode.SEQUENTIAL)
            .build());

        plantBatchCount = sgLogistics.add(new IntSetting.Builder()
            .name("批量补种数量")
            .description("一次补种决策最多锁定并连续补种的空耕地数（1~32），越大补满越快")
            .defaultValue(8).min(1).max(32).noSlider()
            .build());

        unloadThreshold = sgLogistics.add(new IntSetting.Builder()
            .name("卸货阈值")
            .description("背包可卸货物品满多少组时触发卸货")
            .defaultValue(20).min(1).max(36).noSlider()
            .build());

        poisonUnloadThreshold = sgLogistics.add(new IntSetting.Builder()
            .name("杂物卸货")
            .description("杂物（毒马铃薯 + 仙人掌花）攒够多少个才卸货一次，避免捡一个就跑一次")
            .defaultValue(64).min(1).max(64).noSlider()
            .visible(() -> getEnabledCrops().stream().anyMatch(p -> !p.extraLoot().isEmpty() || p.junk()))
            .build());

        bpt = sgLogistics.add(new IntSetting.Builder()
            .name("发包速率(BPT)")
            .description("每 tick 最多发送多少个破坏/播种/容器操作包")
            .defaultValue(10).min(1).max(30).noSlider()
            .build());

        reachDistance = sgLogistics.add(new IntSetting.Builder()
            .name("收割距离")
            .description("能操作多远的方块，原版上限约 4.5 格，低于 3 会导致寻路卡住")
            .defaultValue(4).min(3).max(8).noSlider()
            .build());

        // ─── 安全保护 ───
        antiTrample = sgSafety.add(new BoolSetting.Builder()
            .name("防踩踏")
            .description("农田范围内拦截跳跃键，避免踩坏耕地")
            .defaultValue(true)
            .build());

        autoTill = sgSafety.add(new BoolSetting.Builder()
            .name("自动锄地")
            .description("农田范围内发现草方块/泥土时，自动拿锄头锄成耕地；背包无锄头则跳过")
            .defaultValue(true)
            .build());

        // ─── 渲染辅助 ───
        renderBounds = sgRender.add(new BoolSetting.Builder()
            .name("农田边界")
            .description("只渲染农场范围的外框一圈（不填面），大农场也不卡")
            .defaultValue(true)
            .build());

        boundsColor = sgRender.add(new ColorSetting.Builder()
            .name("边界框颜色")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> renderBounds.get())
            .build());

        renderTarget = sgRender.add(new BoolSetting.Builder()
            .name("目标显示")
            .description("高亮当前正在作业的目标方块")
            .defaultValue(true)
            .build());

        targetColor = sgRender.add(new ColorSetting.Builder()
            .name("目标颜色")
            .defaultValue(new SettingColor(0, 255, 100, 75))
            .visible(() -> renderTarget.get())
            .build());

        renderLabels = sgRender.add(new BoolSetting.Builder()
            .name("点位字牌")
            .description("各绑定箱头顶显示防呆标签")
            .defaultValue(true)
            .build());

        // ─── 六点位（隐藏设置，由指令管理） ───
        siteStart = hiddenAnchor("_anchor_start", "农场点位1");
        siteEnd = hiddenAnchor("_anchor_end", "农场点位2");
        siteSingle = hiddenAnchor("_anchor_single", "单作物箱");
        siteMulti = hiddenAnchor("_anchor_multi", "多作物箱");
        siteSeed = hiddenAnchor("_anchor_seed", "种子补货箱");
        sitePoison = hiddenAnchor("_anchor_poison", "杂物箱");

        // 构建决策器与控制器（依赖上面的设置字段默认值）
        decision = new FarmDecision(scanner, resources, observer, verifier, broker,
            unloadThreshold.get(), poisonUnloadThreshold.get(), bpt.get(), reachDistance.get());
        controller = new FarmController(scanner, resources, observer, verifier, broker, decision);
        controller.setLogger(this::notify);
    }

    /** 创建隐藏的锚点 StringSetting */
    private Setting<String> hiddenAnchor(String name, String desc) {
        return settings.getDefaultGroup().add(new StringSetting.Builder()
            .name(name)
            .description("内部使用：" + desc)
            .defaultValue(FarmSite.UNBOUND)
            .visible(() -> false)
            .build());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  模块生命周期
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            notifyError("必须在进入世界后才能启动模块。");
            mc.execute(this::toggle);
            return;
        }

        // 清理历史脏数据，确保四个作物选择器只保留本分类方块
        sanitizeCropSelectors();

        // 自检：作物数量、点位、维度、范围一次性列全
        if (!reportSelfCheck(selfCheck.check(getEnabledCrops(), getSitesMap()))) return;

        // 后台挂机：关闭失焦暂停，否则鼠标失焦后游戏暂停、Baritone 停止寻路
        if (prevPauseOnLostFocus == null) {
            prevPauseOnLostFocus = mc.options.pauseOnLostFocus;
        }
        mc.options.pauseOnLostFocus = false;

        // 配置扫描器与资源管理器
        scanner.setEnabledCrops(getEnabledCrops());
        FarmSite start = site(SiteType.START);
        FarmSite end = site(SiteType.END);
        if (start != null && end != null) {
            scanner.setBounds(start.pos(), end.pos());
        }
        // 一次性全量扫描：立即拿到全部成熟/可补种/待锄地目标，补种「一瞬间补满」，
        // 避免分帧扫描导致空耕地一批一批慢慢出现
        scanner.fullScan();
        resources.configure(getEnabledCrops(), buildUnloadGroups(), buildRestockGroups());

        // 重置状态并同步运行配置
        lastNotifiedState = "";
        lastBatchProgress = "";
        controller.configure(getSitesMap(), reachDistance.get());
        decision.update(unloadThreshold.get(), poisonUnloadThreshold.get(), bpt.get(), reachDistance.get());
        decision.updateMode(harvestMode.get(), batchCount.get());
        decision.updatePlantMode(plantMode.get());
        decision.updatePlantBatchCount(plantBatchCount.get());
        decision.updateTill(autoTill.get(), plantBatchCount.get());

        reportStartupInfo();
    }

    /** 启动播报：合并为一条多行消息块，只带一次模块前缀 */
    private void reportStartupInfo() {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 自动农场 · 启动报告");

        Set<CropProfile> enabled = getEnabledCrops();
        StringBuilder crops = new StringBuilder();
        int count = 0;
        for (CropProfile profile : enabled) {
            if (count > 0) crops.append("§f、");
            crops.append(highlightText(profile.displayName()));
            count++;
        }
        report.append("\n§7启用作物　§8▸ ").append(crops).append("§r");

        // 智能识别：单物品作物（种子==收获物）与不补种作物（柱状物/果实）→单作物箱；
        // 双作物判定（需要补种且种子≠收获物）→种子补货箱（种子），多作物箱（成熟掉落物）与种子补货箱同判定
        boolean hasSingle = enabled.stream().anyMatch(p -> !p.needsReplant() || p.plantItem() == p.harvestItem());
        boolean hasDualSeed = enabled.stream().anyMatch(p -> p.needsReplant() && p.plantItem() != p.harvestItem());
        boolean hasDualHarvest = hasDualSeed;

        StringBuilder boxes = new StringBuilder();
        if (hasSingle) boxes.append("单作物箱 ");
        if (hasDualSeed) boxes.append("种子补货箱 ");
        if (hasDualHarvest) boxes.append("多作物箱 ");
        report.append("\n§7作物箱　　§8▸ ").append(highlightText(boxes.toString().trim())).append("§r");

        // 物品类型状态提示：单物品 / 双物品 / 混合
        String kindDesc;
        if (hasDualSeed && hasSingle) {
            kindDesc = "混合（单物品 + 双物品）";
        } else if (hasDualSeed) {
            kindDesc = "双物品（种子 + 收获物分离）";
        } else {
            kindDesc = "单物品（仅一种产物）";
        }
        report.append("\n§7物品类型　§8▸ ").append(highlightText(kindDesc)).append("§r");

        // 农田范围
        FarmSite start = site(SiteType.START);
        FarmSite end = site(SiteType.END);
        if (start != null && end != null) {
            int rangeX = Math.abs(end.pos().getX() - start.pos().getX()) + 1;
            int rangeZ = Math.abs(end.pos().getZ() - start.pos().getZ()) + 1;
            report.append("\n§7农田范围　§8▸ ").append(highlightText(rangeX + "×" + rangeZ)).append("§r");
        }

        report.append("\n§7卸货阈值　§8▸ ").append(highlightText(unloadThreshold.get() + " 组")).append("§r");
        report.append("\n§7自动锄地　§8▸ ").append(autoTill.get() ? "§a开" : "§c关").append("§r");

        // 每种启用作物的独立卸货/补货数量（单种子配置页里逐项可调）
        for (CropProfile profile : enabled) {
            report.append("\n§7").append(highlightText(profile.displayName()))
                .append(" §8▸ §7卸 ").append(highlightText(perCropUnload.get(profile).get() + " 组"));
            if (profile.needsReplant()) {
                report.append(" §r· 补 ").append(highlightText(perCropRestock.get(profile).get() + " 组"));
            }
            report.append("§r");
        }

        String modeText = harvestMode.get() == HarvestMode.BATCH
            ? "批量收割 · " + batchCount.get() + " 个"
            : "单个收割";
        report.append("\n§7收割模式　§8▸ ").append(highlightText(modeText)).append("§r");

        notify(report.toString());
    }

    @Override
    public void onDeactivate() {
        controller.reset();
        ContainerBroker.closeContainer();
        lastNotifiedState = "";

        // 还原失焦暂停原值
        if (prevPauseOnLostFocus != null) {
            mc.options.pauseOnLostFocus = prevPauseOnLostFocus;
            prevPauseOnLostFocus = null;
        }
    }

    /** 收割模式切换时的中文提示（复用已有 notify 机制，不新建通知系统） */
    private void onHarvestModeChanged(HarvestMode mode) {
        if (mode == HarvestMode.BATCH) {
            notify("§b已切换为批量收割 §8▸ 每次最多锁定 " + highlightText(batchCount.get() + " 个目标"));
        } else {
            notify("§a已切换为单个收割 §8▸ 每次只处理一个成熟目标");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  事件处理
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // 防踩踏：农田范围内拦截跳跃
        if (antiTrample.get() && scanner.contains(mc.player.blockPosition())) {
            if (mc.options.keyJump.isDown()) {
                mc.options.keyJump.setDown(false);
            }
        }

        // 同步最新运行配置
        decision.update(unloadThreshold.get(), poisonUnloadThreshold.get(), bpt.get(), reachDistance.get());
        decision.updateMode(harvestMode.get(), batchCount.get());
        decision.updatePlantMode(plantMode.get());
        decision.updatePlantBatchCount(plantBatchCount.get());
        decision.updateTill(autoTill.get(), plantBatchCount.get());
        resources.configure(getEnabledCrops(), buildUnloadGroups(), buildRestockGroups());
        controller.configure(getSitesMap(), reachDistance.get());

        // 推进状态机
        controller.tick();

        // 状态播报（只播物流状态，工作状态高频循环不播）+ 批量进度播报
        broadcastState(controller.state());
        broadcastBatchProgress();
    }

    /** 批量模式下播报计划剩余目标数（每个目标闭环后变化一次，去重锁节流） */
    private void broadcastBatchProgress() {
        if (!controller.hasBatchPlan()) {
            lastBatchProgress = "";
            return;
        }
        String key = "batch:" + controller.batchRemaining();
        if (!key.equals(lastBatchProgress)) {
            lastBatchProgress = key;
            notify("§b批量收割 §8▸ 剩余 " + highlightText(controller.batchRemaining() + " 个目标"));
        }
    }

    private void broadcastState(FarmState state) {
        // 物流工作状态：进入时播进度（§7正在XXX...），带去重锁
        if (isLogisticsState(state)) {
            if (!state.cn().equals(lastNotifiedState)) {
                lastNotifiedState = state.cn();
                notify("§7正在" + state.cn() + "...");
            }
        } else if (state == FarmState.OBSERVE) {
            lastNotifiedState = "";
        }
    }

    /** 是否为需要播报进度的物流工作状态（收割/补种/拾取高频循环不播防刷屏） */
    private boolean isLogisticsState(FarmState state) {
        return state == FarmState.UNLOAD || state == FarmState.RESTOCK || state == FarmState.POISON_DUMP;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!scanner.bounded()) return;

        // 农场边界（只画外框一圈，不渲染面）
        if (renderBounds.get()) {
            FarmRenderer.renderBorder(event, scanner.min(), scanner.max(), boundsColor.get());
        }

        // 当前作业目标
        if (renderTarget.get()) {
            FarmTask task = controller.currentTask();
            BlockPos target = null;
            if (task instanceof HarvestTask harvest) target = harvest.target().pos();
            else if (task instanceof PlantTask plant) target = plant.target().pos().above();
            if (target != null) {
                FarmRenderer.renderTarget(event, target, targetColor.get(), targetColor.get(), ShapeMode.Both);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderLabels.get()) return;
        renderLabel(event, SiteType.START, "§a农场点位1");
        renderLabel(event, SiteType.END, "§e农场点位2");
        renderLabel(event, SiteType.SINGLE_STORAGE, "§6单作物箱");
        renderLabel(event, SiteType.MULTI_STORAGE, "§d多作物箱");
        renderLabel(event, SiteType.SEED_STORAGE, "§b种子补货箱");
        renderLabel(event, SiteType.POISON_STORAGE, "§c杂物箱");
    }

    private void renderLabel(Render2DEvent event, SiteType type, String text) {
        FarmSite site = site(type);
        if (site != null && site.inCurrentDimension()) {
            FarmRenderer.renderLabel(event, site.pos(), text, new SettingColor(255, 255, 255));
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null) return;
        // 静默容器：仅在物流任务（卸货/补货/毒马铃薯，exclusive=true）执行期间取消箱子界面显示，
        // 避免自动化开箱时抢鼠标/焦点。玩家手动开箱（模块空闲/观察/收割/补种/拾取阶段）必须放行，
        // 否则模块开启时玩家无法打开箱子，也无法在后台挂机时切回前台手动开箱。
        FarmTask task = controller.currentTask();
        boolean logisticsRunning = task != null && task.exclusive();
        if (isActive() && logisticsRunning
            && event.screen instanceof AbstractContainerScreen<?>
            && !(event.screen instanceof InventoryScreen)) {
            event.setCancelled(true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  锚点管理（供指令调用）
    // ═══════════════════════════════════════════════════════════════════

    public FarmSite site(SiteType type) {
        return FarmSite.parse(rawSite(type));
    }

    public void bindSite(SiteType type, FarmSite site) {
        setRawSite(type, site.serialize());

        // 农场范围变更时更新扫描器
        if (type == SiteType.START || type == SiteType.END) {
            FarmSite start = site(SiteType.START);
            FarmSite end = site(SiteType.END);
            if (start != null && end != null) {
                scanner.setBounds(start.pos(), end.pos());
            }
        }
    }

    public void clearSite(SiteType type) {
        setRawSite(type, FarmSite.UNBOUND);
    }

    public void clearAllSites() {
        for (SiteType type : SiteType.values()) {
            setRawSite(type, FarmSite.UNBOUND);
        }
        scanner.reset();
    }

    private String rawSite(SiteType type) {
        return switch (type) {
            case START -> siteStart.get();
            case END -> siteEnd.get();
            case SINGLE_STORAGE -> siteSingle.get();
            case MULTI_STORAGE -> siteMulti.get();
            case SEED_STORAGE -> siteSeed.get();
            case POISON_STORAGE -> sitePoison.get();
        };
    }

    private void setRawSite(SiteType type, String value) {
        switch (type) {
            case START -> siteStart.set(value);
            case END -> siteEnd.set(value);
            case SINGLE_STORAGE -> siteSingle.set(value);
            case MULTI_STORAGE -> siteMulti.set(value);
            case SEED_STORAGE -> siteSeed.set(value);
            case POISON_STORAGE -> sitePoison.set(value);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════════

    /** 从四组作物选择器汇总启用作物集合（按各自分类二次过滤，防御历史脏数据） */
    private Set<CropProfile> getEnabledCrops() {
        Set<CropProfile> enabled = EnumSet.noneOf(CropProfile.class);
        addCrops(enabled, cropsDouble.get(), this::isDoubleCrop);
        addCrops(enabled, cropsSingle.get(), this::isSingleCrop);
        addCrops(enabled, cropsPillar.get(), this::isPillar);
        addCrops(enabled, cropsVine.get(), this::isFruit);
        // 仙人掌花跟随仙人掌自动启用：种了仙人掌就会自然长出仙人掌花
        if (enabled.contains(CropProfile.CACTUS)) {
            enabled.add(CropProfile.CACTUS_FLOWER);
        }
        return enabled;
    }

    /** 把符合分类的方块转成作物图鉴并加入集合 */
    private void addCrops(Set<CropProfile> enabled, List<Block> blocks, Predicate<Block> valid) {
        for (Block block : blocks) {
            if (!valid.test(block)) continue;
            CropProfile profile = CropProfile.byBlock(block);
            if (profile != null) enabled.add(profile);
        }
    }

    /** 双作物：普通农作物且种子与产物分离（小麦、甜菜根） */
    private boolean isDoubleCrop(Block block) {
        CropProfile profile = CropProfile.byBlock(block);
        return profile != null && profile.kind() == CropProfile.Kind.CROP
            && profile.plantItem() != profile.harvestItem();
    }

    /** 单作物：普通农作物且产物即种子（胡萝卜、马铃薯、下界疣） */
    private boolean isSingleCrop(Block block) {
        CropProfile profile = CropProfile.byBlock(block);
        return profile != null && profile.kind() == CropProfile.Kind.CROP
            && profile.plantItem() == profile.harvestItem();
    }

    /** 柱状物：竹子、甘蔗、仙人掌 */
    private boolean isPillar(Block block) {
        CropProfile profile = CropProfile.byBlock(block);
        return profile != null && profile.kind() == CropProfile.Kind.PILLAR;
    }

    /** 果实：南瓜、西瓜（仙人掌花是杂物，跟随仙人掌联动，不单独出现在果实选择器） */
    private boolean isFruit(Block block) {
        CropProfile profile = CropProfile.byBlock(block);
        return profile != null && profile.kind() == CropProfile.Kind.FRUIT && !profile.junk();
    }

    /** 清理四个作物选择器里的历史脏数据，确保每个选择器只保留本分类方块 */
    private void sanitizeCropSelectors() {
        sanitize(cropsDouble, this::isDoubleCrop);
        sanitize(cropsSingle, this::isSingleCrop);
        sanitize(cropsPillar, this::isPillar);
        sanitize(cropsVine, this::isFruit);
    }

    /** 移除选择器中不属于指定分类的方块 */
    private void sanitize(Setting<List<Block>> setting, Predicate<Block> valid) {
        setting.get().removeIf(block -> !valid.test(block));
    }

    /** 汇总每作物独立卸货数量（组），供资源管理器热更新 */
    private Map<CropProfile, Integer> buildUnloadGroups() {
        Map<CropProfile, Integer> map = new HashMap<>();
        perCropUnload.forEach((crop, setting) -> map.put(crop, setting.get()));
        return map;
    }

    /** 汇总每作物独立补货种子数量（组），供资源管理器热更新 */
    private Map<CropProfile, Integer> buildRestockGroups() {
        Map<CropProfile, Integer> map = new HashMap<>();
        perCropRestock.forEach((crop, setting) -> map.put(crop, setting.get()));
        return map;
    }

    /** 构建六点位映射 */
    private Map<SiteType, FarmSite> getSitesMap() {
        Map<SiteType, FarmSite> map = new HashMap<>();
        for (SiteType type : SiteType.values()) {
            FarmSite site = site(type);
            if (site != null) map.put(type, site);
        }
        return map;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  使用说明面板
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            table.add(theme.label("§b§l自动农场 §r§8▸ §f全自动农业系统")).expandX();
            table.row();
            table.add(theme.label(statusSummary())).expandX();
            table.row();
            table.add(theme.label(" ")).expandX();
            table.row();

            addUniformButton(theme, table, "§e查看使用说明",
                () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent())));
            table.row();

            // 六点位卡片（两列三行）
            WTable row1 = theme.table();
            buildLocationCard(theme, row1, "农场点位1", SiteType.START, "§a");
            buildLocationCard(theme, row1, "农场点位2", SiteType.END, "§e");
            table.add(row1).expandX();
            table.row();

            WTable row2 = theme.table();
            buildLocationCard(theme, row2, "单作物箱", SiteType.SINGLE_STORAGE, "§6");
            buildLocationCard(theme, row2, "多作物箱", SiteType.MULTI_STORAGE, "§d");
            table.add(row2).expandX();
            table.row();

            WTable row3 = theme.table();
            buildLocationCard(theme, row3, "种子补货箱", SiteType.SEED_STORAGE, "§b");
            buildLocationCard(theme, row3, "杂物箱", SiteType.POISON_STORAGE, "§c");
            table.add(row3).expandX();
            table.row();
        });
    }

    /** 配置页顶部状态摘要：一眼看清启用了什么、锄地是否开启、收割模式 */
    private String statusSummary() {
        Set<CropProfile> enabled = getEnabledCrops();
        StringBuilder crops = new StringBuilder();
        int index = 0;
        for (CropProfile profile : enabled) {
            if (index++ > 0) crops.append("§f、");
            crops.append(highlightText(profile.displayName()));
        }
        String till = autoTill.get() ? "§a开" : "§c关";
        String mode = harvestMode.get() == HarvestMode.BATCH
            ? highlightFunction("批量 " + batchCount.get())
            : highlightFunction("单个");
        return "§7作物 §8▸ " + (enabled.isEmpty() ? "§8未选择" : crops.toString())
            + " §8│ §7锄地 §8▸ " + till
            + " §8│ §7收割 §8▸ " + mode;
    }

    /** 构建点位设置卡片（含坐标、维度、设置、删除按钮） */
    private void buildLocationCard(GuiTheme theme, WTable parentTable, String title, SiteType type, String color) {
        WTable card = theme.table();

        boolean isBound = site(type) != null;
        FarmSite data = site(type);

        card.add(theme.label(color + title)).expandX().center();
        card.row();

        if (isBound && data != null) {
            String coords = String.format("§7X§f%d §7Y§f%d §7Z§f%d",
                data.pos().getX(), data.pos().getY(), data.pos().getZ());
            card.add(theme.label(coords)).expandX().center();
            card.row();
            card.add(theme.label("§7" + dimensionName(data.dimension()))).expandX().center();
            card.row();
        } else {
            card.add(theme.label("§8暂未绑定")).expandX().center();
            card.row();
            card.add(theme.label("§8-")).expandX().center();
            card.row();
        }

        WButton setBtn = theme.button((isBound ? "§a" : "§8") + "设置");
        setBtn.action = () -> {
            NongChangCommand.setBinding(type);
            mc.setScreen(null);
        };
        card.add(setBtn).expandX().center();
        card.row();

        WButton delBtn = theme.button("§c删除");
        delBtn.action = () -> {
            NongChangCommand.removeBinding(type);
            mc.setScreen(null);
        };
        card.add(delBtn).expandX().center();

        parentTable.add(card).expandX();
    }

    private String dimensionName(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (dim == net.minecraft.world.level.Level.OVERWORLD) return "主世界";
        if (dim == net.minecraft.world.level.Level.NETHER) return "下界";
        if (dim == net.minecraft.world.level.Level.END) return "末地";
        return "自定义维度";
    }

    /** 真实使用说明内容 */
    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("准备工作",
                "  §8├─ §f建好农田 §7(耕地或对应底盘)",
                "  §8├─ §f用 .farm set 农场点位1 / 农场点位2 框出矩形范围",
                "  §8├─ §f智能绑定单作物箱/种子补货箱/多作物箱 §7(单物品→单箱，双物品→种子+多箱)",
                "  §8└─ §f绑定杂物箱 §7(独立处理毒马铃薯与仙人掌花)"
            ),
            new HelpScreen.HelpSection("点位设置 §7(指令)",
                "  §8> §3.farm set 农场点位1 §8— §7准星对准农田对角起点",
                "  §8> §3.farm set 农场点位2 §8— §7准星对准农田对角终点",
                "  §8> §3.farm set 单作物箱 §8— §7准星对准箱子(单物品作物)",
                "  §8> §3.farm set 种子补货箱 §8— §7准星对准箱子(双物品种子)",
                "  §8> §3.farm set 多作物箱 §8— §7准星对准箱子(双物品成熟掉落物)",
                "  §8> §3.farm set 杂物箱 §8— §7准星对准箱子"
            ),
            new HelpScreen.HelpSection("作物选择",
                "  §a▸ §f最多同时启用 3 种作物",
                "  §a▸ §f普通作物 §8- §7小麦、胡萝卜、马铃薯、甜菜根、下界疣",
                "  §a▸ §f柱状物 §8- §7竹子、甘蔗、仙人掌",
                "  §a▸ §f果实 §8- §7南瓜、西瓜",
                "  §c▸ §f不支持 §7海带、甜浆果"
            ),
            new HelpScreen.HelpSection("单种子独立配置",
                "  §a▸ §f启用几种作物 §8- §7面板就自动显示几种独立配置",
                "  §a▸ §f卸货数量 §8- §7该作物产物超过此组数才卸入作物箱",
                "  §a▸ §f补货种子数量 §8- §7种植材料低于此组数自动补货",
                "  §e▸ §f双物品成熟掉落物卸入多作物箱 §8· §7其余卸入单作物箱"
            ),
            new HelpScreen.HelpSection("收割模式",
                "  §a▸ §f单个收割 §8- §7每次只收一个成熟目标，收完补种拾取后再观察",
                "  §a▸ §f批量收割 §8- §7一次锁定多个目标，但仍严格串行逐颗处理",
                "  §e▸ §f批量数量默认 8，范围 1~32，仅在批量模式下生效",
                "  §c▸ §f批量不是并发：任何时候都不会同时执行多个收割任务"
            ),
            new HelpScreen.HelpSection("工作流程",
                "  §a[0] §f锄地 §8→ §7范围内草方块/泥土自动锄成耕地",
                "  §a[1] §f观察 §8→ §7分帧扫描农场，发现成熟目标",
                "  §a[2] §f收割 §8→ §7熟一颗收一颗，验证后接补种/拾取",
                "  §a[3] §f补种 §8→ §7需要补种的作物自动播种",
                "  §a[4] §f拾取 §8→ §7就近等待掉落物进入背包",
                "  §a[5] §f物流 §8→ §7卸货/补货/杂物独立处理"
            ),
            new HelpScreen.HelpSection("注意事项",
                "  §c⚠ §f模块运行中无法修改点位，必须先关闭模块",
                "  §c⚠ §f已绑定点位不允许覆盖，必须先删除再重新设置",
                "  §c⚠ §f自动寻路依赖 Baritone，不可用时移动任务会失败并重新规划",
                "  §c⚠ §f关闭模块立即停止，重新开启会重新观察"
            )
        );
    }
}
