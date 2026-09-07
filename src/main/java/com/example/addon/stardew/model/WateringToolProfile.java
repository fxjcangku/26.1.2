package com.example.addon.stardew.model;

import com.example.addon.stardew.persistence.StardewJsonUtil;
import com.google.gson.JsonObject;

/**
 * 浇水工具档案（喷壶等）：描述浇水工具的物品身份与一次可浇的范围。
 *
 * <p>范围不写死（1 格 / 3 格 / 3x3 / 3x6 只是原版参考），全部配置化：
 * shape + range + direction + width + height + maxTargets，最终以服务器实际机制为准。</p>
 */
public final class WateringToolProfile {

    private final String toolId;
    private final String displayName;
    private final String itemIdentityId;
    private final String customData;
    private final String shape;
    private final int range;
    private final String direction;
    private final int width;
    private final int height;
    private final int maxTargets;

    public WateringToolProfile(String toolId, String displayName, String itemIdentityId, String customData,
                               String shape, int range, String direction, int width, int height, int maxTargets) {
        this.toolId = toolId;
        this.displayName = displayName;
        this.itemIdentityId = itemIdentityId;
        this.customData = customData;
        this.shape = shape;
        this.range = range;
        this.direction = direction;
        this.width = width;
        this.height = height;
        this.maxTargets = maxTargets;
    }

    public String toolId() {
        return toolId;
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

    public String shape() {
        return shape;
    }

    public int range() {
        return range;
    }

    public String direction() {
        return direction;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int maxTargets() {
        return maxTargets;
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        StardewJsonUtil.put(obj, "工具ID", toolId);
        StardewJsonUtil.put(obj, "显示名称", displayName);
        StardewJsonUtil.put(obj, "物品ID", itemIdentityId);
        StardewJsonUtil.put(obj, "数据组件", customData);
        StardewJsonUtil.put(obj, "形状", shape);
        StardewJsonUtil.put(obj, "范围", range);
        StardewJsonUtil.put(obj, "方向", direction);
        StardewJsonUtil.put(obj, "宽度", width);
        StardewJsonUtil.put(obj, "高度", height);
        StardewJsonUtil.put(obj, "最大目标数", maxTargets);
        return obj;
    }

    public static WateringToolProfile fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String toolId = StardewJsonUtil.str(obj, "工具ID", null);
        if (toolId == null || toolId.isBlank()) return null;
        return new WateringToolProfile(
            toolId,
            StardewJsonUtil.str(obj, "显示名称", toolId),
            StardewJsonUtil.str(obj, "物品ID", ""),
            StardewJsonUtil.str(obj, "数据组件", null),
            StardewJsonUtil.str(obj, "形状", "single"),
            StardewJsonUtil.integer(obj, "范围", 1),
            StardewJsonUtil.str(obj, "方向", null),
            StardewJsonUtil.integer(obj, "宽度", 1),
            StardewJsonUtil.integer(obj, "高度", 1),
            StardewJsonUtil.integer(obj, "最大目标数", 1)
        );
    }
}
