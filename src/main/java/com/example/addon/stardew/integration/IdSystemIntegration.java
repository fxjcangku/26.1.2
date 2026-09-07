package com.example.addon.stardew.integration;

import com.example.addon.itemid.ItemIdManager;
import com.example.addon.itemid.ItemIdentifier;
import com.example.addon.itemid.ItemIdentity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 现有 ID 三件套的星露谷接入层：把「手持种子」识别并注册进现有 ID 系统。
 *
 * <p>星露谷不另建物品数据库，种子识别、身份匹配、持久化全部复用
 * {@link ItemIdManager} 与 {@link ItemIdentity}，这里只做参数与语义的薄封装。</p>
 */
public final class IdSystemIntegration {

    private final ItemIdManager idManager;

    public IdSystemIntegration(ItemIdManager idManager) {
        this.idManager = idManager;
    }

    public ItemIdManager manager() {
        return idManager;
    }

    /** 识别当前手持物品并注册进现有 ID 系统，返回完整身份（已存在则返回已有身份） */
    public ItemIdentity registerFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ItemIdentity identity = ItemIdentifier.identifyItem(stack);
        if (identity == null) return null;

        String filename = idManager.add(identity);
        if (filename != null) return identity;
        // 已存在（重复添加）则返回已有身份，避免后续按引用查不到
        return idManager.findByKey(identity.identityKey());
    }

    /** 按 ID 系统引用键解析回物品注册表项，解析不到返回 null */
    public Item resolveItem(String identityKey) {
        if (identityKey == null || identityKey.isBlank()) return null;
        ItemIdentity identity = idManager.findByKey(identityKey);
        return identity == null ? null : identity.item();
    }
}
