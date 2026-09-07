package com.example.addon.mining.recognition;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 矿物识别器接口 - 判断方块是否为目标矿物。
 *
 * <p>职责：判断给定方块是否为已知矿物（钻石/绿宝石/远古残骸等）。</p>
 */
public interface OreRecognizer {

    /**
     * 识别方块状态是否为目标矿物。
     *
     * @param state 方块状态
     * @return 识别结果（已知矿物 / 未知）
     */
    OreResult recognize(BlockState state);
}
