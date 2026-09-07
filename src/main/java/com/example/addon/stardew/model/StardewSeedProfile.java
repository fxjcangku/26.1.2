package com.example.addon.stardew.model;

import com.example.addon.stardew.persistence.StardewJsonUtil;
import com.google.gson.JsonObject;

/**
 * 星露谷种子档案：把「服务器自定义种子载体」抽象成可识别、可匹配、可持久化的档案。
 *
 * <p>种子与作物分离：本档案只描述种子本身，通过 {@link #cropId()} 关联到
 * {@link StardewCropProfile}。任意 Minecraft 物品都可能成为服务器种子（纸/木棍/胡萝卜/
 * 自定义物品），最终以「物品 ID + 数据组件 + 服务器自定义数据」为准，绝不死写 Items.PAPER。</p>
 */
public final class StardewSeedProfile {

    private final String seedId;
    private final String displayName;
    private final String minecraftItemId;
    private final String customData;
    private final String idSystemReference;
    private final String cropId;
    private final String resourcePackVisualReference;
    private final boolean enabled;
    private final String serverProfile;

    public StardewSeedProfile(String seedId, String displayName, String minecraftItemId,
                              String customData, String idSystemReference, String cropId,
                              String resourcePackVisualReference, boolean enabled, String serverProfile) {
        this.seedId = seedId;
        this.displayName = displayName;
        this.minecraftItemId = minecraftItemId;
        this.customData = customData;
        this.idSystemReference = idSystemReference;
        this.cropId = cropId;
        this.resourcePackVisualReference = resourcePackVisualReference;
        this.enabled = enabled;
        this.serverProfile = serverProfile;
    }

    public String seedId() {
        return seedId;
    }

    public String displayName() {
        return displayName;
    }

    public String minecraftItemId() {
        return minecraftItemId;
    }

    public String customData() {
        return customData;
    }

    public String idSystemReference() {
        return idSystemReference;
    }

    public String cropId() {
        return cropId;
    }

    public String resourcePackVisualReference() {
        return resourcePackVisualReference;
    }

    public boolean enabled() {
        return enabled;
    }

    public String serverProfile() {
        return serverProfile;
    }

    /** 序列化为中文键 JSON 对象 */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        StardewJsonUtil.put(obj, "种子ID", seedId);
        StardewJsonUtil.put(obj, "显示名称", displayName);
        StardewJsonUtil.put(obj, "物品ID", minecraftItemId);
        StardewJsonUtil.put(obj, "数据组件", customData);
        StardewJsonUtil.put(obj, "ID系统引用", idSystemReference);
        StardewJsonUtil.put(obj, "作物ID", cropId);
        StardewJsonUtil.put(obj, "资源包视觉引用", resourcePackVisualReference);
        StardewJsonUtil.put(obj, "启用", enabled);
        StardewJsonUtil.put(obj, "服务器档案", serverProfile);
        return obj;
    }

    /** 从中文键 JSON 反序列化 */
    public static StardewSeedProfile fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String seedId = StardewJsonUtil.str(obj, "种子ID", null);
        if (seedId == null || seedId.isBlank()) return null;
        return new StardewSeedProfile(
            seedId,
            StardewJsonUtil.str(obj, "显示名称", seedId),
            StardewJsonUtil.str(obj, "物品ID", ""),
            StardewJsonUtil.str(obj, "数据组件", null),
            StardewJsonUtil.str(obj, "ID系统引用", null),
            StardewJsonUtil.str(obj, "作物ID", null),
            StardewJsonUtil.str(obj, "资源包视觉引用", null),
            StardewJsonUtil.bool(obj, "启用", true),
            StardewJsonUtil.str(obj, "服务器档案", "")
        );
    }
}
