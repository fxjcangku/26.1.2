package com.example.addon.enchant.vanilla;

import com.example.addon.enchant.gear.EnchantEvaluationService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 原版装备极品附魔 · 30 级候选匹配器（CandidateMatcher）。
 *
 * <p>职责：判断「附魔台真实产出的 ItemStack」是否属于该装备 30 级候选池的合法结果。
 * 三要素校验（§10 / §11 / §66）：</p>
 * <ol>
 *   <li>每个实装附魔都在该装备 30 级候选池（附魔台适用附魔 + 非宝藏）内；</li>
 *   <li>每个附魔等级都在候选池计算出的可达等级范围内（不含宝藏越级产物）；</li>
 *   <li>多个附魔之间不违反 26.1.2 exclusive_set 互斥。</li>
 * </ol>
 *
 * <p>用于把真实游戏附魔结果正确收进 {@link CandidatePool}，而非凭空猜「点了第一格
 * 所以是效率 V」。任何一项不合法即标记为无效候选，不进入规划。</p>
 */
public final class CandidateMatcher {

    private CandidateMatcher() {
    }

    /** 单件装备的候选校验结果 */
    public record Result(boolean valid,
                         Set<String> notInPool,
                         Set<String> levelMismatch,
                         Set<String> conflicts,
                         String detail) {

        /** 是否为合法 30 级候选（可入候选池） */
        public boolean acceptable() {
            return valid;
        }
    }

    /**
     * 校验实际装备是否为该装备 30 级候选池的合法结果。
     *
     * @param stack 附魔台实际产出装备
     */
    public static Result match(ItemStack stack) {
        Map<String, Integer> actual = EnchantEvaluationService.readEnchantments(stack);
        String itemId = itemIdOf(stack);
        VanillaEnchantDatabase db = VanillaEnchantDatabase.get();
        VanillaEnchantDatabase.GearCandidateRule gear = db.gear(itemId);

        Set<String> notInPool = new LinkedHashSet<>();
        Set<String> levelMismatch = new LinkedHashSet<>();
        Set<String> conflicts = new LinkedHashSet<>();

        // 附魔台适用性 + 等级可达性（§66）
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            String id = e.getKey();
            if (gear == null || !gear.tableReachable(id)) {
                notInPool.add(id);
                continue;
            }
            var reachable = gear.reachable(id);
            if (!reachable.contains(e.getValue())) {
                levelMismatch.add(id);
            }
        }

        // 多附魔互斥（26.1.2 exclusive_set）
        String[] ids = actual.keySet().toArray(new String[0]);
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                if (db.conflictsWith(ids[i], ids[j])) {
                    conflicts.add(ids[i]);
                    conflicts.add(ids[j]);
                }
            }
        }

        boolean valid = notInPool.isEmpty() && levelMismatch.isEmpty() && conflicts.isEmpty();
        return new Result(valid, notInPool, levelMismatch, conflicts,
            valid ? "合法 30 级候选" : "无效候选");
    }

    /** 装备 id（含命名空间） */
    private static String itemIdOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}