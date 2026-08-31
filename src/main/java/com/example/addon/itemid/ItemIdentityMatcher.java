package com.example.addon.itemid;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品身份匹配层：把容器槽位 ItemStack 与目标身份集合做「完整 ItemIdentity」匹配。
 *
 * <p>这是「辅助」三功能共享的匹配环节，位于识别（{@link ItemIdentifier}）之后，
 * 链路为 ItemStack → ItemIdentifier → ItemIdentity → ItemIdentityMatcher。匹配以
 * {@link ItemIdentity#matches(ItemStack)} 的组件级判据为准——Item ID + 自定义名 +
 * Data Components + 附魔——不做中文名等弱比较。</p>
 */
public final class ItemIdentityMatcher {

    private ItemIdentityMatcher() {
        // 工具类，禁止实例化
    }

    /**
     * 在目标身份集合中查找第一个命中给定 ItemStack 的身份。
     *
     * <p>命中的身份连同其身份键一起返回，供 AutoChest 做差额取物时回查目标数量。
     * 目标集合为空或未命中返回 null。</p>
     *
     * @param stack   容器槽位物品
     * @param targets 目标身份集合（AutoChest 目标列表）
     * @return 命中的 ItemIdentity；无命中返回 null
     */
    public static ItemIdentity matchTarget(ItemStack stack, List<ItemIdentity> targets) {
        if (stack == null || stack.isEmpty() || targets == null) return null;
        for (ItemIdentity identity : targets) {
            if (identity.matches(stack)) return identity;
        }
        return null;
    }
}
