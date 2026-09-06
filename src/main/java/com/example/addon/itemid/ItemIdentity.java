package com.example.addon.itemid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 物品身份：把一个 ItemStack 抽象成「可比较、可匹配、可持久化」的完整标识。
 *
 * <p>这是「辅助」三功能共享的核心数据单元：ID识别产出它、ID配置管理存储它、
 * 自动箱子消费它做匹配。与第一阶段不同，本阶段不再只存 itemId 字符串，而是
 * 完整记录物品 ID、中文显示名、自定义名、附魔、Data Component 与数据版本，
 * 保证改名物品（普通钻石 vs 超级钻石）能被正确区分。</p>
 *
 * <p>持久化：整份身份序列化为 JSON 对象（不是字符串列表），写进
 * {@code AutoChest/items/{中文名}.json}，中文名只负责显示与命名，
 * 真正的身份判定仍以 itemId + 组件 + 附魔为准。</p>
 */
public final class ItemIdentity {

    /** 物品 ID，如 {@code minecraft:diamond} */
    private final String itemId;

    /** 中文显示名（已剥离颜色代码），用于聊天显示与文件名，如「钻石」「超级钻石」 */
    private final String displayName;

    /** 物品默认中文名（未改名时的名称），用于与改名物品区分 */
    private final String baseName;

    /** 自定义名（改名后的名称），未改名为 null */
    private final String customName;

    /** Minecraft 数据版本（识别时记录的存档格式版本号） */
    private final int dataVersion;

    /** 附魔信息列表（附魔 ID + 中文名 + 等级），无附魔为空列表 */
    private final List<EnchantmentEntry> enchantments;

    /** Data Component 补丁序列化 JSON（Data Component API，非旧 NBT），无组件为 null */
    private final String dataComponents;

    /** 识别时的物品数量（仅展示与落盘，不参与身份判定；手动添加默认为 1） */
    private final int quantity;

    /** 物品注册表项（瞬态，不序列化；反序列化时按 itemId 回查） */
    private final transient Item item;

    /** 组件模板（瞬态，不序列化；识别时用于精确组件匹配） */
    private final transient ItemStack componentTemplate;

