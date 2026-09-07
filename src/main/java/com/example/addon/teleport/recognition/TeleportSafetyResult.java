package com.example.addon.teleport.recognition;

import net.minecraft.core.BlockPos;

/**
 * 传送安全性识别结果。
 *
 * @param safe 是否安全可传送
 * @param pos 目标位置
 * @param reason 不安全原因
 */
public record TeleportSafetyResult(boolean safe, BlockPos pos, String reason) {

    /**
     * 创建安全结果。
     */
    public static TeleportSafetyResult safe(BlockPos pos) {
        return new TeleportSafetyResult(true, pos, "");
    }

    /**
     * 创建不安全结果。
     */
    public static TeleportSafetyResult unsafe(BlockPos pos, String reason) {
        return new TeleportSafetyResult(false, pos, reason);
    }
}
