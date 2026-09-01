package com.example.addon.enchant.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 铁砧规划器（AnvilPlanner）。
 *
 * <p>职责：把「附魔台随机附魔出的多件同类型装备」通过铁砧「装备 + 装备」两两合并，
 * 把分散在各件装备上的目标附魔叠加到一件上，最终凑齐 {@link TargetProfile} 定义的
 * JSON 极品方案。核心是<b>合并判定</b>：脚本要能分辨「哪些装备该合并、合并是否合理」。</p>
 *
 * <p>合并判定三原则：</p>
 * <ul>
 *   <li><b>互补</b>：材料装备带有主装备「缺失」或「等级不足」的目标附魔，合并才有价值；</li>
 *   <li><b>不互斥</b>：材料附魔与主装备现有附魔互斥（如锋利 vs 亡灵杀手）时合并会丢附魔，判定为不合理；</li>
 *   <li><b>不冗余</b>：材料附魔主装备已有且达标，合并无收益，跳过。</li>
 * </ul>
 *
 * <p>不合理的装备不进入合并计划，交由上层砂轮磨掉重来。</p>
 *
 * <p>铁律：<b>绝不自己模拟铁砧 XP 公式</b>。执行时读 {@code AnvilMenu.getCost()} 实际
 * 显示费用作为唯一决策依据。本类只做「附魔集合的合并模拟 + 互斥判断」，用于规划排序，
 * 不估算任何经验数值。</p>
 */
public final class AnvilPlanner {

    /** 生存模式铁砧费用上限（超过即「太昂贵」） */
    public static final int MAX_ANVIL_COST = 39;

    /** 默认最大铁砧操作次数（防无限操作） */
    public static final int DEFAULT_MAX_OPERATIONS = 6;

    private AnvilPlanner() {
    }

    /**
     * 生成「装备 + 装备」铁砧合并计划。
     *
     * @param profile 目标极品方案（JSON 定义）
     * @param gears   多件已附魔的同类型装备（如 4 把钻石剑）
     * @return 铁砧合并计划（空计划表示无需合并或材料不足）
     */
    public static AnvilPlan plan(TargetProfile profile, List<ItemStack> gears) {
        if (gears == null || gears.size() < 2) {
            return new AnvilPlan(List.of(), DEFAULT_MAX_OPERATIONS);
        }

        // 选对目标贡献最多的装备作为主装备（基础件）
        ItemStack base = selectBase(profile, gears);
        Map<String, Integer> baseEnch = new HashMap<>(EnchantEvaluationService.readEnchantments(base));

        List<AnvilStep> steps = new ArrayList<>();
        int order = 1;
        ItemStack current = base.copy();
        // 逐件判定材料装备：互补且不互斥才合并，否则跳过（上层砂轮磨掉）
        for (ItemStack material : gears) {
            if (material == base) continue;
            Map<String, Integer> matEnch = EnchantEvaluationService.readEnchantments(material);
            if (!isComplementary(baseEnch, matEnch, profile)) continue;
            if (hasConflict(baseEnch, matEnch)) continue;
            String contrib = primaryContribution(material, profile);
            steps.add(new AnvilStep(order++, current.copy(), material.copy(), contrib, targetLevelOf(profile, contrib)));
            // 模拟合并后的附魔集合，供后续材料判定使用（仅逻辑模拟，不算 XP）
            mergeInto(baseEnch, matEnch);
        }
        return new AnvilPlan(steps, DEFAULT_MAX_OPERATIONS);
    }

    /** 选对目标贡献最多的装备作为主装备（贡献分数最高者） */
    public static ItemStack selectBase(TargetProfile profile, List<ItemStack> gears) {
        ItemStack best = gears.get(0);
        int bestScore = -1;
        for (ItemStack gear : gears) {
            int score = contributionScore(gear, profile);
            if (score > bestScore) {
                bestScore = score;
                best = gear;
            }
        }
        return best;
    }

