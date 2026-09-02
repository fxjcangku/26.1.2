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
import com.example.addon.autofarm.model.SiteType;
import com.example.addon.autofarm.navigation.FarmNav;
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

/**
 * 自动农场 - 全自动农业系统（重写版）。
 *
 * 核心流程：Observe → Decide → Act → Verify → Replan。
 * 熟一颗收一颗，收割后验证、补种、拾取，物流任务（卸货/补货/毒马铃薯）独占，
 * 单/双/三作物箱按启用作物数量择一使用，毒马铃薯独立处理。
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
    private final SettingGroup sgLogistics = settings.createGroup("后勤设置", false);
    private final SettingGroup sgSafety = settings.createGroup("安全设置", false);
    private final SettingGroup sgRender = settings.createGroup("显示设置", false);

    // ─── 作物分类选择器 ───
    private final Setting<List<Block>> cropsDouble;
    private final Setting<List<Block>> cropsSingle;
    private final Setting<List<Block>> cropsPillar;
    private final Setting<List<Block>> cropsVine;

    // ─── 后勤配置 ───
    private final Setting<HarvestMode> harvestMode;
    private final Setting<Integer> batchCount;
    private final Setting<Integer> unloadThreshold;
    private final Setting<Integer> seedSafetyStock;
    private final Setting<Integer> bpt;
    private final Setting<Integer> reachDistance;

    // ─── 安全保护 ───
    private final Setting<Boolean> antiTrample;

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
    private final Setting<String> siteDual;
    private final Setting<String> siteTriple;
    private final Setting<String> sitePoison;

    /** 物流状态播报去重锁 */
    private String lastNotifiedState = "";

    /** 批量收割进度播报去重锁 */
    private String lastBatchProgress = "";

    public AutoFarmMatrix() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动农场",
            "熟一颗收一颗，自动补种拾取，单/双/三作物箱与毒马铃薯箱物流自动化。点击按钮查看说明。");

        // ─── 作物分类选择器 ───
        cropsDouble = sgCrops.add(new BlockListSetting.Builder()
            .name("双作物")
            .description("种子与产物分离：小麦、甜菜根")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.CROP
                    && profile.plantItem() != profile.harvestItem();
            })
            .build());

        cropsSingle = sgCrops.add(new BlockListSetting.Builder()
            .name("单作物")
            .description("产物即种子：胡萝卜、马铃薯、下界疣")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.CROP
                    && profile.plantItem() == profile.harvestItem();
            })
            .build());

        cropsPillar = sgCrops.add(new BlockListSetting.Builder()
            .name("柱状物")
            .description("切根部上方：竹子、甘蔗、仙人掌")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.PILLAR;
            })
            .build());

        cropsVine = sgCrops.add(new BlockListSetting.Builder()
            .name("果实")
            .description("只砍果实：南瓜、西瓜")
            .defaultValue(List.of())
            .filter(block -> {
                CropProfile profile = CropProfile.byBlock(block);
                return profile != null && profile.kind() == CropProfile.Kind.FRUIT;
            })
            .build());

        // ─── 后勤配置 ───
        harvestMode = sgLogistics.add(new EnumSetting.Builder<HarvestMode>()
            .name("收割模式")
            .description("单颗收割：每次只处理一个成熟目标；批量收割：一次锁定多个目标后严格串行执行")
            .defaultValue(HarvestMode.SINGLE)
            .onChanged(this::onHarvestModeChanged)
            .build());

        batchCount = sgLogistics.add(new IntSetting.Builder()
            .name("批量收割数量")
            .description("批量模式下一次最多锁定的成熟目标数（1~32），仅在批量收割模式下生效")
            .defaultValue(8).min(1).max(32).noSlider()
            .visible(() -> harvestMode.get() == HarvestMode.BATCH)
            .build());

        unloadThreshold = sgLogistics.add(new IntSetting.Builder()
            .name("卸货阈值")
            .description("背包可卸货物品满多少组时触发卸货")
            .defaultValue(20).min(1).max(36).noSlider()
            .build());

        seedSafetyStock = sgLogistics.add(new IntSetting.Builder()
            .name("种植材料安全库存")
            .description("每类作物种植材料截留多少组，卸货时保留、低于此值触发补货")
            .defaultValue(3).min(1).max(10).noSlider()
            .build());

        bpt = sgLogistics.add(new IntSetting.Builder()
            .name("发包速率(BPT)")
            .description("每 tick 最多发送多少个破坏/播种/容器操作包")
            .defaultValue(10).min(1).max(30).noSlider()
            .build());

        reachDistance = sgLogistics.add(new IntSetting.Builder()
            .name("收割距离")
            .description("能操作多远的方块，原版上限约 4.5 格")
            .defaultValue(4).min(1).max(8).noSlider()
            .build());

        // ─── 安全保护 ───
        antiTrample = sgSafety.add(new BoolSetting.Builder()
            .name("防踩踏")
            .description("农田范围内拦截跳跃键，避免踩坏耕地")
            .defaultValue(true)
            .build());

        // ─── 渲染辅助 ───
        renderBounds = sgRender.add(new BoolSetting.Builder()
            .name("农田边界")
            .description("渲染农场范围的大外接盒")
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
        siteDual = hiddenAnchor("_anchor_dual", "双作物箱");
        siteTriple = hiddenAnchor("_anchor_triple", "三作物箱");
        sitePoison = hiddenAnchor("_anchor_poison", "毒马铃薯箱");

        // 构建决策器与控制器（依赖上面的设置字段默认值）
        decision = new FarmDecision(scanner, resources, observer, verifier, broker,
            unloadThreshold.get(), bpt.get(), reachDistance.get());
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

        // 自检：作物数量、点位、维度、范围一次性列全
        if (!reportSelfCheck(selfCheck.check(getEnabledCrops(), getSitesMap()))) return;

        // 配置扫描器与资源管理器
        scanner.setEnabledCrops(getEnabledCrops());
        FarmSite start = site(SiteType.START);
        FarmSite end = site(SiteType.END);
        if (start != null && end != null) {
            scanner.setBounds(start.pos(), end.pos());
        }
        resources.configure(getEnabledCrops(), seedSafetyStock.get());

        // 重置状态并同步运行配置
        lastNotifiedState = "";
        lastBatchProgress = "";
        controller.configure(getEnabledCrops(), getSitesMap(), reachDistance.get());
        decision.update(unloadThreshold.get(), bpt.get(), reachDistance.get());
        decision.updateMode(harvestMode.get(), batchCount.get());

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

        // 专用作物箱
        SiteType storage = SiteType.cropStorageFor(enabled.size());
        report.append("\n§7作物箱　　§8▸ ").append(highlightText(storage == null ? "未配置" : storage.cn())).append("§r");

        // 农田范围
        FarmSite start = site(SiteType.START);
        FarmSite end = site(SiteType.END);
        if (start != null && end != null) {
            int rangeX = Math.abs(end.pos().getX() - start.pos().getX()) + 1;
            int rangeZ = Math.abs(end.pos().getZ() - start.pos().getZ()) + 1;
            report.append("\n§7农田范围　§8▸ ").append(highlightText(rangeX + "×" + rangeZ)).append("§r");
        }

        report.append("\n§7卸货阈值　§8▸ ").append(highlightText(unloadThreshold.get() + " 组")).append("§r")
            .append("§f · 安全库存 ").append(highlightText(seedSafetyStock.get() + " 组")).append("§r");

        String modeText = harvestMode.get() == HarvestMode.BATCH
            ? "批量收割 · " + batchCount.get() + " 个"
            : "单颗收割";
        report.append("\n§7收割模式　§8▸ ").append(highlightText(modeText)).append("§r");

        notify(report.toString());
    }

    @Override
    public void onDeactivate() {
        controller.reset();
        ContainerBroker.closeContainer();
        lastNotifiedState = "";
    }

    /** 收割模式切换时的中文提示（复用已有 notify 机制，不新建通知系统） */
    private void onHarvestModeChanged(HarvestMode mode) {
        if (mode == HarvestMode.BATCH) {
            notify("§b已切换为批量收割 §8▸ 每次最多锁定 " + highlightText(batchCount.get() + " 个目标"));
        } else {
            notify("§a已切换为单颗收割 §8▸ 每次只处理一个成熟目标");
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
        decision.update(unloadThreshold.get(), bpt.get(), reachDistance.get());
        decision.updateMode(harvestMode.get(), batchCount.get());
        resources.configure(getEnabledCrops(), seedSafetyStock.get());
        controller.configure(getEnabledCrops(), getSitesMap(), reachDistance.get());

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

        // 农场边界
        if (renderBounds.get()) {
            FarmRenderer.renderBounds(event, scanner.min(), scanner.max(),
                boundsColor.get(), boundsColor.get(), ShapeMode.Lines);
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
        renderLabel(event, SiteType.DUAL_STORAGE, "§b双作物箱");
        renderLabel(event, SiteType.TRIPLE_STORAGE, "§d三作物箱");
        renderLabel(event, SiteType.POISON_STORAGE, "§c毒马铃薯箱");
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
        // 静默容器：自动化运行中打开箱子屏幕时取消显示（不抢鼠标），
        // 排除背包界面，玩家手动按 E 打开背包必须放行。
        if (isActive() && event.screen instanceof AbstractContainerScreen<?>
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
            case DUAL_STORAGE -> siteDual.get();
            case TRIPLE_STORAGE -> siteTriple.get();
            case POISON_STORAGE -> sitePoison.get();
        };
    }

    private void setRawSite(SiteType type, String value) {
        switch (type) {
            case START -> siteStart.set(value);
            case END -> siteEnd.set(value);
            case SINGLE_STORAGE -> siteSingle.set(value);
            case DUAL_STORAGE -> siteDual.set(value);
            case TRIPLE_STORAGE -> siteTriple.set(value);
            case POISON_STORAGE -> sitePoison.set(value);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════════

    /** 从四组作物选择器汇总启用作物集合 */
    private Set<CropProfile> getEnabledCrops() {
        Set<CropProfile> enabled = EnumSet.noneOf(CropProfile.class);
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
            WButton helpBtn = theme.button("§e查看使用说明");
            helpBtn.action = () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();

            // 六点位卡片（两列三行）
            WTable row1 = theme.table();
            buildLocationCard(theme, row1, "农场点位1", SiteType.START, "§a");
            buildLocationCard(theme, row1, "农场点位2", SiteType.END, "§e");
            table.add(row1).expandX();
            table.row();

            WTable row2 = theme.table();
            buildLocationCard(theme, row2, "单作物箱", SiteType.SINGLE_STORAGE, "§6");
            buildLocationCard(theme, row2, "双作物箱", SiteType.DUAL_STORAGE, "§b");
            table.add(row2).expandX();
            table.row();

            WTable row3 = theme.table();
            buildLocationCard(theme, row3, "三作物箱", SiteType.TRIPLE_STORAGE, "§d");
            buildLocationCard(theme, row3, "毒马铃薯箱", SiteType.POISON_STORAGE, "§c");
            table.add(row3).expandX();
            table.row();
        });
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
                "  §8├─ §f按启用作物数量绑定对应作物箱 §7(单/双/三作物箱)",
                "  §8└─ §f绑定毒马铃薯箱 §7(独立处理毒马铃薯)"
            ),
            new HelpScreen.HelpSection("点位设置 §7(指令)",
                "  §8> §3.farm set 农场点位1 §8— §7准星对准农田对角起点",
                "  §8> §3.farm set 农场点位2 §8— §7准星对准农田对角终点",
                "  §8> §3.farm set 单作物箱 §8— §7准星对准箱子",
                "  §8> §3.farm set 双作物箱 §8— §7准星对准箱子",
                "  §8> §3.farm set 三作物箱 §8— §7准星对准箱子",
                "  §8> §3.farm set 毒马铃薯箱 §8— §7准星对准箱子"
            ),
            new HelpScreen.HelpSection("作物选择",
                "  §a▸ §f最多同时启用 3 种作物",
                "  §a▸ §f普通作物 §8- §7小麦、胡萝卜、马铃薯、甜菜根、下界疣",
                "  §a▸ §f柱状物 §8- §7竹子、甘蔗、仙人掌",
                "  §a▸ §f果实 §8- §7南瓜、西瓜",
                "  §c▸ §f不支持 §7海带、甜浆果"
            ),
            new HelpScreen.HelpSection("收割模式",
                "  §a▸ §f单颗收割 §8- §7每次只收一个成熟目标，收完补种拾取后再观察",
                "  §a▸ §f批量收割 §8- §7一次锁定多个目标，但仍严格串行逐颗处理",
                "  §e▸ §f批量数量默认 8，范围 1~32，仅在批量模式下生效",
                "  §c▸ §f批量不是并发：任何时候都不会同时执行多个收割任务"
            ),
            new HelpScreen.HelpSection("工作流程",
                "  §a[1] §f观察 §8→ §7分帧扫描农场，发现成熟目标",
                "  §a[2] §f收割 §8→ §7熟一颗收一颗，验证后接补种/拾取",
                "  §a[3] §f补种 §8→ §7需要补种的作物自动播种",
                "  §a[4] §f拾取 §8→ §7就近等待掉落物进入背包",
                "  §a[5] §f物流 §8→ §7卸货/补货/毒马铃薯独立处理"
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
