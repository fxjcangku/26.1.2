package com.example.addon.farm;

/**
 * 农场需要绑定的四个锚点类型。
 *
 * 起点与终点是农田范围的两个对角，只要坐标；
 * 卸货总仓与种子库必须指向真正的容器方块，绑定时要做 Container 接口校验，
 * 否则状态机会对着一块石头反复开容器然后卡死。
 */
public enum SiteType {

    START("起点", "start", false),
    END("终点", "end", false),
    DUMP("卸货箱", "dump", true),
    SUPPLY("补货箱", "supply", true);

    private final String cn;
    private final String en;
    private final boolean requiresContainer;

    SiteType(String cn, String en, boolean requiresContainer) {
        this.cn = cn;
        this.en = en;
        this.requiresContainer = requiresContainer;
    }

    /** 中文子命令字面量，也用于聊天提示 */
    public String cn() {
        return cn;
    }

    /** 英文子命令字面量，给不方便输中文的场合用 */
    public String en() {
        return en;
    }

    /** 绑定时是否必须命中容器方块 */
    public boolean requiresContainer() {
        return requiresContainer;
    }
}
