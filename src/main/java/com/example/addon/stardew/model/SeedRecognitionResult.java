package com.example.addon.stardew.model;

/**
 * 种子识别结果。
 *
 * <p>{@code known=false} 表示无法识别为任何已配置种子，调用方必须安全停止，不得猜测。
 * 命中时携带对应的种子档案 ID 与显示名，供资源管理与种子选择联动。</p>
 */
public record SeedRecognitionResult(
    boolean known,
    String seedId,
    String displayName,
    String itemId,
    String detail
) {

    /** 未知种子结果 */
    public static SeedRecognitionResult unknown(String detail) {
        return new SeedRecognitionResult(false, null, null, null, detail);
    }

    /** 已知种子结果 */
    public static SeedRecognitionResult known(String seedId, String displayName, String itemId) {
        return new SeedRecognitionResult(true, seedId, displayName, itemId, null);
    }
}
