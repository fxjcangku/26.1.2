package com.example.addon.stardew.model;

import com.example.addon.stardew.persistence.StardewJsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 星露谷服务器档案：一个服务器一份配置，避免不同服务器（种子/作物/规则完全不同）混在一起。
 *
 * <p>这是星露谷模式的持久化根对象，落盘为单个 JSON 文件（见
 * {@link com.example.addon.stardew.persistence.StardewJsonRepository}），复用 Gson + 文件的
 * 现有持久化机制，不另造 JSON 保存机制。</p>
 */
public final class StardewServerProfile {

    private final String profileId;
    private final String serverName;
    private final List<StardewSeedProfile> seeds;
    private final List<StardewCropProfile> crops;
    private final List<StardewFertilizerProfile> fertilizers;
    private final List<WateringToolProfile> wateringTools;
    private final List<SprinklerProfile> sprinklers;
    private final List<String> soilBlockIds;
    private final List<String> resourcePackMappings;

    public StardewServerProfile(String profileId, String serverName, List<StardewSeedProfile> seeds,
                                List<StardewCropProfile> crops, List<StardewFertilizerProfile> fertilizers,
                                List<WateringToolProfile> wateringTools, List<SprinklerProfile> sprinklers,
                                List<String> soilBlockIds, List<String> resourcePackMappings) {
        this.profileId = profileId;
        this.serverName = serverName;
        this.seeds = seeds == null ? new ArrayList<>() : new ArrayList<>(seeds);
        this.crops = crops == null ? new ArrayList<>() : new ArrayList<>(crops);
        this.fertilizers = fertilizers == null ? new ArrayList<>() : new ArrayList<>(fertilizers);
        this.wateringTools = wateringTools == null ? new ArrayList<>() : new ArrayList<>(wateringTools);
        this.sprinklers = sprinklers == null ? new ArrayList<>() : new ArrayList<>(sprinklers);
        this.soilBlockIds = soilBlockIds == null ? new ArrayList<>() : new ArrayList<>(soilBlockIds);
        this.resourcePackMappings = resourcePackMappings == null ? new ArrayList<>() : new ArrayList<>(resourcePackMappings);
    }

    public String profileId() {
        return profileId;
    }

    public String serverName() {
        return serverName;
    }

    public List<StardewSeedProfile> seeds() {
        return seeds;
    }

    public List<StardewCropProfile> crops() {
        return crops;
    }

    public List<StardewFertilizerProfile> fertilizers() {
        return fertilizers;
    }

    public List<WateringToolProfile> wateringTools() {
        return wateringTools;
    }

    public List<SprinklerProfile> sprinklers() {
        return sprinklers;
    }

    /** 农田底盘方块 ID 集合（如 note_block），识别不出则视为非农田 */
    public List<String> soilBlockIds() {
        return soilBlockIds;
    }

    public List<String> resourcePackMappings() {
        return resourcePackMappings;
    }

    /** 按 ID 查找作物，未找到返回 null */
    public StardewCropProfile cropById(String cropId) {
        if (cropId == null) return null;
        for (StardewCropProfile crop : crops) {
            if (crop.cropId().equals(cropId)) return crop;
        }
        return null;
    }

    /** 按 ID 查找种子，未找到返回 null */
    public StardewSeedProfile seedById(String seedId) {
        if (seedId == null) return null;
        for (StardewSeedProfile seed : seeds) {
            if (seed.seedId().equals(seedId)) return seed;
        }
        return null;
    }

    /** 按 ID 查找肥料，未找到返回 null */
    public StardewFertilizerProfile fertilizerById(String fertilizerId) {
        if (fertilizerId == null) return null;
        for (StardewFertilizerProfile fertilizer : fertilizers) {
            if (fertilizer.fertilizerId().equals(fertilizerId)) return fertilizer;
        }
        return null;
    }

    /** 按 ID 查找洒水器，未找到返回 null */
    public SprinklerProfile sprinklerById(String sprinklerId) {
        if (sprinklerId == null) return null;
        for (SprinklerProfile sprinkler : sprinklers) {
            if (sprinkler.sprinklerId().equals(sprinklerId)) return sprinkler;
        }
        return null;
    }

