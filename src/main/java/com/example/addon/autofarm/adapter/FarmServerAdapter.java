package com.example.addon.autofarm.adapter;

import com.example.addon.autofarm.recognition.CropRecognitionResult;
import com.example.addon.autofarm.recognition.MaturityResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 农场服务器适配器接口 - 统一服务器差异识别入口。
 *
 * <p>职责：封装作物/成熟/农田识别逻辑，隔离服务器差异（原版/插件/模组服务器）。</p>
 */
public interface FarmServerAdapter {

    /**
     * 识别作物。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return 识别结果
     */
    CropRecognitionResult recognizeCrop(BlockPos pos, BlockState state);

    /**
     * 检测成熟状态。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return 成熟结果
     */
    MaturityResult detectMaturity(BlockPos pos, BlockState state);

    /**
     * 检测是否为农田底盘。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return true=农田，false=非农田
     */
    boolean isSoil(BlockPos pos, BlockState state);

    /**
     * 检测是否为空地（可种植）。
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return true=空地，false=已占用
     */
    boolean isEmpty(BlockPos pos, BlockState state);
}
