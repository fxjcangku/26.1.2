package com.example.addon.autochest.scan;

import com.example.addon.autochest.model.ChestTarget;
import com.example.addon.autochest.service.ContainerRecordManager;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.List;

/**
 * 容器选择器：从扫描候选 / 标点候选中选出「下一个要处理的容器」。
 *
 * <p>选择规则：</p>
 * <ol>
 *   <li>过滤掉非当前维度的容器（维度隔离）。</li>
 *   <li>过滤掉已处理的容器（{@link ContainerRecordManager}，含容器类型比对）。</li>
 *   <li>按到玩家的直线距离升序，取最近的一个。</li>
 * </ol>
 */
public final class ContainerSelector {

    /**
     * 从候选中选出最优目标。
     *
     * @param candidates 候选容器列表
     * @param playerPos  玩家当前坐标
     * @param dimension  当前维度
     * @param records    已处理记录管理器
     * @param expireMs   已处理记录的过期时长（毫秒）
     * @return 最优目标；无可选返回 null
     */
    public ChestTarget select(List<ChestTarget> candidates, BlockPos playerPos, String dimension,
                              ContainerRecordManager records, long expireMs) {
        if (candidates == null || candidates.isEmpty()) return null;

        return candidates.stream()
            .filter(ChestTarget::inCurrentDimension)
            .filter(c -> !records.isProcessed(c.pos(), dimension, c.containerType(), expireMs))
            .min(Comparator.comparingDouble(c -> c.distSqr(playerPos)))
            .orElse(null);
    }
}
