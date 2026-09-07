package com.example.addon.autofarm.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 农田识别器接口 - 判断方块是否为农田底盘。
 *
 * <p>职责：判断给定方块是否为合法农田（耕地/草方块/泥土）。</p>
 */
public interface SoilRecognizer {

    /**
     * 检测方块是否为农田底盘。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return true=农田底盘，false=非农田
     */
    boolean isSoil(BlockPos pos, BlockState state);

    /**
     * 检测方块是否为空地（可种植）。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return true=空地，false=已被占用或非农田
     */
    boolean isEmpty(BlockPos pos, BlockState state);
}
