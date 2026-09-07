package com.example.addon.stardew;

import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.autofarm.task.UnloadTask;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.farm.ContainerBroker;
import com.example.addon.itemid.ItemIdManager;
import com.example.addon.itemid.ItemIdentity;
import com.example.addon.stardew.adapter.DefaultStardewServerAdapter;
import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.config.StardewConfig;
import com.example.addon.stardew.config.StardewSiteType;
import com.example.addon.stardew.debug.StardewDebugLogger;
import com.example.addon.stardew.farming.FarmAction;
import com.example.addon.stardew.farming.FarmDayManager;
import com.example.addon.stardew.farming.FarmPlotMemory;
import com.example.addon.stardew.farming.FarmPlotScanner;
import com.example.addon.stardew.integration.IdSystemIntegration;
import com.example.addon.stardew.integration.ResourceManagerIntegration;
import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewFarmState;
import com.example.addon.stardew.model.StardewFertilizerProfile;
import com.example.addon.stardew.model.StardewSeedProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import com.example.addon.stardew.model.WateringToolProfile;
import com.example.addon.stardew.persistence.StardewJsonRepository;
import com.example.addon.stardew.recognition.CropRecognizer;
import com.example.addon.stardew.recognition.FertilizerRecognizer;
import com.example.addon.stardew.recognition.GrowthStageRecognizer;
import com.example.addon.stardew.recognition.MatureStateRecognizer;
import com.example.addon.stardew.recognition.RuntimeCropRecognizer;
import com.example.addon.stardew.recognition.RuntimeFertilizerRecognizer;
import com.example.addon.stardew.recognition.RuntimeGrowthStageRecognizer;
import com.example.addon.stardew.recognition.RuntimeMatureStateRecognizer;
import com.example.addon.stardew.recognition.RuntimeSeedRecognizer;
import com.example.addon.stardew.recognition.RuntimeSoilRecognizer;
import com.example.addon.stardew.recognition.RuntimeSprinklerRecognizer;
import com.example.addon.stardew.recognition.SeedRecognizer;
import com.example.addon.stardew.recognition.SoilRecognizer;
import com.example.addon.stardew.recognition.SprinklerRecognizer;
import com.example.addon.stardew.task.StardewFertilizeTask;
import com.example.addon.stardew.task.StardewHarvestTask;
import com.example.addon.stardew.task.StardewPlantTask;
import com.example.addon.stardew.task.StardewRestockTask;
import com.example.addon.stardew.task.StardewTaskSupport;
import com.example.addon.stardew.task.StardewWaterTask;
import com.example.addon.stardew.ui.StardewSeedSelector;
import com.example.addon.stardew.watering.WateringPlanner;
import com.example.addon.stardew.watering.WateringService;
import com.example.addon.ui.HelpScreen;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 星露谷农场：与原版自动农场平级的自动化模式，共享 Observe → Decide → Act → Verify → Replan 思路，
 * 复用现有 TaskResult / FarmTask / ContainerBroker / UnloadTask / ID 三件套 / 卸货补货体系。
 *
 * <p>星露谷逻辑独立在本包，绝不侵入原版自动农场；资源包为增强层，当前无资源包也能基于
 * 运行时物品 ID + 手动配置运行。</p>
 */
public final class StardewFarmModule extends YiyiaddonModule {

    // ── 服务实例 ──
    private final ItemIdManager idManager;
    private final ContainerBroker broker = new ContainerBroker();
    private final StardewJsonRepository repo = new StardewJsonRepository();
    private final StardewServerAdapter adapter;
    private final WateringService watering;
    private final FarmPlotScanner scanner;
    private final FarmPlotMemory memory = new FarmPlotMemory();
    private final FarmDayManager dayManager;
    private final ResourceManagerIntegration resources;
    private final IdSystemIntegration idIntegration;
    private final StardewDebugLogger debug;

    private StardewServerProfile profile;
    private FarmTask currentTask;
    private StardewFarmState state = StardewFarmState.OBSERVE;
    private String lastNotifiedState = "";
    private Boolean prevPauseOnLostFocus = null;

    // ═══════════════════════════════════════════════════════════════════
    //  UI 配置
    // ═══════════════════════════════════════════════════════════════════

    private final SettingGroup sgAuto = settings.createGroup("自动化开关", true);
    private final SettingGroup sgRun = settings.createGroup("运行参数", false);

