package com.example.addon.enchant.gear;

import com.example.addon.enchant.vanilla.VanillaEnchantDatabase;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * 原版装备附魔 · 配置界面（WindowScreen）。
 *
 * <p>三级分类动态生成装备选择器：大类（工具 / 武器 / 护甲）→ 类型（镐 / 斧 / 剑 / 头盔…）
 * → 材质（木 / 石 / 铜 / 铁 / 金 / 钻石 / 下界合金 / 皮革 / 锁链 / 海龟壳）。</p>
 *
 * <p>装备全集来自 {@link VanillaEnchantDatabase}（官方 26.1.2 Item Registry，75 件），
 * 绝不硬编码按钮、绝不显示虚构 Item（如钻石弓 / 铜弓）。选择装备后按其 ID 回写
 * {@link GearEnchantSetting}，再由 {@link GearEnchantData} 提供对应极品方案；无方案的
 * 装备明确提示「暂无极品方案」，不凭空生成目标。</p>
 */
public final class GearEnchantScreen extends WindowScreen {

    private final GearEnchantSetting setting;
    private final GearEnchantData data;
    private String categoryKey;
    private String typeKey;
    private String materialKey;
    private WTable table;

    public GearEnchantScreen(GuiTheme theme, GearEnchantSetting setting) {
        super(theme, "原版装备附魔配置");
        this.setting = setting;
        this.data = GearEnchantData.get();
        // 从当前已选装备反推三级分类（未选则默认 工具→镐→钻石）
        VanillaEnchantDatabase.GearCandidateRule current = currentRule();
        this.categoryKey = current == null ? "TOOL" : prefix(current.category());
        this.typeKey = current == null ? "PICKAXE" : suffix(current.category());
        this.materialKey = current == null ? "diamond" : (current.material() == null ? "none" : current.material());
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();
        rebuild();
    }

    /** 重建整个配置界面（任何下拉切换 / 等级加减 / 排除后都调用） */
    private void rebuild() {
        table.clear();

        // 顶部：当前所选装备官方图标 + 名称
        VanillaEnchantDatabase.GearCandidateRule rule = GearCatalog.gear(categoryKey, materialKey, typeKey);
        if (rule != null) {
            WHorizontalList header = table.add(theme.horizontalList()).expandX().centerX().widget();
            header.add(gearIcon(rule.itemId()));
            header.spacing = 8;
            header.add(theme.label("§b§l" + rule.name())).pad(6);
            header.add(theme.label("§8" + rule.itemId())).pad(6);
            table.row();
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        // 第一层：大类
        List<GearCatalog.Category> categories = GearCatalog.categories();
        GearCatalog.Category selectedCategory = findCategory(categoryKey);
        table.add(theme.label("§7类别")).expandX();
        WDropdown<GearCatalog.Category> categoryDropdown = table.add(
            theme.dropdown(categories.toArray(new GearCatalog.Category[0]), selectedCategory)).expandCellX().widget();
        categoryDropdown.action = () -> {
            categoryKey = categoryDropdown.get().key();
            materialKey = GearCatalog.materials(categoryKey).get(0).key();
            typeKey = GearCatalog.types(categoryKey, materialKey).get(0).key();
            rebuild();
        };
        table.row();

        // 第二层：品质（材质）
        List<GearCatalog.Material> materials = GearCatalog.materials(categoryKey);
        if (materials.isEmpty()) {
            table.add(theme.label("§c该类别暂无装备")).expandX();
            return;
        }
        GearCatalog.Material selectedMaterial = findMaterial(materialKey, materials);
        table.add(theme.label("§7品质")).expandX();
        WDropdown<GearCatalog.Material> materialDropdown = table.add(
            theme.dropdown(materials.toArray(new GearCatalog.Material[0]), selectedMaterial)).expandCellX().widget();
        materialDropdown.action = () -> {
            materialKey = materialDropdown.get().key();
            typeKey = GearCatalog.types(categoryKey, materialKey).get(0).key();
            rebuild();
        };
        table.row();

        // 第三层：类型（每个类型一行：官方小图标 + 名称按钮，选中加 ✓）
        List<GearCatalog.Type> types = GearCatalog.types(categoryKey, materialKey);
        table.add(theme.label("§7类型")).expandX();
        table.row();
        for (GearCatalog.Type t : types) {
            VanillaEnchantDatabase.GearCandidateRule g = GearCatalog.gear(categoryKey, materialKey, t.key());
            WHorizontalList row = table.add(theme.horizontalList()).expandX().widget();
            row.spacing = 6;
            row.add(gearIcon(g == null ? null : g.itemId()));
            boolean selected = t.key().equals(typeKey);
            WButton btn = row.add(theme.button((selected ? "§a§l✓ " : "§7") + t.title())).expandCellX().widget();
            btn.action = () -> {
                typeKey = t.key();
                rebuild();
            };
            table.row();
        }
        table.row();

        // 确定唯一装备后，查其极品方案
        if (rule == null) {
            table.add(theme.label("§c未找到对应装备")).expandX();
            return;
        }
        GearEnchantData.GearDefinition profileGear = data.gear(rule.itemId());
        if (profileGear == null || profileGear.profiles.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
            table.add(theme.label("§7该装备 §c暂无极品方案§7，仅作为官方装备数据收录。")).expandX();
            return;
        }

        // 方案下拉（多方案才显示，单方案直接用唯一方案）
        table.add(theme.label("§7极品方案")).expandX();
        GearEnchantData.GearProfile profile = currentProfile(profileGear);
        if (profileGear.profiles.size() > 1) {
            WDropdown<GearEnchantData.GearProfile> profileDropdown = table.add(
                theme.dropdown(profileGear.profiles.toArray(new GearEnchantData.GearProfile[0]),
                    profile == null ? profileGear.profiles.get(0) : profile)).expandCellX().widget();
            profileDropdown.action = () -> {
                setting.applyProfile(profileDropdown.get().id);
                rebuild();
            };
        } else {
            table.add(theme.label("§b" + (profile == null ? profileGear.profiles.get(0).name : profile.name))).widget();
        }
        table.row();

        // 附魔列表：等级加减 + 排除开关
        if (profile != null) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
            for (GearEnchantData.TargetDefinition target : profile.targets) {
                addEnchantRow(target);
            }
        }

        setting.refreshSummary();
    }

