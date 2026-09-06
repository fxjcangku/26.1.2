// 自动图书管理员 核心业务编排器
package com.example.addon.librarian;

import com.example.addon.librarian.config.AutoLibrarianConfig;
import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.StationValidationStatus;
import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.model.VillagerTarget;
import com.example.addon.librarian.model.VillagerStation;
import com.example.addon.librarian.service.ActionResult;
import com.example.addon.librarian.service.ActionStatus;
import com.example.addon.librarian.service.DebugLoggerService;
import com.example.addon.librarian.service.DebugSoundEvent;
import com.example.addon.librarian.service.DebugSoundService;
import com.example.addon.librarian.service.EnchantmentService;
import com.example.addon.librarian.service.InventoryService;
import com.example.addon.librarian.service.LecternPlacementService;
import com.example.addon.librarian.service.MarkerBlockValidation;
import com.example.addon.librarian.service.MovementService;
import com.example.addon.librarian.service.MovementStartResult;
import com.example.addon.librarian.service.MovementStatus;
import com.example.addon.librarian.service.TradeService;
import com.example.addon.librarian.service.VillagerSearchService;
import com.example.addon.librarian.service.VillagerStationService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 自动图书管理员 · 核心业务编排器。
 *
 * <p>将状态机与各业务服务串联，为每个状态注册对应动作（搜索、移动、放置讲台、
 * 交易、验证、完成），是自动图书管理员的运行时大脑。由模块层按 tick 驱动。</p>
 */
public final class AutoLibrarianOrchestrator {
    /** 业务配置 */
    private final AutoLibrarianConfig config;
    /** 运行上下文 */
    private final AutoLibrarianContext context;
    /** 状态机 */
    private final AutoLibrarianStateMachine stateMachine;
    /** 村民搜索服务 */
    private final VillagerSearchService villagerSearchService;
    /** 固定交易位服务 */
    private final VillagerStationService villagerStationService;
    /** 移动服务 */
    private final MovementService movementService;
    /** 讲台放置服务 */
    private final LecternPlacementService lecternPlacementService;
    /** 交易服务 */
    private final TradeService tradeService;
    /** 库存服务 */
    private final InventoryService inventoryService;
    /** 附魔匹配服务 */
    private final EnchantmentService enchantmentService;
    /** 调试日志服务 */
    private final DebugLoggerService logger;
    /** 调试音效服务 */
    private final DebugSoundService sound;
    /** 本 tick 动作是否已提交（防止重复发包） */
    private boolean actionSubmitted;

