package com.example.addon.mining.adapter;

import com.example.addon.mining.recognition.OreRecognizer;
import com.example.addon.mining.recognition.OreResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 默认挖矿适配器 - 组合识别器实现适配器接口。
 *
 * <p>职责：组合矿物识别器，提供统一适配入口。</p>
 */
public final class DefaultMiningAdapter implements MiningServerAdapter {

    private final OreRecognizer oreRecognizer;

    public DefaultMiningAdapter(OreRecognizer oreRecognizer) {
        this.oreRecognizer = oreRecognizer;
    }

    @Override
    public OreResult recognizeOre(BlockState state) {
        return oreRecognizer.recognize(state);
    }
}
