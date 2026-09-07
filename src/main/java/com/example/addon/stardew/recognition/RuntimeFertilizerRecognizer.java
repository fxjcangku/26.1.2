package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.FertilizerResult;
import net.minecraft.core.BlockPos;

/**
 * 运行时肥料识别。
 *
 * <p>当前无资源包且无服务器专属数据，施肥状态无法可靠判定，安全返回 unknown，
 * 由 {@link com.example.addon.stardew.adapter.StardewServerAdapter} 决定是否施肥。
 * 资源包 / 服务器适配层补充后仅需替换本实现。</p>
 */
public final class RuntimeFertilizerRecognizer implements FertilizerRecognizer {

    @Override
    public FertilizerResult detect(BlockPos pos) {
        // 无服务器数据源时无法可靠判定施肥状态，安全起见返回未知，避免误施肥/重复施肥
        return FertilizerResult.unknown("施肥状态需服务器规则或资源包辅助");
    }
}
