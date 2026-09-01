package com.example.addon.enchant.gear;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.client.Minecraft;

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
        WButton select = table.add(theme.button("配置装备附魔")).expandCellX().widget();
        WLabel summary = table.add(theme.label(setting.summaryText())).widget();
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        select.action = () -> Minecraft.getInstance().setScreen(new GearEnchantScreen(theme, setting));
        reset.action = setting::reset;
        // 摘要标签缓存，由 GearEnchantScreen 关闭时调用 refreshSummary 刷新
        setting.summaryLabel = summary;
    }

    private WLabel summaryLabel;

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

    /** 调整某附魔等级（受真实最大等级限制，clamp 到 [1, max]） */
    public void setLevel(String enchantId, int level) {
        int max = GearEnchantData.get().maxLevelOf(enchantId);
        if (max < 1) max = 1;
        int clamped = Math.max(1, Math.min(level, max));
        mutateTarget(enchantId, clamped, -1);
    }

    /** 切换某附魔的排除状态 */
    public void setExcluded(String enchantId, boolean excluded) {
        mutateTarget(enchantId, -1, excluded ? 1 : 0);
    }

    /** 读取某附魔当前等级，不存在返回 -1 */
    public int levelOf(String enchantId) {
        for (int i = 2; i < get().size(); i++) {
            String[] parts = get().get(i).split(":");
            if (parts.length == 3 && parts[0].equals(enchantId)) {
                try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { return -1; }
            }
        }
        return -1;
    }

    /** 读取某附魔是否被排除 */
    public boolean isExcluded(String enchantId) {
        for (int i = 2; i < get().size(); i++) {
            String[] parts = get().get(i).split(":");
            if (parts.length == 3 && parts[0].equals(enchantId)) return "1".equals(parts[2]);
        }
        return false;
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
            String[] parts = get().get(i).split(":");
            if (parts.length != 3) continue;
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
            profile == null ? List.of() : profile.exclusiveWith
        );
    }

    /** 刷新摘要文本 */
    public void refreshSummary() {
        if (summaryLabel != null) summaryLabel.set(summaryText());
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
            String[] parts = next.get(i).split(":");
            if (parts.length == 3 && parts[0].equals(enchantId)) {
                int lvl = level >= 0 ? level : Integer.parseInt(parts[1]);
                int ex = excludedFlag >= 0 ? excludedFlag : Integer.parseInt(parts[2]);
                next.set(i, enchantId + ":" + lvl + ":" + ex);
                set(next);
                return;
            }
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
