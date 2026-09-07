package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.MaturityResult;
import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 运行时成熟状态识别：多层识别（成熟方块 ID → 属性规则 → 未知安全停止）。
 *
 * <p>不单纯依赖纹理判断成熟，以世界实际 BlockState 为准。识别不出成熟状态时安全返回
 * unknown，绝不盲目收割。</p>
 */
public final class RuntimeMatureStateRecognizer implements MatureStateRecognizer {

    private final Supplier<StardewServerProfile> profileSupplier;

    public RuntimeMatureStateRecognizer(Supplier<StardewServerProfile> profileSupplier) {
        this.profileSupplier = profileSupplier;
    }

    @Override
    public MaturityResult detect(BlockPos pos, BlockState state) {
        String blockId = RecognizerSupport.blockId(state);
        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return MaturityResult.unknown("未加载服务器档案");

        for (StardewCropProfile crop : profile.crops()) {
            if (!crop.matchesBlock(blockId)) continue;

            // 第一层：成熟方块 ID 直接命中
            if (crop.matureBlockIds().contains(blockId)) {
                return MaturityResult.of(true);
            }

            // 第二层：BlockState 属性规则（如 age 到顶）
            if (crop.maturePropertyName() != null && crop.maturePropertyValue() != null) {
                String value = RecognizerSupport.propertyValue(state, crop.maturePropertyName());
                if (value != null) {
                    return MaturityResult.of(value.equals(crop.maturePropertyValue()));
                }
            }

            // 已识别为作物但成熟规则缺失：安全未知，等待配置
            return MaturityResult.unknown("作物已识别但未配置成熟规则");
        }
        return MaturityResult.unknown("非已知作物");
    }
}
