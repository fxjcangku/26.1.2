package com.example.addon.villager.fsm;

import com.example.addon.commands.CunminCommand;
import com.example.addon.farm.ContainerBroker;
import com.example.addon.farm.FarmPacketOps;
import com.example.addon.villager.data.VillagerProfessionRegistry;
import com.example.addon.villager.data.VillagerTradeTarget;
import com.example.addon.villager.logistics.PipelineTask;
import com.example.addon.villager.logistics.SupplyService;
import com.example.addon.villager.logistics.UnloadService;
import com.example.addon.villager.navigation.VillagerNavigationService;
import com.example.addon.villager.trade.TradeEngine;
import com.example.addon.villager.trade.TradeMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 村民交易有限状态机（26.1.2）
 *
 * 三种模式的核心差异：
 * · LOCAL（原地交易）：不移动、不碰箱子，只与身边目标村民交易
 * · SINGLE_PATH（寻路单点）：寻路到村民工作站，交易过程中自动补给/卸货
 * · PIPELINE（多任务）：任务队列顺序执行，每个任务等价一次 SINGLE_PATH
 *
 * 交易协议要点：
 * · 服务端只接受玩家 containerMenu 为 MerchantMenu 时的 SelectTrade 包，
 *   所以必须先真实打开村民交易界面再发包，不存在绕过 GUI 的「静默交易」
 * · 交易成功以「offer uses 变化 或 绿宝石减少」为信号，等待服务端回包确认
 * · 所有操作在渲染线程 tick 驱动，禁止 sleep
 */
public final class VillagerTradeFSM {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  组件
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Minecraft mc;
    private final VillagerNavigationService navigation;
    private final SupplyService supplyService;
    private final UnloadService unloadService;
    private Consumer<String> logger;
    private Consumer<String> completeHandler;
    private Consumer<String> errorHandler;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  任务配置
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private VillagerProfession targetProfession;
    private List<VillagerTradeTarget> targets = new ArrayList<>();
    private int maxPrice = 64;
    private int sendCooldownTicks = 10;   // 发包节流（delayMs / 50）
    private int emeraldThreshold = 32;    // 低于该值触发补给
    private int supplyStacks = 1;         // 每次补给追加组数（1组=64个），补给目标=阈值+组数×64
    private boolean drainMode = false;    // 榨干模式：忽略购买总量，买到目标交易全部售罄为止
    private int targetQuantity = 64;      // 本次购买总量（件）
    private double searchRange = 16.0;

    private List<PipelineTask> pipelineTasks = new ArrayList<>();
    private int currentTaskIndex = 0;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  运行时状态
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private Mode mode = Mode.LOCAL;
    private State currentState = State.IDLE;
    private int stateTicks = 0;

    private Villager currentVillager;
    private BlockPos currentWorkstation;
    private final Set<Villager> exhaustedVillagers = new HashSet<>();

    // 交易会话（一次打开界面期间）
    private TradePhase tradePhase = TradePhase.SELECT;
    private final Set<Integer> skipOfferIndexes = new HashSet<>();
    private int offerIndex = -1;
    private int usesSnapshot = -1;
    private int emeraldSnapshot = -1;
    private int confirmTicks = 0;
    private int failStreak = 0;
    private int sendCooldown = 0;
    private int purchasedCount = 0;
    private int tradeCount = 0;
    private int lastProgressAt = 0;
    private int interactTries = 0;
    private int openRetries = 0;

    // 关闭界面后的去向
    private Route afterCloseRoute = Route.SEARCH_NEXT;
    private String stopReason = "";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  超时与阈值
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static final int SEARCH_TIMEOUT = 600;       // 30 秒
    private static final int NAV_TIMEOUT = 2400;         // 120 秒
    private static final int OPEN_TIMEOUT = 100;         // 5 秒
    private static final int CLOSE_TIMEOUT = 60;         // 3 秒
    private static final int TRADE_IDLE_TIMEOUT = 200;   // 10 秒无进展换村民
    private static final int CONFIRM_TICKS = 7;          // 交易确认等待
    private static final int MAX_CONFIRM_FAILS = 3;      // 同一 offer 连败次数
    private static final double INTERACT_RANGE = 3.2;    // 生存实体交互距离 3.0 + 容差

