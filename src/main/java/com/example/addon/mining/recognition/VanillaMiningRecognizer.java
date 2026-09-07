package com.example.addon.mining.recognition;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 原版挖矿识别器 - 包装现有 MinerFSM 硬编码逻辑，实现识别接口。
 *
 * <p>职责：复用现有硬编码矿物判定逻辑，零业务改动，仅接口化包装。</p>
 */
public final class VanillaMiningRecognizer implements OreRecognizer {

    @Override
    public OreResult recognize(BlockState state) {
        Block block = state.getBlock();

        // 钻石矿
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return OreResult.known("diamond");
        }

        // 绿宝石矿
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            return OreResult.known("emerald");
        }

        // 远古残骸
        if (block == Blocks.ANCIENT_DEBRIS) {
            return OreResult.known("ancient_debris");
        }

        // 金矿
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) {
            return OreResult.known("gold");
        }

        // 铁矿
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            return OreResult.known("iron");
        }

        // 煤矿
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
            return OreResult.known("coal");
        }

        // 铜矿
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
            return OreResult.known("copper");
        }

        // 红石矿
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return OreResult.known("redstone");
        }

        // 青金石矿
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            return OreResult.known("lapis");
        }

        // 石英矿
        if (block == Blocks.NETHER_QUARTZ_ORE) {
            return OreResult.known("quartz");
        }

        return OreResult.unknown("非目标矿物");
    }
}
