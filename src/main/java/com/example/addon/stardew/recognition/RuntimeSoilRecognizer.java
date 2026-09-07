package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.CropRecognitionResult;
import com.example.addon.stardew.model.MaturityResult;
import com.example.addon.stardew.model.SoilState;
import com.example.addon.stardew.model.StardewServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 运行时农田识别：底盘方块由服务器档案 soilBlockIds 配置，不写死 Note Block。
 *
 * <p>土地状态推导：底盘正确且上方为空气 → 空地；上方为已识别作物 → 成熟/生长中/已种；
 * 作物成熟状态未知时退回「已种」，绝不误判为成熟。</p>
 */
public final class RuntimeSoilRecognizer implements SoilRecognizer {

    private final Supplier<StardewServerProfile> profileSupplier;
    private final CropRecognizer cropRecognizer;
    private final MatureStateRecognizer matureRecognizer;

    public RuntimeSoilRecognizer(Supplier<StardewServerProfile> profileSupplier,
                                 CropRecognizer cropRecognizer, MatureStateRecognizer matureRecognizer) {
        this.profileSupplier = profileSupplier;
        this.cropRecognizer = cropRecognizer;
        this.matureRecognizer = matureRecognizer;
    }

    @Override
    public boolean isSoil(BlockPos pos) {
        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return false;
        String blockId = RecognizerSupport.blockId(pos);
        return profile.soilBlockIds().contains(blockId);
    }

    @Override
    public SoilState detectState(BlockPos pos) {
        if (!isSoil(pos)) return SoilState.UNKNOWN;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return SoilState.UNKNOWN;

        // 约定：作物生长在底盘上方一格（服务器不同可经适配层扩展）
        BlockPos cropPos = pos.above();
        BlockState above = mc.level.getBlockState(cropPos);
        if (above.isAir()) return SoilState.EMPTY;

        CropRecognitionResult crop = cropRecognizer.recognize(above);
        if (!crop.known()) return SoilState.UNKNOWN;

        MaturityResult maturity = matureRecognizer.detect(cropPos, above);
        if (!maturity.known()) return SoilState.PLANTED;
        return maturity.mature() ? SoilState.MATURE : SoilState.GROWING;
    }
}