    public ItemIdentity(String itemId, String displayName, String baseName, String customName,
                        int dataVersion, List<EnchantmentEntry> enchantments, String dataComponents,
                        Item item, ItemStack componentTemplate, int quantity) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.baseName = baseName;
        this.customName = customName;
        this.dataVersion = dataVersion;
        this.enchantments = enchantments == null ? Collections.emptyList() : List.copyOf(enchantments);
        this.dataComponents = dataComponents;
        this.item = item;
        // 深拷贝组件模板，避免外部修改影响身份判定
        this.componentTemplate = componentTemplate == null ? null : componentTemplate.copy();
        this.quantity = Math.max(1, quantity);
    }

    public String itemId() {
        return itemId;
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

    public List<EnchantmentEntry> enchantments() {
        return enchantments;
    }

    public String dataComponents() {
        return dataComponents;
    }

    public int quantity() {
        return quantity;
    }

    public Item item() {
        return item != null ? item : resolveItem();
    }

    /** 是否改名物品（存在自定义名，且与默认名不同） */
    public boolean isRenamed() {
        return customName != null && !customName.isBlank() && !customName.equals(baseName);
    }

    /** 是否原版物品（minecraft 命名空间且未改名）；改名物品与非 minecraft 物品均视为自定义 */
    public boolean isVanilla() {
        return itemId.startsWith("minecraft:") && !isRenamed();
    }

    /** 是否自定义物品（改名物品或非 minecraft 命名空间的物品） */
    public boolean isCustom() {
        return !isVanilla();
    }

    /** 物品类型中文名：原版 / 自定义 */
    public String typeName() {
        return isVanilla() ? "原版" : "自定义";
    }

    /** 是否带有附魔数据 */
    public boolean hasEnchantments() {
        return !enchantments.isEmpty();
    }

    /**
     * 判断给定 ItemStack 是否命中该身份。
     *
     * <p>识别时带组件模板则走精确组件匹配；从文件反序列化后无组件模板，
     * 退化为 itemId 粗筛（自定义名与附魔的精确比对留给下一阶段 AutoChest 消费层）。</p>
     */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item targetItem = item != null ? item : resolveItem();
        if (targetItem == null || targetItem == Items.AIR) return false;
        if (componentTemplate != null) {
            return ItemStack.isSameItemSameComponents(componentTemplate, stack);
        }
        return stack.is(targetItem);
    }

    /** 按 itemId 回查注册表项（供反序列化后使用） */
    private Item resolveItem() {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item resolved = BuiltInRegistries.ITEM.getValue(id);
        return resolved == null ? null : resolved;
    }

    /**
     * 序列化为 JSON 对象（完整身份，非字符串）。
     *
     * <p>字段采用中文字段名（物品ID / 显示名称 / 原始名称 / 物品类型 / 自定义名称 /
     * 数量 / 附魔 / 数据组件 / 数据版本），方便玩家直接阅读 JSON 文件；Java 内部
     * 仍保持英文类名 / 字段名不变。</p>
     */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("物品ID", itemId);
        obj.addProperty("显示名称", displayName);
        obj.addProperty("原始名称", baseName);
        obj.addProperty("物品类型", typeName());
        if (customName != null) obj.addProperty("自定义名称", customName);
        obj.addProperty("数量", quantity);
        obj.addProperty("数据版本", dataVersion);
        if (!enchantments.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (EnchantmentEntry e : enchantments) {
                JsonObject eo = new JsonObject();
                eo.addProperty("附魔ID", e.id());
                eo.addProperty("中文名称", e.chineseName());
                eo.addProperty("等级", e.level());
                eo.addProperty("显示名称", e.displayName());
                arr.add(eo);
            }
            obj.add("附魔", arr);
        }
        if (dataComponents != null && !dataComponents.isBlank()) {
            JsonElement parsed = parseDataComponents(dataComponents);
            if (parsed != null) obj.add("数据组件", parsed);
        }
        return obj;
    }

    /** 从 JSON 对象反序列化（中文字段优先，兼容旧版英文字段，附魔 + 组件模板恢复） */
    public static ItemIdentity fromJsonObject(JsonObject obj) {
        if (obj == null) return null;
        String itemId = strBoth(obj, "物品ID", "itemId", null);
        if (itemId == null || itemId.isBlank()) return null;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) return null;

        String displayName = strBoth(obj, "显示名称", "displayName", itemId);
        String baseName = strBoth(obj, "原始名称", "baseName", displayName);
        String customName = strBoth(obj, "自定义名称", "customName", null);
        int dataVersion = intBoth(obj, "数据版本", "dataVersion", 0);
        int quantity = intBoth(obj, "数量", "quantity", 1);

        List<EnchantmentEntry> enchants = new ArrayList<>();
        JsonElement enchantEl = jsonBoth(obj, "附魔", "enchantments");
        if (enchantEl != null && enchantEl.isJsonArray()) {
            for (JsonElement el : enchantEl.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject eo = el.getAsJsonObject();
                enchants.add(new EnchantmentEntry(
                    strBoth(eo, "附魔ID", "id", ""),
                    strBoth(eo, "中文名称", "chineseName", ""),
                    intBoth(eo, "等级", "level", 0),
                    strBoth(eo, "显示名称", "displayName", "")
                ));
            }
        }

        String dataComponents = null;
        JsonElement dc = jsonBoth(obj, "数据组件", "dataComponents");
        if (dc != null && !dc.isJsonNull()) {
            dataComponents = dc.toString();
        }

        return new ItemIdentity(itemId, displayName, baseName, customName, dataVersion,
            enchants, dataComponents, item, rebuildComponentTemplate(item, dataComponents), quantity);
    }

    /**
     * 把落盘的 Data Component 补丁 JSON 反序列化回组件模板 ItemStack。
     *
     * <p>识别时组件模板来自原始 ItemStack，可直接做 {@code isSameItemSameComponents}
     * 组件级精确匹配；从文件反序列化后组件模板原本为 null，导致改名/附魔物品退化为
     * itemId 粗筛。这里把 {@code dataComponents} 还原成 {@link DataComponentPatch} 再
     * 应用到默认实例上，恢复组件级精确匹配能力。注册表未就绪或补丁为空时返回 null，
     * 优雅退化为 itemId 粗筛。</p>
     */
    private static ItemStack rebuildComponentTemplate(Item item, String dataComponents) {
        if (dataComponents == null || dataComponents.isBlank()) return null;
        RegistryAccess access = registryAccess();
        if (access == null) return null;
        try {
            JsonElement json = JsonParser.parseString(dataComponents);
            DataResult<DataComponentPatch> result = DataComponentPatch.CODEC.parse(
                access.createSerializationContext(JsonOps.INSTANCE), json);
            DataComponentPatch patch = result.result().orElse(null);
            if (patch == null || patch.isEmpty()) return null;
            ItemStack template = new ItemStack(item);
            template.applyComponents(patch);
            return template;
        } catch (Exception ignored) {
            // 补丁反序列化失败不阻塞加载，退化为 itemId 粗筛
            return null;
        }
    }

    /** 当前可用的注册表访问器（优先玩家，其次世界）；均未就绪返回 null */
    private static RegistryAccess registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) return mc.player.registryAccess();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }

    /** 把 Data Component JSON 字符串解析成 JsonElement（嵌入 JSON 文件时保持对象结构） */
    private static JsonElement parseDataComponents(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 读取中文字段（优先）或英文字段（兼容旧档），均缺失回退 fallback */
    private static String strBoth(JsonObject obj, String cnKey, String enKey, String fallback) {
        if (obj.has(cnKey) && !obj.get(cnKey).isJsonNull()) return obj.get(cnKey).getAsString();
        if (obj.has(enKey) && !obj.get(enKey).isJsonNull()) return obj.get(enKey).getAsString();
        return fallback;
    }

    /** 读取中文字段（优先）或英文字段（兼容旧档）的整数，均缺失回退 fallback */
    private static int intBoth(JsonObject obj, String cnKey, String enKey, int fallback) {
        if (obj.has(cnKey) && !obj.get(cnKey).isJsonNull()) return obj.get(cnKey).getAsInt();
        if (obj.has(enKey) && !obj.get(enKey).isJsonNull()) return obj.get(enKey).getAsInt();
        return fallback;
    }

    /** 读取中文字段（优先）或英文字段（兼容旧档）的 JSON 元素，均缺失返回 null */
    private static JsonElement jsonBoth(JsonObject obj, String cnKey, String enKey) {
        if (obj.has(cnKey) && !obj.get(cnKey).isJsonNull()) return obj.get(cnKey);
        if (obj.has(enKey) && !obj.get(enKey).isJsonNull()) return obj.get(enKey);
        return null;
    }

    /**
     * 生成稳定的身份键（与 {@link #equals} 判据一致：itemId + 自定义名 + 附魔）。
     *
     * <p>供 AutoChest 目标物品选择器做持久化与查找，避免「ID识别 / ID管理 /
     * AutoChest 各存一套字符串」导致三套数据。运行时仍通过 {@link ItemIdManager}
     * 按此键解析回完整 {@link ItemIdentity}，唯一数据源不变。</p>
     */
    public String identityKey() {
        StringBuilder sb = new StringBuilder(itemId);
        if (customName != null && !customName.isBlank()) {
            sb.append('#').append(customName);
        }
        if (!enchantments.isEmpty()) {
            sb.append('#');
            for (int i = 0; i < enchantments.size(); i++) {
                if (i > 0) sb.append(',');
                EnchantmentEntry e = enchantments.get(i);
                sb.append(e.id()).append(':').append(e.level());
            }
        }
        return sb.toString();
    }

    /**
     * 从纯物品 ID 构造身份（手动添加用）。
     *
     * <p>非法 ID、空气方块或注册表不存在时返回 null；合法则复用
     * {@link ItemIdentifier#identifyItem} 的完整识别逻辑（中文名 + 数据版本等）。</p>
     */
    public static ItemIdentity fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) return null;
        return ItemIdentifier.identifyItem(item.getDefaultInstance());
    }

    /**
     * 从物品注册表项 + 自定义名构造身份（手动添加自定义 / 改名物品用）。
     *
     * <p>用于「添加自定义物品」：输入真实物品 ID（如 {@code minecraft:diamond}）+
     * 自定义名称（如「超级钻石」），构造 customName 非空、displayName 为自定义名的
     * 完整身份。真实身份判定仍以 itemId + customName 为准，普通钻石与超级钻石不相等。</p>
     *
     * @param item       物品注册表项（必须真实存在，非空气）
     * @param customName 自定义名称（改名后的名称），为空返回 null
     * @return 完整身份；物品非法或自定义名为空返回 null
     */
    public static ItemIdentity fromItemAndCustomName(Item item, String customName) {
        if (item == null || item == Items.AIR) return null;
        String cn = cleanName(customName);
        if (cn == null || cn.isBlank()) return null;
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        String baseName = cleanName(item.getDefaultInstance().getHoverName().getString());
        return new ItemIdentity(itemId, cn, baseName, cn, currentDataVersion(),
            Collections.emptyList(), null, item, null, 1);
    }

    /** 剥离 Minecraft 颜色代码并去首尾空格（与 ItemIdentifier.clean 同语义） */
    private static String cleanName(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orA-FK-ORx]", "").trim();
    }

    /** 当前 Minecraft 数据版本；获取失败返回 0 */
    private static int currentDataVersion() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * 清洗成合法文件名：剥离 Minecraft 颜色代码、剔除 Windows 非法字符、
     * 压缩空白并去首尾空格；结果为空时回退到给定兜底值。
     */
    public static String sanitizeFileName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        // 1. 剥离颜色代码（§0-§f、§k-§o、§r、§x 等）
        String cleaned = name.replaceAll("§[0-9a-fk-orA-FK-ORx]", "");
        // 2. 剔除 Windows 非法字符与其它不允许作为文件名的字符
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "");
        // 3. 压缩连续空白
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        // 4. 去掉首尾点/空格（Windows 不允许以点结尾）
        cleaned = cleaned.replaceAll("^[. ]+|[. ]+$", "");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemIdentity other)) return false;
        // 身份判定：itemId + 自定义名 + 附魔组合，保证普通钻石与超级钻石不相等
        return itemId.equals(other.itemId)
            && Objects.equals(customName, other.customName)
            && enchantments.equals(other.enchantments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, customName, enchantments);
    }

    @Override
    public String toString() {
        return itemId;
    }

    /** 附魔条目：附魔 ID + 中文名 + 等级 + 显示名（含等级罗马数字） */
    public record EnchantmentEntry(String id, String chineseName, int level, String displayName) {
    }
}
