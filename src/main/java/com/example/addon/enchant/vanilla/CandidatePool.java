package com.example.addon.enchant.vanilla;

import com.example.addon.enchant.gear.TargetProfile;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版装备极品附魔 · 候选池（CandidatePool）。
 *
 * <p>职责：保存当前批次里「有价值」的已附魔中间装备，供规划器从中挑选铁砧组合。
 * 它是有序、可去重的内存容器，不承担任何附魔规则判断（规则判断归
 * {@link TargetMatcher} / {@link CandidateMatcher}）。</p>
 *
 * <p>设计要点（§25 / §26 / §27）：</p>
 * <ul>
 *   <li>只收录「无禁止 / 无互斥 / 至少命中一个目标」的高价值中间态；</li>
 *   <li>高价值中间态即使未达标也必须保留，不能砂轮（如效率 IV + 时运 III）；</li>
 *   <li>按对目标的贡献度排序，便于 {@code EnchantPlanningEngine} 选主装备。</li>
 * </ul>
 */
public final class CandidatePool {

    /** 候选条目：装备 + 匹配结论 + 贡献度 */
    public record Entry(ItemStack stack, TargetMatcher.Result match, int score) {
        /** 是否为最终成品 */
        public boolean complete() {
            return match.complete();
        }
    }

    private final TargetProfile profile;
    private final Map<ItemStack, Entry> entries = new LinkedHashMap<>();

    public CandidatePool(TargetProfile profile) {
        this.profile = profile;
    }

    /**
     * 收录一件装备（重复装备按引用去重）。
     * 无价值的装备（禁止/互斥/零命中）不入池，返回 false。
     */
    public boolean offer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        TargetMatcher.Result match = TargetMatcher.match(stack, profile);
        if (!match.worthKeeping()) return false;
        entries.put(stack, new Entry(stack, match, score(stack, match)));
        return true;
    }

    /** 批量收录 */
    public void offerAll(List<ItemStack> stacks) {
        for (ItemStack s : stacks) offer(s);
    }

    /** 是否存在已完成目标（COMPLETE）的装备 */
    public ItemStack firstComplete() {
        for (Entry e : entries.values()) {
            if (e.complete()) return e.stack();
        }
        return null;
    }

    /** 全部候选（按贡献度降序） */
    public List<ItemStack> candidates() {
        List<ItemStack> list = new ArrayList<>();
        for (Entry e : entries.values()) list.add(e.stack());
        list.sort(Comparator.comparingInt(s -> -score(s, TargetMatcher.match(s, profile))));
        return list;
    }

    /** 候选数量 */
    public int size() {
        return entries.size();
    }

    /** 按匹配结果 + 目标命中数量给贡献度评分 */
    private int score(ItemStack stack, TargetMatcher.Result match) {
        int score = match.satisfied().size() * 3 + match.underleveled().size();
        return score;
    }
}