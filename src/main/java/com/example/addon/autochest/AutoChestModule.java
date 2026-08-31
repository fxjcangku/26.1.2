package com.example.addon.autochest;

import com.example.addon.autochest.model.ChestTarget;
import com.example.addon.autochest.model.ContainerType;
import com.example.addon.autochest.model.ContainerTypeRegistry;
import com.example.addon.autochest.model.ScanMode;
import com.example.addon.autochest.model.WithdrawMode;
import com.example.addon.autochest.scan.ContainerScanner;
import com.example.addon.autochest.scan.ContainerSelector;
import com.example.addon.autochest.service.ChestInteractionService;
import com.example.addon.autochest.service.ChestPointManager;
import com.example.addon.autochest.service.ContainerRecordManager;
import com.example.addon.autochest.service.PathingService;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.itemid.ItemIdManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动箱子模块（辅助体系 · 第三环）。
 *
 * <p>扫描合法容器 → 发现目标 → 判断是否已处理 → 锁定 → 移动/等待 → 开箱
 * → 读取真实 Slot → 精确识别 ItemStack → 按取物模式取物 → 关箱 → 保存记录
 * → ESP 变红 → 寻找下一个。</p>
 *
 * <p>目标物品统一来自「ID 配置管理」（{@link ItemIdManager}），本模块只消费，
 * 不建立第二套物品数据库。</p>
 */
public final class AutoChestModule extends YiyiaddonModule {

    // ── 设置（渲染器 / 状态机需要访问，故用包可见） ──
    final AutoChestSettings moduleSettings;

    // ── 数据（消费 ID 配置管理） ──
    private final ItemIdManager idManager;

    // ── 扫描与选择 ──
    final ContainerScanner scanner;
    final ContainerSelector selector;

    // ── 服务层 ──
    final ContainerRecordManager recordManager;
    final ChestPointManager pointManager;
    final ChestInteractionService interactionService;
    final PathingService pathingService;

    // ── 状态机与渲染 ──
    private final AutoChestStateMachine stateMachine;
    private final AutoChestRenderer renderer;

    // ── 扫描周期计数与容器破坏对账缓存 ──
    private int scanTick = 0;
    private final Set<BlockPos> lastSeenContainers = new HashSet<>();

    // ── 面板主题引用（标点/记录按钮弹确认窗时复用） ──
    private GuiTheme theme;

    public AutoChestModule(ItemIdManager idManager) {
        super(AddonTemplate.CATEGORY_ASSIST, "自动箱子", "扫描并自动处理附近容器，取走ID配置中的目标物品。详细参考下面使用说明。");
        this.idManager = idManager;
        this.moduleSettings = new AutoChestSettings(settings, idManager);

        this.scanner = new ContainerScanner(mc, moduleSettings.containerTypes::enabledTypes);
        this.selector = new ContainerSelector();
        this.recordManager = new ContainerRecordManager(mc);
        this.pointManager = new ChestPointManager(mc);
        this.interactionService = new ChestInteractionService(mc, moduleSettings);
        this.pathingService = new PathingService(mc);
        this.stateMachine = new AutoChestStateMachine(this);
        this.renderer = new AutoChestRenderer(mc, this);

        // 监听 ID 配置变更：增删/清空/重载后，目标选择器实时联动（无需重启）
        idManager.addListener(this::onIdManagerChanged);
    }

    /**
     * ID 配置变更回调：清理选择器里已失效的选中项并刷新计数。
     *
     * <p>删除 ID 后若该 ID 正被选中，这里同步移除并提示「ID 已失效」，
     * 保证 AutoChest 不因数据消失而崩溃。</p>
     */
    private void onIdManagerChanged() {
        int removed = moduleSettings.targetItems.pruneInvalid();
        moduleSettings.targetItems.refreshCount();
        if (removed > 0) {
            notifyError("有 " + removed + " 个目标 ID 已失效，已从选择器移除");
        }
    }

    /** 当前正在处理的容器（供渲染器区分「处理中」态） */
    ChestTarget processingTarget() {
        return stateMachine.processingTarget();
    }

