package com.example.addon.teleport.adapter;

import com.example.addon.teleport.recognition.TeleportSafetyResult;
import net.minecraft.core.BlockPos;

/**
 * 传送适配器接口 - 统一传送目标识别入口。
 *
 * <p>职责：封装传送目标安全性识别逻辑。</p>
 */
public interface TeleportAdapter {

    /**
     * 识别传送目标是否安全。
     *
     * @param pos 目标位置
     * @return 识别结果
     */
    TeleportSafetyResult recognizeSafety(BlockPos pos);
}
