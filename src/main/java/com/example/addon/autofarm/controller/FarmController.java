package com.example.addon.autofarm.controller;

import com.example.addon.autofarm.model.CropProfile;
import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.model.FarmState;
import com.example.addon.autofarm.model.FarmTarget;
import com.example.addon.autofarm.model.SiteType;
import com.example.addon.autofarm.navigation.FarmNav;
import com.example.addon.autofarm.resource.FarmResourceManager;
import com.example.addon.autofarm.scan.FarmScanner;
import com.example.addon.autofarm.task.CollectTask;
import com.example.addon.autofarm.task.FarmTask;
import com.example.addon.autofarm.task.HarvestTask;
import com.example.addon.autofarm.task.PlantTask;
import com.example.addon.autofarm.task.PoisonDumpTask;
import com.example.addon.autofarm.task.RestockTask;
import com.example.addon.autofarm.task.TaskResult;
import com.example.addon.autofarm.task.UnloadTask;
import com.example.addon.farm.ContainerBroker;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 农场总调度器：任务生命周期与状态机。
 *
 * 核心不变量（Observe → Decide → Act → Verify → Replan）：
 * currentTask 非空时只执行该任务，Scanner 继续观察但新目标不创建任务、不抢占 Baritone；
 * currentTask 为空时才允许决策。物流任务（卸货/补货/毒马铃薯）期间同样独占。
 *
 * 批量模式：一次 Decision 锁定多个目标到 BatchHarvestPlan，但仍是严格串行执行，
 * 每个目标完成 Harvest → Verify → Plant → Verify → Collect 完整闭环后才取下一个。
 * 计划绝不跨越物流、世界切换、模块关闭、死亡、断线。
 */
public final class FarmController {

    private final FarmScanner scanner;
    private final FarmResourceManager resources;
    private final FarmObserver observer;
    private final FarmVerifier verifier;
    private final ContainerBroker broker;
    private final FarmDecision decision;

    private FarmTask currentTask;
    private FarmState state = FarmState.OBSERVE;

    /** 结果播报回调（参考自动村民交易的 logger 机制），由模块注入 notify */
    private Consumer<String> logger = msg -> {};

    /** 批量收割计划（可能为 null），一次 Decision 锁定、严格串行消耗 */
    private BatchHarvestPlan batchPlan;

    /** 动态配置（onActivate / 点位变更时更新） */
    private Set<CropProfile> enabledCrops = Set.of();
    private Map<SiteType, FarmSite> sites = Map.of();
    private double collectRange = 4;

    public FarmController(FarmScanner scanner, FarmResourceManager resources, FarmObserver observer,
                          FarmVerifier verifier, ContainerBroker broker, FarmDecision decision) {
        this.scanner = scanner;
        this.resources = resources;
        this.observer = observer;
        this.verifier = verifier;
        this.broker = broker;
        this.decision = decision;
    }

