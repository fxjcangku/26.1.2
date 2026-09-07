package com.example.addon.autofarm.adapter;

import com.example.addon.autofarm.recognition.CropRecognitionResult;
import com.example.addon.autofarm.recognition.CropRecognizer;
import com.example.addon.autofarm.recognition.MaturityRecognizer;
import com.example.addon.autofarm.recognition.MaturityResult;
import com.example.addon.autofarm.recognition.SoilRecognizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 默认农场适配器 - 组合识别器实现适配器接口。
 *
 * <p>职责：组合识别器（作物/成熟/农田），提供统一适配入口。</p>
 */
public final class DefaultFarmAdapter implements FarmServerAdapter {

    private final CropRecognizer cropRecognizer;
    private final MaturityRecognizer maturityRecognizer;
    private final SoilRecognizer soilRecognizer;

    public DefaultFarmAdapter(CropRecognizer cropRecognizer,
                              MaturityRecognizer maturityRecognizer,
                              SoilRecognizer soilRecognizer) {
        this.cropRecognizer = cropRecognizer;
        this.maturityRecognizer = maturityRecognizer;
        this.soilRecognizer = soilRecognizer;
    }

    @Override
    public CropRecognitionResult recognizeCrop(BlockPos pos, BlockState state) {
        return cropRecognizer.recognize(pos, state);
    }

    @Override
    public MaturityResult detectMaturity(BlockPos pos, BlockState state) {
        return maturityRecognizer.detect(pos, state);
    }

    @Override
    public boolean isSoil(BlockPos pos, BlockState state) {
        return soilRecognizer.isSoil(pos, state);
    }

    @Override
    public boolean isEmpty(BlockPos pos, BlockState state) {
        return soilRecognizer.isEmpty(pos, state);
    }
}
