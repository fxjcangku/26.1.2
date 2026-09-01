package com.example.addon.itemid;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品 / 实体识别器（ID 识别的核心）。
 *
 * <p>统一收口「从 ItemStack / Entity 提取完整身份」的动作，本阶段基于
 * 26.1.2 Data Component API（非旧 NBT）解析：物品 ID、中文显示名、自定义名、
 * 附魔、Data Component 补丁与数据版本。</p>
 *
 * <p>改名物品通过 {@code DataComponents.CUSTOM_NAME} 判定，与默认中文名分离，
 * 保证普通钻石与超级钻石是两个不同身份。</p>
 */
public final class ItemIdentifier {

    private static final Minecraft mc = Minecraft.getInstance();

    private ItemIdentifier() {
        // 工具类，禁止实例化
    }

    /** 提取物品 ID（如 {@code minecraft:diamond}）；空物品返回 {@code minecraft:air} */
    public static String itemIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 提取实体类型 ID（如 {@code minecraft:zombie}）；空实体返回 null */
    public static String entityIdOf(Entity entity) {
        if (entity == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /** 识别一个物品为完整 ItemIdentity；空物品返回 null */
    public static ItemIdentity identifyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        Item item = stack.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        // 中文名解析：默认名走注册表项的默认实例，改名名读 CUSTOM_NAME 组件
        String baseName = clean(item.getDefaultInstance().getHoverName().getString());
        Component customComponent = stack.get(DataComponents.CUSTOM_NAME);
        String customName = (customComponent != null && !customComponent.getString().isBlank())
            ? clean(customComponent.getString()) : null;
        String displayName = (customName != null) ? customName : baseName;

        List<ItemIdentity.EnchantmentEntry> enchantments = extractEnchantments(stack);
        String dataComponents = serializeComponents(stack, registryAccess());
        int dataVersion = currentDataVersion();

        return new ItemIdentity(itemId, displayName, baseName, customName, dataVersion,
            enchantments, dataComponents, item, stack, stack.getCount());
    }

    /** 识别一个实体为 EntityIdentity；空实体返回 null */
    public static EntityIdentity identifyEntity(Entity entity) {
        if (entity == null) return null;

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String baseName = clean(entity.getType().getDescription().getString());
        Component customComponent = entity.getCustomName();
        String customName = (customComponent != null && !customComponent.getString().isBlank())
            ? clean(customComponent.getString()) : null;
        String displayName = (customName != null) ? customName : baseName;

        return new EntityIdentity(entityId, displayName, baseName, customName,
            currentDataVersion(), entity.getType());
    }

    /**
     * 按中文显示名精确匹配原版物品（遍历注册表做 equals 判断，非 contains / startsWith）。
     *
     * <p>供「手动添加物品」输入中文名时使用：输入「钻石」匹配 {@code minecraft:diamond}。
     * 可能存在多个同名物品（不同变体），调用方需对多结果展示候选列表，不得随机选择。</p>
     *
     * @param chineseName 中文名称（已剥离颜色代码）
     * @return 命中物品列表（可能为空或多个）
     */
    public static List<Item> findVanillaByChineseName(String chineseName) {
        List<Item> result = new ArrayList<>();
        String target = clean(chineseName);
        if (target.isEmpty()) return result;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (clean(item.getDefaultInstance().getHoverName().getString()).equals(target)) {
                result.add(item);
            }
        }
        return result;
    }

    // ── 附魔解析（Data Component API） ──

    private static List<ItemIdentity.EnchantmentEntry> extractEnchantments(ItemStack stack) {
        List<ItemIdentity.EnchantmentEntry> result = new ArrayList<>();
        // 装备类附魔（ENCHANTMENTS）
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) collectEnchantments(enchantments, result);
        // 附魔书存储附魔（STORED_ENCHANTMENTS）
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) collectEnchantments(stored, result);
        return result;
    }

    private static void collectEnchantments(ItemEnchantments enchantments,
                                            List<ItemIdentity.EnchantmentEntry> out) {
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            // 附魔 ID 走 unwrapKey().identifier()，避免 ResourceKey.toString() 的包装格式
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            // 中文名：description() 是本地化 Component；完整名（含等级罗马数字）用 getFullname
            String chineseName = clean(holder.value().description().getString());
            String fullName = clean(Enchantment.getFullname(holder, level).getString());
            out.add(new ItemIdentity.EnchantmentEntry(id, chineseName, level, fullName));
        }
    }

    // ── Data Component 补丁序列化（26.1.2 Data Component API，非旧 NBT） ──

    private static String serializeComponents(ItemStack stack, RegistryAccess access) {
        if (access == null) return null;
        try {
            DataComponentPatch patch = stack.getComponentsPatch();
            if (patch == null || patch.isEmpty()) return null;
            DataResult<JsonElement> result = DataComponentPatch.CODEC.encodeStart(
                access.createSerializationContext(JsonOps.INSTANCE), patch);
            JsonElement json = result.result().orElse(null);
            return json == null ? null : json.toString();
        } catch (Exception ignored) {
            // 组件序列化失败不阻塞识别，组件字段留空
            return null;
        }
    }

    // ── 环境数据 ──

    private static RegistryAccess registryAccess() {
        if (mc.player != null) return mc.player.registryAccess();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }

    private static int currentDataVersion() {
        try {
            return SharedConstants.getCurrentVersion().dataVersion().version();
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** 剥离 Minecraft 颜色代码并去首尾空格 */
    private static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orA-FK-ORx]", "").trim();
    }
}
