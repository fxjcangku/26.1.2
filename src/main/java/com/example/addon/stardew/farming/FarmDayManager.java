package com.example.addon.stardew.farming;

import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.config.StardewConfig;
import com.example.addon.stardew.model.CropRecognitionResult;
import com.example.addon.stardew.model.FertilizerResult;
import com.example.addon.stardew.model.GrowthStageResult;
import com.example.addon.stardew.model.SoilState;
import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewFertilizerProfile;
import com.example.addon.stardew.model.StardewSeedProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import com.example.addon.stardew.model.WaterState;
import com.example.addon.stardew.watering.WateringService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 星露谷「农场日管理器」：任务规划层（Observe → Decide），不替代状态机。
 *
 * <p>职责：扫描农田 → 判断状态 → 找成熟/缺水/缺肥/空地 → 规划任务。真正的执行仍由
 * {@link com.example.addon.stardew.task} 任务完成，模块层负责创建任务并推进状态机。</p>
 *
 * <p>安全原则：未知状态（UNKNOWN）不产生任何自动动作，宁可不做也不误做。</p>
 */
public final class FarmDayManager {

    private final FarmPlotScanner scanner;
    private final FarmPlotMemory memory;
    private final StardewServerAdapter adapter;
    private final WateringService watering;
    private final Supplier<StardewServerProfile> profileSupplier;
    private final Supplier<String> selectedSeedSupplier;

    private int planCursor;
    private long nowTick;

    public FarmDayManager(FarmPlotScanner scanner, FarmPlotMemory memory, StardewServerAdapter adapter,
                          WateringService watering, Supplier<StardewServerProfile> profileSupplier,
                          Supplier<String> selectedSeedSupplier) {
        this.scanner = scanner;
        this.memory = memory;
        this.adapter = adapter;
        this.watering = watering;
        this.profileSupplier = profileSupplier;
        this.selectedSeedSupplier = selectedSeedSupplier;
    }

    public FarmPlotScanner scanner() {
        return scanner;
    }

    public FarmPlotMemory memory() {
        return memory;
    }

    /** 推进扫描与记忆 TTL 清理 */
    public void observe() {
        nowTick++;
        scanner.tick();
        memory.prune(nowTick, StardewConfig.MEMORY_TTL_TICKS);
    }

    /**
     * 决策下一个动作（优先级：收割 → 浇水 → 施肥 → 种植，实际以开关控制）。
     * 每个候选在真正执行前都重新观察，记忆只作规划参考。
     */
    public Optional<FarmAction> plan(boolean allowHarvest, boolean allowWater,
                                     boolean allowFertilize, boolean allowPlant) {
        observe();

        List<BlockPos> plots = new ArrayList<>(scanner.plots());
        if (plots.isEmpty()) return Optional.empty();

        int budget = StardewConfig.PLAN_BUDGET_PER_TICK;
        for (int i = 0; i < plots.size() && budget > 0; i++) {
            budget--;
            int idx = (planCursor + i) % plots.size();
            BlockPos soil = plots.get(idx);
            Optional<FarmAction> action = assess(soil, allowHarvest, allowWater, allowFertilize, allowPlant);
            if (action.isPresent()) {
                planCursor = (idx + 1) % plots.size();
                return action;
            }
        }
        // 本轮没找到可做动作，游标前进一圈的预算步，避免一直从同一处开始
        planCursor = (planCursor + Math.min(StardewConfig.PLAN_BUDGET_PER_TICK, plots.size())) % plots.size();
        return Optional.empty();
    }

    /** 评估单块农田，产出可执行动作或空 */
    private Optional<FarmAction> assess(BlockPos soil, boolean allowHarvest, boolean allowWater,
                                        boolean allowFertilize, boolean allowPlant) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Optional.empty();

        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return Optional.empty();

        BlockPos cropPos = soil.above();
        BlockState cropState = mc.level.getBlockState(cropPos);
        BlockState soilBlock = mc.level.getBlockState(soil);

