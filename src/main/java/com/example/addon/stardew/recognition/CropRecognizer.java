package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.CropRecognitionResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 作物识别接口：把方块状态识别为某个已配置作物。
 *
 * <p>运行时实现 {@link RuntimeCropRecognizer} 基于作物档案的方块 ID 集合匹配；
 * 未来资源包实现只做视觉/模型辅助，不重写识别主线。</p>
 */
public interface CropRecognizer {

    /** 识别给定方块状态属于哪个作物 */
    CropRecognitionResult recognize(BlockState state);
}
