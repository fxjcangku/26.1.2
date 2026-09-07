package com.example.addon.stardew.farming;

import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewFertilizerProfile;
import com.example.addon.stardew.model.StardewSeedProfile;
import net.minecraft.core.BlockPos;

/**
 * 一次农田规划动作：FarmDayManager 观察后产出的「接下来做什么」。
 *
 * <p>只描述意图与目标，不直接执行；执行由 {@link com.example.addon.stardew.task} 层的
 * FarmTask 完成，保持观察/决策与执行分离。</p>
 */
public record FarmAction(
    Kind kind,
    BlockPos soilPos,
    StardewCropProfile crop,
    StardewSeedProfile seed,
    StardewFertilizerProfile fertilizer
) {

    public enum Kind {
        /** 收割成熟作物 */
        HARVEST,
        /** 浇水 */
        WATER,
        /** 施肥 */
        FERTILIZE,
        /** 种植 */
        PLANT
    }
}
