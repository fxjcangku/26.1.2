package com.example.addon.stardew.adapter;

import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewSeedProfile;

import java.util.Optional;

/**
 * 星露谷资源包适配接口（预留，未来接入，不阻塞核心开发）。
 *
 * <p>资源包回来之后只实现本接口的解析与视觉映射，不改写核心自动化引擎。当前
 * {@link NoOpResourcePackAdapter} 提供空实现，保证无资源包也能运行。</p>
 */
public interface StardewResourcePackAdapter {

    /** 资源包是否可用 */
    boolean isAvailable();

    /** 查找种子对应视觉 */
    Optional<ResourcePackVisual> findSeedVisual(StardewSeedProfile seed);

    /** 查找作物对应视觉 */
    Optional<ResourcePackVisual> findCropVisual(StardewCropProfile crop);

    /** 查找指定生长阶段的视觉 */
    Optional<ResourcePackVisual> findGrowthVisual(StardewCropProfile crop, int growthStage);

    /** 查找成熟状态视觉 */
    Optional<ResourcePackVisual> findMatureVisual(StardewCropProfile crop);

    /** 查找农田视觉 */
    Optional<ResourcePackVisual> findSoilVisual();

    /** 查找浇水视觉 */
    Optional<ResourcePackVisual> findWateringVisual();
}
