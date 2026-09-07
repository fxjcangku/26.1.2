package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.SoilState;
import net.minecraft.core.BlockPos;

/**
 * 农田/土地识别接口。
 *
 * <p>不把 Note Block 等任何方块写死为农田，农田底盘由服务器档案的 soilBlockIds 配置决定，
 * 运行时实现按配置判断「这里是不是星露谷农田」以及「当前土地状态」。</p>
 */
public interface SoilRecognizer {

    /** 该坐标是否为已配置的星露谷农田底盘 */
    boolean isSoil(BlockPos pos);

    /** 识别该坐标当前的土地状态（空/已种/生长中/成熟/未知） */
    SoilState detectState(BlockPos pos);
}