    public VillagerTradeFSM() {
        this.mc = Minecraft.getInstance();
        this.navigation = new VillagerNavigationService();
        this.supplyService = new SupplyService();
        this.unloadService = new UnloadService();
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
        this.supplyService.setLogger(logger);
        this.unloadService.setLogger(logger);
    }

    public void setCompleteHandler(Consumer<String> handler) {
        this.completeHandler = handler;
    }

    public void setErrorHandler(Consumer<String> handler) {
        this.errorHandler = handler;
    }

    public void configure(VillagerProfession profession, List<VillagerTradeTarget> targets,
                          int maxPrice, int packetDelayMs, int emeraldThreshold, int targetQuantity) {
        this.targetProfession = profession;
        this.targets = new ArrayList<>(targets);
        this.maxPrice = maxPrice;
        this.sendCooldownTicks = Math.max(1, packetDelayMs / 50);
        this.emeraldThreshold = emeraldThreshold;
        this.targetQuantity = Math.max(1, targetQuantity);
    }

    public void setSearchRange(double range) {
        this.searchRange = Math.max(4.0, range);
    }

    /**
     * 榨干模式开关（三种模式通用）：开启后忽略 targetQuantity，
     * 一路买到目标交易全部售罄/锁死为止，中间补给卸货循环照常。
     */
    public void setDrainMode(boolean enabled) {
        this.drainMode = enabled;
    }