    /** 注入结果播报回调 */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger == null ? msg -> {} : logger;
    }

    /** 更新动态配置 */
    public void configure(Set<CropProfile> enabledCrops, Map<SiteType, FarmSite> sites, double collectRange) {
        this.enabledCrops = enabledCrops;
        this.sites = sites;
        this.collectRange = collectRange;
    }

    public FarmState state() {
        return state;
    }

    public FarmTask currentTask() {
        return currentTask;
    }

    /** 当前是否有激活的批量收割计划 */
    public boolean hasBatchPlan() {
        return batchPlan != null;
    }

    /** 批量计划剩余未执行目标数，无计划返回 0 */
    public int batchRemaining() {
        return batchPlan == null ? 0 : batchPlan.remaining();
    }

    /** 每 tick 推进一次 */
    public void tick() {
        // 安全闸：世界未就绪不动作；玩家无法继续（死亡等）时暂停并清理任务与批量计划
        if (!observer.worldReady()) return;
        if (!observer.playerAlive()) {
            if (currentTask != null) {
                currentTask.cancel();
                currentTask = null;
            }
            batchPlan = null;
            FarmNav.cancel();
            setState(FarmState.OBSERVE);
            return;
        }

        // 持续观察 + 容器同步
        scanner.tick();
        broker.tick();

        // 返回农场状态
        if (state == FarmState.RETURN_FARM) {
            tickReturnFarm();
            return;
        }

        // 有当前任务：只执行它
        if (currentTask != null) {
            TaskResult result = currentTask.tick();
            if (result.done()) {
                FarmTask finished = currentTask;
                currentTask = null;
                handleTaskResult(finished, result);
            }
            return;
        }

        // 无任务：先物流检查（触发则丢弃批量计划）
        FarmTask logistics = decision.decideLogistics(enabledCrops, sites);
        if (logistics != null) {
            batchPlan = null;
            currentTask = logistics;
            setState(stateFor(logistics));
            return;
        }

        // 批量计划继续：逐个取目标，执行前校验有效性，无效跳过
        if (batchPlan != null) {
            while (batchPlan.hasNext()) {
                FarmTarget target = batchPlan.next();
                if (verifier.targetStillValid(target)) {
                    currentTask = new HarvestTask(target, verifier, decision.reachDistance());
                    setState(FarmState.HARVEST);
                    return;
                }
            }
            batchPlan = null; // 计划耗尽，丢弃
            logger.accept("§a✓ 本轮批量收割完成");
        }

        // 收割决策：单颗返回 1 个，批量返回 N 个；剩余目标进入批量计划
        List<FarmTarget> harvestTargets = decision.selectHarvestTargets();
        if (!harvestTargets.isEmpty()) {
            if (harvestTargets.size() > 1) {
                batchPlan = new BatchHarvestPlan(harvestTargets.subList(1, harvestTargets.size()));
            }
            FarmTarget first = harvestTargets.get(0);
            currentTask = new HarvestTask(first, verifier, decision.reachDistance());
            setState(FarmState.HARVEST);
            return;
        }

        // 补种决策：处理扫描发现的可补种空地
        FarmTask plant = decision.decidePlant();
        if (plant != null) {
            currentTask = plant;
            setState(FarmState.PLANT);
            return;
        }

        setState(FarmState.OBSERVE);
    }

    /** 处理任务结束后的衔接与结果 */
    private void handleTaskResult(FarmTask task, TaskResult result) {
        // 收割成功 → 需要补种则接 PlantTask，否则接 CollectTask
        if (task instanceof HarvestTask harvest) {
            if (result.ok()) {
                CropProfile crop = harvest.target().profile();
                if (crop.needsReplant() && resources.countItem(crop.plantItem()) > 0) {
                    BlockPos soil = harvest.target().pos().below();
                    currentTask = new PlantTask(FarmTarget.plant(crop, soil), verifier, decision.reachDistance());
                    setState(FarmState.PLANT);
                } else {
                    currentTask = new CollectTask(harvest.target().pos(), collectRange);
                    setState(FarmState.COLLECT);
                }
                return;
            }
            // 收割失败：导航系统不可用属系统级失败，丢弃整个批量计划；目标失效只跳过当前目标
            if (result == TaskResult.NAVIGATION_FAILED) {
                batchPlan = null;
            }
            scanner.invalidate(harvest.target().pos());
            setState(FarmState.OBSERVE);
            return;
        }

        // 补种完成（无论成败）→ 拾取掉落物
        if (task instanceof PlantTask plant) {
            scanner.invalidate(plant.target().pos());
            currentTask = new CollectTask(plant.target().pos().above(), collectRange);
            setState(FarmState.COLLECT);
            return;
        }

        // 拾取完成 → 重新观察
        if (task instanceof CollectTask) {
            setState(FarmState.OBSERVE);
            return;
        }

        // 物流任务（卸货/补货/毒马铃薯）完成 → 播报结果并返回农场
        if (task.exclusive()) {
            if (result.ok()) {
                logger.accept("§a✓ " + taskName(task) + "完成");
            } else {
                logger.accept("§c✗ " + taskName(task) + "失败");
            }
            setState(FarmState.RETURN_FARM);
            return;
        }

        setState(FarmState.OBSERVE);
    }

    /** 物流任务的中文名，用于结果播报 */
    private String taskName(FarmTask task) {
        if (task instanceof UnloadTask) return "卸货";
        if (task instanceof RestockTask) return "补货";
        if (task instanceof PoisonDumpTask) return "毒马铃薯处理";
        return "物流";
    }

    /** 返回农场：导航到农场中心，到达后清理陈旧目标与批量计划并重新观察 */
    private void tickReturnFarm() {
        if (FarmNav.available()) {
            BlockPos center = scanner.center();
            if (!FarmNav.arrived(center, 3)) {
                if (!FarmNav.pathing()) FarmNav.goTo(center, 9);
                return;
            }
            FarmNav.cancel();
        }
        // 到达：清理陈旧状态，重新观察（不沿用物流前的旧目标/旧批量计划）
        batchPlan = null;
        setState(FarmState.OBSERVE);
    }

    private FarmState stateFor(FarmTask task) {
        if (task instanceof HarvestTask) return FarmState.HARVEST;
        if (task instanceof PlantTask) return FarmState.PLANT;
        if (task instanceof CollectTask) return FarmState.COLLECT;
        if (task instanceof UnloadTask) return FarmState.UNLOAD;
        if (task instanceof RestockTask) return FarmState.RESTOCK;
        if (task instanceof PoisonDumpTask) return FarmState.POISON_DUMP;
        return FarmState.OBSERVE;
    }

    private void setState(FarmState newState) {
        this.state = newState;
    }

    /** 模块关闭时清理全部状态，重新开启必须 Fresh Observe */
    public void reset() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        batchPlan = null;
        FarmNav.cancel();
        broker.reset();
        scanner.reset();
        state = FarmState.OBSERVE;
    }
}