    /** 装备对目标的贡献分数：命中目标附魔的数量 */
    public static int contributionScore(ItemStack gear, TargetProfile profile) {
        Map<String, Integer> ench = EnchantEvaluationService.readEnchantments(gear);
        int score = 0;
        for (TargetProfile.TargetEnchantment target : profile.activeTargets()) {
            if (ench.containsKey(target.id())) score++;
        }
        return score;
    }

    /** 装备对目标的主要贡献附魔 id（第一个命中的目标附魔），无贡献返回 null */
    public static String primaryContribution(ItemStack gear, TargetProfile profile) {
        Map<String, Integer> ench = EnchantEvaluationService.readEnchantments(gear);
        for (TargetProfile.TargetEnchantment target : profile.activeTargets()) {
            if (ench.containsKey(target.id())) return target.id();
        }
        return null;
    }

    /** 判断装备是否带目标附魔（可作为合并材料） */
    public static boolean hasTargetContribution(ItemStack gear, TargetProfile profile) {
        return primaryContribution(gear, profile) != null;
    }

    /**
     * 互补性判定：材料是否带来主装备「缺失」或「等级不足」的目标附魔。
     * 材料所有目标附魔主装备都已达标 → 冗余，返回 false。
     */
    private static boolean isComplementary(Map<String, Integer> baseEnch, Map<String, Integer> matEnch, TargetProfile profile) {
        for (TargetProfile.TargetEnchantment target : profile.activeTargets()) {
            Integer matLevel = matEnch.get(target.id());
            if (matLevel == null) continue;
            Integer baseLevel = baseEnch.get(target.id());
            if (baseLevel == null) return true;            // 主装备缺失 → 互补
            if (baseLevel < target.level()) return true;   // 主装备等级不足 → 互补
        }
        return false;
    }

    /** 互斥判定：材料附魔与主装备附魔是否存在互斥（用 26.1.2 官方 areCompatible） */
    private static boolean hasConflict(Map<String, Integer> baseEnch, Map<String, Integer> matEnch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false; // 注册表未就绪，跳过互斥检测
        for (String baseId : baseEnch.keySet()) {
            for (String matId : matEnch.keySet()) {
                if (baseId.equals(matId)) continue; // 相同附魔不互斥（会升级）
                Holder<Enchantment> a = enchantmentOf(mc, baseId);
                Holder<Enchantment> b = enchantmentOf(mc, matId);
                if (a != null && b != null && !Enchantment.areCompatible(a, b)) return true;
            }
        }
        return false;
    }

    /** 附魔 id → Holder，找不到返回 null */
    private static Holder<Enchantment> enchantmentOf(Minecraft mc, String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return null;
        var holder = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .get(ResourceKey.create(Registries.ENCHANTMENT, ident));
        return holder.orElse(null);
    }

    /** 模拟附魔合并（仅用于规划排序，不算 XP）：相同附魔取较高 +1，不同附魔叠加 */
    private static void mergeInto(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> e : source.entrySet()) {
            String id = e.getKey();
            int level = e.getValue();
            Integer existing = target.get(id);
            if (existing == null) {
                target.put(id, level);
            } else if (existing >= level) {
                target.put(id, existing + 1);
            }
        }
    }

    /** 查某附魔在目标方案里的目标等级 */
    private static int targetLevelOf(TargetProfile profile, String enchantId) {
        for (TargetProfile.TargetEnchantment target : profile.activeTargets()) {
            if (target.id().equals(enchantId)) return target.level();
        }
        return 0;
    }

    /**
     * 读取铁砧当前实际显示的等级费用。
     * 这是唯一的费用来源，禁止用自算公式替代。
     */
    public static int readCost(AnvilMenu menu) {
        return menu.getCost();
    }

    /** 判断费用是否「太昂贵」（超过生存上限） */
    public static boolean isTooExpensive(int cost) {
        return cost > MAX_ANVIL_COST;
    }
}