    /**
     * 每次绿宝石补给的追加组数（1组=64个）。
     * 补给目标 = 触发阈值(32) + 组数×64，默认 1 组 → 补到 96 个。
     */
    public void setSupplyStacks(int stacks) {
        this.supplyStacks = Math.max(0, stacks);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  启动 / 停止
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 启动 LOCAL 或 SINGLE_PATH 模式。
     */
    public boolean start(Mode startMode) {
        if (currentState != State.IDLE) {
            log("§c状态机未处于 IDLE，无法启动");
            return false;
        }
        if (targetProfession == null || targets.isEmpty()) {
            log("§c未配置职业或目标物品");
            return false;
        }

        this.mode = startMode;
        resetSession();
        enterState(State.SEARCHING);
        // 启动参数已由模块层 announceStartup 统一播报，此处不重复输出，避免聊天栏刷屏
        return true;
    }

    /**
     * 启动 PIPELINE 模式（任务队列）。
     */
    public boolean startPipeline(List<PipelineTask> tasks) {
        if (currentState != State.IDLE) {
            log("§c状态机未处于 IDLE，无法启动");
            return false;
        }
        if (tasks == null || tasks.isEmpty()) {
            log("§c无 Pipeline 任务");
            return false;
        }

        this.mode = Mode.PIPELINE;
        this.pipelineTasks = new ArrayList<>(tasks);
        this.currentTaskIndex = 0;
        resetSession();
        loadTask(0);
        enterState(State.SEARCHING);
        // 启动参数已由模块层 announceStartup 统一播报，此处不重复输出
        return true;
    }

    /**
     * 停止状态机并复位。
     */
    public void stop() {
        boolean wasRunning = isRunning();
        navigation.stop();
        supplyService.reset();
        unloadService.reset();
        ContainerBroker.closeContainer();
        resetSession();
        enterState(State.IDLE);
        if (wasRunning) log("§c✗ 状态机已停止");
    }

    /**
     * 外部异常（断线 / 死亡 / 世界切换）入口，由模块调用并自关。
     */
    public void handleException(String reason) {
        log("§e⚠ 检测到异常 §8▸ " + reason);
        stop();
    }

    private void resetSession() {
        exhaustedVillagers.clear();
        currentVillager = null;
        currentWorkstation = null;
        skipOfferIndexes.clear();
        tradePhase = TradePhase.SELECT;
        offerIndex = -1;
        usesSnapshot = -1;
        emeraldSnapshot = -1;
        confirmTicks = 0;
        failStreak = 0;
        sendCooldown = 0;
        purchasedCount = 0;
        tradeCount = 0;
        lastProgressAt = 0;
        interactTries = 0;
        openRetries = 0;
        afterCloseRoute = Route.SEARCH_NEXT;
        stopReason = "";
    }

    private void loadTask(int index) {
        PipelineTask task = pipelineTasks.get(index);
        this.targetProfession = task.getProfession();
        this.targets = new ArrayList<>(task.getTargets());
        this.maxPrice = task.getMaxPrice();
        this.targetQuantity = task.getTargetQuantity();
        this.skipOfferIndexes.clear();
        this.purchasedCount = 0;
        this.tradeCount = 0;
        this.exhaustedVillagers.clear();
        this.currentVillager = null;
        this.currentWorkstation = null;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  主 tick
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public void tick() {
        stateTicks++;

        switch (currentState) {
            case IDLE -> { }
            case SEARCHING -> tickSearching();
            case RESOLVING_WORKSTATION -> tickResolving();
            case NAVIGATING -> tickNavigating();
            case OPENING_MENU -> tickOpeningMenu();
            case TRADING -> tickTrading();
            case CLOSING_MENU -> tickClosingMenu();
            case SUPPLY_NAV -> tickSupplyNav();
            case SUPPLY_OPEN -> tickSupplyOpen();
            case SUPPLY_TAKE -> tickSupplyTake();
            case UNLOAD_NAV -> tickUnloadNav();
            case UNLOAD_OPEN -> tickUnloadOpen();
            case UNLOAD_TAKE -> tickUnloadTake();
            case NEXT_TASK -> tickNextTask();
            case DONE -> tickDone();
            case ERROR -> tickError();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SEARCHING - 搜索目标村民
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickSearching() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            fail("玩家或世界无效");
            return;
        }
        if (stateTicks > SEARCH_TIMEOUT) {
            // 多任务模式：该职业没找到村民就跳过这个任务，不能中断整个队列
            if (mode == Mode.PIPELINE) {
                log("§e⚠ 搜索超时 §8▸ 未找到" + VillagerProfessionRegistry.getDisplayName(targetProfession) + "村民，跳过此任务");
                enterState(State.NEXT_TASK);
            } else {
                log(drainMode ? "§d⚠ 该职业已榨干 §8▸ 范围内没有更多可用村民" : "§e⚠ 搜索超时 §8▸ 未找到可用村民");
                enterState(State.DONE);
            }
            return;
        }

        // LOCAL 只搜身边，寻路模式搜更大范围（村民寻路距离不受限）
        double range = mode == Mode.LOCAL ? Math.min(searchRange, 16.0) : 96.0;

        List<Villager> villagers = mc.level.getEntitiesOfClass(
            Villager.class,
            player.getBoundingBox().inflate(range),
            this::isValidTarget
        );

        if (villagers.isEmpty()) return;

        Villager nearest = villagers.stream()
            .min((a, b) -> Double.compare(player.distanceTo(a), player.distanceTo(b)))
            .orElse(null);
        if (nearest == null) return;

        currentVillager = nearest;
        log("§a✓ 锁定村民 §8▸ " + VillagerProfessionRegistry.getDisplayName(targetProfession));

        // LOCAL 直接开交易，寻路模式先解析工作站
        interactTries = 0;
        openRetries = 0;
        enterState(mode == Mode.LOCAL ? State.OPENING_MENU : State.RESOLVING_WORKSTATION);
    }

    private boolean isValidTarget(Villager villager) {
        if (villager == null || !villager.isAlive()) return false;
        if (exhaustedVillagers.contains(villager)) return false;

        // 职业匹配
        try {
            if (!villager.getVillagerData().profession().value().equals(targetProfession)) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        // 原地模式必须处于可交互距离内
        if (mode == Mode.LOCAL && mc.player != null && mc.player.distanceTo(villager) > INTERACT_RANGE) {
            return false;
        }
        return true;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  RESOLVING_WORKSTATION / NAVIGATING（寻路模式）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickResolving() {
        if (currentVillager == null || !currentVillager.isAlive()) {
            currentVillager = null;
            enterState(State.SEARCHING);
            return;
        }

        Block workstationBlock = VillagerProfessionRegistry.getWorkstation(targetProfession);
        if (workstationBlock == null) {
            fail("无法获取工作站类型");
            return;
        }

        BlockPos workstation = findNearbyWorkstation(currentVillager.blockPosition(), workstationBlock, 8);
        if (workstation == null) {
            log("§e⚠ 村民附近未找到工作站，换下一个");
            exhaustedVillagers.add(currentVillager);
            currentVillager = null;
            enterState(State.SEARCHING);
            return;
        }

        currentWorkstation = workstation;
        log("§a✓ 工作站 §8▸ " + workstation.toShortString());
        enterState(State.NAVIGATING);
    }

    private void tickNavigating() {
        if (stateTicks == 1) {
            if (!startPathToVillager()) {
                fail("无法发起寻路");
            }
            return;
        }
        if (stateTicks > NAV_TIMEOUT || navigation.isStuck()) {
            fail("寻路超时或卡死");
            return;
        }
        if (currentWorkstation == null) {
            fail("工作站无效");
            return;
        }

        // 路径被意外取消时重试
        if (!navigation.isPathing() && stateTicks % 40 == 0) {
            startPathToVillager();
            return;
        }

        if (navigation.hasArrived(currentWorkstation, 2.5)) {
            navigation.stop();
            interactTries = 0;
            enterState(State.OPENING_MENU);
        }
    }

    private boolean startPathToVillager() {
        if (currentWorkstation == null) return false;
        return navigation.pathToWorkstation(currentWorkstation)
            || navigation.pathToContainer(currentWorkstation);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  OPENING_MENU - 打开村民交易界面
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickOpeningMenu() {
        LocalPlayer player = mc.player;
        if (player == null) {
            fail("玩家无效");
            return;
        }

        // 交易界面已就绪
        if (player.containerMenu instanceof MerchantMenu) {
            prepareTradeSession();
            enterState(State.TRADING);
            return;
        }

        if (currentVillager == null || !currentVillager.isAlive()) {
            currentVillager = null;
            enterState(State.SEARCHING);
            return;
        }

        // 视角自动转向村民：原地模式玩家走到位即可，无需自己瞄准
        faceVillager(player, currentVillager);

        if (stateTicks > OPEN_TIMEOUT || player.distanceTo(currentVillager) > INTERACT_RANGE) {
            // 寻路模式优先回到工作站重新找位，村民可能离开工作站了
            if (mode != Mode.LOCAL && currentWorkstation != null && openRetries == 0) {
                openRetries++;
                enterState(State.NAVIGATING);
                return;
            }
            log("§e⚠ 无法与村民交互，换下一个");
            exhaustedVillagers.add(currentVillager);
            currentVillager = null;
            enterState(State.SEARCHING);
            return;
        }

        // 节流重试交互
        if (stateTicks % 15 == 0) {
            interactTries++;
            if (interactTries > 4 && player.containerMenu == player.inventoryMenu) {
                exhaustedVillagers.add(currentVillager);
                currentVillager = null;
                enterState(State.SEARCHING);
                return;
            }
            if (mc.gameMode != null) {
                mc.gameMode.interact(player, currentVillager,
                    new EntityHitResult(currentVillager), InteractionHand.MAIN_HAND);
            }
        }
    }

    /**
     * 把玩家视角转向村民眼睛位置（只改 yaw/pitch，原地模式无需玩家自行瞄准）。
     */
    private void faceVillager(LocalPlayer player, Villager villager) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = villager.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        // yaw 公式与实体 look 向量互为逆运算：yaw=0 朝向 +Z，atan2(-dx, dz) 归一
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horiz));
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch));

        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private void prepareTradeSession() {
        skipOfferIndexes.clear();
        tradePhase = TradePhase.SELECT;
        offerIndex = -1;
        usesSnapshot = -1;
        emeraldSnapshot = -1;
        confirmTicks = 0;
        failStreak = 0;
        sendCooldown = 0;
        lastProgressAt = 0;
        interactTries = 0;
        openRetries = 0;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  TRADING - 交易循环（SELECT → 发包 → CONFIRM）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickTrading() {
        LocalPlayer player = mc.player;
        if (player == null) {
            fail("玩家无效");
            return;
        }

        // 界面被意外关闭：本次村民会话结束
        if (!(player.containerMenu instanceof MerchantMenu)) {
            log("§e⚠ 交易界面已关闭");
            if (currentVillager != null) exhaustedVillagers.add(currentVillager);
            currentVillager = null;
            enterState(State.SEARCHING);
            return;
        }
        if (currentVillager == null || !currentVillager.isAlive()) {
            currentVillager = null;
            closeMenuAndRoute(Route.SEARCH_NEXT);
            return;
        }

        if (sendCooldown > 0) sendCooldown--;

        // 僵局检测：10 秒没有任何成交
        if (stateTicks - lastProgressAt > TRADE_IDLE_TIMEOUT) {
            log("§e⚠ 该村民长时间无进展，换下一个");
            exhaustedVillagers.add(currentVillager);
            closeMenuAndRoute(Route.SEARCH_NEXT);
            return;
        }

        switch (tradePhase) {
            case SELECT -> tickTradeSelect(player);
            case CONFIRM -> tickTradeConfirm();
        }
    }

    private void tickTradeSelect(LocalPlayer player) {
        // 退出条件 1：购买总量达成（榨干模式忽略总量，只认售罄）
        if (!drainMode && purchasedCount >= targetQuantity) {
            log("§a✓ 购买目标达成 §8▸ " + purchasedCount + " 件");
            closeMenuAndRoute(mode == Mode.LOCAL ? Route.FINISH : Route.UNLOAD);
            return;
        }
        // 退出条件 2：背包满
        if (!TradeEngine.hasSpace()) {
            log("§e⚠ 背包已满 §8▸ 前往卸货");
            closeMenuAndRoute(mode == Mode.LOCAL ? Route.FINISH : Route.UNLOAD);
            return;
        }

        // 单次遍历取最便宜的可交易项，选择与成本判断严格一致
        int index = TradeEngine.findBestOfferIndex(currentVillager, targets, maxPrice, skipOfferIndexes);
        // 退出条件 3：所有匹配交易售罄/无效
        if (index < 0) {
            log("§e⚠ 目标交易已全部售罄或无效");
            exhaustedVillagers.add(currentVillager);
            closeMenuAndRoute(Route.SEARCH_NEXT);
            return;
        }

        var offers = currentVillager.getOffers();
        int cost = TradeMatcher.getEmeraldCost(offers.get(index));
        // 退出条件 4：绿宝石不够买这一单
        if (TradeEngine.countEmeralds() < cost) {
            log("§e⚠ 绿宝石不足 §8▸ 前往补给");
            closeMenuAndRoute(mode == Mode.LOCAL ? Route.FINISH : Route.SUPPLY);
            return;
        }

        // 发包节流
        if (sendCooldown > 0) return;

        offerIndex = index;
        usesSnapshot = offers.get(index).getUses();
        emeraldSnapshot = TradeEngine.countEmeralds();
        TradeEngine.sendSelectTrade(index);
        confirmTicks = 0;
        sendCooldown = sendCooldownTicks;
        tradePhase = TradePhase.CONFIRM;
    }

    private void tickTradeConfirm() {
        confirmTicks++;

        var offers = currentVillager.getOffers();
        int usesNow = (offers != null && offerIndex < offers.size()) ? offers.get(offerIndex).getUses() : -1;
        int emeraldNow = TradeEngine.countEmeralds();

        // 成功信号：服务端刷新了 offer 用量，或绿宝石被扣除
        boolean success = (usesNow >= 0 && usesNow != usesSnapshot) || emeraldNow < emeraldSnapshot;

        if (success) {
            tradeCount++;
            lastProgressAt = stateTicks;
            int gained = TradeEngine.safeResultCount(currentVillager, offerIndex);
            purchasedCount += gained;
            failStreak = 0;
            tradePhase = TradePhase.SELECT;

            // 状态反馈（三种模式通用）：成功播报 + 村民交易提示音
            String itemName = (offerIndex < offers.size() && !offers.get(offerIndex).getResult().isEmpty())
                ? offers.get(offerIndex).getResult().getHoverName().getString()
                : "物品";
            log("§a✓ 第 " + tradeCount + " 笔成功 §7(+" + gained + " " + itemName + ")");
            mc.player.playSound(SoundEvents.VILLAGER_TRADE, 1.0F, 1.0F);
            return;
        }

        // 等待窗口结束仍未确认
        if (confirmTicks >= CONFIRM_TICKS) {
            failStreak++;
            if (failStreak >= MAX_CONFIRM_FAILS) {
                log("§e该交易连续 " + failStreak + " 次确认失败，跳过");
                skipOfferIndexes.add(offerIndex);
                tradePhase = TradePhase.SELECT;
            } else {
                // 重发同一交易，但重发前先刷新快照：
                // 每一发只评判「自己这一发」的结果，避免上一发延迟到账 + 本发成功
                // 在同一快照上连续触发成功信号，导致计数与真实购买量脱钩（重复购买/重复计数）。
                log("§e✗ 第 " + (tradeCount + 1) + " 笔未确认，重发 " + failStreak + "/" + MAX_CONFIRM_FAILS);
                usesSnapshot = (offerIndex < offers.size()) ? offers.get(offerIndex).getUses() : -1;
                emeraldSnapshot = TradeEngine.countEmeralds();
                TradeEngine.sendSelectTrade(offerIndex);
                confirmTicks = 0;
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  CLOSING_MENU - 关闭村民交易界面并按路由分流
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void closeMenuAndRoute(Route route) {
        afterCloseRoute = route;
        enterState(State.CLOSING_MENU);
    }

    private void tickClosingMenu() {
        LocalPlayer player = mc.player;
        if (player == null) {
            fail("玩家无效");
            return;
        }

        boolean containerOpen = player.containerMenu != player.inventoryMenu
            || mc.screen instanceof AbstractContainerScreen<?>;

        if (containerOpen) {
            if (stateTicks % 5 == 0) {
                ContainerBroker.closeContainer();
            }
            if (stateTicks > CLOSE_TIMEOUT) {
                player.closeContainer();
            }
            return;
        }
        dispatchRoute(afterCloseRoute);
    }

    /**
     * 关闭村民界面后的路由。箱子界面（补给/卸货）关闭后也复用 CLOSING_MENU。
     */
    private void dispatchRoute(Route route) {
        switch (route) {
            case SEARCH_NEXT -> {
                skipOfferIndexes.clear();
                currentVillager = null;
                enterState(State.SEARCHING);
            }
            case BACK_TO_VILLAGER -> {
                if (currentVillager != null && currentVillager.isAlive() && currentWorkstation != null) {
                    enterState(State.NAVIGATING);
                } else {
                    enterState(State.SEARCHING);
                }
            }
            case SUPPLY -> enterState(State.SUPPLY_NAV);
            case UNLOAD -> {
                if (unloadService.hasTaskItems(targets)) {
                    enterState(State.UNLOAD_NAV);
                } else if (mode == Mode.PIPELINE) {
                    enterState(State.NEXT_TASK);
                } else {
                    enterState(State.DONE);
                }
            }
            case FINISH -> enterState(State.DONE);
            case NEXT_TASK -> enterState(State.NEXT_TASK);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  补给流程（绿宝石箱）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickSupplyNav() {
        CunminCommand.ContainerBinding binding = CunminCommand.getBinding();
        BlockPos box = binding == null ? null : binding.getEmeraldBox();
        if (box == null) {
            fail("未绑定绿宝石箱");
            return;
        }
        if (stateTicks > NAV_TIMEOUT || navigation.isStuck()) {
            fail("前往绿宝石箱超时或卡死");
            return;
        }

        if (stateTicks == 1 || (!navigation.isPathing() && stateTicks % 60 == 0)) {
            if (!navigation.pathToContainer(box)) {
                fail("无法前往绿宝石箱");
            }
            return;
        }

        if (navigation.hasArrived(box, 3.0)) {
            navigation.stop();
            enterState(State.SUPPLY_OPEN);
        }
    }

    private void tickSupplyOpen() {
        CunminCommand.ContainerBinding binding = CunminCommand.getBinding();
        BlockPos box = binding == null ? null : binding.getEmeraldBox();
        if (box == null) {
            fail("未绑定绿宝石箱");
            return;
        }
        if (stateTicks > OPEN_TIMEOUT) {
            fail("无法打开绿宝石箱");
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            enterState(State.SUPPLY_TAKE);
            return;
        }
        if (stateTicks % 10 == 0 && !FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, box, Direction.UP)) {
            fail("绿宝石箱交互失败");
        }
    }

    private void tickSupplyTake() {
        if (stateTicks == 1) {
            supplyService.start(emeraldThreshold + supplyStacks * 64);
        }
        supplyService.tick();

        if (supplyService.getState() == SupplyService.State.COMPLETED) {
            log("§a✓ 补给完成 §8▸ 背包 " + TradeEngine.countEmeralds() + " 绿宝石");
            supplyService.reset();
            dispatchAfterContainerClose(Route.BACK_TO_VILLAGER);
        } else if (supplyService.getState() == SupplyService.State.ERROR) {
            log("§e⚠ 补给异常 §8▸ 有多少算多少继续交易");
            supplyService.reset();
            dispatchAfterContainerClose(Route.BACK_TO_VILLAGER);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  卸货流程（交易成品箱）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickUnloadNav() {
        CunminCommand.ContainerBinding binding = CunminCommand.getBinding();
        BlockPos box = binding == null ? null : binding.getUnloadBox();
        if (box == null) {
            fail("未绑定交易成品箱");
            return;
        }
        if (stateTicks > NAV_TIMEOUT || navigation.isStuck()) {
            fail("前往成品箱超时或卡死");
            return;
        }

        if (stateTicks == 1 || (!navigation.isPathing() && stateTicks % 60 == 0)) {
            if (!navigation.pathToContainer(box)) {
                fail("无法前往成品箱");
            }
            return;
        }

        if (navigation.hasArrived(box, 3.0)) {
            navigation.stop();
            enterState(State.UNLOAD_OPEN);
        }
    }

    private void tickUnloadOpen() {
        CunminCommand.ContainerBinding binding = CunminCommand.getBinding();
        BlockPos box = binding == null ? null : binding.getUnloadBox();
        if (box == null) {
            fail("未绑定交易成品箱");
            return;
        }
        if (stateTicks > OPEN_TIMEOUT) {
            fail("无法打开成品箱");
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            enterState(State.UNLOAD_TAKE);
            return;
        }
        if (stateTicks % 10 == 0 && !FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, box, Direction.UP)) {
            fail("成品箱交互失败");
        }
    }

    private void tickUnloadTake() {
        if (stateTicks == 1) {
            unloadService.start(targets);
        }
        unloadService.tick();

        if (unloadService.getState() == UnloadService.State.COMPLETED
            || unloadService.getState() == UnloadService.State.ERROR) {
            boolean ok = unloadService.getState() == UnloadService.State.COMPLETED;
            if (!ok) log("§e⚠ 卸货异常 §8▸ 跳过");
            unloadService.reset();
            // 榨干模式：卸完货回去继续榨，不因背包满而收工
            Route afterUnload = drainMode ? Route.BACK_TO_VILLAGER
                : (mode == Mode.PIPELINE ? Route.NEXT_TASK : Route.FINISH);
            dispatchAfterContainerClose(afterUnload);
        }
    }

    /**
     * 箱子界面关闭后的统一出口：复用 CLOSING_MENU 的关闭等待。
     */
    private void dispatchAfterContainerClose(Route route) {
        afterCloseRoute = route;
        enterState(State.CLOSING_MENU);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  NEXT_TASK / DONE / ERROR
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickNextTask() {
        // 上一任务收官播报（purchasedCount 是上一个任务的数据，loadTask 会清零）
        log("§a✓ 任务 " + (currentTaskIndex + 1) + "/" + pipelineTasks.size() + " 完成 §8▸ "
            + VillagerProfessionRegistry.getDisplayName(targetProfession)
            + " · 购入 " + purchasedCount + " 件");

        currentTaskIndex++;
        if (currentTaskIndex >= pipelineTasks.size()) {
            log("§a§l✓ Pipeline 全部任务完成！");
            enterState(State.DONE);
            return;
        }

        loadTask(currentTaskIndex);
        log("§b开始 Pipeline 任务 " + (currentTaskIndex + 1) + "/" + pipelineTasks.size()
            + " §8▸ " + VillagerProfessionRegistry.getDisplayName(targetProfession));
        enterState(State.SEARCHING);
    }

    private void tickDone() {
        if (stateTicks != 1) return;

        String summary;
        if (purchasedCount > 0) {
            summary = "§a✓ 交易完成 §8▸ 共执行 " + tradeCount + " 笔 · 购入 " + purchasedCount + " 件目标物品";
        } else {
            summary = "§e⚠ 交易结束 §8▸ 未购买到任何目标物品";
        }
        if (drainMode) summary += " §7(榨干模式)";
        log(summary);
        if (completeHandler != null) {
            completeHandler.accept(summary);
        }
        enterState(State.IDLE);
    }

    private void tickError() {
        if (stateTicks != 1) return;

        navigation.stop();
        log("§c✗ 运行出错 §8▸ " + stopReason);
        if (errorHandler != null) {
            errorHandler.accept(stopReason);
        }
        enterState(State.IDLE);
    }

    private void fail(String reason) {
        stopReason = reason;
        enterState(State.ERROR);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  工具方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 搜索村民附近的工作站（与旧实现相同的扫描范围）。
     */
    private BlockPos findNearbyWorkstation(BlockPos center, Block targetBlock, int range) {
        if (mc.level == null) return null;

        for (int y = -range; y <= range; y++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() == targetBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void enterState(State newState) {
        currentState = newState;
        stateTicks = 0;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    public boolean isRunning() {
        return currentState != State.IDLE && currentState != State.DONE && currentState != State.ERROR;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  枚举
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 运行模式 */
    public enum Mode {
        LOCAL,          // 原地交易
        SINGLE_PATH,    // 寻路单点
        PIPELINE        // 多任务流水线
    }

    /** 状态 */
    public enum State {
        IDLE,
        SEARCHING,
        RESOLVING_WORKSTATION,
        NAVIGATING,
        OPENING_MENU,
        TRADING,
        CLOSING_MENU,
        SUPPLY_NAV,
        SUPPLY_OPEN,
        SUPPLY_TAKE,
        UNLOAD_NAV,
        UNLOAD_OPEN,
        UNLOAD_TAKE,
        NEXT_TASK,
        DONE,
        ERROR
    }

    /** 交易子阶段 */
    private enum TradePhase {
        SELECT,
        CONFIRM
    }

    /** 关闭界面后的路由 */
    private enum Route {
        SEARCH_NEXT,       // 换下一个村民
        BACK_TO_VILLAGER,  // 返回当前村民继续交易
        SUPPLY,            // 去补给
        UNLOAD,            // 去卸货
        FINISH,            // 直接收尾
        NEXT_TASK          // 推进 Pipeline 任务
    }
}