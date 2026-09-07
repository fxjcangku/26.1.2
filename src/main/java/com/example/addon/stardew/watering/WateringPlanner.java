package com.example.addon.stardew.watering;

import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.model.StardewServerProfile;
import com.example.addon.stardew.model.WaterState;
import com.example.addon.stardew.model.WateringToolProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 浇水规划器（默认实现）：基于服务器适配层查询浇水状态并筛选需要浇水的坐标。
 *
 * <p>浇水范围不写死：范围/形状全部由 {@link WateringToolProfile} 配置决定，
 * 当前无资源包 / 服务器专属规则时，浇水状态由适配层安全返回未知，本规划器不会
 * 盲目把坐标判为「需要浇水」。</p>
 */
public final class WateringPlanner implements WateringService {

    private final StardewServerAdapter adapter;
    private final Supplier<StardewServerProfile> profileSupplier;

    public WateringPlanner(StardewServerAdapter adapter, Supplier<StardewServerProfile> profileSupplier) {
        this.adapter = adapter;
        this.profileSupplier = profileSupplier;
    }

    @Override
    public WaterState getWaterState(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? null : mc.level.getBlockState(pos);
        return adapter.detectWaterState(pos, state).state();
    }

    @Override
    public boolean needsWater(BlockPos pos) {
        return getWaterState(pos) == WaterState.DRY;
    }

    @Override
    public boolean canWaterArea() {
        Optional<WateringToolProfile> tool = getCurrentWateringTool();
        return tool.isPresent() && !"single".equalsIgnoreCase(tool.get().shape());
    }

    @Override
    public Optional<WateringToolProfile> getCurrentWateringTool() {
        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return Optional.empty();
        // 简化策略：取第一个配置的浇水工具。未来可结合手持物品精确匹配 toolId。
        for (WateringToolProfile tool : profile.wateringTools()) {
            return Optional.of(tool);
        }
        return Optional.empty();
    }

    @Override
    public List<BlockPos> planArea(Collection<BlockPos> positions) {
        List<BlockPos> result = new ArrayList<>();
        if (positions == null) return result;
        for (BlockPos pos : positions) {
            if (needsWater(pos)) result.add(pos);
        }
        return result;
    }
}
