package com.example.addon.autofarm.controller;

import com.example.addon.autofarm.model.CropProfile;
import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.model.FarmTarget;
import com.example.addon.autofarm.model.HarvestMode;
import com.example.addon.autofarm.model.SiteType;
import com.example.addon.autofarm.resource.FarmResourceManager;
import com.example.addon.autofarm.scan.FarmScanner;
import com.example.addon.autofarm.task.PlantTask;
import com.example.addon.autofarm.task.PoisonDumpTask;
import com.example.addon.autofarm.task.RestockTask;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.UnloadTask;
import com.example.addon.farm.ContainerBroker;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 决策器：只在 currentTask 为空时被调用，决定「下一步做什么」。
 *
 * 职责边界：决策器只产出「任务」或「目标清单」，绝不直接执行 breakBlock / placeBlock / clickContainer。
 * 实际执行全部由 Task 层负责。
 *
 * 物流（毒马铃薯 / 补货 / 卸货）优先级最高，且会打断并丢弃当前批量计划；
 * 收割目标选择支持单颗 / 批量两种模式，补种兜底处理遗留空地。
 */
public final class FarmDecision {

    private final FarmScanner scanner;
    private final FarmResourceManager resources;
    private final FarmObserver observer;
    private final FarmVerifier verifier;
    private final ContainerBroker broker;

    /** 运行期可变配置，由模块每 tick 同步最新设置值 */
    private int unloadThreshold;
    private int bpt;
    private double reachDistance;

    /** 收割模式与批量数量，随设置热更新 */
    private HarvestMode mode = HarvestMode.SINGLE;
    private int batchCount = 8;

    public FarmDecision(FarmScanner scanner, FarmResourceManager resources, FarmObserver observer,
                        FarmVerifier verifier, ContainerBroker broker,
                        int unloadThreshold, int bpt, double reachDistance) {
        this.scanner = scanner;
        this.resources = resources;
        this.observer = observer;
        this.verifier = verifier;
        this.broker = broker;
        this.unloadThreshold = unloadThreshold;
        this.bpt = bpt;
        this.reachDistance = reachDistance;
    }

    /** 同步最新设置值 */
    public void update(int unloadThreshold, int bpt, double reachDistance) {
        this.unloadThreshold = unloadThreshold;
        this.bpt = bpt;
        this.reachDistance = reachDistance;
    }

    /** 同步收割模式与批量数量 */
    public void updateMode(HarvestMode mode, int batchCount) {
        this.mode = mode;
        this.batchCount = Math.max(1, batchCount);
    }

    /** 收割距离，供 Controller 在任务衔接时复用 */
    public double reachDistance() {
        return reachDistance;
    }

    /**
     * 物流决策：毒马铃薯 → 补货 → 卸货。
     * 返回非 null 表示需要执行物流任务，Controller 应丢弃当前批量计划。
     */
    public FarmTask decideLogistics(Set<CropProfile> enabledCrops, Map<SiteType, FarmSite> sites) {
        // 1. 毒马铃薯处理（独立，最高优先级）
        if (resources.countPoisonousPotato() > 0) {
            FarmSite poison = validSite(sites, SiteType.POISON_STORAGE);
            if (poison != null) {
                return new PoisonDumpTask(poison.pos(), broker, reachDistance, resources, bpt);
            }
        }

        // 2. 补货：某作物种植材料不足安全库存
        CropProfile restockCrop = resources.firstNeedsRestock();
        if (restockCrop != null) {
            FarmSite box = validSite(sites, SiteType.cropStorageFor(enabledCrops.size()));
            if (box != null) {
                return new RestockTask(box.pos(), broker, reachDistance, resources, restockCrop);
            }
        }

        // 3. 卸货：产物超过阈值 或 背包快满
        if (resources.depositableStacks() >= unloadThreshold || observer.freeInventorySlots() <= 2) {
            FarmSite box = validSite(sites, SiteType.cropStorageFor(enabledCrops.size()));
            if (box != null) {
                return new UnloadTask(box.pos(), broker, reachDistance, resources, bpt);
            }
        }

        return null;
    }

    /**
     * 收割目标选择：按当前模式返回待处理的成熟目标清单。
     * 单颗返回 0~1 个，批量返回 0~batchCount 个（按距离升序）。
     */
    public List<FarmTarget> selectHarvestTargets() {
        int max = (mode == HarvestMode.BATCH) ? batchCount : 1;
        return scanner.nearestHarvests(max);
    }

    /** 补种决策：处理扫描发现的可补种空地，材料不足则返回 null 交由资源检查 */
    public FarmTask decidePlant() {
        FarmTarget plant = scanner.nearestPlantable();
        if (plant != null && resources.countItem(plant.profile().plantItem()) > 0) {
            return new PlantTask(plant, verifier, reachDistance);
        }
        return null;
    }

    /** 取已绑定且位于当前维度的站点，否则返回 null */
    private FarmSite validSite(Map<SiteType, FarmSite> sites, SiteType type) {
        if (type == null) return null;
        FarmSite site = sites.get(type);
        if (site == null || !site.inCurrentDimension()) return null;
        return site;
    }
}
