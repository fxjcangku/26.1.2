package com.example.addon.stardew.model;

/**
 * 生长阶段识别结果。
 *
 * <p>{@code known=false} 表示阶段无法确定。阶段信息只做规划参考（内存/显示），
 * 真正执行前必须重新观察真实世界数据，绝不因记忆显示成熟就直接收割。</p>
 */
public record GrowthStageResult(
    boolean known,
    int stage,
    int totalStages,
    String detail
) {

    /** 未知生长阶段 */
    public static GrowthStageResult unknown(String detail) {
        return new GrowthStageResult(false, -1, -1, detail);
    }

    /** 已知生长阶段（stage 从 1 开始） */
    public static GrowthStageResult known(int stage, int totalStages) {
        return new GrowthStageResult(true, stage, totalStages, null);
    }
}
