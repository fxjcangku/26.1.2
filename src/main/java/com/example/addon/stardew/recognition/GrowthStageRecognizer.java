package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.GrowthStageResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 生长阶段识别接口：种子识别 ≠ 成熟识别，阶段信息只做规划参考。
 *
 * <p>成熟状态可能通过 BlockState / NBT / Data Component / BlockEntity / 服务器自定义数据判断，
 * 运行时实现基于作物档案阶段规则做保守识别，识别不出返回 unknown。</p>
 */
public interface GrowthStageRecognizer {

    /** 识别给定坐标的生长阶段 */
    GrowthStageResult detect(BlockPos pos, BlockState state);
}
