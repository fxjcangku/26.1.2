package com.example.addon.stardew.model;

/**
 * 浇水状态识别结果（含状态与说明）。
 *
 * <p>识别不出时 {@link WaterState#UNKNOWN}，由浇水服务决定是否需要浇水；绝不盲目浇水。</p>
 */
public record WaterStateResult(
    WaterState state,
    String detail
) {

    /** 未知浇水状态 */
    public static WaterStateResult unknown(String detail) {
        return new WaterStateResult(WaterState.UNKNOWN, detail);
    }

    /** 已知浇水状态 */
    public static WaterStateResult of(WaterState state) {
        return new WaterStateResult(state, null);
    }
}
