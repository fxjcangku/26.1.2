package com.example.addon.stardew.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 星露谷 JSON 序列化工具：统一中文键读写，避免每个模型重复 Gson 样板。
 *
 * <p>只做纯字段读写，不负责文件落盘（文件落盘见 {@link StardewJsonRepository}）。
 * 全部读写都对缺失/非法字段做兜底，单个字段损坏不阻塞整体加载。</p>
 */
public final class StardewJsonUtil {

    private StardewJsonUtil() {
        // 工具类，禁止实例化
    }

    /** 读取字符串（缺失/空回退 fallback） */
    public static String str(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        String value = obj.get(key).getAsString();
        return value == null ? fallback : value;
    }

    /** 读取整数（缺失/非法回退 fallback） */
    public static int integer(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /** 读取布尔（缺失/非法回退 fallback） */
    public static boolean bool(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /** 读取字符串列表（缺失返回空列表） */
    public static List<String> strList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return result;
        JsonElement el = obj.get(key);
        if (!el.isJsonArray()) return result;
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonPrimitive()) result.add(item.getAsString());
        }
        return result;
    }

    /** 写入字符串（非空才写） */
    public static void put(JsonObject obj, String key, String value) {
        if (value != null && !value.isBlank()) obj.addProperty(key, value);
    }

    /** 写入整数 */
    public static void put(JsonObject obj, String key, int value) {
        obj.addProperty(key, value);
    }

    /** 写入布尔 */
    public static void put(JsonObject obj, String key, boolean value) {
        obj.addProperty(key, value);
    }

    /** 写入字符串列表（非空才写） */
    public static void put(JsonObject obj, String key, List<String> values) {
        if (values == null || values.isEmpty()) return;
        JsonArray arr = new JsonArray();
        for (String value : values) arr.add(value);
        obj.add(key, arr);
    }

    /** 写入 JsonArray（非空才写） */
    public static void putArray(JsonObject obj, String key, JsonArray arr) {
        if (arr != null && arr.size() > 0) obj.add(key, arr);
    }

    /** 解析一段 JSON 字符串为 JsonObject，失败返回 null */
    public static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
