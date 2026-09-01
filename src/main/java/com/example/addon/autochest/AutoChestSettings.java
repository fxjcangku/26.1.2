package com.example.addon.autochest;

import com.example.addon.autochest.model.ScanMode;
import com.example.addon.autochest.model.WithdrawMode;
import com.example.addon.itemid.ItemIdManager;
import com.example.addon.itemid.ItemTargetSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * AutoChest 设置：承载自动箱子的全部可配置项。
 *
 * <p>按「运行模式」动态呈现：玩家控制模式 / 寻路模式 / 标点模式三组配置互斥，
 * 只显示当前模式相关项；容器类型 / 检测范围 / 目标物品 / 取物模式为通用配置。</p>
 *
 * <p>目标物品不建第二套数据：选择器（{@link ItemTargetSetting}）与数量配置
 * 都只存身份键，运行时回查 {@link ItemIdManager}。</p>
 */
public final class AutoChestSettings {

    // ── 分组 ──
    private final SettingGroup grpMode;
    private final SettingGroup grpPlayer;
    private final SettingGroup grpPathing;
    private final SettingGroup grpMarker;
    private final SettingGroup grpContainer;
    private final SettingGroup grpProtect;
    private final SettingGroup grpTarget;
    private final SettingGroup grpWithdraw;
    private final SettingGroup grpRender;

    // ── 运行模式 ──
    public final Setting<ScanMode> scanMode;
    public final InfoTextSetting currentMode;

    // ── 玩家控制模式 ──
    public final Setting<Integer> triggerDistance;

    // ── 寻路模式 ──
    public final Setting<Integer> arriveDistance;

    // ── 标点模式 ──
    public final InfoTextSetting markerHint;

    // ── 容器 ──
    public final ContainerTypeSetting containerTypes;
    public final Setting<Integer> scanRadius;
    public final Setting<Integer> scanInterval;

    // ── 保护（多人保护 / 有限重试 / 临时冷却） ──
    public final Setting<Boolean> multiplayerProtect;
    public final Setting<Integer> playerDetectDistance;
    public final Setting<Integer> maxRetries;
    public final Setting<Integer> cooldownTicks;

    // ── 目标物品（数据源唯一来自 ItemIdManager） ──
    public final ItemTargetSetting targetItems;

    // ── 取物 ──
    public final Setting<WithdrawMode> withdrawMode;
    public final ItemQuantitySetting itemQuantities;
    public final Setting<Integer> actionDelay;

    // ── 已处理记录过期时长（分钟） ──
    public final Setting<Integer> recordExpireMinutes;

    // ── 渲染 ──
    public final Setting<Boolean> renderEsp;
    public final Setting<SettingColor> unprocessedColor;
    public final Setting<SettingColor> processedColor;
    public final Setting<SettingColor> processingColor;

