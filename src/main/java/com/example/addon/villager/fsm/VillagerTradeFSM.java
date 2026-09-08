package com.example.addon.villager.fsm;

import com.example.addon.villager.command.CunminCommand;
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 村民交易有限状态机（26.1.2）
 *
 * 三种模式的核心差异：
 * · LOCAL（原地交易）：不寻路工作站，只与身边目标村民交易，只自动交易；
 *   绿宝石不足/背包满仅提示玩家手动补给/卸货，不自动寻路箱子
 * · SINGLE_PATH（寻路单点）：寻路到村民附近，交易过程中自动补给/卸货
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
    private int sendCooldownTicks = 2;    // 交易发包节流（2 tick，仅等服务端确认，实现「一次买满」连续交易）
    private int emeraldThreshold = 32;    // 低于该值触发补给
    private int supplyStacks = 1;         // 每次补给追加组数（1组=64个），补给目标=阈值+组数×64
    private int targetQuantity = 64;      // 本次购买总量（件，默认榨干下不参与退出判定）
    private int searchRange = 96;         // 寻路模式搜索目标村民的半径（格，可配置）
    private boolean idleLoop = false;     // 挂机循环模式：榨干后等待补货倒计时，到点重新循环（仅寻路用）
    private int restockWaitTicks = 2400;  // 挂机循环补货等待（tick，默认 2400 = 2 分钟）

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
    private int confirmTicks = 0;
    private int failStreak = 0;
    private int sendCooldown = 0;
    private int purchasedCount = 0;
    private int tradeCount = 0;
    private final Map<Item, Integer> perItemPurchased = new HashMap<>();
    private int lastProgressAt = 0;
    private int interactTries = 0;
    private int openRetries = 0;

    // 关闭界面后的去向
    private Route afterCloseRoute = Route.SEARCH_NEXT;
    private String stopReason = "";
    private WaitReason waitReason = WaitReason.SUPPLY;   // 原地模式等待玩家操作的原因

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  超时与阈值
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static final int SEARCH_TIMEOUT = 600;       // 30 秒
    private static final int NAV_TIMEOUT = 2400;         // 120 秒
    private static final int OPEN_TIMEOUT = 100;         // 5 秒
    private static final int CLOSE_TIMEOUT = 60;         // 3 秒
    private static final int TRADE_IDLE_TIMEOUT = 200;   // 10 秒无进展换村民
    private static final int CONFIRM_TICKS = 20;         // 交易确认等待（1 秒，避免同步延迟误判重发导致重复购买）
    private static final int MAX_CONFIRM_FAILS = 2;      // 同一 offer 连败次数（最多重发 1 次）
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
                          int maxPrice, int emeraldThreshold, int targetQuantity) {
        this.targetProfession = profession;
        this.targets = new ArrayList<>(targets);
        this.maxPrice = maxPrice;
        this.emeraldThreshold = emeraldThreshold;
        this.targetQuantity = Math.max(1, targetQuantity);
    }

    /**
     * 每次绿宝石补给的追加组数（1组=64个）。
     * 补给目标 = 触发阈值(32) + 组数×64，默认 1 组 → 补到 96 个。
     */
    public void setSupplyStacks(int stacks) {
        this.supplyStacks = Math.max(0, stacks);
    }

    /**
     * 设置寻路模式的村民搜索半径（格）。
     * 原地模式不受影响（固定按交互距离过滤），仅影响寻路单点/多任务模式。
     */
    public void setSearchRange(int range) {
        this.searchRange = Math.max(8, range);
    }

    /**
     * 挂机循环模式开关（仅寻路单点/多任务模式生效）：榨干全部村民后不结束，
     * 而是进入补货倒计时，到点清空「已榨干」记录重新循环交易。
     */
    public void setIdleLoop(boolean enabled) {
        this.idleLoop = enabled;
    }

    /**
     * 挂机循环的补货等待时长（tick）。默认 2400（2 分钟），
     * 与村民补货机制 {@code allowedToRestock()} 的 2400 tick 冷却一致。
     */
    public void setRestockWaitTicks(int ticks) {
        this.restockWaitTicks = Math.max(400, ticks);
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
        confirmTicks = 0;
        failStreak = 0;
        sendCooldown = 0;
        purchasedCount = 0;
        tradeCount = 0;
        perItemPurchased.clear();
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
        this.perItemPurchased.clear();
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
            case WAITING_RESTOCK -> tickWaitingRestock();
            case WAITING_PLAYER -> tickWaitingPlayer();
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
            } else if (idleLoop) {
                // 挂机循环：榨干全部村民后不结束，进入补货倒计时，到点重新循环
                enterState(State.WAITING_RESTOCK);
            } else {
                log("§d⚠ " + profName() + "已榨干 §8▸ 范围内没有更多可用村民");
                enterState(State.DONE);
            }
            return;
        }

        // LOCAL 只搜身边，寻路模式搜可配置半径（村民寻路距离不受限，默认 96 格）
        double range = mode == Mode.LOCAL ? 16.0 : (double) searchRange;

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
        // 幼年村民无法交易，直接排除
        if (villager.isBaby()) return false;
        if (exhaustedVillagers.contains(villager)) return false;

        // 职业匹配（26.1.2：VillagerProfession 是 Record，常量是 ResourceKey，
        // 用注册表 Identifier 比较，value().equals() 不可靠，会把傻子/失业村民误匹配进来）
        try {
            Identifier targetId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(targetProfession);
            if (targetId == null || !villager.getVillagerData().profession().is(targetId)) {
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

        // 工作方块只在村民脚下或紧邻水平一格：范围收窄到 1，避免搜到隔壁村民的工作方块
        // 导致「正前方」方向算错、把站位带偏到侧面
        BlockPos workstation = findNearbyWorkstation(currentVillager.blockPosition(), workstationBlock, 1);
        if (workstation == null) {
            log("§e⚠ " + profName() + "村民附近未找到工作站 §8▸ 换下一个");
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
        if (currentVillager == null || !currentVillager.isAlive()) {
            fail("村民无效");
            return;
        }

        // 路径被意外取消时重试
        if (!navigation.isPathing() && stateTicks % 40 == 0) {
            startPathToVillager();
            return;
        }

        // 到达判断：有精确站位时必须走到「工作方块前面」才算到达，
        // 否则玩家从村民后面/侧面经过（距离 ≤3）会提前触发交易，永远到不了前面。
        // range 取 0.5：玩家必须站在目标方块上（distSqr=0），侧面一格(distSqr=1)不算到达。
        BlockPos standTarget = navigation.getVillagerStandTarget();
        boolean arrived = standTarget != null
            ? navigation.hasArrived(standTarget, 0.5)
            : mc.player.distanceTo(currentVillager) <= INTERACT_RANGE;
        if (arrived) {
            navigation.stop();
            interactTries = 0;
            enterState(State.OPENING_MENU);
        }
    }

    private boolean startPathToVillager() {
        // 站位优先用「工作方块前面」（玩家-工作方块-村民共线），隔工作方块正面交互；
        // 工作方块已收窄到紧邻一格，识别可靠。正前方不可站立时 navigation 内部回退近程寻路。
        if (currentVillager != null && currentVillager.isAlive()) {
            return navigation.pathToVillager(currentVillager.blockPosition(), currentWorkstation);
        }
        return false;
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

        if (stateTicks > OPEN_TIMEOUT || player.distanceTo(currentVillager) > INTERACT_RANGE) {
            // 寻路模式优先回到工作站重新找位，村民可能离开工作站了
            if (mode != Mode.LOCAL && currentWorkstation != null && openRetries == 0) {
                openRetries++;
                enterState(State.NAVIGATING);
                return;
            }
            log("§e⚠ 无法与" + profName() + "村民交互 §8▸ 换下一个");
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
                // 只在交互发包瞬间转视角，避免每 tick 覆盖玩家视角（不抢鼠标/视角）
                faceVillager(player, currentVillager);
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
            // 只有玩家背包屏幕真的开着（mc.screen 是 InventoryScreen）才算玩家手动干预；
            // containerMenu == inventoryMenu 只是「无容器打开」的默认态，不能据此误判玩家打开背包
            if (mc.screen instanceof InventoryScreen) {
                fail("检测到玩家打开背包，交易已中断");
                return;
            }
            log("§e⚠ " + profName() + "交易界面已关闭");
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
            log("§e⚠ " + profName() + "长时间无进展 §8▸ 换下一个");
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
        // 默认榨干：不因购买总量达成而退出，一路买到目标交易全部售罄/锁死为止
        // 退出条件 1：背包满
        if (!TradeEngine.hasSpace()) {
            // 原地模式：玩家自主卸货，只提示不自动寻路卸货箱
            if (mode == Mode.LOCAL) {
                waitReason = WaitReason.UNLOAD;
                closeMenuAndRoute(Route.WAIT_PLAYER);
                return;
            }
            log("§e⚠ 背包已满 §8▸ " + profName() + "交易暂停，前往卸货");
            closeMenuAndRoute(Route.UNLOAD);
            return;
        }

        // 客户端报价列表必须来自交易界面 MerchantMenu，Villager.getOffers() 客户端会抛异常
        var offers = ((MerchantMenu) player.containerMenu).getOffers();

        // 单次遍历按「已购最少优先 + 同物品最便宜」轮换选择，勾选多个目标时轮流买入
        int index = TradeEngine.findBestOfferIndex(offers, targets, maxPrice, skipOfferIndexes, perItemPurchased);
        // 退出条件 3：所有匹配交易售罄/无效
        if (index < 0) {
            // 区分「村民没刷出目标物品」与「目标物品已售罄/超价」，避免笼统提示误导
            boolean hasItem = false;
            for (var offer : offers) {
                if (TradeMatcher.matchesItemOnly(offer, targets)) { hasItem = true; break; }
            }
            if (hasItem) {
                log("§e⚠ " + targetSummary() + "已售罄或超价 §8▸ 换下一个");
            } else {
                List<String> names = new ArrayList<>();
                for (VillagerTradeTarget t : targets) names.add(t.getDisplayName());
                log("§e⚠ " + profName() + "未刷出目标物品 §8▸ " + String.join("、", names));
            }
            exhaustedVillagers.add(currentVillager);
            closeMenuAndRoute(Route.SEARCH_NEXT);
            return;
        }

        int cost = TradeMatcher.getEmeraldCost(offers.get(index));
        // 退出条件 4：绿宝石不够买这一单
        if (TradeEngine.countEmeralds() < cost) {
            // 原地模式：玩家自主补给，只提示不自动寻路绿宝石箱
            if (mode == Mode.LOCAL) {
                waitReason = WaitReason.SUPPLY;
                closeMenuAndRoute(Route.WAIT_PLAYER);
                return;
            }
            log("§e⚠ 绿宝石不足 §8▸ 无法购买" + targetSummary() + "，前往补给");
            closeMenuAndRoute(Route.SUPPLY);
            return;
        }

        // 发包节流
        if (sendCooldown > 0) return;

        offerIndex = index;
        usesSnapshot = offers.get(index).getUses();
        failStreak = 0; // 换新 offer 后重开连败计数，避免上一 offer 的连败残留累加

        TradeEngine.executeTrade(index);
        confirmTicks = 0;
        sendCooldown = sendCooldownTicks;
        tradePhase = TradePhase.CONFIRM;
    }

    private void tickTradeConfirm() {
        confirmTicks++;

        var offers = ((MerchantMenu) mc.player.containerMenu).getOffers();
        int usesNow = (offerIndex < offers.size()) ? offers.get(offerIndex).getUses() : -1;

        // 成功信号：只认 offer 用量 uses 递增（服务端真正成交才会 increaseUses）。
        // 不再用「绿宝石减少」判定：SelectTrade 会把绿宝石挪进付款槽，导致假阳性。
        boolean success = usesNow > usesSnapshot;

        if (success) {
            tradeCount++;
            lastProgressAt = stateTicks;
            // QUICK_MOVE 会循环购买多次：实际成交次数 = uses 差值，乘以单次产出得到本笔获得数量
            int trades = usesNow - usesSnapshot;
            int gained = trades * TradeEngine.safeResultCount(offers, offerIndex);
            purchasedCount += gained;
            // 记录每个物品的累计购入量，供下一笔轮换选择（避免只买最便宜的一种）
            if (offerIndex < offers.size() && !offers.get(offerIndex).getResult().isEmpty()) {
                perItemPurchased.merge(offers.get(offerIndex).getResult().getItem(), gained, Integer::sum);
            }
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
                failStreak = 0; // 跳过当前 offer 后复位，下一 offer 从 0 重新累计
                tradePhase = TradePhase.SELECT;
            } else {
                // 重发同一笔完整交易（含结果槽点击），重发前刷新快照：
                // 每一发只评判「自己这一发」的结果，避免上一发延迟到账 + 本发成功
                // 在同一快照上连续触发成功信号，导致计数与真实购买量脱钩。
                log("§e✗ 第 " + (tradeCount + 1) + " 笔未确认，重发 " + failStreak + "/" + MAX_CONFIRM_FAILS);
                usesSnapshot = (offerIndex < offers.size()) ? offers.get(offerIndex).getUses() : -1;
                TradeEngine.executeTrade(offerIndex);
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

        // 静默模式下没有 Screen，containerMenu 是唯一权威判断：
        // 不等于 inventoryMenu 说明村民界面或箱子界面仍开着
        boolean containerOpen = player.containerMenu != player.inventoryMenu;

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
            case WAIT_PLAYER -> enterState(State.WAITING_PLAYER);
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
        if (!isSameDimension(binding.getEmeraldBoxDimension())) {
            fail("绿宝石箱在其他维度");
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
        if (mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu.containerId != 0) {
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
            supplyService.reset();
            // 补给结束后校验：背包绿宝石仍低于阈值，说明绿宝石箱已空拿不到货，
            // 必须停机，否则会陷入「绿宝石不足 → 空箱补给 → 不足」的无限循环
            if (TradeEngine.countEmeralds() < emeraldThreshold) {
                fail("绿宝石箱已空 §8▸ 无法补给，任务停止");
                return;
            }
            log("§a✓ 补给完成 §8▸ 背包 " + TradeEngine.countEmeralds() + " 绿宝石");
            dispatchAfterContainerClose(Route.BACK_TO_VILLAGER);
        } else if (supplyService.getState() == SupplyService.State.ERROR) {
            supplyService.reset();
            if (TradeEngine.countEmeralds() < emeraldThreshold) {
                fail("补给异常 §8▸ 绿宝石不足，任务停止");
                return;
            }
            log("§e⚠ 补给异常 §8▸ 有多少算多少继续交易");
            dispatchAfterContainerClose(Route.BACK_TO_VILLAGER);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  卸货流程（成品交易箱）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void tickUnloadNav() {
        CunminCommand.ContainerBinding binding = CunminCommand.getBinding();
        BlockPos box = binding == null ? null : binding.getUnloadBox();
        if (box == null) {
            fail("未绑定成品交易箱");
            return;
        }
        if (!isSameDimension(binding.getUnloadBoxDimension())) {
            fail("成品交易箱在其他维度");
            return;
        }
        if (stateTicks > NAV_TIMEOUT || navigation.isStuck()) {
            fail("前往成品交易箱超时或卡死");
            return;
        }

        if (stateTicks == 1 || (!navigation.isPathing() && stateTicks % 60 == 0)) {
            if (!navigation.pathToContainer(box)) {
                fail("无法前往成品交易箱");
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
            fail("未绑定成品交易箱");
            return;
        }
        if (stateTicks > OPEN_TIMEOUT) {
            fail("无法打开成品交易箱");
            return;
        }
        if (mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu.containerId != 0) {
            enterState(State.UNLOAD_TAKE);
            return;
        }
        if (stateTicks % 10 == 0 && !FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, box, Direction.UP)) {
            fail("成品交易箱交互失败");
        }
    }

    private void tickUnloadTake() {
        if (stateTicks == 1) {
            unloadService.start(targets);
        }
        unloadService.tick();

        // 成品箱满：停机提示，不能「跳过继续」否则背包一直满、交易永远失败
        if (unloadService.getState() == UnloadService.State.FULL) {
            unloadService.reset();
            fail("成品交易箱已满 §8▸ 请清空后重试");
            return;
        }

        if (unloadService.getState() == UnloadService.State.COMPLETED
            || unloadService.getState() == UnloadService.State.ERROR) {
            boolean ok = unloadService.getState() == UnloadService.State.COMPLETED;
            if (!ok) log("§e⚠ 卸货异常 §8▸ 跳过");
            unloadService.reset();
            // 默认榨干：卸完货回去继续榨，不因背包满而收工
            dispatchAfterContainerClose(Route.BACK_TO_VILLAGER);
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
    //  WAITING_RESTOCK / WAITING_PLAYER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 挂机循环补货等待：榨干全部村民后倒计时，到点清空榨干记录重新循环。
     *
     * <p>村民补货由服务端 {@code WorkAtPoi} 行为触发，补货冷却
     * {@code allowedToRestock()} 要求距上次补货 ≥ 2400 tick（2 分钟），
     * 因此默认等待 2400 tick，等待结束重新打开交易界面即可买到补货后的新库存。</p>
     */
    private void tickWaitingRestock() {
        if (stateTicks == 1) {
            log("§d⏳ 全部村民已榨干 §8▸ 等待补货 " + (restockWaitTicks / 20) + " 秒后循环");
            return;
        }

        int remainingTicks = restockWaitTicks - stateTicks;
        if (remainingTicks > 0) {
            // 倒计时节流播报（每 100 tick = 5 秒一次，避免刷屏）
            if (stateTicks % 100 == 0) {
                log("§7等待村民补货 §8▸ 剩余 " + (remainingTicks / 20) + " 秒");
            }
            return;
        }

        // 倒计时结束：清空榨干记录，重新搜索开始新一轮
        exhaustedVillagers.clear();
        currentVillager = null;
        currentWorkstation = null;
        log("§a✓ 补货等待结束 §8▸ 开始新一轮交易");
        enterState(State.SEARCHING);
    }

    /**
     * 原地模式玩家自主等待：不自动补给/卸货，仅提示玩家手动处理，处理完自动恢复交易。
     */
    private void tickWaitingPlayer() {
        LocalPlayer player = mc.player;
        if (player == null) {
            fail("玩家无效");
            return;
        }

        // 提示节流（首帧 + 每 60 tick = 3 秒一次）
        if (stateTicks == 1 || stateTicks % 60 == 0) {
            if (waitReason == WaitReason.SUPPLY) {
                log("§e⚠ 绿宝石不足 §8▸ 请手动补给绿宝石后继续");
            } else {
                log("§e⚠ 背包已满 §8▸ 请手动卸货后继续");
            }
        }

        // 条件恢复判定
        boolean recovered = waitReason == WaitReason.SUPPLY
            ? TradeEngine.countEmeralds() >= emeraldThreshold
            : TradeEngine.hasSpace();

        if (recovered) {
            // 玩家处理完毕且村民仍在附近：重新打开交易界面继续自动交易
            if (currentVillager != null && currentVillager.isAlive()
                && player.distanceTo(currentVillager) <= 6.0) {
                interactTries = 0;
                openRetries = 0;
                enterState(State.OPENING_MENU);
            } else {
                // 村民丢失/走远：重新搜索
                enterState(State.SEARCHING);
            }
        }
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
            summary = "§a✓ 交易完成 §8▸ " + profName() + " · 共执行 " + tradeCount + " 笔 · 购入 " + purchasedCount + " 件";
        } else {
            summary = "§e⚠ 交易结束 §8▸ " + profName() + "未购买到任何目标物品";
        }
        summary += " §7(榨干模式)";
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
     * 判断绑定箱子的维度是否等于当前玩家所在维度（用字符串 contains 兼容裸 ID 与旧 ResourceKey 格式）。
     */
    private boolean isSameDimension(String boundDim) {
        if (mc.level == null || boundDim == null) return false;
        String cur = mc.level.dimension().toString();
        if (boundDim.contains("the_nether")) return cur.contains("the_nether");
        if (boundDim.contains("the_end")) return cur.contains("the_end");
        return cur.contains("overworld");
    }

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
            logger.accept("§8[" + modePrefix() + "]§r " + message);
        }
    }

    /** 运行模式中文前缀（每次日志统一带，方便区分当前模式）。 */
    private String modePrefix() {
        return switch (mode) {
            case LOCAL -> "原地交易";
            case SINGLE_PATH -> "寻路单点";
            case PIPELINE -> "多任务";
        };
    }

    /** 目标职业中文名（统一取用，避免各处重复拼职业名）。 */
    private String profName() {
        return VillagerProfessionRegistry.getDisplayName(targetProfession);
    }

    /** 目标物品摘要：单物品返回其名，多物品返回「首个 等N种」。 */
    private String targetSummary() {
        if (targets == null || targets.isEmpty()) return "目标物品";
        if (targets.size() == 1) return targets.get(0).getDisplayName();
        return targets.get(0).getDisplayName() + " 等" + targets.size() + "种";
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
        WAITING_RESTOCK,   // 挂机循环：等待村民补货倒计时
        WAITING_PLAYER,    // 原地模式：等待玩家手动补给/卸货
        NEXT_TASK,
        DONE,
        ERROR
    }

    /** 交易子阶段 */
    private enum TradePhase {
        SELECT,
        CONFIRM
    }

    /** 原地模式等待玩家操作的原因 */
    private enum WaitReason {
        SUPPLY,   // 等玩家手动补给绿宝石
        UNLOAD    // 等玩家手动卸货
    }

    /** 关闭界面后的路由 */
    private enum Route {
        SEARCH_NEXT,       // 换下一个村民
        BACK_TO_VILLAGER,  // 返回当前村民继续交易
        SUPPLY,            // 去补给
        UNLOAD,            // 去卸货
        FINISH,            // 直接收尾
        NEXT_TASK,         // 推进 Pipeline 任务
        WAIT_PLAYER        // 原地模式进入等待玩家操作
    }
}