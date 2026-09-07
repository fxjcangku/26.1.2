package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.MaturityResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 成熟状态识别接口（多层识别主入口）。
 *
 * <p>安全原则：未知成熟状态绝不收割。运行时实现先按成熟方块 ID 命中，再按可选属性规则
 * （如 age）兜底，仍无法确定则返回 unknown。</p>
 */
public interface MatureStateRecognizer {

    /** 识别给定坐标是否成熟（unknown 表示无法确定，禁止收割） */
    MaturityResult detect(BlockPos pos, BlockState state);
}
