package com.example.addon.enchant.gear;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版装备附魔 · UI 控件层（装备配置入口）。
 *
 * <p>继承 {@link StringListSetting}，用约定格式的字符串列表持久化完整配置：
 * <pre>
 *   [0] 装备 ID（minecraft:diamond_sword）
 *   [1] 方案 ID（fortune）
 *   [2..] 每个目标附魔 "enchantId:level:excludedFlag"（flag 0/1）
 * </pre>
 * 点击后打开 {@link GearEnchantScreen} 进行类别 / 装备 / 方案 / 等级 / 排除的完整配置。</p>
 *
 * <p>职责边界：本类只负责「状态序列化 + UI 入口」，不解析 JSON，不做目标判断。</p>
 */
public final class GearEnchantSetting extends StringListSetting {

    private static final String EMPTY = "";

    public GearEnchantSetting(String name, IVisible visible) {
        super(name, "配置原版装备的极品附魔目标", new ArrayList<>(), null, null, visible, null, null);
    }

    /** 注册自定义渲染工厂（供 GearEnchantScreen 打开入口） */
    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(GearEnchantSetting.class,
            theme -> (table, setting) -> createWidget(theme, table, (GearEnchantSetting) setting));
    }

    private static void createWidget(GuiTheme theme, WTable table, GearEnchantSetting setting) {
        // 图标 + 按钮 + 摘要 + 重置统一放进横向列表（官方 BlockSetting 同款布局），
        // 避免多个控件直接平铺进设置表格造成行宽/命中区异常
        WHorizontalList row = table.add(theme.horizontalList()).expandX().widget();
        WItem icon = row.add(theme.item(setting.currentIconStack())).widget();
        WButton select = row.add(theme.button("配置装备附魔")).expandCellX().widget();
        WLabel summary = row.add(theme.label(setting.summaryText())).widget();
        WButton reset = row.add(theme.button(GuiRenderer.RESET)).widget();
        // 延迟到下一 tick 再弹屏：主菜单等环境中立即 setScreen 会被后续 UI 事件覆盖，
        // 出现「点了没反应」，进世界后事件时序不同才正常
        select.action = () -> Minecraft.getInstance().execute(() ->
            Minecraft.getInstance().setScreen(new GearEnchantScreen(theme, setting)));
        // 重置后同步刷新摘要与图标，避免配置页文字停留在旧值
        reset.action = () -> {
            setting.reset();
            setting.refreshSummary();
        };
        // 摘要/图标缓存，GearEnchantScreen 每次重建都会调 refreshSummary 同步刷新
        setting.summaryLabel = summary;
        setting.iconLabel = icon;
    }

    private WLabel summaryLabel;
    private WItem iconLabel;

    /** 当前装备 ID，未选择返回 null */
    public String gearId() {
        List<String> v = get();
        return v.isEmpty() ? null : emptyToNull(v.get(0));
    }

    /** 当前方案 ID，未选择返回 null */
    public String profileId() {
        List<String> v = get();
        return v.size() < 2 ? null : emptyToNull(v.get(1));
    }

    /** 应用装备选择：切到该装备的默认方案，并重置附魔为该方案默认值 */
    public void applyGear(String gearId) {
        GearEnchantData.GearDefinition gear = GearEnchantData.get().gear(gearId);
        if (gear == null || gear.profiles.isEmpty()) {
            set(List.of(gearId, EMPTY));
            return;
        }
        GearEnchantData.GearProfile profile = defaultProfile(gear);
        applyProfileInternal(gearId, profile);
    }

    /** 应用方案选择：重置附魔为该方案默认值 */
    public void applyProfile(String profileId) {
        String gearId = gearId();
        if (gearId == null) return;
        GearEnchantData.GearDefinition gear = GearEnchantData.get().gear(gearId);
        if (gear == null) return;
        for (GearEnchantData.GearProfile profile : gear.profiles) {
            if (profile.id.equals(profileId)) {
                applyProfileInternal(gearId, profile);
                return;
            }
        }
    }

    private void applyProfileInternal(String gearId, GearEnchantData.GearProfile profile) {
        List<String> encoded = new ArrayList<>();
        encoded.add(gearId);
        encoded.add(profile.id);
        for (GearEnchantData.TargetDefinition target : profile.targets) {
            encoded.add(target.id + ":" + target.level + ":0");
        }
        set(encoded);
    }

    /** 调整某附魔等级（受真实最大等级限制，clamp 到 [1, max]；注册表不可用时只做下限保护） */
    public void setLevel(String enchantId, int level) {
        int max = GearEnchantData.get().maxLevelOf(enchantId);
        int clamped = Math.max(1, level);
        // maxLevelOf 返回 -1 表示注册表尚未就绪/附魔不存在，此时不强制降级为 1，
        // 否则会出现「点减号从 V 直接跳到 I、点加号无反应」——把 -1 误当 max=1 所致
        if (max >= 1) clamped = Math.min(clamped, max);
        mutateTarget(enchantId, clamped, -1);
    }

    /** 切换某附魔的排除状态 */
    public void setExcluded(String enchantId, boolean excluded) {
        mutateTarget(enchantId, -1, excluded ? 1 : 0);
    }

    /** 读取某附魔当前等级，不存在返回 -1 */
    public int levelOf(String enchantId) {
        for (int i = 2; i < get().size(); i++) {
            String[] parts = parseTarget(get().get(i));
            if (parts != null && parts[0].equals(enchantId)) {
                try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { return -1; }
            }
        }
        return -1;
    }

    /** 读取某附魔是否被排除 */
    public boolean isExcluded(String enchantId) {
        for (int i = 2; i < get().size(); i++) {
            String[] parts = parseTarget(get().get(i));
            if (parts != null && parts[0].equals(enchantId)) return "1".equals(parts[2]);
        }
        return false;
    }

    /**
     * 从右向左解析「附魔ID:等级:排除标记」条目。
     * 附魔 ID 自带命名空间冒号（如 minecraft:sharpness），不能直接 split(":")，
     * 必须定位最后两个冒号：前面的等级、最后的标记，剩余部分是完整 ID。
     * 非法条目返回 null。
     */
    private static String[] parseTarget(String entry) {
        if (entry == null) return null;
        int flagColon = entry.lastIndexOf(':');
        if (flagColon <= 0) return null;
        int levelColon = entry.lastIndexOf(':', flagColon - 1);
        if (levelColon <= 0) return null;
        return new String[]{
            entry.substring(0, levelColon),
            entry.substring(levelColon + 1, flagColon),
            entry.substring(flagColon + 1)
        };
    }

    /** 生成当前配置对应的 TargetProfile 快照（后续评分 / 规划的唯一依据） */
    public TargetProfile currentProfile() {
        String gearId = gearId();
        String profileId = profileId();
        GearEnchantData.GearDefinition gear = GearEnchantData.get().gear(gearId == null ? "" : gearId);
        GearEnchantData.GearProfile profile = findProfile(gear, profileId);
        if (gear == null) return null;

        List<TargetProfile.TargetEnchantment> targets = new ArrayList<>();
        for (int i = 2; i < get().size(); i++) {
            String[] parts = parseTarget(get().get(i));
            if (parts == null) continue;
            int level;
            try { level = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { continue; }
            boolean excluded = "1".equals(parts[2]);
            boolean required = !isExcludable(profile, parts[0]);
            targets.add(new TargetProfile.TargetEnchantment(parts[0], enchantName(parts[0]), level, excluded, required));
        }

        return new TargetProfile(
            gear.id, gear.name, gear.category,
            profileId == null ? "" : profileId,
            profile == null ? "" : profile.name,
            targets,
            profile == null ? List.of() : profile.exclusiveWith,
            profile == null ? List.of() : profile.forbidden
        );
    }

    /** 刷新摘要文本与装备图标 */
    public void refreshSummary() {
        if (summaryLabel != null) summaryLabel.set(summaryText());
        if (iconLabel != null) iconLabel.set(currentIconStack());
    }

    /** 按当前装备 ID 查原版物品的图标堆，未选择/查不到返回空堆（不渲染） */
    private ItemStack currentIconStack() {
        String gearId = gearId();
        if (gearId == null || gearId.isEmpty()) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(gearId);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return (item == null || item == Items.AIR) ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    private String summaryText() {
        GearEnchantData.GearDefinition gear = GearEnchantData.get().gear(gearId() == null ? "" : gearId());
        String profileId = profileId();
        if (gear == null) return "未选择装备";
        GearEnchantData.GearProfile profile = findProfile(gear, profileId);
        String gearText = gear.name;
        String profileText = profile == null ? "未选择方案" : profile.name;
        int active = currentProfile() == null ? 0 : currentProfile().activeTargets().size();
        return "§a" + gearText + " §8▸ §b" + profileText + " §8▸ §e" + active + " 项目标";
    }

    private void mutateTarget(String enchantId, int level, int excludedFlag) {
        List<String> next = new ArrayList<>(get());
        for (int i = 2; i < next.size(); i++) {
            String[] parts = parseTarget(next.get(i));
            if (parts == null || !parts[0].equals(enchantId)) continue;
            String lvl = level >= 0 ? String.valueOf(level) : parts[1];
            String ex = excludedFlag >= 0 ? String.valueOf(excludedFlag) : parts[2];
            next.set(i, parts[0] + ":" + lvl + ":" + ex);
            set(next);
            return;
        }
    }

    private GearEnchantData.GearProfile findProfile(GearEnchantData.GearDefinition gear, String profileId) {
        if (gear == null || profileId == null) return null;
        for (GearEnchantData.GearProfile profile : gear.profiles) {
            if (profile.id.equals(profileId)) return profile;
        }
        return null;
    }

    /** 判断某附魔是否可排除（核心附魔 excludable=false 不可排除，返回 false 表示必需） */
    private boolean isExcludable(GearEnchantData.GearProfile profile, String enchantId) {
        if (profile == null) return true;
        for (GearEnchantData.TargetDefinition target : profile.targets) {
            if (target.id.equals(enchantId)) return target.excludable;
        }
        return true;
    }

    private GearEnchantData.GearProfile defaultProfile(GearEnchantData.GearDefinition gear) {
        for (GearEnchantData.GearProfile profile : gear.profiles) {
            if (profile.isDefault) return profile;
        }
        return gear.profiles.get(0);
    }

    private String enchantName(String enchantId) {
        GearEnchantData.GearDefinition gear = GearEnchantData.get().gear(gearId() == null ? "" : gearId());
        GearEnchantData.GearProfile profile = findProfile(gear, profileId());
        if (profile != null) {
            for (GearEnchantData.TargetDefinition target : profile.targets) {
                if (target.id.equals(enchantId)) return target.name;
            }
        }
        return enchantId;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}
