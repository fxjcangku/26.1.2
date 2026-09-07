package com.example.addon.stardew.watering;

import com.example.addon.stardew.model.WaterState;
import com.example.addon.stardew.model.WateringToolProfile;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 浇水服务接口：查询浇水状态、判断是否需要浇水、规划浇水目标。
 *
 * <p>实际「使用工具浇水」的动作由 {@link com.example.addon.stardew.task.StardewWaterTask}
 * 在任务层完成（Observe → Act → Verify），本接口只负责「看与算」，不直接发包。
 * 浇水状态未知时（{@link WaterState#UNKNOWN}）绝不盲目浇水。</p>
 */
public interface WateringService {

    /** 查询某坐标当前的浇水状态 */
    WaterState getWaterState(BlockPos pos);

    /** 是否需要浇水（仅当状态确定为干旱） */
    boolean needsWater(BlockPos pos);

    /** 是否支持一次浇多个格子（取决于当前浇水工具的形状配置） */
    boolean canWaterArea();

    /** 当前可用的浇水工具档案（无工具返回空） */
    Optional<WateringToolProfile> getCurrentWateringTool();

    /** 从候选坐标中筛选出需要浇水的坐标，作为浇水任务的目标 */
    List<BlockPos> planArea(Collection<BlockPos> positions);
}
