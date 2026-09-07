package com.example.addon.stardew.config;

/**
 * 星露谷农场全局常量。
 *
 * <p>只放跨类共享的常量，不做业务逻辑，避免把配置常量散落各处。</p>
 */
public final class StardewConfig {

    private StardewConfig() {
        // 常量类，禁止实例化
    }

    /** 服务器档案持久化目录（相对客户端数据目录） */
    public static final String PROFILE_DIR = "stardew";

    /** 默认档案 ID */
    public static final String DEFAULT_PROFILE_ID = "server_default";

    /** 未配置的默认卸货数量（组） */
    public static final int DEFAULT_UNLOAD_GROUPS = 8;

    /** 未配置的默认补货数量（组） */
    public static final int DEFAULT_RESTOCK_GROUPS = 3;

    /** 农田扫描每 tick 预算（格） */
    public static final int SCAN_BUDGET_PER_TICK = 512;

    /** 农田记忆条目的存活 tick（超过则强制重新观察） */
    public static final int MEMORY_TTL_TICKS = 600;

    /** 每 tick 决策遍历农田的预算（格），控制规划成本 */
    public static final int PLAN_BUDGET_PER_TICK = 64;
}
