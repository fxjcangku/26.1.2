package com.example.addon.villager.trade;

import com.example.addon.villager.data.VillagerTradeTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

/**
 * 交易匹配器
 * 
 * 根据目标白名单和价格限制筛选 MerchantOffer。
 * 
 * 匹配规则：
 * · 输出物品必须在目标白名单内
 * · 绿宝石价格 <= 价格上限
 * · 交易未售罄
 * · 普通物品通过 Item 匹配
 * · 附魔书通过 EnchantmentMatcher 三重匹配
 */
public final class TradeMatcher {

    /**
     * 判断交易是否匹配目标列表
     * 
     * @param offer 村民交易
     * @param targets 目标白名单
     * @param maxPrice 绿宝石价格上限
     * @return true 表示匹配
     */
    public static boolean matches(MerchantOffer offer, List<VillagerTradeTarget> targets, int maxPrice) {
        // 基础验证
        if (offer == null || offer.isOutOfStock()) {
            return false;
        }

        // 价格验证（绿宝石数量）
        int emeraldCost = getEmeraldCost(offer);
        if (emeraldCost > maxPrice) {
            return false;
        }

        // 输出物品验证
        ItemStack result = offer.getResult();
        if (result.isEmpty()) {
            return false;
        }

        // 遍历目标白名单
        for (VillagerTradeTarget target : targets) {
            if (matchesTarget(result, target)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 只判断交易输出物品是否命中目标白名单（忽略售罄与价格，用于区分「未刷出」与「售罄/超价」）。
     */
    public static boolean matchesItemOnly(MerchantOffer offer, List<VillagerTradeTarget> targets) {
        if (offer == null) return false;
        ItemStack result = offer.getResult();
        if (result.isEmpty()) return false;
        for (VillagerTradeTarget target : targets) {
            if (matchesTarget(result, target)) return true;
        }
        return false;
    }

    /**
     * 判断 ItemStack 是否匹配单个目标
     */
    private static boolean matchesTarget(ItemStack stack, VillagerTradeTarget target) {
        // 物品类型必须匹配
        if (stack.getItem() != target.getItem()) {
            return false;
        }

        // 附魔书需要进一步匹配附魔和等级
        if (target.isEnchantedBook()) {
            return EnchantmentMatcher.matches(stack, target);
        }

        // 普通物品：Item 匹配即可
        return true;
    }

    /**
     * 计算交易的绿宝石成本
     * 
     * MerchantOffer 可能消耗：
     * · baseCostA（主要成本，通常是绿宝石）
     * · costB（次要成本，可选）
     * 
     * 本系统只关注绿宝石，暂时只统计 baseCostA。
     * 
     * @param offer 村民交易
     * @return 绿宝石数量
     */
    public static int getEmeraldCost(MerchantOffer offer) {
        ItemStack baseCost = offer.getBaseCostA();
        
        // 绿宝石在 baseCostA（大部分交易如此）
        if (!baseCost.isEmpty() && baseCost.is(Items.EMERALD)) {
            return baseCost.getCount();
        }

        // 如果 baseCostA 不是绿宝石，检查 costB
        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty() && costB.is(Items.EMERALD)) {
            return costB.getCount();
        }

        // 都不是绿宝石，返回 0（此交易不消耗绿宝石）
        return 0;
    }

    /**
     * 获取交易详情（用于调试）
     * 
     * @param offer 村民交易
     * @return 交易描述字符串
     */
    public static String getOfferDetails(MerchantOffer offer) {
        if (offer == null) {
            return "null";
        }

        ItemStack baseCost = offer.getBaseCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();

        String costStr = baseCost.getCount() + "x" + baseCost.getItem();
        if (!costB.isEmpty()) {
            costStr += " + " + costB.getCount() + "x" + costB.getItem();
        }

        String resultStr = result.getCount() + "x" + result.getItem();
        String stockStr = offer.isOutOfStock() ? " [售罄]" : " [剩余:" + (offer.getMaxUses() - offer.getUses()) + "]";

        return costStr + " → " + resultStr + stockStr;
    }

    private TradeMatcher() {
        // 工具类禁止实例化
    }
}