        SoilState soilState = adapter.detectSoilState(soil);
        CropRecognitionResult cropRec = adapter.recognizeCrop(cropState);
        WaterState waterState = adapter.detectWaterState(soil, soilBlock).state();
        FertilizerResult fert = adapter.detectFertilizer(soil);
        GrowthStageResult growth = adapter.detectGrowthStage(cropPos, cropState);

        // 刷新记忆（仅规划参考）
        memory.put(soil, new FarmPlotMemory.Entry(
            cropRec.known() ? cropRec.cropId() : null,
            seedIdFor(profile, cropRec.cropId()),
            growth.known() ? growth.stage() : -1,
            soilState == SoilState.MATURE,
            waterState,
            fert.known() && fert.fertilized(),
            nowTick));

        return switch (soilState) {
            case MATURE -> harvestAction(profile, cropRec, soil, allowHarvest);
            case EMPTY -> plantAction(profile, soil, allowPlant);
            case GROWING, PLANTED -> careAction(profile, cropRec, soil, cropState, fert, allowWater, allowFertilize);
            default -> Optional.empty(); // UNKNOWN 安全停止
        };
    }

    private Optional<FarmAction> harvestAction(StardewServerProfile profile, CropRecognitionResult cropRec,
                                               BlockPos soil, boolean allowHarvest) {
        if (!allowHarvest || !cropRec.known()) return Optional.empty();
        StardewCropProfile crop = profile.cropById(cropRec.cropId());
        if (crop == null) return Optional.empty();
        return Optional.of(new FarmAction(FarmAction.Kind.HARVEST, soil, crop, null, null));
    }

    private Optional<FarmAction> plantAction(StardewServerProfile profile, BlockPos soil, boolean allowPlant) {
        if (!allowPlant) return Optional.empty();

        String selected = selectedSeedSupplier.get();
        if (selected != null && !selected.isEmpty()) {
            StardewSeedProfile seed = profile.seedById(selected);
            if (seed != null && seed.enabled()) {
                StardewCropProfile crop = profile.cropById(seed.cropId());
                if (crop != null) {
                    return Optional.of(new FarmAction(FarmAction.Kind.PLANT, soil, crop, seed, null));
                }
            }
        }

        for (StardewSeedProfile seed : profile.seeds()) {
            if (!seed.enabled()) continue;
            StardewCropProfile crop = profile.cropById(seed.cropId());
            if (crop != null) {
                return Optional.of(new FarmAction(FarmAction.Kind.PLANT, soil, crop, seed, null));
            }
        }
        return Optional.empty();
    }

    private Optional<FarmAction> careAction(StardewServerProfile profile, CropRecognitionResult cropRec,
                                            BlockPos soil, BlockState cropState, FertilizerResult fert,
                                            boolean allowWater, boolean allowFertilize) {
        StardewCropProfile crop = cropRec.known() ? profile.cropById(cropRec.cropId()) : null;
        if (crop == null) return Optional.empty();

        if (allowWater && crop.wateringRequired() && watering.needsWater(soil)) {
            return Optional.of(new FarmAction(FarmAction.Kind.WATER, soil, crop, null, null));
        }

        if (allowFertilize && crop.fertilizerSupported()) {
            StardewFertilizerProfile fertilizer = fertilizerFor(profile, crop.cropId());
            // 未施肥（含未知，交由任务层安全判定）才施肥
            if (fertilizer != null && !(fert.known() && fert.fertilized())) {
                return Optional.of(new FarmAction(FarmAction.Kind.FERTILIZE, soil, crop, null, fertilizer));
            }
        }
        return Optional.empty();
    }

    private String seedIdFor(StardewServerProfile profile, String cropId) {
        if (cropId == null) return null;
        for (StardewSeedProfile seed : profile.seeds()) {
            if (cropId.equals(seed.cropId())) return seed.seedId();
        }
        return null;
    }

    private StardewFertilizerProfile fertilizerFor(StardewServerProfile profile, String cropId) {
        for (StardewFertilizerProfile fertilizer : profile.fertilizers()) {
            if (!fertilizer.enabled()) continue;
            if (cropId.equals(fertilizer.targetCropId())) return fertilizer;
        }
        return null;
    }
}
