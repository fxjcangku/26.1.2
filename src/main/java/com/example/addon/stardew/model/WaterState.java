package com.example.addon.stardew.model;

/**
 * 农田浇水状态。
 *
 * <p>服务器可能通过方块状态 / NBT / 数据组件表达「是否已浇水」，识别不出时返回
 * UNKNOWN，由 {@link com.example.addon.stardew.adapter.StardewServerAdapter} 决定是否
 * 需要浇水，绝不盲目浇水。</p>
 */
public enum WaterState {

    /** 干旱：需要浇水 */
    DRY("干旱"),

    /** 湿润：无需浇水 */
    WET("湿润"),

    /** 未知：无法确定浇水状态 */
    UNKNOWN("未知");

    private final String cn;

    WaterState(String cn) {
        this.cn = cn;
    }

    public String cn() {
        return cn;
    }
}
