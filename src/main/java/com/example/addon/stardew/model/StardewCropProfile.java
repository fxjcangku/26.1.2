package com.example.addon.stardew.model;

import com.example.addon.stardew.persistence.StardewJsonUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 星露谷作物档案：星露谷模式的核心数据模型之一。
 *
 * <p>成熟判断不写死在代码里，而是由 {@link #matureBlockIds()} + 可选属性规则描述，
 * 支持不同服务器的方块 ID / BlockState / 生长阶段差异。识别不出成熟状态时必须安全停止，
 * 绝不误收。</p>
 */
public final class StardewCropProfile {

    private final String cropId;
    private final String displayName;
    private final int growthStages;
    private final String matureState;
    private final String harvestBehavior;
    private final String replantBehavior;
    private final boolean wateringRequired;
    private final boolean fertilizerSupported;
    private final String seedId;
    private final List<String> cropBlockIds;
    private final List<String> matureBlockIds;
    private final String maturePropertyName;
    private final String maturePropertyValue;

    public StardewCropProfile(String cropId, String displayName, int growthStages, String matureState,
                              String harvestBehavior, String replantBehavior, boolean wateringRequired,
                              boolean fertilizerSupported, String seedId, List<String> cropBlockIds,
                              List<String> matureBlockIds, String maturePropertyName, String maturePropertyValue) {
        this.cropId = cropId;
        this.displayName = displayName;
        this.growthStages = Math.max(1, growthStages);
        this.matureState = matureState;
        this.harvestBehavior = harvestBehavior;
        this.replantBehavior = replantBehavior;
        this.wateringRequired = wateringRequired;
        this.fertilizerSupported = fertilizerSupported;
        this.seedId = seedId;
        this.cropBlockIds = cropBlockIds == null ? new ArrayList<>() : new ArrayList<>(cropBlockIds);
        this.matureBlockIds = matureBlockIds == null ? new ArrayList<>() : new ArrayList<>(matureBlockIds);
        this.maturePropertyName = maturePropertyName;
        this.maturePropertyValue = maturePropertyValue;
    }

    public String cropId() {
        return cropId;
    }

    public String displayName() {
        return displayName;
    }

    public int growthStages() {
        return growthStages;
    }

    public String matureState() {
        return matureState;
    }

    public String harvestBehavior() {
        return harvestBehavior;
    }

    public String replantBehavior() {
        return replantBehavior;
    }

    public boolean wateringRequired() {
        return wateringRequired;
    }

    public boolean fertilizerSupported() {
        return fertilizerSupported;
    }

    public String seedId() {
        return seedId;
    }

    /** 该作物任一生长阶段对应的方块 ID 集合 */
    public List<String> cropBlockIds() {
        return cropBlockIds;
    }

    /** 该作物成熟状态的方块 ID 集合（未配置则退化为属性规则或安全未知） */
    public List<String> matureBlockIds() {
        return matureBlockIds;
    }

    /** 成熟判定用的 BlockState 属性名（如 age），无则为 null */
    public String maturePropertyName() {
        return maturePropertyName;
    }

    /** 成熟判定用的 BlockState 属性值（与 {@link #maturePropertyName()} 配合），无则为 null */
    public String maturePropertyValue() {
        return maturePropertyValue;
    }

    /** 判断某个方块 ID 是否属于该作物（任一生长阶段） */
    public boolean matchesBlock(String blockId) {
        return cropBlockIds.contains(blockId);
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        StardewJsonUtil.put(obj, "作物ID", cropId);
        StardewJsonUtil.put(obj, "显示名称", displayName);
        StardewJsonUtil.put(obj, "生长阶段", growthStages);
        StardewJsonUtil.put(obj, "成熟状态", matureState);
        StardewJsonUtil.put(obj, "收割行为", harvestBehavior);
        StardewJsonUtil.put(obj, "补种行为", replantBehavior);
        StardewJsonUtil.put(obj, "需要浇水", wateringRequired);
        StardewJsonUtil.put(obj, "支持施肥", fertilizerSupported);
        StardewJsonUtil.put(obj, "种子ID", seedId);
        StardewJsonUtil.put(obj, "作物方块", cropBlockIds);
        StardewJsonUtil.put(obj, "成熟方块", matureBlockIds);
        StardewJsonUtil.put(obj, "成熟属性名", maturePropertyName);
        StardewJsonUtil.put(obj, "成熟属性值", maturePropertyValue);
        return obj;
    }

    public static StardewCropProfile fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String cropId = StardewJsonUtil.str(obj, "作物ID", null);
        if (cropId == null || cropId.isBlank()) return null;
        return new StardewCropProfile(
            cropId,
            StardewJsonUtil.str(obj, "显示名称", cropId),
            StardewJsonUtil.integer(obj, "生长阶段", 1),
            StardewJsonUtil.str(obj, "成熟状态", null),
            StardewJsonUtil.str(obj, "收割行为", "破坏"),
            StardewJsonUtil.str(obj, "补种行为", "重新种植"),
            StardewJsonUtil.bool(obj, "需要浇水", false),
            StardewJsonUtil.bool(obj, "支持施肥", false),
            StardewJsonUtil.str(obj, "种子ID", null),
            StardewJsonUtil.strList(obj, "作物方块"),
            StardewJsonUtil.strList(obj, "成熟方块"),
            StardewJsonUtil.str(obj, "成熟属性名", null),
            StardewJsonUtil.str(obj, "成熟属性值", null)
        );
    }
}
