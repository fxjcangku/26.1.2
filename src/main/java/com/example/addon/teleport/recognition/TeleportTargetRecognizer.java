package com.example.addon.teleport.recognition;

import net.minecraft.core.BlockPos;

/**
 * 传送目标识别器接口 - 判断目标位置是否安全可传送。
 *
 * <p>职责：判断给定位置是否为安全传送目标（无碰撞/足够空间/稳定地面）。</p>
 */
public interface TeleportTargetRecognizer {

    /**
     * 识别传送目标是否安全。
     *
     * @param pos 目标位置
     * @return 识别结果
     */
    TeleportSafetyResult recognize(BlockPos pos);
}
