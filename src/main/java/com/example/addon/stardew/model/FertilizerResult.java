package com.example.addon.stardew.model;

/**
 * 施肥检测结果。
 *
 * <p>{@code known=false} 表示施肥状态无法确定，安全原则下不执行施肥，避免重复施肥或误施肥。</p>
 */
public record FertilizerResult(
    boolean known,
    boolean fertilized,
    String fertilizerId,
    String detail
) {

    /** 未知施肥状态 */
    public static FertilizerResult unknown(String detail) {
        return new FertilizerResult(false, false, null, detail);
    }

    /** 已知施肥状态 */
    public static FertilizerResult of(boolean fertilized, String fertilizerId) {
        return new FertilizerResult(true, fertilized, fertilizerId, null);
    }
}
