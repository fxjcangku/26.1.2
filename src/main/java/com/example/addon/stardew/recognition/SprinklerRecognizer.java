package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.SprinklerInfo;
import net.minecraft.core.BlockPos;

/**
 * 洒水器识别接口：识别某坐标是否为已配置洒水器。
 *
 * <p>覆盖范围不固定，由 {@link com.example.addon.stardew.model.SprinklerProfile} 描述；
 * 识别只负责「是不是、哪一个」。</p>
 */
public interface SprinklerRecognizer {

    /** 识别该坐标的洒水器信息，非洒水器返回 {@link SprinklerInfo#none()} */
    SprinklerInfo detect(BlockPos pos);
}