    private final Setting<Boolean> autoHarvest;
    private final Setting<Boolean> autoPlant;
    private final Setting<Boolean> autoWater;
    private final Setting<Boolean> autoFertilize;
    private final Setting<Boolean> autoRestock;
    private final Setting<Boolean> autoUnload;
    private final Setting<Boolean> debugLog;

    private final Setting<Integer> reachDistance;
    private final Setting<Integer> bpt;
    private final Setting<Integer> restockGroups;
    private final Setting<Integer> unloadGroups;

    private final Setting<String> selectedSeed;
    private final Setting<String> siteStart;
    private final Setting<String> siteEnd;
    private final Setting<String> siteSeed;
    private final Setting<String> siteHarvest;

    public StardewFarmModule(ItemIdManager idManager) {
        super(AddonTemplate.CATEGORY_AUTOMATION, "星露谷农场",
            "适配不同 Minecraft 星露谷服务器的自动农场：种子/作物/成熟/浇水/施肥/洒水器全部配置化，复用现有 ID 与卸货补货体系。");

        this.idManager = idManager;
        this.debug = new StardewDebugLogger(this::notify);

        // ── 自动化开关 ──
        autoHarvest = sgAuto.add(new BoolSetting.Builder()
            .name("自动收割").description("发现确定成熟的作物时自动收割").defaultValue(true).build());
        autoPlant = sgAuto.add(new BoolSetting.Builder()
            .name("自动种植").description("发现空地时自动种植选中的种子").defaultValue(true).build());
        autoWater = sgAuto.add(new BoolSetting.Builder()
            .name("自动浇水").description("发现干旱土地时自动浇水（需服务器浇水规则）").defaultValue(false).build());
        autoFertilize = sgAuto.add(new BoolSetting.Builder()
            .name("自动施肥").description("发现未施肥土地时自动施肥（需服务器施肥规则）").defaultValue(false).build());
        autoRestock = sgAuto.add(new BoolSetting.Builder()
            .name("自动补货").description("种子低于安全库存时自动从种子箱补货").defaultValue(true).build());
        autoUnload = sgAuto.add(new BoolSetting.Builder()
            .name("自动卸货").description("种子超过安全库存时自动卸回种子箱").defaultValue(true).build());
        debugLog = sgAuto.add(new BoolSetting.Builder()
            .name("调试日志").description("记录未知种子/作物/农田/成熟状态，帮助定位缺配项").defaultValue(false)
            .onChanged(debug::setEnabled).build());

        // ── 运行参数 ──
        reachDistance = sgRun.add(new IntSetting.Builder()
            .name("操作距离").description("能操作多远的方块").defaultValue(4).min(3).max(8).noSlider().build());
        bpt = sgRun.add(new IntSetting.Builder()
            .name("发包速率(BPT)").description("每 tick 最多发送多少个交互/容器操作包").defaultValue(10).min(1).max(30).noSlider().build());
        restockGroups = sgRun.add(new IntSetting.Builder()
            .name("补货种子数量(组)").description("种子低于此组数自动补货，卸货时始终保留").defaultValue(StardewConfig.DEFAULT_RESTOCK_GROUPS).min(1).max(10).noSlider().build());
        unloadGroups = sgRun.add(new IntSetting.Builder()
            .name("卸货数量(组)").description("种子超过此组数才卸回种子箱").defaultValue(StardewConfig.DEFAULT_UNLOAD_GROUPS).min(1).max(36).noSlider().build());

        // ── 种子选择（由种子选择器按钮联动） ──
        selectedSeed = sgRun.add(new StringSetting.Builder()
            .name("当前种子").description("自动种植时使用的种子 ID（点击「种子选择器」选择）").defaultValue("").build());

        // ── 四点位（隐藏，由指令管理） ──
        siteStart = hiddenAnchor("_stardew_start", "农场点位1");
        siteEnd = hiddenAnchor("_stardew_end", "农场点位2");
        siteSeed = hiddenAnchor("_stardew_seed", "种子箱");
        siteHarvest = hiddenAnchor("_stardew_harvest", "收获箱");

        // ── 构建识别与规划服务 ──
        java.util.function.Supplier<StardewServerProfile> profileSupplier = this::currentProfile;
        SeedRecognizer seedRecognizer = new RuntimeSeedRecognizer(profileSupplier, idManager);
        CropRecognizer cropRecognizer = new RuntimeCropRecognizer(profileSupplier);
        GrowthStageRecognizer growthRecognizer = new RuntimeGrowthStageRecognizer(profileSupplier);
        MatureStateRecognizer matureRecognizer = new RuntimeMatureStateRecognizer(profileSupplier);
        FertilizerRecognizer fertilizerRecognizer = new RuntimeFertilizerRecognizer();
        SprinklerRecognizer sprinklerRecognizer = new RuntimeSprinklerRecognizer(profileSupplier);
        SoilRecognizer soilRecognizer = new RuntimeSoilRecognizer(profileSupplier, cropRecognizer, matureRecognizer);

        this.adapter = new DefaultStardewServerAdapter(seedRecognizer, cropRecognizer, growthRecognizer,
            matureRecognizer, fertilizerRecognizer, sprinklerRecognizer, soilRecognizer);
        this.watering = new WateringPlanner(adapter, profileSupplier);
        this.scanner = new FarmPlotScanner(adapter);
        this.dayManager = new FarmDayManager(scanner, memory, adapter, watering, profileSupplier, this::selectedSeedId);
        this.resources = new ResourceManagerIntegration(broker);
        this.idIntegration = new IdSystemIntegration(idManager);
    }

