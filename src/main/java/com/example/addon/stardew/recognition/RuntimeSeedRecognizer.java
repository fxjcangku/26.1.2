package com.example.addon.stardew.recognition;

import com.example.addon.itemid.ItemIdManager;
import com.example.addon.itemid.ItemIdentity;
import com.example.addon.stardew.model.SeedRecognitionResult;
import com.example.addon.stardew.model.StardewSeedProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * 运行时种子识别：基于 ID 三件套 + 物品 ID 匹配，不依赖资源包。
 *
 * <p>识别顺序：先按种子档案的 idSystemReference 回查 {@link ItemIdentity} 做组件级精确匹配
 * （复用 ID 系统，不另建物品数据库），回查失败退化为物品 ID 粗筛。未知种子安全返回 unknown。</p>
 */
public final class RuntimeSeedRecognizer implements SeedRecognizer {

    private final Supplier<StardewServerProfile> profileSupplier;
    private final ItemIdManager idManager;

    public RuntimeSeedRecognizer(Supplier<StardewServerProfile> profileSupplier, ItemIdManager idManager) {
        this.profileSupplier = profileSupplier;
        this.idManager = idManager;
    }

    @Override
    public SeedRecognitionResult recognize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return SeedRecognitionResult.unknown("空物品");
        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return SeedRecognitionResult.unknown("未加载服务器档案");

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        for (StardewSeedProfile seed : profile.seeds()) {
            if (!seed.enabled()) continue;

            // 组件级精确匹配：优先走 ID 系统回查的完整身份
            ItemIdentity identity = idManager.findByKey(seed.idSystemReference());
            if (identity != null && identity.matches(stack)) {
                return SeedRecognitionResult.known(seed.seedId(), seed.displayName(), itemId);
            }

            // 退化：物品 ID 粗筛（无 ID 系统引用或组件不匹配时）
            if (itemId.equals(seed.minecraftItemId())) {
                return SeedRecognitionResult.known(seed.seedId(), seed.displayName(), itemId);
            }
        }
        return SeedRecognitionResult.unknown("未识别为任何已配置种子");
    }
}
