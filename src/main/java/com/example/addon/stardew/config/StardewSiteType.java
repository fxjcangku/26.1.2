package com.example.addon.stardew.config;

/**
 * 星露谷农场需要绑定的锚点类型。
 *
 * <p>与 {@link com.example.addon.autofarm.model.SiteType} 平级独立：星露谷只关心
 * 农田范围、种子箱、收获箱；肥料/浇水工具若需要额外箱子，可后续扩展，不在本期硬编码。</p>
 */
public enum StardewSiteType {

    START("农场点位1", "start", false),
    END("农场点位2", "end", false),
    SEED_STORAGE("种子箱", "seed", true),
    HARVEST_STORAGE("收获箱", "harvest", true);

    private final String cn;
    private final String en;
    private final boolean requiresContainer;

    StardewSiteType(String cn, String en, boolean requiresContainer) {
        this.cn = cn;
        this.en = en;
        this.requiresContainer = requiresContainer;
    }

    public String cn() {
        return cn;
    }

    public String en() {
        return en;
    }

    public boolean requiresContainer() {
        return requiresContainer;
    }
}