    private Setting<String> hiddenAnchor(String name, String desc) {
        return settings.getDefaultGroup().add(new StringSetting.Builder()
            .name(name).description("内部使用：" + desc).defaultValue(FarmSite.UNBOUND).visible(() -> false).build());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  对外访问（UI / 指令调用）
    // ═══════════════════════════════════════════════════════════════════

    public StardewServerProfile currentProfile() {
        return profile;
    }

    public StardewJsonRepository repository() {
        return repo;
    }

    public String selectedSeedId() {
        return selectedSeed.get();
    }

    public void selectSeed(String seedId) {
        selectedSeed.set(seedId);
        notify("已选择种子 §a" + seedId);
    }

    public void addSeedFromHeld() {
        if (mc.player == null) {
            notifyError("必须在游戏内操作。");
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            notifyError("主手未持有物品，请手持服务器种子后再添加。");
            return;
        }

        ItemIdentity identity = idIntegration.registerFromStack(held);
        if (identity == null) {
            notifyError("无法识别该物品，请确认物品有效。");
            return;
        }

        StardewServerProfile p = ensureProfile();
        for (StardewSeedProfile seed : p.seeds()) {
            if (seed.minecraftItemId().equals(identity.itemId())) {
                notify("该物品已添加为种子 §a" + seed.displayName());
                return;
            }
        }

        String seedId = "seed_" + identity.identityKey().replaceAll("[^a-zA-Z0-9_]", "_");
        String cropId = "crop_" + seedId;

        StardewSeedProfile seed = new StardewSeedProfile(seedId, identity.displayName(), identity.itemId(),
            identity.dataComponents(), identity.identityKey(), cropId, null, true, p.profileId());
        StardewCropProfile crop = new StardewCropProfile(cropId, identity.displayName() + "作物", 1, null,
            "破坏", "重新种植", false, false, seedId, new ArrayList<>(), new ArrayList<>(), null, null);

        p.seeds().add(seed);
        p.crops().add(crop);
        repo.save(p);
        selectedSeed.set(seedId);

        notify("§a✓ 已添加种子 " + seed.displayName() + " §8▸ 需在档案 JSON 配置作物方块/成熟规则后方可识别");
    }

    public void removeSeed(String seedId) {
        StardewServerProfile p = ensureProfile();
        StardewSeedProfile seed = p.seedById(seedId);
        if (seed == null) return;
        p.seeds().remove(seed);
        StardewCropProfile crop = p.cropById(seed.cropId());
        if (crop != null) p.crops().remove(crop);
        repo.save(p);
        notify("已删除种子 §a" + seed.displayName());
    }

    /** 把准星命中的方块登记为星露谷农田底盘（供无资源包手动配置） */
    public void addSoilFromCrosshair() {
        if (mc.level == null) {
            notifyError("当前不在游戏世界。");
            return;
        }
        BlockPos target = targetBlock();
        if (target == null) {
            notifyError("准星未对准方块。");
            return;
        }
        StardewServerProfile p = ensureProfile();
        String blockId = com.example.addon.stardew.recognition.RecognizerSupport.blockId(target);
        if (p.soilBlockIds().contains(blockId)) {
            notify("该方块已是农田底盘 §a" + blockId);
            return;
        }
        p.soilBlockIds().add(blockId);
        repo.save(p);
        notify("§a✓ 已登记农田底盘 " + blockId);
    }

    private StardewServerProfile ensureProfile() {
        if (profile == null) {
            profile = new StardewServerProfile(StardewConfig.DEFAULT_PROFILE_ID, "默认服务器",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            repo.save(profile);
        }
        return profile;
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

        // 加载档案
        if (profile == null) {
            profile = repo.loadDefault();
            if (profile == null) ensureProfile();
        }

        if (!reportSelfCheck(selfCheck())) return;

        if (prevPauseOnLostFocus == null) prevPauseOnLostFocus = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false;

        FarmSite start = site(StardewSiteType.START);
        FarmSite end = site(StardewSiteType.END);
        if (start != null && end != null) scanner.setBounds(start.pos(), end.pos());
        scanner.fullScan();

        lastNotifiedState = "";
        setState(StardewFarmState.OBSERVE);
        reportStartupInfo();
    }

    @Override
    public void onDeactivate() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        FarmNav.cancel();
        ContainerBroker.closeContainer();
        broker.reset();
        scanner.reset();
        memory.clear();
        lastNotifiedState = "";
        setState(StardewFarmState.OBSERVE);

        if (prevPauseOnLostFocus != null) {
            mc.options.pauseOnLostFocus = prevPauseOnLostFocus;
            prevPauseOnLostFocus = null;
        }
    }

    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();
        StardewServerProfile p = currentProfile();
        if (p == null) {
            missing.add("服务器档案未加载");
            return missing;
        }
        boolean hasSeed = p.seeds().stream().anyMatch(StardewSeedProfile::enabled);
        if (!hasSeed) missing.add("未添加任何启用的种子（点击「种子选择器」添加）");
        if (p.soilBlockIds().isEmpty()) missing.add("未配置农田方块（用 .stardew soiladd 登记，或编辑档案 JSON）");
        boolean hasCrop = p.crops().stream().anyMatch(c -> !c.cropBlockIds().isEmpty());
        if (!hasCrop) missing.add("未配置作物可识别方块（需在档案 JSON 填写作物方块/成熟方块）");
        if (site(StardewSiteType.START) == null || site(StardewSiteType.END) == null)
            missing.add("农田范围未绑定（.stardew set 农场点位1/2）");
        if (autoRestock.get() && site(StardewSiteType.SEED_STORAGE) == null)
            missing.add("种子箱未绑定（自动补货需要）");
        if (autoUnload.get() && site(StardewSiteType.HARVEST_STORAGE) == null)
            missing.add("收获箱未绑定（自动卸货需要）");
        return missing;
    }

