package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.CropRecognitionResult;
import com.example.addon.stardew.model.StardewCropProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 运行时作物识别：按作物档案的方块 ID 集合匹配。
 *
 * <p>未知方块安全返回 unknown，调用方不得据此执行收割/种植。</p>
 */
public final class RuntimeCropRecognizer implements CropRecognizer {

    private final Supplier<StardewServerProfile> profileSupplier;

    public RuntimeCropRecognizer(Supplier<StardewServerProfile> profileSupplier) {
        this.profileSupplier = profileSupplier;
    }

    @Override
    public CropRecognitionResult recognize(BlockState state) {
        String blockId = RecognizerSupport.blockId(state);
        if (blockId.isEmpty()) return CropRecognitionResult.unknown(blockId);

        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return CropRecognitionResult.unknown(blockId);

        for (StardewCropProfile crop : profile.crops()) {
            if (crop.matchesBlock(blockId)) {
                return CropRecognitionResult.known(crop.cropId(), crop.displayName(), blockId);
            }
        }
        return CropRecognitionResult.unknown(blockId);
    }
}
