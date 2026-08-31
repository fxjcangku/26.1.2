package com.example.addon.itemid;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * 实体身份：把玩家准星对准的实体抽象成「可持久化」的标识。
 *
 * <p>对应 {@code .id 实体} 识别功能，保存到 {@code AutoChest/entities/}。
 * 与物品身份不同，实体身份暂不参与自动箱子的取物匹配，仅做识别与归档，
 * 供玩家快速查证实体类型 ID 与中文名。</p>
 */
public final class EntityIdentity {

    /** 实体类型 ID，如 {@code minecraft:zombie} */
    private final String entityId;

    /** 中文显示名（已剥离颜色代码），如「僵尸」；命名实体显示自定义名 */
    private final String displayName;

    /** 实体默认中文名（类型名），用于区分命名实体 */
    private final String baseName;

    /** 实体自定义名（命名牌命名），未命名的为 null */
    private final String customName;

    /** Minecraft 数据版本 */
    private final int dataVersion;

    /** 实体类型（瞬态，不序列化） */
    private final transient EntityType<?> type;

    public EntityIdentity(String entityId, String displayName, String baseName, String customName,
                          int dataVersion, EntityType<?> type) {
        this.entityId = entityId;
        this.displayName = displayName;
        this.baseName = baseName;
        this.customName = customName;
        this.dataVersion = dataVersion;
        this.type = type;
    }

    public String entityId() {
        return entityId;
    }

    public String displayName() {
        return displayName;
    }

    public String baseName() {
        return baseName;
    }

    public String customName() {
        return customName;
    }

    public int dataVersion() {
        return dataVersion;
    }

    /** 是否命名实体（命名牌命名） */
    public boolean isNamed() {
        return customName != null && !customName.isBlank();
    }

    /** 序列化为 JSON 对象（中文字段，便于玩家阅读；Java 内部仍用英文字段） */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("实体ID", entityId);
        obj.addProperty("显示名称", displayName);
        obj.addProperty("原始名称", baseName);
        if (customName != null) obj.addProperty("自定义名称", customName);
        obj.addProperty("数据版本", dataVersion);
        return obj;
    }

    /** 从 JSON 对象反序列化（中文字段优先，兼容旧版英文字段） */
    public static EntityIdentity fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String entityId = strBoth(obj, "实体ID", "entityId", null);
        if (entityId == null || entityId.isBlank()) return null;
        Identifier id = Identifier.tryParse(entityId);
        if (id == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) return null;

        String displayName = strBoth(obj, "显示名称", "displayName", entityId);
        String baseName = strBoth(obj, "原始名称", "baseName", displayName);
        String customName = strBoth(obj, "自定义名称", "customName", null);
        int dataVersion = intBoth(obj, "数据版本", "dataVersion", 0);
        return new EntityIdentity(entityId, displayName, baseName, customName, dataVersion, type);
    }

    /** 读取中文字段（优先）或英文字段（兼容旧档） */
    private static String strBoth(JsonObject obj, String cnKey, String enKey, String fallback) {
        if (obj.has(cnKey) && !obj.get(cnKey).isJsonNull()) return obj.get(cnKey).getAsString();
        if (obj.has(enKey) && !obj.get(enKey).isJsonNull()) return obj.get(enKey).getAsString();
        return fallback;
    }

    private static int intBoth(JsonObject obj, String cnKey, String enKey, int fallback) {
        if (obj.has(cnKey) && !obj.get(cnKey).isJsonNull()) return obj.get(cnKey).getAsInt();
        if (obj.has(enKey) && !obj.get(enKey).isJsonNull()) return obj.get(enKey).getAsInt();
        return fallback;
    }

    @Override
    public String toString() {
        return entityId;
    }
}
