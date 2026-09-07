package com.example.addon.stardew.adapter;

import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewSeedProfile;

import java.util.Optional;

/**
 * 资源包空实现：无资源包时统一返回不可用，星露谷核心逻辑照常运行。
 *
 * <p>资源包接入后替换本实现即可，不重写核心自动化引擎。</p>
 */
public final class NoOpResourcePackAdapter implements StardewResourcePackAdapter {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<ResourcePackVisual> findSeedVisual(StardewSeedProfile seed) {
        return Optional.empty();
    }

    @Override
    public Optional<ResourcePackVisual> findCropVisual(StardewCropProfile crop) {
        return Optional.empty();
    }

    @Override
    public Optional<ResourcePackVisual> findGrowthVisual(StardewCropProfile crop, int growthStage) {
        return Optional.empty();
    }

    @Override
    public Optional<ResourcePackVisual> findMatureVisual(StardewCropProfile crop) {
        return Optional.empty();
    }

    @Override
    public Optional<ResourcePackVisual> findSoilVisual() {
        return Optional.empty();
    }

    @Override
    public Optional<ResourcePackVisual> findWateringVisual() {
        return Optional.empty();
    }
}
