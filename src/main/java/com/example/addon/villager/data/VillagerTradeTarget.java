package com.example.addon.villager.data;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * 村民交易目标定义
 * 
 * 代表一个交易目标：
 * · 普通物品（直接指定 Item）
 * · 附魔书（Item = ENCHANTED_BOOK + enchantmentId）
 */
public class VillagerTradeTarget {

    private final Item item;
    private final String displayName;
    private String enchantmentId; // 附魔ID（仅附魔书使用）
    
    public VillagerTradeTarget(Item item, String displayName) {
        this.item = item;
        this.displayName = displayName;
        this.enchantmentId = null;
    }
    
    public Item getItem() {
        return item;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 设置附魔ID（用于附魔书匹配）
     */
    public void setEnchantmentId(String enchantmentId) {
        this.enchantmentId = enchantmentId;
    }
    
    /**
     * 获取附魔ID
     */
    public String getEnchantmentId() {
        return enchantmentId;
    }
    
    /**
     * 是否为附魔书目标
     */
    public boolean isEnchantedBook() {
        return item == Items.ENCHANTED_BOOK;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        VillagerTradeTarget that = (VillagerTradeTarget) obj;
        return Objects.equals(item, that.item) &&
               Objects.equals(enchantmentId, that.enchantmentId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(item, enchantmentId);
    }
    
    @Override
    public String toString() {
        if (enchantmentId != null) {
            return String.format("VillagerTradeTarget[%s, enchant=%s]", item, enchantmentId);
        }
        return String.format("VillagerTradeTarget[%s]", item);
    }
}
