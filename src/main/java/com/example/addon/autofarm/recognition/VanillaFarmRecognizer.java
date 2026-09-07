package com.example.addon.autofarm.recognition;

import com.example.addon.autofarm.model.CropProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * 原版农场识别器 - 包装现有 FarmObserver 逻辑，实现识别接口。
 *
 * <p>职责：复用现有硬编码作物判定逻辑，零业务改动，仅接口化包装。</p>
 */
public final class VanillaFarmRecognizer implements CropRecognizer, MaturityRecognizer, SoilRecognizer {

    private final Minecraft mc;
    private final List<Block> cropsDouble;
    private final List<Block> cropsSingle;
    private final List<Block> cropsPillar;
    private final List<Block> cropsVine;

    public VanillaFarmRecognizer(Minecraft mc, List<Block> cropsDouble, List<Block> cropsSingle,
                                 List<Block> cropsPillar, List<Block> cropsVine) {
        this.mc = mc;
        this.cropsDouble = cropsDouble;
        this.cropsSingle = cropsSingle;
        this.cropsPillar = cropsPillar;
        this.cropsVine = cropsVine;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CropRecognizer 实现
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public CropRecognitionResult recognize(BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        // 双作物判定（小麦/甜菜根）
        if (cropsDouble.contains(block)) {
            // 查找匹配的CropProfile枚举
            for (CropProfile profile : CropProfile.values()) {
                if (profile.block() == block) return CropRecognitionResult.known(profile);
            }
        }

        // 单作物判定（胡萝卜/马铃薯/下界疣）
        if (cropsSingle.contains(block)) {
            for (CropProfile profile : CropProfile.values()) {
                if (profile.block() == block) return CropRecognitionResult.known(profile);
            }
        }

        // 柱状作物判定（甘蔗/竹子/仙人掌）
        if (cropsPillar.contains(block)) {
            for (CropProfile profile : CropProfile.values()) {
                if (profile.block() == block) return CropRecognitionResult.known(profile);
            }
        }

        // 果实作物判定（可可豆/西瓜/南瓜/甜浆果）
        if (cropsVine.contains(block)) {
            for (CropProfile profile : CropProfile.values()) {
                if (profile.block() == block) return CropRecognitionResult.known(profile);
            }
        }

        return CropRecognitionResult.unknown("非已配置作物");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MaturityRecognizer 实现
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public MaturityResult detect(BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        // 原版 CropBlock（小麦/胡萝卜/马铃薯/甜菜根）
        if (block instanceof CropBlock cropBlock) {
            int age = state.getValue(CropBlock.AGE);
            return MaturityResult.known(age == cropBlock.getMaxAge());
        }

        // 地狱疣
        if (block == Blocks.NETHER_WART) {
            int age = state.getValue(NetherWartBlock.AGE);
            return MaturityResult.known(age == 3);
        }

        // 可可豆
        if (block == Blocks.COCOA) {
            int age = state.getValue(CocoaBlock.AGE);
            return MaturityResult.known(age == 2);
        }

        // 甜浆果丛
        if (block == Blocks.SWEET_BERRY_BUSH) {
            int age = state.getValue(BlockStateProperties.AGE_3);
            return MaturityResult.known(age >= 2); // 2龄可收，3龄最佳
        }

        // 柱状作物（甘蔗/竹子/仙人掌）- 顶部方块视为成熟
        if (cropsPillar.contains(block)) {
            BlockPos above = pos.above();
            BlockState aboveState = mc.level.getBlockState(above);
            boolean hasBlockAbove = aboveState.getBlock() == block;
            return MaturityResult.known(!hasBlockAbove); // 顶部=成熟
        }

        // 西瓜/南瓜（果实方块本身就是成熟）
        if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
            return MaturityResult.known(true);
        }

        return MaturityResult.unknown("无法判定成熟状态");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  SoilRecognizer 实现
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean isSoil(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.FARMLAND || block == Blocks.GRASS_BLOCK || block == Blocks.DIRT;
    }

    @Override
    public boolean isEmpty(BlockPos pos, BlockState state) {
        if (!isSoil(pos, state)) return false;
        BlockPos above = pos.above();
        BlockState aboveState = mc.level.getBlockState(above);
        return aboveState.isAir();
    }
}
