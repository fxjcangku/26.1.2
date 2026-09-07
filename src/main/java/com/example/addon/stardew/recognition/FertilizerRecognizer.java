package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.FertilizerResult;
import net.minecraft.core.BlockPos;

/**
 * 肥料识别接口。
 *
 * <p>肥料与种子分离，是否真的需要先施肥由服务器规则配置决定；识别不出施肥状态时
 * 返回 unknown，安全起见不执行施肥。</p>
 */
public interface FertilizerRecognizer {

    /** 识别该坐标当前的施肥状态 */
    FertilizerResult detect(BlockPos pos);
}
