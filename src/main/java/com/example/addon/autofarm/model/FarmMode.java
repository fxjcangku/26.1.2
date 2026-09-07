package com.example.addon.autofarm.model;

/**
 * 自动农场的运行模式。
 *
 * <p>「自动农场」是唯一顶级 Meteor 模块入口，内部通过本枚举在
 * 原版自动农场 与 星露谷农场 两个平级模式之间切换。</p>
 */
public enum FarmMode {
    /** 原版自动农场：熟一颗收一颗 + 补种 + 拾取 + 卸货/补货物流 */
    VANILLA("原版自动农场"),

    /** 星露谷农场：服务器自定义作物的种子/成熟/浇水/施肥通用自动化 */
    STARDEW("星露谷农场");

    /** 中文显示名 */
    private final String cn;

    FarmMode(String cn) {
        this.cn = cn;
    }

    /** 中文显示名，用于配置页下拉框 */
    public String cn() {
        return cn;
    }

    /** 是否为星露谷模式 */
    public boolean isStardew() {
        return this == STARDEW;
    }
}
