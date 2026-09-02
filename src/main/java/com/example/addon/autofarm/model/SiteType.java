package com.example.addon.autofarm.model;

/**
 * 自动农场需要绑定的六个锚点类型。
 *
 * 点位一与点位二是农田范围的两个对角，只要坐标；
 * 单/双/三作物箱与毒马铃薯箱必须指向真正的容器方块，绑定时做 Container 校验。
 *
 * 单/双/三作物箱按「当前启用作物数量」择一使用，同时承担卸货 + 补货；
 * 毒马铃薯箱独立处理有毒产物，绝不与普通作物箱混用。
 */
public enum SiteType {

    START("农场点位1", "start", false),
    END("农场点位2", "end", false),
    SINGLE_STORAGE("单作物箱", "single", true),
    DUAL_STORAGE("双作物箱", "dual", true),
    TRIPLE_STORAGE("三作物箱", "triple", true),
    POISON_STORAGE("毒马铃薯箱", "poison", true);

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

    /** 是否属于作物存储箱（单/双/三），供按启用作物数量选择对应箱子 */
    public boolean isCropStorage() {
        return this == SINGLE_STORAGE || this == DUAL_STORAGE || this == TRIPLE_STORAGE;
    }

    /** 按启用作物数量返回对应的作物存储箱类型；数量非法返回 null */
    public static SiteType cropStorageFor(int enabledCount) {
        return switch (enabledCount) {
            case 1 -> SINGLE_STORAGE;
            case 2 -> DUAL_STORAGE;
            case 3 -> TRIPLE_STORAGE;
            default -> null;
        };
    }
}
