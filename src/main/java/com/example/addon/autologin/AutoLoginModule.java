package com.example.addon.autologin;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mixin.KeyboardInvoker;
import com.example.addon.ui.HelpScreen;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.glfw.GLFW;

/**
 * 自动登入 - 服务器进服助手。
 *
 * 状态机流程：
 *   IDLE → (GameJoinedEvent) → WORLD_LOAD_WAIT
 *        → (倒计时) → DETECTING_AUTH
 *        → (聊天注册提示) → REGISTERING → DETECTING_AUTH
 *        → (聊天登录提示) → LOGGING_IN → EXECUTING_COMMANDS / ACTIVE
 *        → (超时未收到任何提示，判定无需登录) → EXECUTING_COMMANDS / ACTIVE
 *
 * 是否执行登录/注册完全由服务器聊天提示决定，与账号是否正版无关
 * （正版账号同样可能进入离线模式服务器并被要求 /login）。
 *
 * 断线：任意状态 → (GameLeftEvent/DisconnectedScreen) → RECONNECT_WAIT → IDLE
 */
public class AutoLoginModule extends YiyiaddonModule {

    private static final int RECONNECT_STABLE_TICKS = 200;
    private static final int SUBSERVER_SCAN_DELAY_TICKS = 30;
    private static final Pattern SUBSERVER_NAME_PATTERN = Pattern.compile("(?:当前)?(?:服务器|子服|分区|线路|频道|区域)\\s*[:：|]?\\s*([\\p{IsHan}A-Za-z0-9_-]{2,24})|([\\p{IsHan}A-Za-z]{1,12}[一二三四五六七八九十百0-9]+区)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINECRAFT_FORMATTING_PATTERN = Pattern.compile("(?i)(?:§|&)(?:[0-9A-FK-ORX]|#[0-9A-F]{6})");
    private static final Pattern INVISIBLE_CHARACTER_PATTERN = Pattern.compile("[\\u00AD\\u034F\\u061C\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]");
    private static final int[] LEYUAN_DIRECT_MAIN_CITY_BOOK_SLOTS = {0, 8, 9, 17, 18, 26};

    private final AutoLoginSettings cfg;

    // 子处理器
    private RegisterHandler     registerHandler;
    private LoginHandler        loginHandler;
    private CommandHandler      commandHandler;
    private ReconnectHandler    reconnectHandler;

    // 状态机
    private AutoLoginState state = AutoLoginState.IDLE;

    // 世界加载等待计时
    private int worldLoadTicks;

    // 登录/注册提示监听计时：超时则判定该服务器无需登录
    private int authDetectTicks;

    // 防重复标志
    private boolean registerSent;
    private boolean loginSent;
    private boolean commandSent;
    private boolean accountChecked;
    private boolean handlingMessage;
    private Object sessionNetworkHandler;
    private String physicalServerAddress;
    private boolean authFlowCompleted;
    private boolean allowActiveAuthPrompt;
    private boolean lobbyConnectionAnnounced;
    private Object announcedSubserverHandler;
    private boolean detectSubserverPreviously;
    private int pendingSubserverScanTicks;
    private boolean subserverWorkflowRunning;
    private int subserverMenuStep;
    private int subserverActionTicks;
    private int subserverTimeoutTicks;
    private LeyuanRouteState leyuanRouteState = LeyuanRouteState.IDLE;
    private int leyuanStateTicks;
    private int leyuanActionTicks;
    private int leyuanLastScreenFingerprint;
    private int leyuanScanIndex;
    private int leyuanScanClickTicks;
    private int leyuanPendingMenuSlot = -1;
    private int leyuanMenuUseCooldown;
    private boolean leyuanCityMenuFallbackUsed;
    private int leyuanCityMenuFallbackReleaseTicks;
    private int leyuanCityMenuFallbackPhase;
    private int leyuanCityMenuFallbackAttempts;
    private int leyuanCityMenuFallbackRetryTick;
    private float leyuanScanBaseYaw;
    private float leyuanScanBasePitch;
    private Object leyuanStageWorld;
    private String leyuanStageDimension = "";
    private Vec3 leyuanStagePosition;
    private boolean leyuanAfkAreaHandled;
    private int leyuanAfkAreaWaitTicks;
    private boolean subserverCommandPending;
    private int subserverCommandTicks;
    private boolean waitingForTargetArea;
    private boolean targetTransitionObserved;
    private boolean subserverTransitionExpected;
    private Object routeStartWorld;
    private String routeStartDimension;
    private int reconnectStableTicks;
    private boolean waitingForReconnectStability;
    private boolean leyuanRecoveryActive;
    private int leyuanRecoveryElapsedTicks;
    private LeyuanRouteState leyuanRecoveryResumeState = LeyuanRouteState.IDLE;

    public AutoLoginModule() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动登入", "自动注册、登录、断线重连、进服后执行指令序列。详细参考下面使用说明。");
        chatFeedback = false;
        runInMainMenu = true;

        cfg = new AutoLoginSettings(settings, mc);

        registerHandler     = new RegisterHandler(mc, cfg, this::onRegisterComplete);
        loginHandler        = new LoginHandler(mc, cfg, this::onLoginComplete);
        commandHandler      = new CommandHandler(mc, cfg, this::onCommandComplete);
        reconnectHandler    = new ReconnectHandler(mc, cfg, this::onReconnectStart);
    }

    @Override
    protected void notify(String message) {
        super.notify(message != null && message.startsWith("自动登入：") ? message.substring(5) : message);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Module 生命周期
    // ══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        // 重连等待时显示倒计时控件
        if (state == AutoLoginState.RECONNECT_WAIT) {
            WTable table = theme.table();
            if (reconnectHandler.isScheduled()) {
                int secs = reconnectHandler.getTicksLeft() / 20;
                table.add(theme.label("§e重连倒计时：§f" + secs + " 秒")).expandX();
                table.row();
            } else {
                table.add(theme.label("§a正在连接...")).expandX();
                table.row();
            }
            WButton btnNow = table.add(theme.button("立即重连")).expandX().widget();
            btnNow.action = () -> {
                if (reconnectHandler.isScheduled()) reconnectHandler.reconnectNow();
            };
            table.row();
            WButton btnStop = table.add(theme.button("§c停止重连")).expandX().widget();
            btnStop.action = () -> {
                reconnectHandler.reset();
                transitionTo(AutoLoginState.IDLE);
                notify("§c已停止自动重连。");
            };
            return table;
        }

        return buildInfoWidget(theme,
            table -> {
                // 使用说明按钮（置顶显眼位置）
                WButton helpBtn = theme.button("§e查看使用说明");
                helpBtn.action = () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent()));
                table.add(helpBtn).expandX().minWidth(200);
                table.row();
            },
            new String[]{ "§l自动登入 · 配置说明" },
            new String[]{
                "§e§l▌ 快速上手",
                "§f  1. 按服务器需求开启自动登录或自动注册，并填写对应密码",
                "§f  2. 需要进入目标区域时，开启「自动进入目标区域」并选择进入方式",
                "§f  3. 点击上方「查看使用说明」查看完整流程、路线与注意事项"
            }
        );
    }

    /**
     * 构建使用说明内容（HelpScreen 独立窗口风格，参考 AutoMinerModule）
     */
    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("快速上手",
                "  §8├─ §f按服务器需求开启自动登录或自动注册，并填写对应密码",
                "  §8├─ §f需要进入目标区域时，开启「自动进入目标区域」并选择进入方式",
                "  §8└─ §f开启模块后按配置完成认证、回服、重连和到达确认"
            ),
            new HelpScreen.HelpSection("自动流程",
                "  §a[1] §f等待世界加载并监听登录、注册或免登录提示",
                "  §a[2] §f根据认证结果发送一次登录或注册指令",
                "  §a[3] §f认证完成后执行进服指令或目标区域路线",
                "  §a[4] §f断线后按自动重连设置恢复上一次服务器连接",
                "  §a[5] §f到达目标区域并稳定等待后，可执行一次目标区域指令"
            ),
            new HelpScreen.HelpSection("乐源服定制路线",
                "  §b▸ §f选择「乐源服定制」后，「启用乐源服功能」控制首次进服路线",
                "  §b▸ §f「挂机区自动回服」是独立开关，可单独检测挂机区并启动回服",
                "  §b▸ §f回服顺序固定：返回主城大区 §8→ §7世界传送 §8→ §7资源大区 §8→ §7资源二区",
                "  §b▸ §f默认用欢迎页「书本直达主城」入口，也可切换为扫描乐源城入口",
                "  §b▸ §f主城菜单优先用快捷栏物品，失败时可用 Shift＋F 兜底"
            ),
            new HelpScreen.HelpSection("路线与指令",
                "  §8├─ §f通用子服路线仅用于直接进入以外的前三种普通模式",
                "  §8├─ §f开启「检测服务器子服」后，子服连接沿用大厅认证状态",
                "  §8├─ §f乐源路线通过侧边栏关键词确认主城、挂机区和最终目标",
                "  §8├─ §f挂机区菜单延迟只影响首次菜单打开，不影响登录和其他路线",
                "  §8└─ §f每步等待和单步超时均按 §e20 tick = 1 秒 §f计算"
            ),
            new HelpScreen.HelpSection("状态说明",
                "  §a就绪     §8— §f已完成登录，模块正常运行",
                "  §e重连中   §8— §f断线后等待重连倒计时",
                "  §7无状态   §8— §f未进服或正在初始化"
            ),
            new HelpScreen.HelpSection("注意事项",
                "  §c⚠ §f自动登录与自动注册同时开启：老账号关「自动注册」，新账号关「自动登录」",
                "  §c⚠ §f乐源定制路线仅适用于该服务器，请勿用于其他服务器",
                "  §c⚠ §f密码明文存储在配置文件中，请勿在公共电脑使用"
            )
        );
    }

    @Override
    public String getInfoString() {
        if (state == AutoLoginState.RECONNECT_WAIT) {
            int ticks = reconnectHandler.getTicksLeft();
            if (ticks >= 0) return "§e" + (ticks / 20) + "s";
            return "§a连接中";
        }
        return state == AutoLoginState.ACTIVE ? "§a就绪" : null;
    }

    @Override
    public void onActivate() {
        if (closeForUnsupportedEnvironment()) return;
        resetAllHandlers();
        sessionNetworkHandler = null;
        physicalServerAddress = null;
        authFlowCompleted = false;
        allowActiveAuthPrompt = false;
        lobbyConnectionAnnounced = false;
        announcedSubserverHandler = null;
        detectSubserverPreviously = cfg.detectSubserver.get();
        pendingSubserverScanTicks = 0;
        resetSubserverWorkflow();
        state = AutoLoginState.IDLE;
        notify("§a§l已启动 §8│ §f§l等待进入服务器...");
        debugMsg("模块已启动，等待进入服务器...");

        // 新老玩家冲突检测：自动登录和自动注册同时开启时提醒
        if (cfg.autoLogin.get() && cfg.autoRegister.get()) {
            notify("§e注意：自动登录和自动注册同时开启。新用户请关闭自动登录；老玩家请关闭自动注册。");
        }

        // 模块激活时若已在服务器中，记录服务器信息并进入就绪状态
        if (mc.player != null && mc.level != null && !mc.hasSingleplayerServer()) {
            sessionNetworkHandler = mc.getConnection();
            physicalServerAddress = getCurrentServerAddress();
            recordCurrentServer();
            resetSessionFlags();
            beginPostLoadFlow();
        }
    }

    @Override
    public void onDeactivate() {
        resetAllHandlers();
        sessionNetworkHandler = null;
        physicalServerAddress = null;
        authFlowCompleted = false;
        allowActiveAuthPrompt = false;
        lobbyConnectionAnnounced = false;
        announcedSubserverHandler = null;
        detectSubserverPreviously = cfg.detectSubserver.get();
        pendingSubserverScanTicks = 0;
        resetSubserverWorkflow();
        state = AutoLoginState.IDLE;
    }

    // ══════════════════════════════════════════════════════════════════
    //  事件处理
    // ══════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (closeForUnsupportedEnvironment()) return;
        // 重连倒计时必须在 player==null 检查之前驱动，断线后才能正常触发
        reconnectHandler.tick();

        if (leyuanRecoveryActive && ++leyuanRecoveryElapsedTicks >= leyuanRecoveryWaitSeconds() * 20) {
            leyuanRecoveryActive = false;
            reconnectHandler.reset();
            leyuanRouteState = LeyuanRouteState.FAILED;
            transitionTo(AutoLoginState.IDLE);
            notify("乐源路线异常恢复等待已超过设置上限，当前步骤失败。");
            debugMsg("乐源路线异常恢复超时：步骤=" + leyuanRouteState);
            return;
        }

        if (mc.player == null || mc.level == null) return;

        boolean detectSubserver = cfg.detectSubserver.get();
        if (detectSubserver && !detectSubserverPreviously && !mc.hasSingleplayerServer() && mc.getConnection() != null) {
            sessionNetworkHandler = mc.getConnection();
            physicalServerAddress = getCurrentServerAddress();
            if (authFlowCompleted) {
                allowActiveAuthPrompt = false;
                transitionTo(AutoLoginState.ACTIVE);
                notify("已将当前服务器设为登录大厅，后续子服将沿用当前登录状态。");
                startSubserverWorkflow();
            } else {
                notify("已开启服务器子服检测，当前登录服将先重新检测登录状态。");
                beginPostLoadFlow();
            }
        }
        detectSubserverPreviously = detectSubserver;

        if (!cfg.usesStandardMenuRoute()) stopStandardMenuRoute();
        if (!canRunLeyuanRoute()) stopLeyuanRoute();

        tickSubserverWorkflow();

        tickLeyuanAfkRecoveryTrigger();
        tickLeyuanRoute();

        tickTargetAreaDetection();

        if (subserverCommandPending && ++subserverCommandTicks >= cfg.subserverCommandDelay.get()) {
            subserverCommandPending = false;
            String command = cfg.subserverCommandText.get().trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isEmpty() && mc.getConnection() != null) {
                notify("已回到目标子服，正在执行 " + highlightCommand("/" + command) + "。");
                mc.getConnection().sendCommand(command);
                notify("回服指令已执行 " + highlightCommand("/" + command) + "。");
            }
        }

        if (pendingSubserverScanTicks > 0 && --pendingSubserverScanTicks == 0) {
            if (cfg.serverEntryMode.get() == ServerEntryMode.LEYUAN_CUSTOM
                && cfg.leyuanEnabled.get()
                && findSidebarKeyword(cfg.leyuanMainCityKeywords.get()) != null) {
                notify("已识别当前位置为" + highlightLocation("主城大区") + "，继续执行乐源服定制路线。");
                return;
            }
            String subserverName = detectSubserverName();
            if (subserverName != null) {
                notify(highlightText("已进入子服") + "：" + highlightText(subserverName) + "，沿用§b§l大厅登录状态§r§f§l。");
            }
        }

        if (!mc.hasSingleplayerServer() && cfg.autoCheckAccount.get() && !accountChecked) {
            accountChecked = true;
            notify(AccountChecker.describe(mc));
            debugMsg(AccountChecker.describeDebug(mc));
        }

        if (waitingForReconnectStability) {
            reconnectStableTicks++;
            if (reconnectStableTicks >= RECONNECT_STABLE_TICKS) {
                reconnectHandler.markConnectionStable();
                reconnectStableTicks = 0;
                waitingForReconnectStability = false;
            }
        }

        switch (state) {

            case WORLD_LOAD_WAIT -> {
                worldLoadTicks++;
                if (worldLoadTicks >= cfg.worldLoadWait.get()) {
                    if (cfg.noLoginDetection.get() && !cfg.autoLogin.get()) {
                        cfg.autoLogin.set(true);
                        notify("未检测到服务器免登录消息，已开启自动登录检测。");
                    }
                    transitionTo(AutoLoginState.DETECTING_AUTH);
                }
            }

            case DETECTING_AUTH -> {
                // 监听期内始终等待聊天提示；超时则判定该服务器无需登录
                authDetectTicks++;
                if (authDetectTicks == 1) {
                    notify("开始监听认证提示... (超时: " + cfg.authDetectTimeout.get() + " tick)");
                }
                if (authDetectTicks >= cfg.authDetectTimeout.get()) {
                    notify("未检测到认证提示，跳过登录流程。");
                    proceedAfterAuth();
                }
            }

            case REGISTERING -> {
                registerHandler.tick();
            }

            case LOGGING_IN -> {
                loginHandler.tick();
            }

            case EXECUTING_COMMANDS -> {
                commandHandler.tick();
            }

            // RECONNECT_WAIT：后台线程处理，不需要 tick
            default -> { /* IDLE / ACTIVE / RECONNECT_WAIT：不需要 tick 操作 */ }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;
        if (closeForUnsupportedEnvironment()) return;

        Object networkHandler = mc.getConnection();
        if (isLeyuanRouteRunning()) {
            sessionNetworkHandler = networkHandler;
            leyuanRecoveryActive = false;
            leyuanRecoveryElapsedTicks = 0;
            if (leyuanRouteState == LeyuanRouteState.CLICK_LOGIN_SURVIVAL) {
                setLeyuanState(LeyuanRouteState.OPEN_LOGIN_MENU, cfg.leyuanStepDelay.get());
            } else if (leyuanRouteState == LeyuanRouteState.CLICK_RETURN_MAIN_CITY_HALL) {
                setLeyuanState(LeyuanRouteState.OPEN_AFK_MENU, cfg.leyuanStepDelay.get());
            } else if (leyuanRouteState == LeyuanRouteState.CLICK_WORLD_TRANSFER
                || leyuanRouteState == LeyuanRouteState.CLICK_SURVIVAL_FIRST
                || leyuanRouteState == LeyuanRouteState.CLICK_SURVIVAL_SECOND
                || leyuanRouteState == LeyuanRouteState.CLICK_TARGET_SERVER) {
                setLeyuanState(leyuanAfkAreaHandled
                    ? LeyuanRouteState.OPEN_MAIN_CITY_HALL_MENU
                    : LeyuanRouteState.OPEN_CITY_MENU, cfg.leyuanStepDelay.get());
            }
            leyuanLastScreenFingerprint = 0;
            leyuanMenuUseCooldown = 0;
            debugMsg("乐源路线已恢复当前步骤：" + leyuanRouteState);
            markLeyuanTransition();
            return;
        }
        if (networkHandler != null && networkHandler == sessionNetworkHandler) {
            if (subserverTransitionExpected && authFlowCompleted && cfg.detectSubserver.get()) {
                pendingSubserverScanTicks = SUBSERVER_SCAN_DELAY_TICKS;
                subserverTransitionExpected = false;
            } else if (waitingForTargetArea && cfg.serverEntryMode.get() == ServerEntryMode.SUBSERVER_NETWORK) {
                targetTransitionObserved = true;
            }
            return;
        }
        String currentAddress = getCurrentServerAddress();
        if (cfg.detectSubserver.get() && authFlowCompleted && subserverTransitionExpected) {
            sessionNetworkHandler = networkHandler;
            allowActiveAuthPrompt = false;
            if (state != AutoLoginState.ACTIVE && state != AutoLoginState.EXECUTING_COMMANDS) {
                transitionTo(AutoLoginState.ACTIVE);
            }
            if (networkHandler != announcedSubserverHandler) {
                announcedSubserverHandler = networkHandler;
                pendingSubserverScanTicks = SUBSERVER_SCAN_DELAY_TICKS;
                subserverWorkflowRunning = false;
                targetTransitionObserved = true;
            }
            subserverTransitionExpected = false;
            return;
        }
        if (cfg.detectSubserver.get() && physicalServerAddress != null && physicalServerAddress.equals(currentAddress)) {
            sessionNetworkHandler = networkHandler;
            if (state == AutoLoginState.IDLE || state == AutoLoginState.RECONNECT_WAIT) beginPostLoadFlow();
            return;
        }
        sessionNetworkHandler = networkHandler;
        physicalServerAddress = currentAddress;
        authFlowCompleted = false;
        subserverTransitionExpected = false;
        if (!lobbyConnectionAnnounced) {
            lobbyConnectionAnnounced = true;
            notify("你已进入登录大厅，正在检测登录状态。");
        }

        // 记录服务器连接信息（地址 + ServerData），供断线重连使用
        recordCurrentServer();

        resetSessionFlags();
        if (reconnectHandler.getReconnectAttempts() > 0) {
            waitingForReconnectStability = true;
        }

        beginPostLoadFlow();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;
    }

    /** 捕获断线画面（GameLeftEvent 有时不触发，这里作为补充） */
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!isActive()) return;
        if (event.screen instanceof DisconnectedScreen) {
            handleDisconnect();
            return;
        }
        // 用户手动点击「返回服务器列表」或回到主菜单时，取消待执行的重连
        if (state == AutoLoginState.RECONNECT_WAIT
                && (event.screen instanceof JoinMultiplayerScreen || event.screen instanceof TitleScreen)) {
            reconnectHandler.reset();
            transitionTo(AutoLoginState.IDLE);
            notify("§e已检测到手动返回主菜单，自动重连已取消。");
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive() || handlingMessage) return;
        handlingMessage = true;
        try {
            String raw = event.getMessage().getString();
            if (ChatAnalyzer.stripColors(raw).contains("[Jeraddon]")) return;

            if (cfg.noLoginDetection.get() && ChatAnalyzer.isLoginSuccess(raw)) {
                cfg.autoLogin.set(false);
                authFlowCompleted = true;
                allowActiveAuthPrompt = false;
                notify("检测到服务器免登录成功消息，当前仍在两小时免登录期，已关闭自动登录。");
                transitionTo(AutoLoginState.ACTIVE);
                startSubserverWorkflow();
                return;
            }

            if (state != AutoLoginState.DETECTING_AUTH
                    && state != AutoLoginState.WORLD_LOAD_WAIT
                    && (state != AutoLoginState.ACTIVE || !allowActiveAuthPrompt)) return;

            AuthRule rule = ChatAnalyzer.analyze(raw);
            if (rule == null) return;
            if (cfg.noLoginDetection.get() && rule.type == AuthRule.Type.LOGIN && !cfg.autoLogin.get()) {
                cfg.autoLogin.set(true);
                notify("检测到服务器登录提示，免登录已失效，已开启自动登录。");
            }
            if (!cfg.autoLogin.get() && !cfg.autoRegister.get()) return;

            if (state == AutoLoginState.WORLD_LOAD_WAIT) {
                debugMsg("加载等待期间收到认证提示，立即跳过等待。");
                transitionTo(AutoLoginState.DETECTING_AUTH);
            }

            debugMsg("识别到" + (rule.type == AuthRule.Type.REGISTER ? "注册" : "登录") + "提示，规则：" + rule.template);

            if (rule.type == AuthRule.Type.REGISTER && cfg.autoRegister.get() && !registerSent) {
                String password = cfg.registerPassword.get();
                if (password.isEmpty()) {
                    notify("注册密码未设置，跳过自动注册");
                    return;
                }
                registerSent = true;
                notify("正在自动注册...");
                transitionTo(AutoLoginState.REGISTERING);
                registerHandler.start(rule);
            } else if (rule.type == AuthRule.Type.LOGIN && cfg.autoLogin.get() && !loginSent) {
                String password = cfg.loginPassword.get();
                if (password.isEmpty()) {
                    notify("登录密码未设置，跳过自动登录");
                    return;
                }
                loginSent = true;
                allowActiveAuthPrompt = false;
                notify("正在自动登录...");
                transitionTo(AutoLoginState.LOGGING_IN);
                loginHandler.start(rule);
            }
        } finally {
            handlingMessage = false;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Handler 回调
    // ══════════════════════════════════════════════════════════════════

    private void onRegisterComplete() {
        // 注册完成，继续监听登录提示
        transitionTo(AutoLoginState.DETECTING_AUTH);
    }

    private void onLoginComplete() {
        proceedAfterAuth();
    }

    /**
     * 认证阶段收尾：无论是登录成功，还是判定该服务器无需登录，都走这里。
     * 保证自动执行指令在两种情况下都能触发。
     */
    private void proceedAfterAuth() {
        authFlowCompleted = true;
        allowActiveAuthPrompt = cfg.noLoginDetection.get();
        if (cfg.autoEnterSubserver.get() && cfg.serverEntryMode.get() == ServerEntryMode.DIRECT) {
            confirmTargetArea("服务器已就绪");
        } else {
            startSubserverWorkflow();
        }
        if (!cfg.autoEnterSubserver.get() && cfg.autoCommand.get() && !commandSent) {
            commandSent = true;
            String cmd = cfg.autoCommandText.get();
            notify("执行: " + cmd);
            transitionTo(AutoLoginState.EXECUTING_COMMANDS);
            commandHandler.start();
        } else {
            transitionTo(AutoLoginState.ACTIVE);
        }
    }

    private void onCommandComplete() {
        transitionTo(AutoLoginState.ACTIVE);
    }

    private void onReconnectStart() {
        notify("正在重新连接... (第 " + reconnectHandler.getReconnectAttempts() + " 次)");
    }

    // ══════════════════════════════════════════════════════════════════
    //  内部工具
    // ══════════════════════════════════════════════════════════════════

    private void handleDisconnect() {
        if (state == AutoLoginState.RECONNECT_WAIT && reconnectHandler.isScheduled()) return;

        boolean recoveringLeyuan = isLeyuanRouteRunning();
        if (recoveringLeyuan) {
            leyuanRecoveryResumeState = leyuanRouteState;
            if (!leyuanRecoveryActive) leyuanRecoveryElapsedTicks = 0;
        }
        waitingForReconnectStability = false;
        reconnectStableTicks = 0;
        resetSessionFlags();
        sessionNetworkHandler = null;
        physicalServerAddress = null;
        authFlowCompleted = false;
        allowActiveAuthPrompt = true;
        announcedSubserverHandler = null;
        pendingSubserverScanTicks = 0;
        if (recoveringLeyuan) {
            stopStandardMenuRoute();
        } else {
            resetSubserverWorkflow();
        }

        // 检测流星自带 AutoReconnect，若开启则强制关闭并提示
        AutoReconnect meteorReconnect = Modules.get().get(AutoReconnect.class);
        if (meteorReconnect != null && meteorReconnect.isActive()) {
            meteorReconnect.toggle();
            notify("§e已自动关闭流星自带【自动重连】，本模块将接管重连。");
        }

        if (cfg.autoReconnect.get()) {
            int delayTicks = cfg.reconnectDelay.get();
            int maxAttempts = cfg.maxReconnectAttempts.get();
            if (recoveringLeyuan) {
                leyuanRecoveryActive = true;
                leyuanRecoveryElapsedTicks = 0;
                delayTicks = leyuanRecoveryDelayTicks();
                maxAttempts = cfg.leyuanRecoveryMaxRetries.get();
                debugMsg("乐源路线进入异常恢复：步骤=" + leyuanRouteState + "，等待=" + (delayTicks / 20) + " 秒");
            }
            int delaySeconds = delayTicks / 20;
            if (reconnectHandler.startReconnect(delayTicks, maxAttempts)) {
                notify("服务器断开，" + delaySeconds + " 秒后重新连接。");
                transitionTo(AutoLoginState.RECONNECT_WAIT);
            } else {
                if (recoveringLeyuan) {
                    leyuanRecoveryActive = false;
                    leyuanRouteState = LeyuanRouteState.FAILED;
                    notify("乐源路线异常恢复已达到重试上限，当前步骤失败。");
                } else {
                    notify("已达到最大重连次数，自动重连停止。");
                }
                transitionTo(AutoLoginState.IDLE);
            }
        } else {
            transitionTo(AutoLoginState.IDLE);
        }
    }

    private void transitionTo(AutoLoginState next) {
        debugMsg("状态: " + state + " → " + next);
        // 每次进入监听阶段都重新计时（注册完成后回到监听也需要完整窗口）
        if (next == AutoLoginState.DETECTING_AUTH) authDetectTicks = 0;
        state = next;
    }

    private int leyuanRecoveryDelayTicks() {
        int attempt = Math.max(1, reconnectHandler.getReconnectAttempts());
        int base = cfg.leyuanRecoveryRetrySeconds.get() * 20;
        int maxWait = leyuanRecoveryWaitSeconds() * 20;
        return Math.min(maxWait, Math.max(base, base * attempt));
    }

    private int leyuanRecoveryWaitSeconds() {
        return cfg.leyuanRecoveryWaitMinutes.get() * 60;
    }

    private void beginPostLoadFlow() {
        if (cfg.noLoginDetection.get()) {
            cfg.autoLogin.set(false);
            notify("进服已开启服务器免登录检测，正在监听成功登录或登录提示...");
            transitionTo(AutoLoginState.WORLD_LOAD_WAIT);
            worldLoadTicks = 0;
        } else if (cfg.autoLogin.get() || cfg.autoRegister.get()) {
            transitionTo(AutoLoginState.WORLD_LOAD_WAIT);
            worldLoadTicks = 0;
        } else {
            proceedAfterAuth();
        }
    }

    private void resetSessionFlags() {
        registerSent    = false;
        loginSent       = false;
        commandSent     = false;
        accountChecked  = false;
        worldLoadTicks  = 0;
        authDetectTicks = 0;
        reconnectStableTicks = 0;
        waitingForReconnectStability = false;
    }

    private String detectSubserverName() {
        if (mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return null;

        String name = extractSubserverName(objective.getDisplayName().getString());
        if (name != null) return name;

        return scoreboard.listPlayerScores(objective).stream()
            .filter(entry -> !entry.isHidden())
            .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
            .limit(15)
            .map(entry -> PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), Component.literal(entry.owner())).getString())
            .map(this::extractSubserverName)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    private void startSubserverWorkflow() {
        if (!cfg.autoEnterSubserver.get() || cfg.serverEntryMode.get() == ServerEntryMode.DIRECT) return;
        if (cfg.serverEntryMode.get() == ServerEntryMode.LEYUAN_CUSTOM) {
            startLeyuanRoute();
            return;
        }
        if (cfg.menuClickSteps.get().isEmpty()) return;
        subserverWorkflowRunning = true;
        subserverMenuStep = -1;
        subserverActionTicks = cfg.menuActionDelay.get();
        subserverTimeoutTicks = 0;
        routeStartWorld = mc.level;
        routeStartDimension = mc.level == null ? "" : mc.level.dimension().identifier().toString();
        targetTransitionObserved = false;
        notify("大厅认证完成，准备自动打开子服菜单。");
    }

    private void tickSubserverWorkflow() {
        if (!subserverWorkflowRunning || !cfg.usesStandardMenuRoute()) return;
        if (++subserverTimeoutTicks >= cfg.menuTimeout.get()) {
            notify("等待子服菜单超时，已停止自动点击。");
            subserverWorkflowRunning = false;
            return;
        }
        if (subserverActionTicks > 0 && --subserverActionTicks > 0) return;

        if (subserverMenuStep < 0) {
            int slot = findMenuItemSlot();
            if (slot < 0 || mc.gameMode == null) return;
            mc.player.getInventory().setSelectedSlot(slot);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            subserverMenuStep = 0;
            subserverActionTicks = cfg.menuActionDelay.get();
            subserverTimeoutTicks = 0;
            notify("已使用菜单物品，等待服务器选择界面。");
            return;
        }

        if (subserverMenuStep >= cfg.menuClickSteps.get().size()) {
            subserverWorkflowRunning = false;
            waitingForTargetArea = true;
            subserverTimeoutTicks = 0;
            notify("菜单点击完成，正在确认目标区域。");
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || mc.gameMode == null) return;

        String keyword = cfg.menuClickSteps.get().get(subserverMenuStep).trim();
        if (keyword.isEmpty()) {
            subserverMenuStep++;
            return;
        }
        AbstractContainerMenu handler = screen.getMenu();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.container == mc.player.getInventory() || !matchesItemKeyword(slot.getItem(), keyword)) continue;
            boolean isFinalStep = subserverMenuStep + 1 >= cfg.menuClickSteps.get().size();
            mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            subserverMenuStep++;
            if (isFinalStep && cfg.serverEntryMode.get() == ServerEntryMode.SUBSERVER_NETWORK) {
                subserverTransitionExpected = true;
            }
            subserverActionTicks = cfg.menuActionDelay.get();
            subserverTimeoutTicks = 0;
            notify("已点击菜单步骤 " + subserverMenuStep + "：" + keyword + "。");
            return;
        }
    }

    private int findMenuItemSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(cfg.menuTool.get())) return i;
            for (String keyword : cfg.menuItemKeywords.get()) {
                if (matchesItemKeyword(stack, keyword)) return i;
            }
        }
        return -1;
    }

    private void startLeyuanRoute() {
        if (!cfg.leyuanEnabled.get()) {
            notify("已选择乐源服定制模式，但定制路线开关未开启。");
            return;
        }
        subserverWorkflowRunning = false;
        waitingForTargetArea = false;
        leyuanLastScreenFingerprint = 0;
        leyuanScanIndex = 0;
        leyuanScanClickTicks = 0;
        leyuanPendingMenuSlot = -1;
        leyuanMenuUseCooldown = 0;
        leyuanCityMenuFallbackUsed = false;
        leyuanCityMenuFallbackReleaseTicks = 0;
        leyuanCityMenuFallbackPhase = 0;
        leyuanCityMenuFallbackAttempts = 0;
        leyuanCityMenuFallbackRetryTick = 0;
        captureLeyuanStageOrigin();
        setLeyuanState(LeyuanRouteState.OPEN_LOGIN_MENU, cfg.leyuanStepDelay.get());
        notifyLeyuanProgress(1, "已启动，准备打开登录服菜单。");
    }

    private void tickLeyuanRoute() {
        if (!isLeyuanRouteRunning()) return;
        if (!canRunLeyuanRoute()) {
            stopLeyuanRoute();
            return;
        }
        if ((leyuanRouteState == LeyuanRouteState.OPEN_CITY_MENU
                || leyuanRouteState == LeyuanRouteState.OPEN_AFK_MENU
                || leyuanRouteState == LeyuanRouteState.OPEN_MAIN_CITY_HALL_MENU)
            && leyuanCityMenuFallbackAttempts >= 2
            && leyuanCityMenuFallbackPhase == 0 && leyuanStateTicks >= leyuanCityMenuFallbackRetryTick + 40) {
            leyuanRouteState = LeyuanRouteState.FAILED;
            notify("主城菜单连续两次没打开，已停止操作。请确认 Shift＋F 是否仍是服务器快捷键。");
            return;
        }
        if (++leyuanStateTicks >= cfg.leyuanStepTimeout.get()) {
            String stage = leyuanStateName();
            leyuanRouteState = LeyuanRouteState.FAILED;
            notify("等了太久还没完成“" + stage + "”，已停止操作，防止误点。");
            return;
        }
        if (mc.screen == null && leyuanStateTicks > cfg.leyuanStepDelay.get() + 20) {
            if (leyuanRouteState == LeyuanRouteState.CLICK_LOGIN_SURVIVAL) {
                setLeyuanState(LeyuanRouteState.OPEN_LOGIN_MENU, cfg.leyuanStepDelay.get());
                return;
            }
            if (leyuanRouteState == LeyuanRouteState.CLICK_RETURN_MAIN_CITY_HALL) {
                setLeyuanState(LeyuanRouteState.OPEN_AFK_MENU, cfg.leyuanStepDelay.get());
                return;
            }
            if (leyuanRouteState == LeyuanRouteState.CLICK_WORLD_TRANSFER
                || leyuanRouteState == LeyuanRouteState.CLICK_SURVIVAL_FIRST
                || leyuanRouteState == LeyuanRouteState.CLICK_SURVIVAL_SECOND
                || leyuanRouteState == LeyuanRouteState.CLICK_TARGET_SERVER) {
                setLeyuanState(leyuanAfkAreaHandled
                    ? LeyuanRouteState.OPEN_MAIN_CITY_HALL_MENU
                    : LeyuanRouteState.OPEN_CITY_MENU, cfg.leyuanStepDelay.get());
                return;
            }
        }
        if (leyuanActionTicks > 0 && --leyuanActionTicks > 0) return;

        switch (leyuanRouteState) {
            case OPEN_LOGIN_MENU -> {
                if (openLeyuanMenu()) {
                    leyuanLastScreenFingerprint = 0;
                    if (cfg.leyuanWelcomeEntryMode.get() == LeyuanWelcomeEntryMode.BOOK_DIRECT) {
                        setLeyuanState(LeyuanRouteState.SCAN_WELCOME, cfg.leyuanStepDelay.get());
                        notifyLeyuanProgress(2, "已打开登录服菜单，正在寻找" + highlightServer("直达主城书本") + "。");
                    } else if (leyuanRecoveryResumeState == LeyuanRouteState.CLICK_LOGIN_SURVIVAL) {
                        setLeyuanState(LeyuanRouteState.CLICK_LOGIN_SURVIVAL, cfg.leyuanStepDelay.get());
                    } else {
                        setLeyuanState(LeyuanRouteState.CLICK_LOGIN_SURVIVAL, cfg.leyuanStepDelay.get());
                        notify("乐源路线已打开登录服菜单。");
                    }
                }
            }
            case CLICK_LOGIN_SURVIVAL -> {
                if (cfg.leyuanWelcomeEntryMode.get() == LeyuanWelcomeEntryMode.BOOK_DIRECT) {
                    leyuanLastScreenFingerprint = 0;
                    setLeyuanState(LeyuanRouteState.SCAN_WELCOME, 0);
                } else if (clickLeyuanMenuKeyword(cfg.leyuanLoginSurvivalKeyword.get())) {
                    captureLeyuanStageOrigin();
                    setLeyuanState(LeyuanRouteState.WAIT_WELCOME, 40);
                    notifyLeyuanProgress(2, "已点击登录服“" + highlightServer(cfg.leyuanLoginSurvivalKeyword.get()) + "”，等待欢迎页。");
                }
            }
            case WAIT_WELCOME -> {
                if (cfg.leyuanWelcomeEntryMode.get() == LeyuanWelcomeEntryMode.BOOK_DIRECT) {
                    leyuanLastScreenFingerprint = 0;
                    setLeyuanState(LeyuanRouteState.SCAN_WELCOME, 0);
                } else if (mc.screen == null) {
                    captureLeyuanStageOrigin();
                    leyuanScanBaseYaw = mc.player.getYRot();
                    leyuanScanBasePitch = mc.player.getXRot();
                    leyuanScanIndex = 33;
                    leyuanScanClickTicks = 0;
                    setLeyuanState(LeyuanRouteState.SCAN_WELCOME, 0);
                    notifyLeyuanProgress(3, "正在寻找" + highlightServer("乐源城") + "入口。");
                }
            }
            case SCAN_WELCOME -> tickLeyuanWelcomeScan();
            case WAIT_MAIN_CITY -> {
                if (leyuanReachedMainCity()) {
                    setLeyuanState(LeyuanRouteState.OPEN_CITY_MENU, cfg.leyuanStepDelay.get());
                    notifyLeyuanProgress(4, "已到达" + highlightLocation("主城大区") + "，准备打开菜单。");
                }
            }
            case OPEN_AFK_MENU -> {
                tickLeyuanCityMenuFallback();
                if (openLeyuanMenu()) {
                    setLeyuanState(LeyuanRouteState.CLICK_RETURN_MAIN_CITY_HALL, cfg.leyuanStepDelay.get());
                    notifyLeyuanProgress(1, "已打开挂机区菜单，正在点击返回主城大厅。");
                }
            }
            case CLICK_RETURN_MAIN_CITY_HALL -> {
                if (clickLeyuanMenuKeywords(cfg.leyuanReturnMainCityKeywords.get())) {
                    captureLeyuanStageOrigin();
                    setLeyuanState(LeyuanRouteState.WAIT_MAIN_CITY_HALL, cfg.leyuanStepDelay.get());
                    notifyLeyuanProgress(2, "已点击返回主城大厅，等待到达主城。");
                }
            }
            case WAIT_MAIN_CITY_HALL -> {
                if (leyuanReachedMainCity()) {
                    setLeyuanState(LeyuanRouteState.OPEN_MAIN_CITY_HALL_MENU, cfg.leyuanStepDelay.get());
                    leyuanCityMenuFallbackAttempts = 0;
                    leyuanCityMenuFallbackRetryTick = 0;
                    notifyLeyuanProgress(3, "已到达主城大厅，准备再次打开菜单。");
                }
            }
            case OPEN_MAIN_CITY_HALL_MENU -> {
                tickLeyuanCityMenuFallback();
                if (openLeyuanMenu()) {
                    setLeyuanState(leyuanRecoveryResumeState == LeyuanRouteState.CLICK_SURVIVAL_FIRST
                        ? LeyuanRouteState.CLICK_SURVIVAL_FIRST
                        : leyuanRecoveryResumeState == LeyuanRouteState.CLICK_SURVIVAL_SECOND
                            ? LeyuanRouteState.CLICK_SURVIVAL_SECOND
                            : leyuanRecoveryResumeState == LeyuanRouteState.CLICK_TARGET_SERVER
                                ? LeyuanRouteState.CLICK_TARGET_SERVER
                                : LeyuanRouteState.CLICK_WORLD_TRANSFER, cfg.leyuanStepDelay.get());
                    notifyLeyuanProgress(4, "已打开主城大厅菜单，正在点击世界传送。");
                }
            }
            case OPEN_CITY_MENU -> {
                tickLeyuanCityMenuFallback();
                if (openLeyuanMenu()) {
                    setLeyuanState(leyuanRecoveryResumeState == LeyuanRouteState.CLICK_SURVIVAL_FIRST
                        ? LeyuanRouteState.CLICK_SURVIVAL_FIRST
                        : leyuanRecoveryResumeState == LeyuanRouteState.CLICK_SURVIVAL_SECOND
                            ? LeyuanRouteState.CLICK_SURVIVAL_SECOND
                            : leyuanRecoveryResumeState == LeyuanRouteState.CLICK_TARGET_SERVER
                                ? LeyuanRouteState.CLICK_TARGET_SERVER
                                : LeyuanRouteState.CLICK_WORLD_TRANSFER, cfg.leyuanStepDelay.get());
                    notifyLeyuanProgress(5, "已打开主城菜单，正在前往目标子服。");
                }
            }
            case CLICK_WORLD_TRANSFER -> advanceLeyuanMenuStep(
                cfg.leyuanWorldTransferKeyword.get(), LeyuanRouteState.CLICK_SURVIVAL_FIRST, "世界传送");
            case CLICK_SURVIVAL_FIRST -> advanceLeyuanMenuStep(
                cfg.leyuanSurvivalFirstKeyword.get(), LeyuanRouteState.CLICK_SURVIVAL_SECOND, "第一层生存大区");
            case CLICK_SURVIVAL_SECOND -> advanceLeyuanMenuStep(
                cfg.leyuanSurvivalSecondKeyword.get(), LeyuanRouteState.CLICK_TARGET_SERVER, "第二层生存大区");
            case CLICK_TARGET_SERVER -> {
                if (clickLeyuanMenuKeyword(cfg.leyuanTargetServerKeyword.get())) {
                    captureLeyuanStageOrigin();
                    setLeyuanState(LeyuanRouteState.WAIT_TARGET, cfg.leyuanStepDelay.get());
                    notifyLeyuanProgress(6, "已点击最终目标“" + highlightText(cfg.leyuanTargetServerKeyword.get()) + "”，正在确认到达。");
                }
            }
            case WAIT_TARGET -> {
                String keyword = findLeyuanTargetKeyword();
                if (keyword != null) {
                    leyuanRouteState = LeyuanRouteState.COMPLETE;
                    confirmTargetArea("乐源服路线已识别 " + highlightText(keyword));
                }
            }
            default -> {
            }
        }
    }

    private void advanceLeyuanMenuStep(String keyword, LeyuanRouteState next, String label) {
        if (!clickLeyuanMenuKeyword(keyword)) return;
        setLeyuanState(next, cfg.leyuanStepDelay.get());
        String emphasizedKeyword = label.contains("主城")
            ? highlightLocation(keyword)
            : label.contains("登录") ? highlightServer(keyword) : highlightText(keyword);
        notify("乐源路线已点击" + label + "“" + emphasizedKeyword + "”。");
    }

    private boolean openLeyuanMenu() {
        if (!canRunLeyuanMenu()) return false;
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            leyuanMenuUseCooldown = 0;
            leyuanLastScreenFingerprint = 0;
            return true;
        }
        if (mc.screen != null || mc.gameMode == null || mc.getConnection() == null) return false;
        if (leyuanMenuUseCooldown > 0) {
            leyuanMenuUseCooldown--;
            return false;
        }
        int slot = findLeyuanMenuItemSlot();
        if (slot < 0) return false;
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            leyuanPendingMenuSlot = slot;
            leyuanMenuUseCooldown = 2;
            return false;
        }
        if (leyuanRouteState == LeyuanRouteState.OPEN_CITY_MENU
            || leyuanRouteState == LeyuanRouteState.OPEN_MAIN_CITY_HALL_MENU
            || leyuanRouteState == LeyuanRouteState.OPEN_AFK_MENU) {
            float yaw = mc.player.getYRot();
            float pitch = -75.0f;
            mc.player.setXRot(pitch);
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        }
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        leyuanPendingMenuSlot = -1;
        leyuanMenuUseCooldown = 10;
        return false;
    }

    private void tickLeyuanCityMenuFallback() {
        if (!canRunLeyuanMenu()) {
            releaseLeyuanShortcutKeys();
            return;
        }
        InputConstants.Key shiftKey = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT);
        InputConstants.Key fKey = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F);
        KeyboardInvoker keyboard = (KeyboardInvoker) mc.keyboardHandler;
        long window = mc.getWindow().handle();

        if (leyuanCityMenuFallbackPhase == 1) {
            if (--leyuanCityMenuFallbackReleaseTicks > 0) return;
            KeyMapping.set(fKey, true);
            keyboard.yiyiaddon$keyPress(window, GLFW.GLFW_PRESS, new KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_SHIFT));
            leyuanCityMenuFallbackPhase = 2;
            return;
        }
        if (leyuanCityMenuFallbackPhase == 2) {
            KeyMapping.set(fKey, false);
            keyboard.yiyiaddon$keyPress(window, GLFW.GLFW_RELEASE, new KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_SHIFT));
            leyuanCityMenuFallbackPhase = 3;
            return;
        }
        if (leyuanCityMenuFallbackPhase == 3) {
            KeyMapping.set(shiftKey, false);
            keyboard.yiyiaddon$keyPress(window, GLFW.GLFW_RELEASE, new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0));
            leyuanCityMenuFallbackPhase = 0;
            leyuanCityMenuFallbackUsed = true;
            leyuanCityMenuFallbackAttempts++;
            leyuanCityMenuFallbackRetryTick = leyuanStateTicks + 40;
            return;
        }
        boolean hasClock = findLeyuanMenuItemSlot() >= 0;
        if (!cfg.leyuanCityMenuFallback.get() || mc.screen != null || leyuanCityMenuFallbackAttempts >= 2
            || (hasClock && leyuanStateTicks < cfg.leyuanCityMenuFallbackDelay.get())
            || (leyuanCityMenuFallbackAttempts > 0 && leyuanStateTicks < leyuanCityMenuFallbackRetryTick)) return;
        KeyMapping.set(shiftKey, true);
        keyboard.yiyiaddon$keyPress(window, GLFW.GLFW_PRESS, new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0));
        leyuanCityMenuFallbackPhase = 1;
        leyuanCityMenuFallbackReleaseTicks = 2;
        notify(hasClock
            ? "主城钟未打开，正在尝试 Shift＋F 快捷菜单（第 " + (leyuanCityMenuFallbackAttempts + 1) + " 次）。"
            : "主城未识别到钟，正在尝试 Shift＋F 快捷菜单（第 " + (leyuanCityMenuFallbackAttempts + 1) + " 次）。"
        );
    }

    private void notifyLeyuanProgress(int step, String message) {
        notify("§d§l乐源回服 §r§8[§f§l" + step + "§r§8/6] §r§f§l" + message);
    }

    private int findLeyuanMenuItemSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(cfg.leyuanMenuTool.get())) return i;
            for (String keyword : cfg.leyuanMenuItemKeywords.get()) {
                if (matchesItemKeyword(stack, keyword)) return i;
            }
        }
        return -1;
    }

    private boolean clickLeyuanMenuKeyword(String keyword) {
        return clickLeyuanMenuKeywords(List.of(keyword));
    }

    private boolean clickLeyuanMenuKeywords(List<String> keywords) {
        if (!canRunLeyuanMenu()) return false;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || mc.gameMode == null) return false;
        AbstractContainerMenu handler = screen.getMenu();
        int fingerprint = leyuanScreenFingerprint(screen, handler);
        if (fingerprint == leyuanLastScreenFingerprint) return false;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.container == mc.player.getInventory() || !matchesAnyItemKeyword(slot.getItem(), keywords)) continue;
            mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            leyuanLastScreenFingerprint = fingerprint;
            return true;
        }
        return false;
    }

    private boolean clickLeyuanDirectMainCityBook() {
        if (cfg.leyuanWelcomeEntryMode.get() != LeyuanWelcomeEntryMode.BOOK_DIRECT
            || !(mc.screen instanceof AbstractContainerScreen<?> screen)
            || mc.gameMode == null) return false;

        AbstractContainerMenu handler = screen.getMenu();
        int fingerprint = leyuanScreenFingerprint(screen, handler);
        if (fingerprint == leyuanLastScreenFingerprint) return false;

        for (int slotIndex : LEYUAN_DIRECT_MAIN_CITY_BOOK_SLOTS) {
            if (slotIndex >= handler.slots.size()) continue;
            Slot slot = handler.getSlot(slotIndex);
            ItemStack stack = slot.getItem();
            if (slot.container == mc.player.getInventory() || stack.isEmpty()
                || !stack.is(Items.KNOWLEDGE_BOOK)
                || !normalizeMatchText(stack.getHoverName().getString()).isEmpty()) continue;
            mc.gameMode.handleContainerInput(handler.containerId, slotIndex, 0, ContainerInput.PICKUP, mc.player);
            leyuanLastScreenFingerprint = fingerprint;
            return true;
        }
        return false;
    }

    private int leyuanScreenFingerprint(AbstractContainerScreen<?> screen, AbstractContainerMenu handler) {
        int result = 31 * handler.containerId + normalizeMatchText(screen.getTitle().getString()).hashCode();
        for (Slot slot : handler.slots) {
            if (slot.container == mc.player.getInventory()) continue;
            result = 31 * result + normalizeMatchText(slot.getItem().getHoverName().getString()).hashCode();
            result = 31 * result + slot.getItem().getCount();
        }
        return result;
    }

    private void tickLeyuanWelcomeScan() {
        if (leyuanReachedMainCity()) {
            setLeyuanState(LeyuanRouteState.OPEN_CITY_MENU, cfg.leyuanStepDelay.get());
            notify("已确认到达" + highlightLocation("主城大区") + "，继续执行路线。");
            return;
        }
        if (cfg.leyuanWelcomeEntryMode.get() == LeyuanWelcomeEntryMode.BOOK_DIRECT) {
            if (clickLeyuanDirectMainCityBook()) {
                captureLeyuanStageOrigin();
                setLeyuanState(LeyuanRouteState.WAIT_MAIN_CITY, cfg.leyuanStepDelay.get());
                notifyLeyuanProgress(4, "已点击" + highlightServer("直达主城书本") + "，正在进入" + highlightLocation("主城大区") + "。");
            }
            return;
        }
        if (mc.screen != null || mc.getConnection() == null) return;
        if (leyuanScanClickTicks > 0) {
            leyuanScanClickTicks--;
            if (leyuanScanClickTicks == 1) {
                performLeyuanLeftClick();
            }
            return;
        }

        int columns = 7;
        int rows = 5;
        int total = columns * rows;
        int point = leyuanScanIndex % total;
        int row = point / columns;
        int rawColumn = point % columns;
        int column = row % 2 == 0 ? rawColumn : columns - 1 - rawColumn;
        float yaw = leyuanScanBaseYaw + (float) (-35.0 + 70.0 * column / (columns - 1));
        float pitch = Math.max(-90.0f, Math.min(90.0f, leyuanScanBasePitch + (float) (-22.5 + 45.0 * row / (rows - 1))));
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
            yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        leyuanScanIndex++;
        leyuanScanClickTicks = 2;
    }

    private void performLeyuanLeftClick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.hitResult instanceof EntityHitResult hit) {
            mc.gameMode.attack(mc.player, hit.getEntity());
        } else if (mc.hitResult instanceof BlockHitResult hit) {
            mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean leyuanReachedMainCity() {
        return findSidebarKeyword(cfg.leyuanMainCityKeywords.get()) != null;
    }

    private void tickLeyuanAfkRecoveryTrigger() {
        if (!isLeyuanAfkRecoveryEnabled()) return;
        boolean inAfkArea = findSidebarKeyword(cfg.leyuanAfkAreaKeywords.get()) != null;
        int configuredMinutes = Math.max(0, cfg.leyuanAfkMenuDelayMinutes.get());
        int requiredWaitTicks = configuredMinutes * 60 * 20;
        if (!inAfkArea) {
            leyuanAfkAreaWaitTicks = 0;
            leyuanAfkAreaHandled = false;
            return;
        }
        if (leyuanAfkAreaHandled || isLeyuanRouteRunning() || state != AutoLoginState.ACTIVE) return;

        if (leyuanAfkAreaWaitTicks < requiredWaitTicks) {
            leyuanAfkAreaWaitTicks++;
            return;
        }

        leyuanAfkAreaHandled = true;
        leyuanLastScreenFingerprint = 0;
        leyuanMenuUseCooldown = 0;
        leyuanCityMenuFallbackAttempts = 0;
        leyuanCityMenuFallbackRetryTick = 0;
        leyuanCityMenuFallbackPhase = 0;
        setLeyuanState(LeyuanRouteState.OPEN_AFK_MENU, cfg.leyuanStepDelay.get());
        notify("检测到" + highlightLocation("挂机区") + "，开始执行乐源自动回服路线。");
    }

    private String findLeyuanTargetKeyword() {
        String target = cfg.leyuanTargetServerKeyword.get().trim();
        if (!target.isEmpty() && findSidebarKeyword(List.of(target)) != null) return target;
        return findSidebarKeyword(cfg.leyuanTargetAreaKeywords.get());
    }

    private String findSidebarKeyword(List<String> keywords) {
        if (mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return null;
        List<String> texts = new ArrayList<>();
        texts.add(objective.getDisplayName().getString());
        scoreboard.listPlayerScores(objective).stream()
            .filter(entry -> !entry.isHidden())
            .limit(15)
            .map(entry -> PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), Component.literal(entry.owner())).getString())
            .forEach(texts::add);
        for (String keyword : keywords) {
            String target = normalizeMatchText(keyword);
            if (target.isEmpty()) continue;
            if (texts.stream().map(this::normalizeMatchText).anyMatch(text -> text.contains(target))) return keyword.trim();
        }
        return null;
    }

    private void captureLeyuanStageOrigin() {
        leyuanStageWorld = mc.level;
        leyuanStageDimension = mc.level == null ? "" : mc.level.dimension().identifier().toString();
        leyuanStagePosition = mc.player == null ? null : new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private boolean leyuanStageChanged() {
        if (mc.level == null || mc.player == null) return false;
        if (mc.level != leyuanStageWorld) return true;
        if (!mc.level.dimension().identifier().toString().equals(leyuanStageDimension)) return true;
        return leyuanStagePosition != null
            && new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceToSqr(leyuanStagePosition) > 16.0;
    }

    private void markLeyuanTransition() {
        if (leyuanRouteState == LeyuanRouteState.SCAN_WELCOME) {
            setLeyuanState(LeyuanRouteState.WAIT_MAIN_CITY, cfg.leyuanStepDelay.get());
        }
    }

    private boolean isLeyuanRouteRunning() {
        return leyuanRouteState != LeyuanRouteState.IDLE
            && leyuanRouteState != LeyuanRouteState.COMPLETE
            && leyuanRouteState != LeyuanRouteState.FAILED;
    }

    private boolean isLeyuanAfkRecoveryEnabled() {
        return cfg.usesLeyuanMode() && cfg.leyuanAfkRecoveryEnabled.get();
    }

    private boolean canRunLeyuanRoute() {
        return cfg.usesLeyuanMode() && (cfg.leyuanEnabled.get() || cfg.leyuanAfkRecoveryEnabled.get());
    }

    private boolean canRunLeyuanMenu() {
        return cfg.usesLeyuanMode() && (cfg.leyuanEnabled.get() || leyuanRouteState == LeyuanRouteState.OPEN_AFK_MENU
            || leyuanRouteState == LeyuanRouteState.CLICK_RETURN_MAIN_CITY_HALL
            || leyuanRouteState == LeyuanRouteState.WAIT_MAIN_CITY_HALL
            || leyuanRouteState == LeyuanRouteState.OPEN_MAIN_CITY_HALL_MENU
            || leyuanRouteState == LeyuanRouteState.CLICK_WORLD_TRANSFER
            || leyuanRouteState == LeyuanRouteState.CLICK_SURVIVAL_FIRST
            || leyuanRouteState == LeyuanRouteState.CLICK_SURVIVAL_SECOND
            || leyuanRouteState == LeyuanRouteState.CLICK_TARGET_SERVER);
    }

    private void setLeyuanState(LeyuanRouteState next, int delay) {
        leyuanRouteState = next;
        leyuanStateTicks = 0;
        leyuanActionTicks = Math.max(0, delay);
    }

    private String leyuanStateName() {
        return switch (leyuanRouteState) {
            case OPEN_LOGIN_MENU -> "打开登录服菜单";
            case CLICK_LOGIN_SURVIVAL -> "点击登录服生存服";
            case WAIT_WELCOME, SCAN_WELCOME -> "欢迎页扫描";
            case WAIT_MAIN_CITY, OPEN_CITY_MENU -> "等待并打开主城菜单";
            case OPEN_AFK_MENU -> "打开挂机区菜单";
            case CLICK_RETURN_MAIN_CITY_HALL -> "点击返回主城大厅";
            case WAIT_MAIN_CITY_HALL -> "等待主城大厅";
            case OPEN_MAIN_CITY_HALL_MENU -> "打开主城大厅菜单";
            case CLICK_WORLD_TRANSFER -> "点击世界传送";
            case CLICK_SURVIVAL_FIRST -> "点击第一层生存大区";
            case CLICK_SURVIVAL_SECOND -> "点击第二层生存大区";
            case CLICK_TARGET_SERVER, WAIT_TARGET -> "进入并确认目标子服";
            default -> leyuanRouteState.toString();
        };
    }

    private String leyuanHotbarSnapshot() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            result.append(i).append('=').append(stack.getItem()).append(':').append(normalizeText(stack.getHoverName().getString())).append(';');
        }
        return result.toString();
    }

    private boolean matchesItemKeyword(ItemStack stack, String keyword) {
        if (stack.isEmpty() || keyword == null || keyword.isBlank()) return false;
        String target = normalizeMatchText(keyword);
        if (target.isEmpty()) return false;
        if (normalizeMatchText(stack.getHoverName().getString()).contains(target)) return true;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            if (normalizeMatchText(line.getString()).contains(target)) return true;
        }
        return false;
    }

    private boolean matchesAnyItemKeyword(ItemStack stack, List<String> keywords) {
        for (String keyword : keywords) {
            if (matchesItemKeyword(stack, keyword)) return true;
        }
        return false;
    }

    private String normalizeText(String value) {
        return INVISIBLE_CHARACTER_PATTERN.matcher(MINECRAFT_FORMATTING_PATTERN.matcher(
                Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)).replaceAll(""))
            .replaceAll("")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeMatchText(String value) {
        return normalizeText(value)
            .replaceAll("[\\s\\p{Z}]+", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    private void resetSubserverWorkflow() {
        subserverWorkflowRunning = false;
        subserverMenuStep = -1;
        subserverActionTicks = 0;
        subserverTimeoutTicks = 0;
        subserverCommandPending = false;
        subserverCommandTicks = 0;
        waitingForTargetArea = false;
        targetTransitionObserved = false;
        subserverTransitionExpected = false;
        routeStartWorld = null;
        routeStartDimension = "";
        leyuanRouteState = LeyuanRouteState.IDLE;
        leyuanStateTicks = 0;
        leyuanActionTicks = 0;
        leyuanLastScreenFingerprint = 0;
        leyuanScanIndex = 0;
        leyuanScanClickTicks = 0;
        leyuanPendingMenuSlot = -1;
        leyuanMenuUseCooldown = 0;
        leyuanCityMenuFallbackUsed = false;
        leyuanCityMenuFallbackReleaseTicks = 0;
        leyuanStageWorld = null;
        leyuanStageDimension = "";
        leyuanStagePosition = null;
        leyuanAfkAreaHandled = false;
        leyuanAfkAreaWaitTicks = 0;
    }

    private void stopStandardMenuRoute() {
        if (!subserverWorkflowRunning && !waitingForTargetArea && !subserverTransitionExpected) return;
        subserverWorkflowRunning = false;
        subserverMenuStep = -1;
        subserverActionTicks = 0;
        subserverTimeoutTicks = 0;
        waitingForTargetArea = false;
        targetTransitionObserved = false;
        subserverTransitionExpected = false;
    }

    private void stopLeyuanRoute() {
        if (!isLeyuanRouteRunning() && leyuanCityMenuFallbackPhase == 0) return;
        releaseLeyuanShortcutKeys();
        leyuanRouteState = LeyuanRouteState.IDLE;
        leyuanStateTicks = 0;
        leyuanActionTicks = 0;
        leyuanMenuUseCooldown = 0;
        leyuanPendingMenuSlot = -1;
        leyuanCityMenuFallbackAttempts = 0;
        leyuanCityMenuFallbackRetryTick = 0;
    }

    private void releaseLeyuanShortcutKeys() {
        InputConstants.Key shiftKey = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT);
        InputConstants.Key fKey = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F);
        KeyMapping.set(fKey, false);
        KeyMapping.set(shiftKey, false);
        if (leyuanCityMenuFallbackPhase != 0 && mc.keyboardHandler instanceof KeyboardInvoker keyboard) {
            long window = mc.getWindow().handle();
            keyboard.yiyiaddon$keyPress(window, GLFW.GLFW_RELEASE, new KeyEvent(GLFW.GLFW_KEY_F, 0, 0));
            keyboard.yiyiaddon$keyPress(window, GLFW.GLFW_RELEASE, new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0));
        }
        leyuanCityMenuFallbackPhase = 0;
        leyuanCityMenuFallbackReleaseTicks = 0;
    }

    private void tickTargetAreaDetection() {
        if (!waitingForTargetArea || !cfg.autoEnterSubserver.get()) return;
        if (++subserverTimeoutTicks >= cfg.menuTimeout.get()) {
            waitingForTargetArea = false;
            notify("目标区域确认超时，未执行目标区域指令。");
            return;
        }

        String currentDimension = mc.level.dimension().identifier().toString();
        if (mc.level != routeStartWorld || !currentDimension.equals(routeStartDimension)) {
            targetTransitionObserved = true;
        }
        String matchedKeyword = findTargetAreaKeyword();
        if (matchedKeyword != null) {
            confirmTargetArea("侧边栏已识别 " + matchedKeyword);
            return;
        }
        if (!cfg.requireTargetKeyword.get() && targetTransitionObserved && !(mc.screen instanceof AbstractContainerScreen<?>)) {
            confirmTargetArea(cfg.serverEntryMode.get() == ServerEntryMode.MENU_TRANSFER ? "已检测到世界或维度变化" : "已检测到子服切换");
        }
    }

    private String findTargetAreaKeyword() {
        if (mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return null;
        }
        List<String> texts = new ArrayList<>();
        texts.add(objective.getDisplayName().getString());
        scoreboard.listPlayerScores(objective).stream()
            .filter(entry -> !entry.isHidden())
            .limit(15)
            .map(entry -> PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), Component.literal(entry.owner())).getString())
            .forEach(texts::add);
        for (String keyword : cfg.targetAreaKeywords.get()) {
            String target = normalizeText(keyword).toLowerCase(java.util.Locale.ROOT);
            if (target.isEmpty()) continue;
            if (texts.stream().map(this::normalizeText).map(value -> value.toLowerCase(java.util.Locale.ROOT)).anyMatch(value -> value.contains(target))) {
                return keyword.trim();
            }
        }
        return null;
    }

    private void confirmTargetArea(String reason) {
        waitingForTargetArea = false;
        subserverWorkflowRunning = false;
        notify("已确认到达目标区域（" + reason + "）。");
        if (cfg.subserverCommand.get()) {
            subserverCommandPending = true;
            subserverCommandTicks = -cfg.targetStableDelay.get();
        }
    }

    private String extractSubserverName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replaceAll("(?i)§[0-9A-FK-OR]", "")
            .replaceAll("[\\u200B-\\u200D\\u2060\\uFEFF]", "")
            .replaceAll("\\s+", " ")
            .trim();
        Matcher matcher = SUBSERVER_NAME_PATTERN.matcher(normalized);
        if (!matcher.find()) return null;
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (name == null || name.equalsIgnoreCase("大厅") || name.equalsIgnoreCase("lobby")) return null;
        return name;
    }

    private void resetAllHandlers() {
        registerHandler.reset();
        loginHandler.reset();
        commandHandler.reset();
        reconnectHandler.reset();
        resetSessionFlags();
    }

    private boolean closeForUnsupportedEnvironment() {
        if (!isActive()) return false;
        if (!mc.hasSingleplayerServer() && !isAuthenticatedServer()) return false;
        notify("§c§l单人世界或正版验证服务器无需自动登录，模块已自动关闭。");
        toggle();
        return true;
    }

    private boolean isAuthenticatedServer() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return false;
        if (getCurrentServerAddress() == null || getCurrentServerAddress().isBlank()) return false;
        if (AccountChecker.check(mc) != AccountChecker.Result.PREMIUM) return false;
        UUID sessionUuid = mc.getUser().getProfileId();
        return sessionUuid != null && sessionUuid.equals(mc.player.getUUID());
    }

    /**
     * 记录当前服务器连接信息，供断线重连使用。
     * 兼容直连（getCurrentServer() == null）：从网络连接中读取地址。
     */
    private void recordCurrentServer() {
        ServerData entry = mc.getCurrentServer();
        if (entry != null) {
            reconnectHandler.recordServer(
                ServerAddress.parseString(entry.ip),
                entry
            );
        } else if (mc.getConnection() != null
                && mc.getConnection().getServerData() != null) {
            // 直连场景（命令行 / 启动参数等）：从 networkHandler 获取
            ServerData info = mc.getConnection().getServerData();
            reconnectHandler.recordServer(
                ServerAddress.parseString(info.ip),
                info
            );
        }
    }

    private String getCurrentServerAddress() {
        ServerData entry = mc.getCurrentServer();
        if (entry != null) return entry.ip;
        if (mc.getConnection() != null && mc.getConnection().getServerData() != null) {
            return mc.getConnection().getServerData().ip;
        }
        return null;
    }

    public ReconnectHandler getReconnectHandler() { return reconnectHandler; }
    public AutoLoginSettings getCfg()              { return cfg; }

    private void debugMsg(String msg) {
        if (cfg.debugMode.get()) {
            notify("[Debug] " + msg);
        }
    }
}