    public AutoLibrarianOrchestrator(
        AutoLibrarianConfig config,
        AutoLibrarianContext context,
        VillagerSearchService villagerSearchService,
        VillagerStationService villagerStationService,
        MovementService movementService,
        LecternPlacementService lecternPlacementService,
        TradeService tradeService,
        InventoryService inventoryService,
        EnchantmentService enchantmentService,
        DebugLoggerService logger,
        DebugSoundService sound
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.context = Objects.requireNonNull(context, "context");
        this.villagerSearchService = Objects.requireNonNull(villagerSearchService, "villagerSearchService");
        this.villagerStationService = Objects.requireNonNull(villagerStationService, "villagerStationService");
        this.movementService = Objects.requireNonNull(movementService, "movementService");
        this.lecternPlacementService = Objects.requireNonNull(lecternPlacementService, "lecternPlacementService");
        this.tradeService = Objects.requireNonNull(tradeService, "tradeService");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.enchantmentService = Objects.requireNonNull(enchantmentService, "enchantmentService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.sound = Objects.requireNonNull(sound, "sound");
        this.stateMachine = new AutoLibrarianStateMachine(this::onTransition, this::onStateFailure);
        registerHandlers();
    }

    public void start() {
        context.clearVillagerCycle();
        stateMachine.start();
    }

    public void tick() {
        stateMachine.tick();
    }

    public void stop() {
        movementService.stop();
        tradeService.close();
        context.clearVillagerCycle();
        stateMachine.reset("模块停止");
    }

    public void softReset() {
        movementService.stop();
        tradeService.close();
        context.clearVillagerCycle();
        stateMachine.reset("暂停重置");
    }

    public void restart() {
        start();
    }

    public AutoLibrarianState getState() {
        return stateMachine.getCurrentState();
    }

    public AutoLibrarianContext getContext() {
        return context;
    }

    private void registerHandlers() {
        stateMachine.register(AutoLibrarianState.START, this::handleStart);
        stateMachine.register(AutoLibrarianState.SEARCH_VILLAGER, this::handleSearchVillager);
        stateMachine.register(AutoLibrarianState.SELECT_TARGET_VILLAGER, this::handleSelectTargetVillager);
        stateMachine.register(AutoLibrarianState.MOVE_TO_VILLAGER, this::handleMoveToVillager);
        stateMachine.register(AutoLibrarianState.FIND_LECTERN_POSITION, this::handleFindLecternPosition);
        stateMachine.register(AutoLibrarianState.MOVE_TO_STAND_POSITION, this::handleMoveToStandPosition);
        stateMachine.register(AutoLibrarianState.BREAK_OBSTACLE, this::handleBreakObstacle);
        stateMachine.register(AutoLibrarianState.PLACE_LECTERN, this::handlePlaceLectern);
        stateMachine.register(AutoLibrarianState.WAIT_PROFESSION, this::handleWaitProfession);
        stateMachine.register(AutoLibrarianState.OPEN_TRADE, this::handleOpenTrade);
        stateMachine.register(AutoLibrarianState.WAIT_TRADE_SCREEN, this::handleWaitTradeScreen);
        stateMachine.register(AutoLibrarianState.READ_TRADES, this::handleReadTrades);
        stateMachine.register(AutoLibrarianState.CHECK_ENCHANTMENT, this::handleCheckEnchantment);
        stateMachine.register(AutoLibrarianState.RESET, this::handleReset);
        stateMachine.register(AutoLibrarianState.BREAK_LECTERN, this::handleBreakLectern);
        stateMachine.register(AutoLibrarianState.WAIT_UNEMPLOYED, this::handleWaitUnemployed);
        stateMachine.register(AutoLibrarianState.SUCCESS_FOUND, this::handleSuccessFound);
        stateMachine.register(AutoLibrarianState.TRADE_PROCESS, this::handleTradeProcess);
        stateMachine.register(AutoLibrarianState.SELECT_TRADE, this::handleSelectTrade);
        stateMachine.register(AutoLibrarianState.WAIT_TRADE_SYNC, this::handleWaitTradeSync);
        stateMachine.register(AutoLibrarianState.TAKE_TRADE_OUTPUT, this::handleTakeTradeOutput);
        stateMachine.register(AutoLibrarianState.VERIFY_PURCHASE, this::handleVerifyPurchase);
        stateMachine.register(AutoLibrarianState.COMPLETE_TARGET, this::handleCompleteTarget);
        stateMachine.register(AutoLibrarianState.END_VILLAGER_CYCLE, this::handleEndVillagerCycle);
        stateMachine.register(AutoLibrarianState.FINISH, () -> {
        });
        stateMachine.register(AutoLibrarianState.ERROR, this::handleError);
    }

    private void handleStart() {
        if (context.targetProgress().allCompleted()) {
            transition(AutoLibrarianState.FINISH, "所有附魔目标均已完成");
            return;
        }
        if (!movementService.isAvailable()) {
            fail("Baritone 不可用，无法启动自动移动");
            return;
        }
        transition(AutoLibrarianState.SEARCH_VILLAGER, "开始搜索未绑定职业村民");
    }

    private void handleSearchVillager() {
        Optional<VillagerTarget> target = villagerSearchService.findNearestUnemployedVillager(config.villagerSearchRadius());
        if (target.isEmpty()) {
            // 每200tick提示一次附近没有失业村民
            long ticks = stateMachine.getTicksInCurrentState();
            if (ticks == 1 || ticks % 200 == 0) {
                logger.info("附近没有失业村民，等待中...");
            }
            return;
        }
        context.setVillagerTarget(target.get());
        transition(AutoLibrarianState.SELECT_TARGET_VILLAGER, "发现并保存当前目标村民");
    }

    private void handleSelectTargetVillager() {
        VillagerTarget target = requireVillager();
        if (!villagerSearchService.isValid(target) || !villagerSearchService.isUnemployed(target)) {
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "候选村民失效或已有职业");
            return;
        }
        transition(AutoLibrarianState.MOVE_TO_VILLAGER, "候选村民验证通过");
    }