    private void reportStartupInfo() {
        StardewServerProfile p = currentProfile();
        int seeds = p == null ? 0 : (int) p.seeds().stream().filter(StardewSeedProfile::enabled).count();
        int crops = p == null ? 0 : (int) p.crops().stream().filter(c -> !c.cropBlockIds().isEmpty()).count();
        int soils = p == null ? 0 : p.soilBlockIds().size();
        notify("§a§l✓ 星露谷农场 · 启动报告\n"
            + "§7种子 §8▸ " + highlightNumber(String.valueOf(seeds))
            + " §8│ §7作物 §8▸ " + highlightNumber(String.valueOf(crops))
            + " §8│ §7农田方块 §8▸ " + highlightNumber(String.valueOf(soils)));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  事件处理（状态机）
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (!worldReady() || !playerAlive()) {
            if (currentTask != null) {
                currentTask.cancel();
                currentTask = null;
            }
            FarmNav.cancel();
            setState(StardewFarmState.OBSERVE);
            return;
        }

        broker.tick();

        if (currentTask != null) {
            TaskResult result = currentTask.tick();
            if (result.done()) {
                FarmTask finished = currentTask;
                currentTask = null;
                handleResult(finished, result);
            }
            return;
        }

        FarmTask logistics = decideLogistics();
        if (logistics != null) {
            currentTask = logistics;
            setState(stateFor(logistics));
            return;
        }

        Optional<FarmAction> action = dayManager.plan(autoHarvest.get(), autoWater.get(),
            autoFertilize.get(), autoPlant.get());
        if (action.isPresent()) {
            FarmTask task = taskFor(action.get());
            if (task != null) {
                currentTask = task;
                setState(stateFor(task));
                return;
            }
        }
        setState(StardewFarmState.OBSERVE);
    }

