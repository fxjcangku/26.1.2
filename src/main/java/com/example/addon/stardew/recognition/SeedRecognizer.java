package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.SeedRecognitionResult;
import net.minecraft.world.item.ItemStack;

/**
 * 种子识别接口（接口优先，资源包增强不重写业务层）。
 *
 * <p>运行时实现 {@link RuntimeSeedRecognizer} 基于 ID 三件套 + 物品 ID 匹配；
 * 未来 {@code ResourcePackSeedRecognizer} 只补充视觉/映射，不改识别主线。</p>
 */
public interface SeedRecognizer {

    /** 识别一个 ItemStack 是否为已配置种子 */
    SeedRecognitionResult recognize(ItemStack stack);
}