    /** 状态机播报入口（包可见，供状态机调用统一前缀消息） */
    void notifyStatus(String message) {
        notify(message);
    }

    /**
     * 致命条件导致停止自动箱子：播报原因并关闭模块。
     *
     * <p>背包满等场景下继续取物无意义，关闭容器后停止模块，等玩家清理背包再手动开启。
     * 关闭走 mc.execute 延后到下一帧，避免在状态机 tick 内直接 toggle() 造成重入。</p>
     */
    void stopAutomation(String reason) {
        notifyStatus("§c✗ " + reason + "，已停止自动箱子");
        chatFeedback = false;
        mc.execute(() -> {
            if (isActive()) toggle();
            chatFeedback = true;
        });
    }

    @Override
    public void onActivate() {
        // 世界就绪判断：自检会访问 idManager / pointManager，先确保已进入世界
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            notifyError("必须在进入世界后才能启动模块。");
            chatFeedback = false;
            mc.execute(() -> {
                if (isActive()) toggle();
                chatFeedback = true;
            });
            return;
        }

        // 装载当前服务器的 ID 配置、已处理记录、标点（自检依赖这些数据）
        idManager.reload();
        recordManager.reload();
        pointManager.reload();

        // 启动自检：缺项一次列全，配好一项下次就少一条
        if (!reportSelfCheck(selfCheck())) return;

        // 同步扫描半径并清空破坏对账缓存
        scanner.setRadius(moduleSettings.scanRadius.get());
        lastSeenContainers.clear();

