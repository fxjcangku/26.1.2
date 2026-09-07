package com.example.addon.stardew.model;

/**
 * 成熟状态识别结果。
 *
 * <p>{@code known=false} 表示无法确定是否成熟，安全原则下绝不收割，等待配置或人工识别。
 * 只有 {@code known=true && mature=true} 才允许执行收割。</p>
 */
public record MaturityResult(
    boolean known,
    boolean mature,
    String detail
) {

    /** 未知成熟状态（安全停止） */
    public static MaturityResult unknown(String detail) {
        return new MaturityResult(false, false, detail);
    }

    /** 已知成熟 / 未成熟 */
    public static MaturityResult of(boolean mature) {
        return new MaturityResult(true, mature, null);
    }
}
