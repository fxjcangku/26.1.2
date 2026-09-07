package com.example.addon.stardew.adapter;

import com.example.addon.stardew.model.CropRecognitionResult;
import com.example.addon.stardew.model.FertilizerResult;
import com.example.addon.stardew.model.GrowthStageResult;
import com.example.addon.stardew.model.MaturityResult;
import com.example.addon.stardew.model.SeedRecognitionResult;
import com.example.addon.stardew.model.SoilState;
import com.example.addon.stardew.model.SprinklerInfo;
import com.example.addon.stardew.model.WaterStateResult;
import com.example.addon.stardew.recognition.CropRecognizer;
import com.example.addon.stardew.recognition.FertilizerRecognizer;
import com.example.addon.stardew.recognition.GrowthStageRecognizer;
import com.example.addon.stardew.recognition.MatureStateRecognizer;
import com.example.addon.stardew.recognition.SeedRecognizer;
import com.example.addon.stardew.recognition.SoilRecognizer;
import com.example.addon.stardew.recognition.SprinklerRecognizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星露谷服务器适配默认实现：组合运行时识别器，把各自独立识别收口为统一入口。
 *
 * <p>浇水状态当前无服务器数据源，安全返回 unknown（不盲目浇水）；其余识别均委托给
 * 可替换的运行时识别器，未来资源包 / 服务器专属适配只替换对应识别器，不改本类骨架。</p>
 */
public final class DefaultStardewServerAdapter implements StardewServerAdapter {

    private final SeedRecognizer seedRecognizer;
    private final CropRecognizer cropRecognizer;
    private final GrowthStageRecognizer growthStageRecognizer;
    private final MatureStateRecognizer matureStateRecognizer;
    private final FertilizerRecognizer fertilizerRecognizer;
    private final SprinklerRecognizer sprinklerRecognizer;
    private final SoilRecognizer soilRecognizer;

    public DefaultStardewServerAdapter(SeedRecognizer seedRecognizer, CropRecognizer cropRecognizer,
                                       GrowthStageRecognizer growthStageRecognizer,
                                       MatureStateRecognizer matureStateRecognizer,
                                       FertilizerRecognizer fertilizerRecognizer,
                                       SprinklerRecognizer sprinklerRecognizer, SoilRecognizer soilRecognizer) {
        this.seedRecognizer = seedRecognizer;
        this.cropRecognizer = cropRecognizer;
        this.growthStageRecognizer = growthStageRecognizer;
        this.matureStateRecognizer = matureStateRecognizer;
        this.fertilizerRecognizer = fertilizerRecognizer;
        this.sprinklerRecognizer = sprinklerRecognizer;
        this.soilRecognizer = soilRecognizer;
    }

    @Override
    public SeedRecognitionResult recognizeSeed(ItemStack stack) {
        return seedRecognizer.recognize(stack);
    }

    @Override
    public CropRecognitionResult recognizeCrop(BlockState state) {
        return cropRecognizer.recognize(state);
    }

    @Override
    public GrowthStageResult detectGrowthStage(BlockPos pos, BlockState state) {
        return growthStageRecognizer.detect(pos, state);
    }

    @Override
    public MaturityResult detectMaturity(BlockPos pos, BlockState state) {
        return matureStateRecognizer.detect(pos, state);
    }

    @Override
    public WaterStateResult detectWaterState(BlockPos pos, BlockState state) {
        // 无服务器数据源 / 资源包时无法可靠判定浇水状态，安全返回未知
        return WaterStateResult.unknown("浇水状态需服务器规则或资源包辅助");
    }

    @Override
    public FertilizerResult detectFertilizer(BlockPos pos) {
        return fertilizerRecognizer.detect(pos);
    }

    @Override
    public SprinklerInfo detectSprinkler(BlockPos pos) {
        return sprinklerRecognizer.detect(pos);
    }

    @Override
    public boolean isSoil(BlockPos pos) {
        return soilRecognizer.isSoil(pos);
    }

    @Override
    public SoilState detectSoilState(BlockPos pos) {
        return soilRecognizer.detectState(pos);
    }
}
