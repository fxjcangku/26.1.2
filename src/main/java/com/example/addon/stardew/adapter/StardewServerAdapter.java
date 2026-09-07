package com.example.addon.stardew.adapter;

import com.example.addon.stardew.model.CropRecognitionResult;
import com.example.addon.stardew.model.FertilizerResult;
import com.example.addon.stardew.model.GrowthStageResult;
import com.example.addon.stardew.model.MaturityResult;
import com.example.addon.stardew.model.SeedRecognitionResult;
import com.example.addon.stardew.model.SoilState;
import com.example.addon.stardew.model.SprinklerInfo;
import com.example.addon.stardew.model.WaterStateResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星露谷服务器适配接口：描述当前服务器的特殊规则（种子/作物/生长/成熟/浇水/施肥/洒水器）。
 *
 * <p>这是星露谷模式的识别与规则统一入口，FarmDayManager 只依赖本接口，不直接依赖具体识别器。
 * 不同服务器无需改 Java 核心逻辑，仅需通过服务器档案 + 适配实现差异化。</p>
 *
 * <p>方法签名已按 26.1.2 Mojang 映射调整：{@link BlockPos} / {@link BlockState} /
 * {@link ItemStack}，返回类型统一使用识别结果对象（含 known 语义）以保证「未知状态安全停止」。</p>
 */
public interface StardewServerAdapter {

    /** 识别物品是否为已配置种子 */
    SeedRecognitionResult recognizeSeed(ItemStack stack);

    /** 识别方块状态是否为已配置作物 */
    CropRecognitionResult recognizeCrop(BlockState state);

    /** 识别生长阶段 */
    GrowthStageResult detectGrowthStage(BlockPos pos, BlockState state);

    /** 识别是否成熟（unknown 表示无法确定，禁止收割） */
    MaturityResult detectMaturity(BlockPos pos, BlockState state);

    /** 识别浇水状态 */
    WaterStateResult detectWaterState(BlockPos pos, BlockState state);

    /** 识别施肥状态 */
    FertilizerResult detectFertilizer(BlockPos pos);

    /** 识别洒水器 */
    SprinklerInfo detectSprinkler(BlockPos pos);

    /** 该坐标是否为已配置的星露谷农田底盘 */
    boolean isSoil(BlockPos pos);

    /** 识别该坐标当前的土地状态 */
    SoilState detectSoilState(BlockPos pos);
}