    private void handleMoveToVillager() {
        VillagerTarget target = requireVillager();
        if (!villagerSearchService.isValid(target)) {
            movementService.stop();
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "移动期间村民失效");
            return;
        }
        if (movementService.hasArrived()) {
            movementService.stop();
            transition(AutoLibrarianState.FIND_LECTERN_POSITION, "已到达村民附近");
            return;
        }
        if (stateMachine.getTicksInCurrentState() == 1) {
            // 移动阶段直接以村民当前坐标为目标，不做空间模型检测
            // 空间模型检测（岩浆块、讲台位置）延迟到到达后的 FIND_LECTERN_POSITION 执行
            logger.debug("[移动] 启动 Baritone，目标村民坐标: " + target.position());
            MovementStartResult result = movementService.gotoPosition(
                target.position(),
                config.movementArrivalRadius()
            );
            if (result != MovementStartResult.STARTED && result != MovementStartResult.ALREADY_RUNNING) {
                fail("Baritone 移动启动失败: " + result);
            }
            return;
        }
        if (stateMachine.getTicksInCurrentState() > config.movementTimeoutTicks()) {
            movementService.stop();
            fail("Baritone 移动超时");
            return;
        }
        MovementStatus status = movementService.getStatus();
        if (status == MovementStatus.FAILED || status == MovementStatus.CANCELED || status == MovementStatus.TIMED_OUT) {
            fail("Baritone 移动失败: " + status);
        }
    }

    private void handleFindLecternPosition() {
        VillagerTarget target = requireVillager();
        // 到达村民附近后，在此阶段才建立空间模型并做完整工位检测
        if (context.villagerStation().isEmpty()) {
            // 检测到60 tick 后仍没有找到岩浆块，放弃
            if (stateMachine.getTicksInCurrentState() > 60) {
                fail("工位检测超时：村民相邻4格内始终没有岩浆块，请检查场地布置");
                return;
            }
            Optional<VillagerStation> detectedStation = villagerStationService.detect(target);
            if (detectedStation.isEmpty()) {
                // 还没找到，等下一tick（村民可能还没转身）
                return;
            }
            context.setVillagerStation(detectedStation.get().withValidationStatus(StationValidationStatus.UNVALIDATED));
            logger.debug("[工位检测] 发现岩浆块，讲台位置: " + detectedStation.get().lecternPosition());
        }
        VillagerStation station = requireStation();
        MarkerBlockValidation validation = villagerStationService.validate(station);
        if (!validation.valid()) {
            fail("[工位检测] 验证失败: " + validation.reason());
            return;
        }
        context.setLecternPosition(station.lecternPosition());
        transition(AutoLibrarianState.MOVE_TO_STAND_POSITION, "固定讲台位置验证通过，移动到放置位");
    }

    private void handleMoveToStandPosition() {
        VillagerStation station = requireStation();
        if (!villagerSearchService.isValid(requireVillager())) {
            movementService.stop();
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "移动到站位期间村民失效");
            return;
        }
        if (movementService.hasArrived()) {
            movementService.stop();
            VillagerStation arrived = requireStation();
            if (lecternPlacementService.hasObstacle(arrived)) {
                logger.info("讲台位有障碍方块，进入清除流程");
                transition(AutoLibrarianState.BREAK_OBSTACLE, "讲台位有障碍，清除后放置");
                return;
            }
            transition(AutoLibrarianState.PLACE_LECTERN, "已到达讲台放置站位");
            return;
        }
        if (stateMachine.getTicksInCurrentState() == 1) {
            logger.debug("[移动到站位] 目标: " + station.playerStandPosition());
            MovementStartResult result = movementService.gotoPosition(
                station.playerStandPosition(), 1
            );
            if (result != MovementStartResult.STARTED && result != MovementStartResult.ALREADY_RUNNING) {
                fail("移动到讲台站位失败: " + result);
            }
            return;
        }
        if (stateMachine.getTicksInCurrentState() > config.movementTimeoutTicks()) {
            movementService.stop();
            fail("移动到讲台站位超时");
            return;
        }
        MovementStatus status = movementService.getStatus();
        if (status == MovementStatus.FAILED || status == MovementStatus.CANCELED || status == MovementStatus.TIMED_OUT) {
            fail("移动到讲台站位失败: " + status);
        }
    }

    private void handleBreakObstacle() {
        VillagerStation station = requireStation();
        logger.debug("清除障碍状态 tick=" + stateMachine.getTicksInCurrentState()
            + " 位置=" + station.lecternPosition());
        if (!villagerSearchService.isValid(requireVillager())) {
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "清除障碍期间村民失效");
            return;
        }
        // 超过200tick仍未清除，才判定失败（给足够时间挖硬方块）
        if (actionTimedOut(200)) {
            fail("清除障碍超时（200tick）");
            return;
        }
        ActionResult result = lecternPlacementService.breakObstacle(station);
        logger.debug("清除障碍结果=" + result.status() + " 原因=" + result.reason());
        if (result.status() == ActionStatus.SUCCESS) {
            logger.info("已清除障碍方块，恢复运行");
            transition(AutoLibrarianState.PLACE_LECTERN, "障碍已清除，开始放置讲台");
        } else if (result.status() == ActionStatus.FAILED) {
            fail("清除障碍失败: " + result.reason());
        }
        // WAITING / RETRY 状态：继续下一tick执行，不做任何操作
    }

    private void handlePlaceLectern() {
        VillagerStation station = requireStation();
        logger.debug("放置讲台状态 tick=" + stateMachine.getTicksInCurrentState()
            + " 位置=" + station.lecternPosition()
            + " 障碍=" + lecternPlacementService.hasObstacle(station));
        if (!validateCurrentStation("放置讲台前")) return;
        // 到达站位后等15tick让玩家完全停下，防止从村民后方寻路过来时移动惯性导致放置方向偏差
        if (stateMachine.getTicksInCurrentState() < 15) return;
        // 重复检测：放置前再确认无障碍，如仍有则回到清除流程
        if (lecternPlacementService.hasObstacle(station)) {
            logger.info("放置讲台前检测到障碍仍存在，重新清除");
            transition(AutoLibrarianState.BREAK_OBSTACLE, "讲台位仍有障碍，重新清除");
            return;
        }
        if (actionSubmitted) {
            if (lecternPlacementService.validatePlacement(station)) {
                transition(AutoLibrarianState.WAIT_PROFESSION, "讲台精准放置及朝向验证成功");
            } else if (actionTimedOut(config.professionTimeoutTicks())) {
                fail("讲台放置结果确认超时");
            }
            return;
        }
        if (!actionDelayElapsed()) return;
        ActionResult result = lecternPlacementService.place(station);
        if (result.status() == ActionStatus.SUCCESS) {
            if (!lecternPlacementService.validatePlacement(station)) {
                fail("讲台放置验证失败：位置或阅读面朝向不正确。");
                return;
            }
            transition(AutoLibrarianState.WAIT_PROFESSION, "讲台精准放置及朝向验证成功");
        } else if (result.status() == ActionStatus.WAITING) {
            actionSubmitted = true;
        } else if (result.status() == ActionStatus.FAILED) {
            fail("讲台放置失败: " + result.reason());
        } else if (actionTimedOut(config.professionTimeoutTicks())) {
            fail("讲台放置重试超时: " + result.reason());
        }
    }

    // 记录检测到图书管理员职业时的tick，用于等待交易数据初始化
    private long librarianDetectedTick = -1;

    private void handleWaitProfession() {
        VillagerTarget target = requireVillager();
        if (!villagerSearchService.isValid(target)) {
            librarianDetectedTick = -1;
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "等待职业期间村民失效");
            return;
        }
        if (!validateCurrentStation("职业确认前")) return;
        if (villagerSearchService.isLibrarian(target)) {
            long now = stateMachine.getTicksInCurrentState();
            if (librarianDetectedTick < 0) {
                librarianDetectedTick = now;
                logger.debug("[职业] 检测到图书管理员，等待交易数据初始化...");
                return;
            }
            // 检测到职业后再等10tick，确保服务端生成了交易列表
            if (now - librarianDetectedTick >= 10) {
                librarianDetectedTick = -1;
                transition(AutoLibrarianState.OPEN_TRADE, "村民已获得图书管理员职业，交易数据就绪");
            }
            return;
        }
        librarianDetectedTick = -1;
        if (stateMachine.getTicksInCurrentState() > config.professionTimeoutTicks()) {
            transition(AutoLibrarianState.RESET, "等待图书管理员职业超时");
        }
    }

    private void handleOpenTrade() {
        if (!validateCurrentStation("交易前")) return;
        if (tradeService.isTradeScreenReady()) {
            transition(AutoLibrarianState.WAIT_TRADE_SCREEN, "交易界面已打开");
            return;
        }
        if (actionTimedOut(config.tradeScreenTimeoutTicks())) {
            fail("交易界面打开超时");
            return;
        }
        // 前3tick等待村民AI稳定，之后每2tick重试一次，直到界面打开或超时
        long ticks = stateMachine.getTicksInCurrentState();
        if (ticks < 3) return;
        if ((ticks - 3) % 2 != 0) return;
        ActionResult result = tradeService.open(requireVillager());
        if (result.status() == ActionStatus.FAILED) {
            fail("打开交易失败: " + result.reason());
        }
    }

    private void handleWaitTradeScreen() {
        if (tradeService.isTradeScreenReady()) {
            transition(AutoLibrarianState.READ_TRADES, "交易界面已同步");
        } else if (stateMachine.getTicksInCurrentState() > config.tradeScreenTimeoutTicks()) {
            transition(AutoLibrarianState.RESET, "交易界面打开超时");
        }
    }

    private void handleReadTrades() {
        if (!validateCurrentStation("读取交易前")) return;
        // 调试：输出全部交易列表
        if (config.debugLogging()) {
            java.util.List<TradeOfferSnapshot> all = tradeService.scanTrades();
            if (all.isEmpty()) {
                logger.debug("[交易扫描] 当前村民没有任何附魔书交易");
            } else {
                for (TradeOfferSnapshot t : all) {
                    logger.debug("[交易扫描] index=" + t.tradeIndex()
                        + " 附魔=" + t.enchantmentIdentifier()
                        + " 等级=" + t.enchantmentLevel() + "/" + t.maximumEnchantmentLevel()
                        + " 绿宝石=" + t.emeraldCost()
                        + " 书=" + t.bookCost()
                        + " 可交易=" + t.tradable()
                    );
                }
            }
        }
        Optional<TradeOfferSnapshot> offer = tradeService.readFirstEnchantedBookTrade();
        if (offer.isEmpty()) {
            transition(AutoLibrarianState.RESET, "首轮交易没有附魔书");
            return;
        }
        TradeOfferSnapshot o = offer.get();
        logger.debug("[交易] 附魔=" + o.enchantmentIdentifier()
            + " 等级=" + o.enchantmentLevel()
            + " 绿宝石x" + o.emeraldCost()
            + " 书x" + o.bookCost()
        );
        context.setTradeOffer(o);
        transition(AutoLibrarianState.CHECK_ENCHANTMENT, "已读取第一本附魔书交易");
    }

    private void handleCheckEnchantment() {
        TradeOfferSnapshot offer = context.tradeOffer().orElseThrow();
        Optional<EnchantmentTarget> matchedTarget = context.targetProgress().incompleteTargets().stream()
            .filter(target -> enchantmentService.matches(offer, target, config.maximumEmeraldPrice()))
            .findFirst();
        if (matchedTarget.isEmpty()) {
            transition(AutoLibrarianState.RESET, "附魔书不属于未完成目标");
            return;
        }
        EnchantmentTarget hit = matchedTarget.get();
        logger.info("§a✓ 命中目标附魔 §8▸ " + hit.displayName() + " Lv." + hit.level()
            + " §8▸ 绿宝石x" + offer.emeraldCost() + " §8▸ 书x" + offer.bookCost());
        context.setMatchedTarget(hit);
        transition(AutoLibrarianState.SUCCESS_FOUND, "命中目标附魔，保留讲台、职业和交易");
    }

    private void handleReset() {
        movementService.stop();
        tradeService.close();
        context.resetRefreshAttempt();
        transition(AutoLibrarianState.BREAK_LECTERN, "本轮刷新数据已清理，继续使用当前村民");
    }

    private void handleBreakLectern() {
        if (!villagerSearchService.isValid(requireVillager())) {
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "拆除讲台期间村民失效");
            return;
        }
        tradeService.close();
        if (actionSubmitted) {
            // 已开始拆除：每 tick 继续攻击，直到方块消失或超时
            if (lecternPlacementService.validateRemoval(requireStation())) {
                transition(AutoLibrarianState.WAIT_UNEMPLOYED, "讲台已拆除，等待村民解除职业");
                return;
            }
            if (actionTimedOut(config.professionTimeoutTicks())) {
                fail("讲台拆除结果确认超时");
                return;
            }
            lecternPlacementService.breakLectern(requireStation());
            return;
        }
        if (!actionDelayElapsed()) return;
        ActionResult result = lecternPlacementService.breakLectern(requireStation());
        if (result.status() == ActionStatus.SUCCESS) {
            transition(AutoLibrarianState.WAIT_UNEMPLOYED, "讲台已拆除，等待村民解除职业");
        } else if (result.status() == ActionStatus.WAITING) {
            actionSubmitted = true;
        } else if (result.status() == ActionStatus.FAILED) {
            fail("讲台拆除失败: " + result.reason());
        } else if (actionTimedOut(config.professionTimeoutTicks())) {
            fail("讲台拆除重试超时: " + result.reason());
        }
    }

    private void handleWaitUnemployed() {
        VillagerTarget target = requireVillager();
        if (!villagerSearchService.isValid(target)) {
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "等待解除职业期间村民失效");
            return;
        }
        if (villagerSearchService.isUnemployed(target)
            && stateMachine.getTicksInCurrentState() > config.resetDelayTicks()) {
            context.completeLecternRemoval();
            transition(AutoLibrarianState.FIND_LECTERN_POSITION, "当前村民已解除职业，重新寻找讲台位置");
            return;
        }
        if (stateMachine.getTicksInCurrentState() > config.professionTimeoutTicks()) {
            fail("等待村民解除职业超时");
        }
    }

    private void handleSuccessFound() {
        context.beginTradeProcess();
        transition(AutoLibrarianState.TRADE_PROCESS, "目标交易状态已锁定，进入交易流程");
    }

    private void handleTradeProcess() {
        TradeOfferSnapshot offer = context.tradeOffer().orElseThrow();
        if (offer.soldOut() || !inventoryService.canAfford(offer) || !inventoryService.hasOutputCapacity()) {
            fail("目标交易不可购买或库存空间不足");
            return;
        }
        EnchantmentTarget target = context.matchedTarget().orElseThrow();
        context.setMatchingBooksBeforePurchase(inventoryService.countMatchingBooks(target));
        transition(AutoLibrarianState.SELECT_TRADE, "购买条件已确认，准备选择目标交易");
    }

    private void handleSelectTrade() {
        if (!validateCurrentStation("选择交易前")) return;
        TradeOfferSnapshot offer = context.tradeOffer().orElseThrow();
        if (tradeService.isSelectedTradeSynchronized(offer)) {
            transition(AutoLibrarianState.WAIT_TRADE_SYNC, "目标交易已选择");
            return;
        }
        if (actionSubmitted) {
            if (actionTimedOut(config.tradeSyncTimeoutTicks())) fail("选择交易结果确认超时");
            return;
        }
        if (!actionDelayElapsed()) return;
        ActionResult result = tradeService.select(offer);
        if (result.status() == ActionStatus.SUCCESS) {
            actionSubmitted = true;
            transition(AutoLibrarianState.WAIT_TRADE_SYNC, "已选择目标交易");
        } else if (result.status() == ActionStatus.WAITING) {
            actionSubmitted = true;
        } else if (result.status() == ActionStatus.FAILED) {
            fail("选择交易失败: " + result.reason());
        } else if (actionTimedOut(config.tradeSyncTimeoutTicks())) {
            fail("选择交易重试超时: " + result.reason());
        }
    }

    private void handleWaitTradeSync() {
        if (tradeService.isSelectedTradeSynchronized(context.tradeOffer().orElseThrow())) {
            transition(AutoLibrarianState.TAKE_TRADE_OUTPUT, "目标交易已同步");
        } else if (stateMachine.getTicksInCurrentState() > config.tradeSyncTimeoutTicks()) {
            fail("目标交易同步超时");
        }
    }

    private void handleTakeTradeOutput() {
        if (!validateCurrentStation("领取交易前")) return;
        if (actionSubmitted) {
            if (actionTimedOut(config.tradeSyncTimeoutTicks())) fail("领取交易输出结果确认超时");
            return;
        }
        if (!actionDelayElapsed()) return;
        ActionResult result = tradeService.takeOutput();
        if (result.status() == ActionStatus.SUCCESS) {
            actionSubmitted = true;
            transition(AutoLibrarianState.VERIFY_PURCHASE, "已领取交易输出");
        } else if (result.status() == ActionStatus.FAILED) {
            fail("领取交易输出失败: " + result.reason());
        } else if (actionTimedOut(config.tradeSyncTimeoutTicks())) {
            fail("领取交易输出等待超时: " + result.reason());
        }
    }

    private void handleVerifyPurchase() {
        EnchantmentTarget target = context.matchedTarget().orElseThrow();
        int currentCount = inventoryService.countMatchingBooks(target);
        if (currentCount > context.matchingBooksBeforePurchase() && context.verifyCurrentPurchase(currentCount)) {
            transition(AutoLibrarianState.COMPLETE_TARGET, "库存已确认新增目标附魔书");
            return;
        }
        if (stateMachine.getTicksInCurrentState() > config.tradeSyncTimeoutTicks()) {
            context.verifyCurrentPurchase(currentCount);
            fail("库存未确认目标附魔书，购买验证失败");
        }
    }

    private void handleCompleteTarget() {
        EnchantmentTarget completed = context.matchedTarget().orElseThrow();
        context.completeVerifiedCurrentTarget(config.removeCompletedTarget());
        tradeService.close();
        String completedName = completed.displayName() + " Lv." + completed.level();
        logger.info("§a✓ 目标完成 §8▸ " + completedName
            + (config.removeCompletedTarget() ? "（已移除）" : ""));
        if (context.targetProgress().allCompleted()) {
            logger.info("§a✓ 全部目标附魔已完成");
            transition(AutoLibrarianState.FINISH, "全部目标附魔购买完成，保留讲台、职业和交易结果");
        } else {
            List<EnchantmentTarget> remaining = context.targetProgress().incompleteTargets();
            logger.info("§7剩余目标 §8▸ " + remaining.stream()
                .map(t -> t.displayName() + " Lv." + t.level())
                .reduce((a, b) -> a + ", " + b).orElse("无"));
            transition(AutoLibrarianState.END_VILLAGER_CYCLE, "当前附魔目标已完成，结束当前村民周期");
        }
    }

    private void handleEndVillagerCycle() {
        movementService.stop();
        tradeService.close();
        context.clearVillagerCycle();
        transition(AutoLibrarianState.SEARCH_VILLAGER, "当前村民周期已安全结束");
    }

    private void handleError() {
        movementService.stop();
        tradeService.close();
    }

    private boolean actionDelayElapsed() {
        return stateMachine.getTicksInCurrentState() > config.actionDelayTicks();
    }

    private boolean actionTimedOut(int timeoutTicks) {
        return stateMachine.getTicksInCurrentState() > timeoutTicks;
    }

    private boolean validateCurrentStation(String phase) {
        // 使用已建立的空间模型做静态验证：只检查岩浆块存在性和讲台位置状态
        // 不重新 detect()，避免村民临时转身导致误判
        VillagerStation station = requireStation();
        MarkerBlockValidation validation = villagerStationService.validate(station);
        if (!validation.valid()) {
            fail(phase + ": " + validation.reason());
            return false;
        }
        return true;
    }

    private VillagerTarget requireVillager() {
        return context.villagerTarget().orElseThrow(() -> new IllegalStateException("当前没有村民目标"));
    }

    private VillagerStation requireStation() {
        return context.villagerStation().orElseThrow(() -> new IllegalStateException("当前没有固定交易位"));
    }

    private void fail(String reason) {
        context.setLastFailureReason(reason);
        logger.error(reason);
        transition(AutoLibrarianState.ERROR, reason);
    }

    private void transition(AutoLibrarianState state, String reason) {
        validateContextFor(state);
        actionSubmitted = false;
        stateMachine.transitionTo(state, reason);
    }

    private void validateContextFor(AutoLibrarianState state) {
        switch (state) {
            case SEARCH_VILLAGER -> {
                if (context.villagerTarget().isPresent() || context.hasPendingLectern() || context.tradeProcessActive()) {
                    throw new IllegalStateException("当前村民周期未清理，不能搜索新村民");
                }
            }
            case MOVE_TO_VILLAGER, FIND_LECTERN_POSITION, PLACE_LECTERN, WAIT_PROFESSION, OPEN_TRADE,
                 WAIT_TRADE_SCREEN, READ_TRADES, CHECK_ENCHANTMENT, RESET, BREAK_LECTERN,
                 WAIT_UNEMPLOYED, SUCCESS_FOUND, TRADE_PROCESS, SELECT_TRADE, WAIT_TRADE_SYNC,
                 TAKE_TRADE_OUTPUT, VERIFY_PURCHASE, COMPLETE_TARGET, END_VILLAGER_CYCLE -> requireVillager();
            case FINISH -> {
                if (!context.targetProgress().allCompleted()) throw new IllegalStateException("仍有未完成目标，不能结束任务");
            }
            case IDLE, START, SELECT_TARGET_VILLAGER, ERROR -> {
            }
        }
        if ((state == AutoLibrarianState.RESET || state == AutoLibrarianState.BREAK_LECTERN) && !context.hasPendingLectern()) {
            throw new IllegalStateException("没有待处理讲台，不能进入职业刷新重置");
        }
        if ((state == AutoLibrarianState.TRADE_PROCESS || state == AutoLibrarianState.SELECT_TRADE
            || state == AutoLibrarianState.WAIT_TRADE_SYNC || state == AutoLibrarianState.TAKE_TRADE_OUTPUT
            || state == AutoLibrarianState.VERIFY_PURCHASE || state == AutoLibrarianState.COMPLETE_TARGET)
            && !context.tradeProcessActive()) {
            throw new IllegalStateException("目标交易尚未锁定，不能进入交易流程");
        }
    }

    private void onTransition(StateTransition transition) {
        if (config.debugLogging()) logger.state(transition.currentState(), context, movementService.getStatus());
        if (config.debugSound()) sound.play(soundEvent(transition.currentState()));
    }

    private void onStateFailure(RuntimeException exception) {
        context.setLastFailureReason(exception.getMessage());
        logger.error("状态执行异常: " + exception.getMessage());
    }

    private DebugSoundEvent soundEvent(AutoLibrarianState state) {
        return switch (state) {
            case START -> DebugSoundEvent.START;
            case SEARCH_VILLAGER, SELECT_TARGET_VILLAGER, FIND_LECTERN_POSITION -> DebugSoundEvent.SEARCH;
            case MOVE_TO_VILLAGER, MOVE_TO_STAND_POSITION -> DebugSoundEvent.MOVE;
            case PLACE_LECTERN, BREAK_OBSTACLE -> DebugSoundEvent.PLACE;
            case WAIT_PROFESSION, BREAK_LECTERN, WAIT_UNEMPLOYED -> DebugSoundEvent.REFRESH;
            case OPEN_TRADE, WAIT_TRADE_SCREEN, READ_TRADES, CHECK_ENCHANTMENT, SUCCESS_FOUND,
                 TRADE_PROCESS, SELECT_TRADE, WAIT_TRADE_SYNC, TAKE_TRADE_OUTPUT, VERIFY_PURCHASE -> DebugSoundEvent.TRADE;
            case COMPLETE_TARGET, FINISH -> DebugSoundEvent.SUCCESS;
            case ERROR -> DebugSoundEvent.ERROR;
            case IDLE, RESET, END_VILLAGER_CYCLE -> DebugSoundEvent.RESET;
        };
    }
}