        stateMachine.reset();
        scanner.reset();
    }

    /**
     * 启动自检：收集全部缺项，交给 reportSelfCheck 一次性多行播报。
     *
     * <p>收集全部而不是遇到第一个就返回，用户一次就能看到还差什么，
     * 配好一项下次启动就少一条，不用反复开关模块试错。</p>
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();

        // 容器类型：至少选择一种可识别容器
        if (moduleSettings.containerTypes.enabledTypes().isEmpty()) {
            missing.add("§a容器类型§f·未选择任何可识别的容器");
        }

        // 目标物品：非「全部拿空」模式必须有目标物品，否则无从取物
        if (moduleSettings.withdrawMode.get() != WithdrawMode.TAKE_ALL
            && moduleSettings.targetItems.selectedIdentities().isEmpty()) {
            missing.add("§a目标物品§f·当前取物模式需要目标物品，但未选择任何目标");
        }

        // 标点模式：必须有已保存点位，否则没有处理对象
        if (moduleSettings.scanMode.get() == ScanMode.MARKER && !pointManager.hasPoints()) {
            missing.add("§a标点模式§f·尚未保存任何点位（用 .autochest add 添加）");
        }

        return missing;
    }

    @Override
    public void onDeactivate() {
        // 停止寻路、关闭容器、重置状态机，避免残留
        pathingService.stop();
        interactionService.close();
        stateMachine.reset();
        lastSeenContainers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        if (mc.player == null || mc.level == null) return;

        // 状态机推进
        stateMachine.tick();

        BlockPos playerPos = mc.player.blockPosition();

        // 扫描周期：每 scanInterval tick 请求一轮新扫描（缓存 + 分帧，不整世界全扫）
        if (++scanTick >= moduleSettings.scanInterval.get()) {
            scanTick = 0;
            scanner.requestScan(playerPos);
        }
        // 分帧推进扫描；本轮完成时做容器破坏对账（旧记录失效）
        if (scanner.tick(playerPos)) {
            reconcileDestroyed(playerPos);
        }

        // 容器交互同步推进
        interactionService.tick();
    }

    /**
     * 容器破坏对账：本轮扫描仍覆盖但已消失的容器位置，视为被破坏，使旧记录失效。
     *
     * <p>这样「破坏 → 重新放置同类容器」能被重新识别为未处理，ESP 恢复绿色。
     * 只对当前扫描范围（切比雪夫 ≤ radius）内的消失位置生效，避免移动导致的误判。</p>
     */
    private void reconcileDestroyed(BlockPos playerPos) {
        Set<BlockPos> seen = new HashSet<>();
        for (ChestTarget t : scanner.results()) {
            if (t.inCurrentDimension()) seen.add(t.pos());
        }

        String dim = WorldIdentity.dimension(mc);
        int radius = moduleSettings.scanRadius.get();
        for (BlockPos old : lastSeenContainers) {
            if (seen.contains(old)) continue;
            if (withinCube(old, playerPos, radius)) {
                recordManager.invalidate(old, dim);
            }
        }
        lastSeenContainers.clear();
        lastSeenContainers.addAll(seen);
    }

    /** 坐标是否落在以玩家为中心的扫描立方体内（切比雪夫距离 ≤ radius） */
    private static boolean withinCube(BlockPos pos, BlockPos center, int radius) {
        return Math.abs(pos.getX() - center.getX()) <= radius
            && Math.abs(pos.getY() - center.getY()) <= radius
            && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isActive()) return;
        renderer.render(event);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        this.theme = theme;
        return buildInfoWidget(theme, this::buildHeader, buildSections());
    }

    /** 面板头部按钮：标点管理 + 处理记录清除，全部直接调用 Service，不模拟聊天输入 */
    private void buildHeader(WTable table) {
        addUniformButton(theme, table, "设置箱子点位", this::addPointFromCrosshair);
        table.row();
        addUniformButton(theme, table, "删除箱子点位", this::removePointFromCrosshair);
        table.row();
        addUniformButton(theme, table, "查看当前设置箱子信息", this::showPoints);
        table.row();
        addUniformButton(theme, table, "清空全部点位", () ->
            mc.setScreen(new ConfirmScreen(theme, "清空点位", "确定要清空全部标点吗？此操作不可恢复。", this::clearAllPoints)));
        table.row();
        addUniformButton(theme, table, "清除当前维度处理记录", () ->
            mc.setScreen(new ConfirmScreen(theme, "清除记录", "确定清除当前维度的已处理记录吗？", this::clearCurrentDimensionRecords)));
        table.row();
        addUniformButton(theme, table, "清除全部处理记录", () ->
            mc.setScreen(new ConfirmScreen(theme, "清除记录", "确定清除全部服务器的已处理记录吗？", this::clearAllRecords)));
        table.row();
    }

    // ── 标点 / 记录管理（GUI 与指令共用底层 ChestPointManager / ContainerRecordManager） ──

    /** 读取准星指向的方块坐标，未对准方块返回 null */
    private BlockPos crosshairBlock() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return null;
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    /** 判定坐标是否为当前启用的合法容器类型，非容器返回 null */
    private ContainerType containerTypeAt(BlockPos pos) {
        if (mc.level == null) return null;
        Block block = mc.level.getBlockState(pos).getBlock();
        return ContainerTypeRegistry.match(block, moduleSettings.containerTypes.enabledTypes());
    }

    /** 设置箱子点位：校验准星方块为合法容器后保存 */
    private void addPointFromCrosshair() {
        if (mc.player == null || mc.level == null) {
            notifyError("玩家未加载");
            return;
        }
        BlockPos target = crosshairBlock();
        if (target == null) {
            notifyError("准星未对准任何方块");
            return;
        }
        ContainerType type = containerTypeAt(target);
        if (type == null) {
            notifyError("当前目标不是可绑定容器");
            return;
        }
        String dim = WorldIdentity.dimension(mc);
        if (pointManager.add(target, dim, type.id())) {
            notify("§a§l✓ 已添加标点 §8▸ " + YiyiaddonModule.formatCoords(target.getX(), target.getY(), target.getZ())
                + " §8▸ §a" + type.displayName());
        } else {
            notifyError("该标点已存在");
        }
    }

    /** 删除箱子点位：按服务器 + 维度 + 坐标删除，避免误删其它服务器 / 维度相同坐标 */
    private void removePointFromCrosshair() {
        if (mc.player == null || mc.level == null) {
            notifyError("玩家未加载");
            return;
        }
        BlockPos target = crosshairBlock();
        if (target == null) {
            notifyError("准星未对准任何方块");
            return;
        }
        String dim = WorldIdentity.dimension(mc);
        if (pointManager.remove(target, dim)) {
            notify("§c§l✗ 已删除标点 §8▸ " + YiyiaddonModule.formatCoords(target.getX(), target.getY(), target.getZ()));
        } else {
            notifyError("该坐标没有标点");
        }
    }

    /** 查看当前维度点位信息：服务器/世界、维度、坐标、容器类型、处理状态与数量 */
    private void showPoints() {
        if (mc.player == null || mc.level == null) {
            notifyError("玩家未加载");
            return;
        }
        List<ChestTarget> points = pointManager.pointsInCurrentDimension();
        notify("§b§l━━ 自动箱子 ▸ 标点管理 ━━");
        if (points.isEmpty()) {
            notify("§7当前维度没有标点");
        } else {
            String server = WorldIdentity.serverDisplayName(mc);
            long expireMs = moduleSettings.recordExpireMinutes.get() * 60_000L;
            for (ChestTarget p : points) {
                String typeName = ContainerTypeRegistry.byId(p.containerType()) == null
                    ? p.containerType() : ContainerTypeRegistry.byId(p.containerType()).displayName();
                boolean processed = recordManager.isProcessed(p.pos(), p.dimension(), p.containerType(), expireMs);
                String status = processed ? "§c已处理" : "§a未处理";
                notify("§d■ §8▸ §f" + typeName + " §8▸ "
                    + YiyiaddonModule.formatCoords(p.pos().getX(), p.pos().getY(), p.pos().getZ())
                    + " §8▸ " + status);
            }
            notify("§7服务器 §8▸ §f" + server
                + " §7维度 §8▸ §f" + WorldIdentity.dimensionDisplayName(WorldIdentity.dimension(mc))
                + " §7数量 §8▸ §e" + points.size());
        }
        notify("§b§l━━━━━━━━━━━━━━━━━━━━━━");
    }

    /** 清空全部标点（确认后执行） */
    private void clearAllPoints() {
        int count = pointManager.size();
        pointManager.clear();
        notify("§c§l✗ 已清空全部标点 §8▸ 共 " + count + " 个");
    }

    /** 清除当前维度处理记录（确认后执行），ESP 恢复绿色 */
    private void clearCurrentDimensionRecords() {
        String dim = WorldIdentity.dimension(mc);
        int removed = recordManager.clearDimension(dim);
        notify("§c§l✗ 已清除当前维度处理记录 §8▸ 共 " + removed + " 条");
    }

    /** 清除全部服务器处理记录（确认后执行） */
    private void clearAllRecords() {
        int files = recordManager.clearAllServers();
        notify("§c§l✗ 已清除全部处理记录 §8▸ 共 " + files + " 个文件");
    }

    private String[][] buildSections() {
        return new String[][]{
            {"§l自动箱子 · 使用说明"},
            {"§e§l▌ 使用方法", "§f  1. 先用「ID识别」添加目标物品ID", "§f  2. 在设置页选择运行模式（玩家控制/寻路/标点）", "§f  · 开启后自动处理容器，取走目标物品"},
            {"§a§l▌ 三种模式", "§f  · 玩家控制模式：玩家自己走，进入触发距离自动处理", "§f  · 寻路模式：自动扫描并寻路到容器面前安全站位", "§f  · 标点模式：只处理 .autochest 保存的点位"},
            {"§d§l▌ 保护机制", "§f  · 目标锁：同一容器同时只处理一次", "§f  · 多人保护：他人正在用箱不抢，临时跳过", "§f  · 有限重试 + 临时冷却：失败不无限卡箱"},
            {"§c§l▌ 注意", "§f  · 目标物品来自 ID 配置管理，本模块不建独立物品库", "§f  · 后台挂机不抢鼠标/焦点，走客户端内部 API"}
        };
    }
}
