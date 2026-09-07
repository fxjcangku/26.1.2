package com.example.addon.mining.adapter;

import com.example.addon.mining.recognition.OreResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 挖矿服务器适配器接口 - 统一服务器差异识别入口。
 *
 * <p>职责：封装矿物识别逻辑，隔离服务器差异（原版/插件/模组服务器）。</p>
 */
public interface MiningServerAdapter {

    /**
     * 识别矿物。
     *
     * @param state 方块状态
     * @return 识别结果
     */
    OreResult recognizeOre(BlockState state);

    /**
     * 判断是否为目标矿物（快捷方法）。
     *
     * @param state 方块状态
     * @return true=目标矿物，false=非目标
     */
    default boolean isTargetOre(BlockState state) {
        return recognizeOre(state).known();
    }
}