    private boolean worldReady() {
        return mc.player != null && mc.level != null && mc.getConnection() != null;
    }

    private boolean playerAlive() {
        return mc.player != null && !mc.player.isDeadOrDying();
    }

    private FarmTask decideLogistics() {
        StardewServerProfile p = currentProfile();
        if (p == null) return null;

        // 补货优先
        if (autoRestock.get()) {
            FarmSite seedBox = site(StardewSiteType.SEED_STORAGE);
            if (seedBox != null) {
                for (StardewSeedProfile seed : p.seeds()) {
                    if (!seed.enabled()) continue;
                    Item item = StardewTaskSupport.resolveItem(seed.minecraftItemId());
                    if (item == null) continue;
                    int safety = restockGroups.get() * 64;
                    if (resources.countItem(item) < safety) {
                        return new StardewRestockTask(seedBox.pos(), broker, reachDistance.get(), item, safety,
                            () -> resources.countItem(item));
                    }
                }
            }
        }

        // 卸货：超出安全库存的种子卸回种子箱
        if (autoUnload.get()) {
            FarmSite seedBox = site(StardewSiteType.SEED_STORAGE);
            if (seedBox != null) {
                int safety = restockGroups.get() * 64;
                Predicate<ItemStack> deposit = stack -> {
                    for (StardewSeedProfile seed : p.seeds()) {
                        if (!seed.enabled()) continue;
                        Item item = StardewTaskSupport.resolveItem(seed.minecraftItemId());
                        if (item != null && stack.is(item) && resources.countItem(item) > safety) return true;
                    }
                    return false;
                };
                if (resources.hasDepositable(deposit)) {
                    return new UnloadTask(seedBox.pos(), broker, reachDistance.get(), bpt.get(), deposit);
                }
            }
        }
        return null;
    }

    private FarmTask taskFor(FarmAction action) {
        return switch (action.kind()) {
            case HARVEST -> new StardewHarvestTask(action.soilPos(), action.crop(), adapter, reachDistance.get());
            case PLANT -> {
                if (action.seed() == null) yield null;
                Item seedItem = StardewTaskSupport.resolveItem(action.seed().minecraftItemId());
                yield seedItem == null ? null : new StardewPlantTask(action.soilPos(), seedItem, adapter, reachDistance.get());
            }
            case WATER -> {
                WateringToolProfile tool = watering.getCurrentWateringTool().orElse(null);
                if (tool == null) yield null;
                Item toolItem = idIntegration.resolveItem(tool.itemIdentityId());
                if (toolItem == null) toolItem = StardewTaskSupport.resolveItem(tool.itemIdentityId());
                yield toolItem == null ? null : new StardewWaterTask(action.soilPos(), toolItem, adapter, reachDistance.get());
            }
            case FERTILIZE -> {
                StardewFertilizerProfile fertilizer = action.fertilizer();
                if (fertilizer == null) yield null;
                Item fertItem = idIntegration.resolveItem(fertilizer.itemIdentityId());
                if (fertItem == null) fertItem = StardewTaskSupport.resolveItem(fertilizer.itemIdentityId());
                yield fertItem == null ? null : new StardewFertilizeTask(action.soilPos(), fertItem, adapter, reachDistance.get());
            }
        };
    }

    private void handleResult(FarmTask task, TaskResult result) {
        if (task instanceof StardewHarvestTask harvest) {
            memory.remove(harvest.soilPos());
        } else if (task instanceof StardewPlantTask plant) {
            memory.remove(plant.soilPos());
        } else if (task instanceof StardewRestockTask restock) {
            if (result == TaskResult.CONTAINER_EMPTY) notify("§e⚠ 种子箱无货 §8▸ 已跳过补货");
            else if (result.ok()) notify("§a✓ 补货完成");
            else notify("§c✗ 补货失败");
        } else if (task instanceof UnloadTask) {
            if (result.ok()) notify("§a✓ 卸货完成");
            else if (result != TaskResult.CONTAINER_FULL) notify("§c✗ 卸货失败");
        }
        setState(StardewFarmState.OBSERVE);
    }

