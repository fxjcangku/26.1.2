package com.example.addon.stardew.model;

import com.example.addon.stardew.persistence.StardewJsonUtil;
import com.google.gson.JsonObject;

/**
 * 星露谷肥料档案：与种子/作物分离，是否真的需要先施肥由服务器规则配置决定。
 *
 * <p>{@link #itemIdentityId()} 描述肥料的物品身份（物品 ID + 数据组件），施肥动作与
 * 资源管理复用现有物品身份体系，不单独再造一套物品数据库。</p>
 */
public final class StardewFertilizerProfile {

    private final String fertilizerId;
    private final String displayName;
    private final String itemIdentityId;
    private final String customData;
    private final String targetCropId;
    private final String applicationRule;
    private final boolean enabled;

    public StardewFertilizerProfile(String fertilizerId, String displayName, String itemIdentityId,
                                    String customData, String targetCropId, String applicationRule, boolean enabled) {
        this.fertilizerId = fertilizerId;
        this.displayName = displayName;
        this.itemIdentityId = itemIdentityId;
        this.customData = customData;
        this.targetCropId = targetCropId;
        this.applicationRule = applicationRule;
        this.enabled = enabled;
    }

    public String fertilizerId() {
        return fertilizerId;
    }

    public String displayName() {
        return displayName;
    }

    public String itemIdentityId() {
        return itemIdentityId;
    }

    public String customData() {
        return customData;
    }

    public String targetCropId() {
        return targetCropId;
    }

    public String applicationRule() {
        return applicationRule;
    }

    public boolean enabled() {
        return enabled;
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        StardewJsonUtil.put(obj, "肥料ID", fertilizerId);
        StardewJsonUtil.put(obj, "显示名称", displayName);
        StardewJsonUtil.put(obj, "物品ID", itemIdentityId);
        StardewJsonUtil.put(obj, "数据组件", customData);
        StardewJsonUtil.put(obj, "目标作物", targetCropId);
        StardewJsonUtil.put(obj, "施用规则", applicationRule);
        StardewJsonUtil.put(obj, "启用", enabled);
        return obj;
    }

    public static StardewFertilizerProfile fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String fertilizerId = StardewJsonUtil.str(obj, "肥料ID", null);
        if (fertilizerId == null || fertilizerId.isBlank()) return null;
        return new StardewFertilizerProfile(
            fertilizerId,
            StardewJsonUtil.str(obj, "显示名称", fertilizerId),
            StardewJsonUtil.str(obj, "物品ID", ""),
            StardewJsonUtil.str(obj, "数据组件", null),
            StardewJsonUtil.str(obj, "目标作物", null),
            StardewJsonUtil.str(obj, "施用规则", null),
            StardewJsonUtil.bool(obj, "启用", true)
        );
    }
}
