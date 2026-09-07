package com.example.addon.autofarm.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 作物识别器接口 - 识别方块是否为作物及其分类。
 *
 * <p>职责：判断给定方块是否为已知作物，返回作物类型（双作物/单作物/柱状/果实）。</p>
 */
public interface CropRecognizer {

    /**
     * 识别方块状态是否为作物。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return 识别结果（已知作物 / 未知）
     */
    CropRecognitionResult recognize(BlockPos pos, BlockState state);
}