    private StardewFarmState stateFor(FarmTask task) {
        if (task instanceof StardewHarvestTask) return StardewFarmState.HARVEST;
        if (task instanceof StardewPlantTask) return StardewFarmState.PLANT;
        if (task instanceof StardewWaterTask) return StardewFarmState.WATER;
        if (task instanceof StardewFertilizeTask) return StardewFarmState.FERTILIZE;
        if (task instanceof StardewRestockTask) return StardewFarmState.RESTOCK;
        if (task instanceof UnloadTask) return StardewFarmState.UNLOAD;
        return StardewFarmState.OBSERVE;
    }

    private void setState(StardewFarmState newState) {
        this.state = newState;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null) return;
        FarmTask task = currentTask;
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

    public FarmSite site(StardewSiteType type) {
        return FarmSite.parse(rawSite(type));
    }

    public void bindSite(StardewSiteType type, FarmSite site) {
        setRawSite(type, site.serialize());
        if (type == StardewSiteType.START || type == StardewSiteType.END) {
            FarmSite start = site(StardewSiteType.START);
            FarmSite end = site(StardewSiteType.END);
            if (start != null && end != null) scanner.setBounds(start.pos(), end.pos());
        }
    }

    public void clearSite(StardewSiteType type) {
        setRawSite(type, FarmSite.UNBOUND);
    }

    public void clearAllSites() {
        for (StardewSiteType type : StardewSiteType.values()) setRawSite(type, FarmSite.UNBOUND);
        scanner.reset();
    }

    private String rawSite(StardewSiteType type) {
        return switch (type) {
            case START -> siteStart.get();
            case END -> siteEnd.get();
            case SEED_STORAGE -> siteSeed.get();
            case HARVEST_STORAGE -> siteHarvest.get();
        };
    }

    private void setRawSite(StardewSiteType type, String value) {
        switch (type) {
            case START -> siteStart.set(value);
            case END -> siteEnd.set(value);
            case SEED_STORAGE -> siteSeed.set(value);
            case HARVEST_STORAGE -> siteHarvest.set(value);
        }
    }

    private BlockPos targetBlock() {
        var hit = mc.hitResult;
        if (hit == null || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  配置面板
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            table.add(theme.label("§b§l星露谷农场 §r§8▸ §f与原版自动农场平级的通用模式")).expandX();
            table.row();
            table.add(theme.label(statusSummary())).expandX();
            table.row();
            table.add(theme.label(" ")).expandX();
            table.row();

            addUniformButton(theme, table, "§a种子选择器",
                () -> mc.setScreen(new StardewSeedSelector(theme, this)));
            table.row();
            addUniformButton(theme, table, "§e查看使用说明",
                () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent())));
        });
    }

    private String statusSummary() {
        StardewServerProfile p = currentProfile();
        int seeds = p == null ? 0 : p.seeds().size();
        int soils = p == null ? 0 : p.soilBlockIds().size();
        return "§7种子 §8▸ " + highlightNumber(String.valueOf(seeds))
            + " §8│ §7农田方块 §8▸ " + highlightNumber(String.valueOf(soils))
            + " §8│ §7状态 §8▸ " + highlightFunction(state.cn());
    }

    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("准备",
                "  §8├─ §f手持服务器种子，点击「种子选择器」→「添加当前手持物品为种子」",
                "  §8├─ §f用 §3.stardew soiladd §7登记准星对准的农田底盘方块",
                "  §8├─ §f编辑档案 JSON 配置作物方块/成熟方块/成熟属性",
                "  §8└─ §f用 §3.stardew set 农场点位1/2 §7框出农田范围"
            ),
            new HelpScreen.HelpSection("点位设置",
                "  §8> §3.stardew set 农场点位1 §8— §7准星对准农田对角起点",
                "  §8> §3.stardew set 农场点位2 §8— §7准星对准农田对角终点",
                "  §8> §3.stardew set 种子箱 §8— §7准星对准种子补货箱",
                "  §8> §3.stardew set 收获箱 §8— §7准星对准收获箱"
            ),
            new HelpScreen.HelpSection("档案位置",
                "  §a▸ §f每个服务器一份档案，位于 §e.stardew/§f 目录",
                "  §a▸ §f不同服务器可分别配置种子/作物/农田/浇水/施肥规则"
            ),
            new HelpScreen.HelpSection("安全原则",
                "  §c⚠ §f未知成熟状态绝不收割，宁可不收也不误收",
                "  §c⚠ §f未知浇水/施肥状态绝不盲目浇水/施肥",
                "  §c⚠ §f资源包为增强层，缺失时仍可基于物品 ID 手动配置运行"
            )
        );
    }
}
