package com.example.addon.autofarm.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 成熟状态识别器接口 - 判断作物是否成熟。
 *
 * <p>职责：判断给定作物方块是否达到成熟状态（可收割）。</p>
 */
public interface MaturityRecognizer {

    /**
     * 检测作物成熟状态。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return 成熟状态结果（已知成熟 / 已知未成熟 / 未知）
     */
    MaturityResult detect(BlockPos pos, BlockState state);
}
