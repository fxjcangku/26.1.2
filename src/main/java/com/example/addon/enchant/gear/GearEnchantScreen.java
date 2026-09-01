package com.example.addon.enchant.gear;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 原版装备附魔 · 配置界面（WindowScreen）。
 *
 * <p>提供「装备类别 → 目标装备 → 极品方案 → 附魔等级 / 排除」的完整配置流：
 * 类别下拉按 weapon/armor 过滤装备，装备选择后动态过滤 JSON 方案（单方案隐藏下拉），
 * 方案下每个附魔支持等级加减（受 26.1.2 真实最大等级限制）与排除开关。</p>
 *
 * <p>职责边界：本类只负责 UI 交互与回写 {@link GearEnchantSetting}，不解析 JSON，
 * 不做目标判断。所有数据来自 {@link GearEnchantData}。</p>
 */
public final class GearEnchantScreen extends WindowScreen {

    /** 装备类别（UI 显示中文，内部 key 对应 JSON category 字段） */
    private enum GearCategory {
        WEAPON("工具 / 武器", "weapon"),
        ARMOR("护甲", "armor");

        final String title;
        final String key;

        GearCategory(String title, String key) {
            this.title = title;
            this.key = key;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final GearEnchantSetting setting;
    private final GearEnchantData data;
    private String category;
    private WTable table;

    public GearEnchantScreen(GuiTheme theme, GearEnchantSetting setting) {
        super(theme, "原版装备附魔配置");
        this.setting = setting;
        this.data = GearEnchantData.get();
        GearEnchantData.GearDefinition gear = currentGear();
        this.category = gear == null ? "weapon" : gear.category;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();
        rebuild();
    }

    /** 重建整个配置界面（下拉切换 / 等级加减 / 排除后都调用） */
    private void rebuild() {
        table.clear();

        // 类别下拉
        table.add(theme.label("§7装备类别")).expandX();
        WDropdown<GearCategory> categoryDropdown = table.add(theme.dropdown(categoryEnum())).expandCellX().widget();
        categoryDropdown.action = () -> {
            category = categoryDropdown.get().key;
            scheduleRebuild();
        };
        table.row();

        // 装备下拉（按类别过滤）
        List<GearEnchantData.GearDefinition> gears = data.gearsByCategory(category);
        if (gears.isEmpty()) {
            table.add(theme.label("§c该类别暂无可用装备")).expandX();
            return;
        }
        GearEnchantData.GearDefinition currentGear = currentGear();
        GearEnchantData.GearDefinition selectedGear = currentGear != null && category.equals(currentGear.category)
            ? currentGear : gears.get(0);
        table.add(theme.label("§7目标装备")).expandX();
        WDropdown<GearEnchantData.GearDefinition> gearDropdown = table.add(
            theme.dropdown(gears.toArray(new GearEnchantData.GearDefinition[0]), selectedGear)).expandCellX().widget();
        gearDropdown.action = () -> {
            setting.applyGear(gearDropdown.get().id);
            scheduleRebuild();
        };
        table.row();

        // 方案下拉（多方案才显示，单方案直接使用唯一方案）
        GearEnchantData.GearDefinition gear = currentGear();
        GearEnchantData.GearProfile profile = currentProfile(gear);
        if (gear != null && gear.profiles.size() > 1) {
            table.add(theme.label("§7极品方案")).expandX();
            WDropdown<GearEnchantData.GearProfile> profileDropdown = table.add(
                theme.dropdown(gear.profiles.toArray(new GearEnchantData.GearProfile[0]), profile == null ? gear.profiles.get(0) : profile))
                .expandCellX().widget();
            profileDropdown.action = () -> {
                setting.applyProfile(profileDropdown.get().id);
                scheduleRebuild();
            };
            table.row();
        }

        // 附魔列表：等级加减 + 排除开关
        if (gear != null && profile != null) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
            for (GearEnchantData.TargetDefinition target : profile.targets) {
                addEnchantRow(target);
            }
        }

        // 每次重建同步回写摘要标签，保证关闭界面后主面板显示最新配置
        setting.refreshSummary();
    }

    /**
     * 延迟到下一 tick 重建界面。
     * 交互按钮（下拉/加减/启用）的 action 在鼠标释放事件回调里触发，若在此同步 clear + 重建，
     * 会把正在分发事件的控件提前从 widget 树移除，导致事件链中断、控件点击失灵或卡顿。
     */
    private void scheduleRebuild() {
        Minecraft.getInstance().execute(this::rebuild);
    }

    /** 渲染单个附魔行：名称 + 排除开关 + [-] 等级 [+] */
    private void addEnchantRow(GearEnchantData.TargetDefinition target) {
        int rawLevel = setting.levelOf(target.id);
        final int level = rawLevel < 1 ? target.level : rawLevel;
        boolean excluded = setting.isExcluded(target.id);

        table.add(theme.label(excluded ? "§8" + target.name : "§f" + target.name)).expandX();

        // 排除开关（仅可排除附魔显示；核心附魔固定为目标）
        if (target.excludable) {
            WButton exclude = table.add(theme.button(excluded ? "§c已排除" : "§a启用")).widget();
            exclude.action = () -> {
                setting.setExcluded(target.id, !excluded);
                scheduleRebuild();
            };
        } else {
            table.add(theme.label("§8核心")).widget();
        }

        // 等级加减
        WMinus minus = table.add(theme.minus()).widget();
        minus.action = () -> {
            setting.setLevel(target.id, level - 1);
            scheduleRebuild();
        };

        table.add(theme.label("§e" + roman(level))).widget();

        WPlus plus = table.add(theme.plus()).widget();
        plus.action = () -> {
            setting.setLevel(target.id, level + 1);
            scheduleRebuild();
        };

        table.row();
    }

    private GearCategory categoryEnum() {
        return "armor".equals(category) ? GearCategory.ARMOR : GearCategory.WEAPON;
    }

    private GearEnchantData.GearDefinition currentGear() {
        String gearId = setting.gearId();
        return data.gear(gearId == null ? "" : gearId);
    }

    private GearEnchantData.GearProfile currentProfile(GearEnchantData.GearDefinition gear) {
        String profileId = setting.profileId();
        if (gear == null || profileId == null) return null;
        for (GearEnchantData.GearProfile profile : gear.profiles) {
            if (profile.id.equals(profileId)) return profile;
        }
        return null;
    }

    /** 附魔等级罗马数字（1-5 → I-V，超出范围回退阿拉伯数字） */
    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
