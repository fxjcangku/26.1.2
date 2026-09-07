package com.example.addon.autofarm.recognition;

/**
 * 成熟状态识别结果。
 *
 * @param known 是否能判定成熟状态
 * @param mature 是否成熟（known=true 时有效）
 * @param reason 未知原因（known=false 时说明）
 */
public record MaturityResult(boolean known, boolean mature, String reason) {

    /**
     * 创建已知成熟结果。
     */
    public static MaturityResult known(boolean mature) {
        return new MaturityResult(true, mature, "");
    }

    /**
     * 创建未知结果（无法判定成熟状态）。
     */
    public static MaturityResult unknown(String reason) {
        return new MaturityResult(false, false, reason);
    }
}