    /** 渲染单个附魔行：名称 + 排除开关 + [-] 等级 [+] */
    private void addEnchantRow(GearEnchantData.TargetDefinition target) {
        int rawLevel = setting.levelOf(target.id);
        final int level = rawLevel < 1 ? target.level : rawLevel;
        boolean excluded = setting.isExcluded(target.id);

        table.add(theme.label(excluded ? "§8" + target.name : "§f" + target.name)).expandX();

        if (target.excludable) {
            WButton exclude = table.add(theme.button(excluded ? "§c已排除" : "§a启用")).widget();
            exclude.action = () -> {
                setting.setExcluded(target.id, !excluded);
                rebuild();
            };
        } else {
            table.add(theme.label("§8核心")).widget();
        }

        WMinus minus = table.add(theme.minus()).widget();
        minus.action = () -> {
            setting.setLevel(target.id, level - 1);
            rebuild();
        };

        table.add(theme.label("§e" + roman(level))).widget();

        WPlus plus = table.add(theme.plus()).widget();
        plus.action = () -> {
            setting.setLevel(target.id, level + 1);
            rebuild();
        };

        table.row();
    }

    /** 按装备 ID 查原版物品并渲染官方图标（带附魔光效，暗示极品目标） */
    private WItem gearIcon(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null) return theme.item(ItemStack.EMPTY);
        ItemStack stack = item.getDefaultInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Holder<Enchantment> unbreaking = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(Enchantments.UNBREAKING)
                .orElseThrow();
            Utils.addEnchantment(stack, unbreaking, 1);
        }
        return theme.item(stack);
    }

    /** 当前已选装备对应的规则条目（未选/不在全集返回 null） */
    private VanillaEnchantDatabase.GearCandidateRule currentRule() {
        String gearId = setting.gearId();
        if (gearId == null) return null;
        return VanillaEnchantDatabase.get().gear(gearId);
    }

    private GearEnchantData.GearProfile currentProfile(GearEnchantData.GearDefinition gear) {
        String profileId = setting.profileId();
        if (gear == null || profileId == null) return null;
        for (GearEnchantData.GearProfile profile : gear.profiles) {
            if (profile.id.equals(profileId)) return profile;
        }
        return null;
    }

    private GearCatalog.Category findCategory(String key) {
        for (GearCatalog.Category c : GearCatalog.categories()) {
            if (c.key().equals(key)) return c;
        }
        return GearCatalog.categories().get(0);
    }

    private GearCatalog.Material findMaterial(String key, List<GearCatalog.Material> materials) {
        for (GearCatalog.Material m : materials) {
            if (m.key().equals(key)) return m;
        }
        return materials.get(0);
    }

    private static String prefix(String category) {
        int idx = category.indexOf('_');
        return idx <= 0 ? category : category.substring(0, idx);
    }

    private static String suffix(String category) {
        int idx = category.indexOf('_');
        return idx <= 0 ? category : category.substring(idx + 1);
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
