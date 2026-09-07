package com.example.addon.stardew.model;

import com.example.addon.stardew.persistence.StardewJsonUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 洒水器档案：描述洒水器的识别方块与覆盖范围。
 *
 * <p>不假定固定覆盖（原版星露谷的 3x3 / 5x5 仅作参考），覆盖形状与范围全部配置化，
 * 最终由 {@link com.example.addon.stardew.recognition.SprinklerRecognizer} +
 * {@link com.example.addon.stardew.adapter.StardewServerAdapter} 识别。</p>
 */
public final class SprinklerProfile {

    private final String sprinklerId;
    private final String displayName;
    private final String blockId;
    private final String coverageShape;
    private final int coverageRange;
    private final List<String> coveredPositions;
    private final boolean requiresPower;
    private final boolean requiresActivation;

    public SprinklerProfile(String sprinklerId, String displayName, String blockId, String coverageShape,
                            int coverageRange, List<String> coveredPositions, boolean requiresPower,
                            boolean requiresActivation) {
        this.sprinklerId = sprinklerId;
        this.displayName = displayName;
        this.blockId = blockId;
        this.coverageShape = coverageShape;
        this.coverageRange = coverageRange;
        this.coveredPositions = coveredPositions == null ? new ArrayList<>() : new ArrayList<>(coveredPositions);
        this.requiresPower = requiresPower;
        this.requiresActivation = requiresActivation;
    }

    public String sprinklerId() {
        return sprinklerId;
    }

    public String displayName() {
        return displayName;
    }

    public String blockId() {
        return blockId;
    }

    public String coverageShape() {
        return coverageShape;
    }

    public int coverageRange() {
        return coverageRange;
    }

    public List<String> coveredPositions() {
        return coveredPositions;
    }

    public boolean requiresPower() {
        return requiresPower;
    }

    public boolean requiresActivation() {
        return requiresActivation;
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        StardewJsonUtil.put(obj, "洒水器ID", sprinklerId);
        StardewJsonUtil.put(obj, "显示名称", displayName);
        StardewJsonUtil.put(obj, "方块ID", blockId);
        StardewJsonUtil.put(obj, "覆盖形状", coverageShape);
        StardewJsonUtil.put(obj, "覆盖范围", coverageRange);
        StardewJsonUtil.put(obj, "覆盖坐标", coveredPositions);
        StardewJsonUtil.put(obj, "需要供能", requiresPower);
        StardewJsonUtil.put(obj, "需要激活", requiresActivation);
        return obj;
    }

    public static SprinklerProfile fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String sprinklerId = StardewJsonUtil.str(obj, "洒水器ID", null);
        if (sprinklerId == null || sprinklerId.isBlank()) return null;
        return new SprinklerProfile(
            sprinklerId,
            StardewJsonUtil.str(obj, "显示名称", sprinklerId),
            StardewJsonUtil.str(obj, "方块ID", ""),
            StardewJsonUtil.str(obj, "覆盖形状", "square"),
            StardewJsonUtil.integer(obj, "覆盖范围", 1),
            StardewJsonUtil.strList(obj, "覆盖坐标"),
            StardewJsonUtil.bool(obj, "需要供能", false),
            StardewJsonUtil.bool(obj, "需要激活", false)
        );
    }
}
