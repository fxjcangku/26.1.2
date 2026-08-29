package com.example.addon.villager.trade;

import com.example.addon.villager.data.VillagerTradeTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Set;

/**
 * 交易引擎（非阻塞工具类，全部在渲染线程调用）
 * 
 * 26.1.2 交易协议要点：
 * · ServerboundSelectTradePacket 只携带 offer 序号，服务端要求玩家当前
 *   containerMenu 必须是 MerchantMenu，未打开村民交易界面时发包会被直接忽略。
 * · 因此交易必须在打开村民交易界面后执行，本类只负责「查找 / 发包 / 计数」，
 *   打开界面与结果确认由状态机驱动，禁止在 tick 内 sleep。
 */
public final class TradeEngine {

    private TradeEngine() {
    }

    /**
     * 查找最便宜的可交易 offer 序号（单次遍历，选择与成本判断严格一致）。
     * 过滤条件：未列入黑名单、未售罄、价格不超上限、输出物品匹配目标白名单。
     *
     * @return offer 序号，无可交易项返回 -1
     */
    public static int findBestOfferIndex(Villager villager, List<VillagerTradeTarget> targets, int maxPrice, Set<Integer> skipIndexes) {
        var offers = villager.getOffers();
        if (offers == null || offers.isEmpty()) return -1;

        int bestIndex = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int i = 0; i < offers.size(); i++) {
            if (skipIndexes.contains(i)) continue;

            MerchantOffer offer = offers.get(i);
            if (offer.isOutOfStock()) continue;
            if (!TradeMatcher.matches(offer, targets, maxPrice)) continue;

            int cost = TradeMatcher.getEmeraldCost(offer);
            if (cost < bestCost) {
                bestCost = cost;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * 发送交易选择包（调用方保证村民交易界面已打开）。
     */
    public static void sendSelectTrade(int offerIndex) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) return;

        player.connection.send(new ServerboundSelectTradePacket(offerIndex));
        player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 统计玩家背包（含副手与盔甲槽）中指定物品的数量。
     */
    public static int countItem(Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;

        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 统计玩家背包绿宝石数量。
     */
    public static int countEmeralds() {
        return countItem(Items.EMERALD);
    }

    /**
     * 主背包（0-35）是否还有空槽位。
     */
    public static boolean hasSpace() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) return true;
        }
        return false;
    }

    /**
     * 读当前村民 offer 序号对应的结果数量（offers 可能已被服务端刷新替换，需防御越界）。
     */
    public static int safeResultCount(Villager villager, int offerIndex) {
        var offers = villager.getOffers();
        if (offers == null || offerIndex < 0 || offerIndex >= offers.size()) return 1;

        ItemStack result = offers.get(offerIndex).getResult();
        return result.isEmpty() ? 1 : result.getCount();
    }
}