    public AutoChestSettings(Settings settings, ItemIdManager idManager) {
        grpMode      = settings.createGroup("运行模式");
        grpPlayer    = settings.createGroup("玩家控制模式");
        grpPathing   = settings.createGroup("寻路模式");
        grpMarker    = settings.createGroup("标点模式");
        grpContainer = settings.createGroup("容器");
        grpProtect   = settings.createGroup("保护");
        grpTarget    = settings.createGroup("目标物品");
        grpWithdraw  = settings.createGroup("取物");
        grpRender    = settings.createGroup("渲染");

        // ── 运行模式 ─────────────────────────────────────────
        scanMode = grpMode.add(new EnumSetting.Builder<ScanMode>()
            .name("运行模式")
            .description("玩家控制模式：玩家自己走位，进入触发距离自动处理；寻路模式：自动扫描并寻路；标点模式：只处理保存点位。")
            .defaultValue(ScanMode.PLAYER_CONTROL)
            .build());

        // 当前模式实时显示（随上方下拉切换自动刷新）
        currentMode = grpMode.add(new InfoTextSetting(
            "当前模式",
            "当前选中的运行模式（实时显示）。",
            () -> "§a§l" + scanMode.get().toString()));

        // ── 玩家控制模式 ─────────────────────────────────────
        triggerDistance = grpPlayer.add(new IntSetting.Builder()
            .name("触发距离")
            .description("玩家控制模式下，距离容器多少格内自动处理。")
            .defaultValue(4)
            .min(1)
            .noSlider()
            .visible(() -> scanMode.get() == ScanMode.PLAYER_CONTROL)
            .build());

        // ── 寻路模式 ─────────────────────────────────────────
        arriveDistance = grpPathing.add(new IntSetting.Builder()
            .name("到达判定距离")
            .description("寻路模式下，走到距容器多少格内即视为到达并开箱。")
            .defaultValue(2)
            .min(1)
            .noSlider()
            .visible(() -> scanMode.get() == ScanMode.PATHING)
            .build());

        // ── 标点模式 ─────────────────────────────────────────
        markerHint = grpMarker.add(new InfoTextSetting(
            "标点管理",
            "标点通过说明面板按钮或指令管理，模块只处理已保存的点位。",
            () -> "§7用面板按钮管理点位，或指令 §e.autochest add/remove/clear/status",
            () -> scanMode.get() == ScanMode.MARKER));

        // ── 容器 ─────────────────────────────────────────────
        containerTypes = grpContainer.add(new ContainerTypeSetting(
            "容器类型",
            "选择要识别的合法容器类型（箱子/陷阱箱/16色潜影盒/木桶/铜箱）。"));

        scanRadius = grpContainer.add(new IntSetting.Builder()
            .name("检测范围")
            .description("扫描附近合法容器的半径（格），负责发现容器。")
            .defaultValue(16)
            .min(4)
            .noSlider()
            .build());

        scanInterval = grpContainer.add(new IntSetting.Builder()
            .name("扫描周期")
            .description("每多少 Tick 推进一轮扫描（分帧扫描，不整世界全扫）。")
            .defaultValue(20)
            .min(1)
            .noSlider()
            .build());

        recordExpireMinutes = grpContainer.add(new IntSetting.Builder()
            .name("已处理记录过期")
            .description("已处理容器记录多少分钟后失效，可被再次处理。")
            .defaultValue(30)
            .min(0)
            .noSlider()
            .build());

        // ── 保护（多人保护 / 有限重试 / 临时冷却） ─────────────
        multiplayerProtect = grpProtect.add(new BoolSetting.Builder()
            .name("多人保护")
            .description("检测到其他玩家正在使用目标容器时，不抢箱，暂时跳过并进入冷却。")
            .defaultValue(true)
            .build());

        playerDetectDistance = grpProtect.add(new IntSetting.Builder()
            .name("玩家检测距离")
            .description("其他玩家距离容器多少格内视为正在使用，触发多人保护。")
            .defaultValue(3)
            .min(1)
            .noSlider()
            .visible(multiplayerProtect::get)
            .build());

        maxRetries = grpProtect.add(new IntSetting.Builder()
            .name("最大重试次数")
            .description("开箱/寻路/交互失败后最多重试几次，超过则本轮跳过该容器。")
            .defaultValue(3)
            .min(1)
            .noSlider()
            .build());

        cooldownTicks = grpProtect.add(new IntSetting.Builder()
            .name("临时冷却")
            .description("连续失败或多人保护后，容器进入暂时不可用的冷却时长（Tick）。")
            .defaultValue(100)
            .min(20)
            .noSlider()
            .build());

        // ── 取物模式（先于目标物品，供其可见性引用） ─────────
        withdrawMode = grpWithdraw.add(new EnumSetting.Builder<WithdrawMode>()
            .name("取物模式")
            .description("按目标数量取：每种目标物品单独配置数量；目标物品拿空：只拿空目标列表物品；全部拿空：忽略目标列表取走所有合法物品。")
            .defaultValue(WithdrawMode.TARGET_COUNT)
            .build());

        // ── 目标物品 ─────────────────────────────────────────
        // 全部拿空模式不需要目标列表，此时隐藏目标物品选择器
        targetItems = grpTarget.add(new ItemTargetSetting(
            "目标物品",
            "选择 AutoChest 要取的目标物品（数据源唯一来自 ID 配置管理）。",
            idManager,
            () -> withdrawMode.get() != WithdrawMode.TAKE_ALL));

        // ── 每种目标物品数量（按目标数量取专属） ─────────────
        itemQuantities = grpWithdraw.add(new ItemQuantitySetting(
            "每种物品数量",
            "按目标数量取模式下，为每种目标物品单独配置目标数量。",
            targetItems,
            () -> withdrawMode.get() == WithdrawMode.TARGET_COUNT));

        actionDelay = grpWithdraw.add(new IntSetting.Builder()
            .name("动作延迟")
            .description("两次槽位操作之间的 Tick 间隔。")
            .defaultValue(2)
            .min(1)
            .noSlider()
            .build());

        // ── 渲染 ─────────────────────────────────────────────
        renderEsp = grpRender.add(new BoolSetting.Builder()
            .name("ESP高亮")
            .description("高亮显示已发现但未处理的容器。")
            .defaultValue(true)
            .build());

        unprocessedColor = grpRender.add(new ColorSetting.Builder()
            .name("未处理颜色")
            .description("未处理容器的 ESP 颜色。")
            .defaultValue(new SettingColor(0, 255, 0, 80))
            .visible(renderEsp::get)
            .build());

        processedColor = grpRender.add(new ColorSetting.Builder()
            .name("已处理颜色")
            .description("已处理容器的 ESP 颜色。")
            .defaultValue(new SettingColor(255, 0, 0, 80))
            .visible(renderEsp::get)
            .build());

        processingColor = grpRender.add(new ColorSetting.Builder()
            .name("处理中颜色")
            .description("正在处理容器的 ESP 颜色（处理中不显示为已处理红色）。")
            .defaultValue(new SettingColor(255, 200, 0, 80))
            .visible(renderEsp::get)
            .build());
    }
}
