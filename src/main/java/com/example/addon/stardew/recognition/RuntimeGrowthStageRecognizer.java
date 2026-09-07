package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.GrowthStageResult;
import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 运行时生长阶段识别：按作物档案 cropBlockIds 的顺序推断阶段。
 *
 * <p>阶段只做规划参考，真正执行前必须重新观察真实世界数据；识别不出返回 unknown。</p>
 */
public final class RuntimeGrowthStageRecognizer implements GrowthStageRecognizer {

    private final Supplier<StardewServerProfile> profileSupplier;

    public RuntimeGrowthStageRecognizer(Supplier<StardewServerProfile> profileSupplier) {
        this.profileSupplier = profileSupplier;
    }

    @Override
    public GrowthStageResult detect(BlockPos pos, BlockState state) {
        String blockId = RecognizerSupport.blockId(state);
        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return GrowthStageResult.unknown("未加载服务器档案");

        for (StardewCropProfile crop : profile.crops()) {
            if (!crop.matchesBlock(blockId)) continue;
            int index = crop.cropBlockIds().indexOf(blockId);
            if (index >= 0) {
                int stage = Math.min(index + 1, crop.growthStages());
                return GrowthStageResult.known(stage, crop.growthStages());
            }
            return GrowthStageResult.unknown("未配置阶段方块顺序");
        }
        return GrowthStageResult.unknown("非已知作物");
    }
}
