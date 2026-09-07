package com.example.addon.stardew.model;

/**
 * 农田施肥状态。
 *
 * <p>与种子/作物分离，施肥是否必要由服务器规则配置决定，不把原版星露谷机制硬编码。</p>
 */
public enum FertilizerState {

    /** 未施肥 */
    NONE("未施肥"),

    /** 已施肥 */
    FERTILIZED("已施肥"),

    /** 未知：无法确定施肥状态 */
    UNKNOWN("未知");

    private final String cn;

    FertilizerState(String cn) {
        this.cn = cn;
    }

    public String cn() {
        return cn;
    }
}
