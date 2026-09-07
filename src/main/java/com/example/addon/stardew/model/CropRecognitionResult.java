package com.example.addon.stardew.model;

/**
 * 作物识别结果。
 *
 * <p>{@code known=false} 表示该方块不属于任何已配置作物，调用方不得据此执行收割/种植。</p>
 */
public record CropRecognitionResult(
    boolean known,
    String cropId,
    String displayName,
    String blockId
) {

    /** 未知作物结果 */
    public static CropRecognitionResult unknown(String blockId) {
        return new CropRecognitionResult(false, null, null, blockId);
    }

    /** 已知作物结果 */
    public static CropRecognitionResult known(String cropId, String displayName, String blockId) {
        return new CropRecognitionResult(true, cropId, displayName, blockId);
    }
}
