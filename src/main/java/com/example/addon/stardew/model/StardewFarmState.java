package com.example.addon.stardew.model;

/**
 * 星露谷农场的顶层状态（Observe → Decide → Act → Verify → Replan 的对外投影）。
 *
 * <p>与 {@link com.example.addon.autofarm.model.FarmState} 平级但独立，不共享枚举。
 * 决策（DECIDE）与资源检查（RESOURCE_CHECK）发生在 OBSERVE 状态且仅在无任务时执行，
 * 不单列；任务内部 ACT/WAIT/VERIFY 属于任务自身子阶段，不在此展开。</p>
 */
public enum StardewFarmState {

    /** 观察：无任务，扫描农田并决策 */
    OBSERVE("观察"),

    /** 收割：StardewHarvestTask 执行中 */
    HARVEST("收割"),

    /** 种植：StardewPlantTask 执行中 */
    PLANT("种植"),

    /** 浇水：StardewWaterTask 执行中 */
    WATER("浇水"),

    /** 施肥：StardewFertilizeTask 执行中 */
    FERTILIZE("施肥"),

    /** 卸货：UnloadTask 执行中（独占） */
    UNLOAD("卸货"),

    /** 补货：StardewRestockTask 执行中（独占） */
    RESTOCK("补货");

    private final String cn;

    StardewFarmState(String cn) {
        this.cn = cn;
    }

    public String cn() {
        return cn;
    }
}
