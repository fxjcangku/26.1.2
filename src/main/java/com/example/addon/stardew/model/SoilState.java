package com.example.addon.stardew.model;

/**
 * 农田土地占用/生长状态。
 *
 * <p>只描述「这块地现在是什么」：空、已种、生长中、已成熟；识别不出则 UNKNOWN，
 * 绝不猜测。浇水状态（干/湿）与施肥状态单独建模，见 {@link WaterState} 与
 * {@link FertilizerState}，避免把正交概念揉进一个枚举。</p>
 */
public enum SoilState {

    /** 空地：底盘正确但上方无可识别作物 */
    EMPTY("空地"),

    /** 已种：上方存在已识别作物，但生长阶段未确定 */
    PLANTED("已种"),

    /** 生长中：已识别作物，且确定尚未成熟 */
    GROWING("生长中"),

    /** 成熟：已识别作物且确定成熟 */
    MATURE("成熟"),

    /** 未知：无法确定该土地状态，安全起见不执行任何自动动作 */
    UNKNOWN("未知");

    private final String cn;

    SoilState(String cn) {
        this.cn = cn;
    }

    public String cn() {
        return cn;
    }
}