    /** 按 ID 查找浇水工具，未找到返回 null */
    public WateringToolProfile wateringToolById(String toolId) {
        if (toolId == null) return null;
        for (WateringToolProfile tool : wateringTools) {
            if (tool.toolId().equals(toolId)) return tool;
        }
        return null;
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        StardewJsonUtil.put(obj, "档案ID", profileId);
        StardewJsonUtil.put(obj, "服务器名称", serverName);
        obj.add("种子", toArray(seeds));
        obj.add("作物", toArray(crops));
        obj.add("肥料", toArray(fertilizers));
        obj.add("浇水工具", toArray(wateringTools));
        obj.add("洒水器", toArray(sprinklers));
        StardewJsonUtil.put(obj, "农田规则", soilBlockIds);
        StardewJsonUtil.put(obj, "资源包映射", resourcePackMappings);
        return obj;
    }

    private static JsonArray toArray(List<?> items) {
        JsonArray arr = new JsonArray();
        for (Object item : items) {
            if (item instanceof StardewSeedProfile seed) arr.add(seed.toJsonObject());
            else if (item instanceof StardewCropProfile crop) arr.add(crop.toJsonObject());
            else if (item instanceof StardewFertilizerProfile fertilizer) arr.add(fertilizer.toJsonObject());
            else if (item instanceof WateringToolProfile tool) arr.add(tool.toJsonObject());
            else if (item instanceof SprinklerProfile sprinkler) arr.add(sprinkler.toJsonObject());
        }
        return arr;
    }

    public static StardewServerProfile fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String profileId = StardewJsonUtil.str(obj, "档案ID", null);
        if (profileId == null || profileId.isBlank()) return null;

        List<StardewSeedProfile> seeds = new ArrayList<>();
        List<StardewCropProfile> crops = new ArrayList<>();
        List<StardewFertilizerProfile> fertilizers = new ArrayList<>();
        List<WateringToolProfile> wateringTools = new ArrayList<>();
        List<SprinklerProfile> sprinklers = new ArrayList<>();

        for (JsonElement el : arrayOf(obj, "种子")) {
            if (el.isJsonObject()) {
                StardewSeedProfile seed = StardewSeedProfile.fromJsonObject(el.getAsJsonObject());
                if (seed != null) seeds.add(seed);
            }
        }
        for (JsonElement el : arrayOf(obj, "作物")) {
            if (el.isJsonObject()) {
                StardewCropProfile crop = StardewCropProfile.fromJsonObject(el.getAsJsonObject());
                if (crop != null) crops.add(crop);
            }
        }
        for (JsonElement el : arrayOf(obj, "肥料")) {
            if (el.isJsonObject()) {
                StardewFertilizerProfile fertilizer = StardewFertilizerProfile.fromJsonObject(el.getAsJsonObject());
                if (fertilizer != null) fertilizers.add(fertilizer);
            }
        }
        for (JsonElement el : arrayOf(obj, "浇水工具")) {
            if (el.isJsonObject()) {
                WateringToolProfile tool = WateringToolProfile.fromJsonObject(el.getAsJsonObject());
                if (tool != null) wateringTools.add(tool);
            }
        }
        for (JsonElement el : arrayOf(obj, "洒水器")) {
            if (el.isJsonObject()) {
                SprinklerProfile sprinkler = SprinklerProfile.fromJsonObject(el.getAsJsonObject());
                if (sprinkler != null) sprinklers.add(sprinkler);
            }
        }

        return new StardewServerProfile(
            profileId,
            StardewJsonUtil.str(obj, "服务器名称", profileId),
            seeds,
            crops,
            fertilizers,
            wateringTools,
            sprinklers,
            StardewJsonUtil.strList(obj, "农田规则"),
            StardewJsonUtil.strList(obj, "资源包映射")
        );
    }

    private static List<JsonElement> arrayOf(JsonObject obj, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return result;
        JsonElement el = obj.get(key);
        if (!el.isJsonArray()) return result;
        for (JsonElement item : el.getAsJsonArray()) result.add(item);
        return result;
    }
}
