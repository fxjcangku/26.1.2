// 自动图书管理员 Meteor 模块骨架
package com.example.addon.librarian;

import baritone.api.BaritoneAPI;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.ui.HelpScreen;
import com.example.addon.librarian.config.AutoLibrarianConfig;
import com.example.addon.librarian.config.AutoLibrarianSettings;
import com.example.addon.librarian.config.SuccessSound;
import com.example.addon.librarian.fsm.AutoLibrarianState;
import com.example.addon.librarian.integration.FabricBaritoneMovementService;
import com.example.addon.librarian.integration.FabricEnchantmentService;
import com.example.addon.librarian.integration.FabricInventoryService;
import com.example.addon.librarian.integration.FabricLecternPlacementService;
import com.example.addon.librarian.integration.FabricTradeService;
import com.example.addon.librarian.integration.FabricVillagerSearchService;
import com.example.addon.librarian.integration.FabricVillagerStationService;
import com.example.addon.librarian.model.AutoLibrarianContext;
import com.example.addon.librarian.model.EnchantmentTarget;
import com.example.addon.librarian.model.TargetProgress;
import com.example.addon.librarian.orchestrator.AutoLibrarianOrchestrator;
import com.example.addon.librarian.service.AutoLibrarianServices;
import com.example.addon.librarian.service.DebugLoggerService;
import com.example.addon.librarian.service.DebugSoundEvent;
import com.example.addon.librarian.service.DebugSoundService;
import com.example.addon.librarian.service.MovementStatus;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 自动图书管理员模块（Meteor Module 入口）。
 *
 * <p>负责装配设置、业务服务与编排器，处理模块启停、tick 驱动、启动自检、
 * 状态播报与快捷键暂停。继承 {@link YiyiaddonModule} 以复用统一消息格式。</p>
 */
public final class AutoLibrarianModule extends YiyiaddonModule {
    /** Meteor 设置 */
    private final AutoLibrarianSettings moduleSettings;
    /** 运行上下文（每次启动重新创建） */
    private AutoLibrarianContext context;
    /** 业务服务集合 */
    private AutoLibrarianServices services;
    /** 业务编排器 */
    private AutoLibrarianOrchestrator orchestrator;
    /** 是否已暂停 */
    private boolean paused = false;

    public AutoLibrarianModule() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动图书管理员",
            "自动寻路失业村民、放置讲台刷新交易、命中目标附魔自动购买，未命中自动拆台循环。详细参考下面使用说明。");
        chatFeedback = false;
        moduleSettings = new AutoLibrarianSettings(settings);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            // 使用说明按钮（置顶显眼位置）
            WButton helpBtn = theme.button("§e查看使用说明");
            helpBtn.action = () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();
        });
    }

    /** 构建使用说明内容（点击「查看使用说明」按钮打开） */
    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("准备",
                "  §8├─ §f背包携带：讲台 × N、书 × N、绿宝石 × 足够数量",
                "  §8├─ §f在「目标附魔」中设置想要的附魔类型",
                "  §8├─ §f站在已搭建好的岩浆块工位阵列附近",
                "  §8└─ §f开启模块即自动运行"
            ),
            new HelpScreen.HelpSection("自动流程",
                "  §a[1] §f在搜索半径内寻找失业村民",
                "  §a[2] §f识别岩浆块工位，清除障碍方块",
                "  §a[3] §f放置讲台，等待村民接受图书管理员职业",
                "  §a[4] §f静默读取交易列表（不打开界面），命中目标附魔则自动发包购买",
                "  §a[5] §f未命中则拆除讲台，刷新村民交易，重复循环"
            ),
            new HelpScreen.HelpSection("静默交易",
                "  §7▸ §f交易全程不打开交易界面，发包静默完成",
                "  §7▸ §f命中目标附魔后自动选中并领取附魔书"
            ),
            new HelpScreen.HelpSection("场地要求",
                "  §6▸ §f每个村民工位：岩浆块 + 相邻空地（用于放置讲台）",
                "  §6▸ §f支持活版门卡位场地（村民无法逃跑）",
                "  §6▸ §f安装 Baritone 可自动寻路移动到村民位置"
            ),
            new HelpScreen.HelpSection("注意",
                "  §c⚠ §f背包缺少讲台/书/绿宝石时模块会报错并自动关闭",
                "  §c⚠ §f绿宝石价格超过「最大价格」上限的交易会跳过"
            )
        );
    }

    @Override
    public void onActivate() {
        // 世界就绪判断：selfCheck 会访问 mc.level，必须先确认已进入世界
        if (mc.player == null || mc.level == null) {
            notifyError("必须在进入世界后才能启动模块。");
            mc.execute(this::toggle);
            return;
        }

        // 开机自检：缺项一次列全（目标附魔、Baritone、背包物料），配好一项下次就少一条
        if (!reportSelfCheck(selfCheck())) return;

        // 应用目标附魔并初始化运行时
        Registry<Enchantment> enchantments = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<EnchantmentTarget> targets = moduleSettings.targetEnchantments.get().stream()
            .map(key -> createTarget(enchantments, key))
            .toList();
        context = new AutoLibrarianContext(new TargetProgress(targets));
        FabricVillagerStationService stationService = new FabricVillagerStationService();
        FabricLecternPlacementService lecternService = new FabricLecternPlacementService();
        FabricEnchantmentService enchantmentService = new FabricEnchantmentService();
        FabricTradeService tradeService = new FabricTradeService();
        services = new AutoLibrarianServices(stationService, lecternService, enchantmentService, tradeService);
        AutoLibrarianConfig defaults = AutoLibrarianConfig.defaults();
        AutoLibrarianConfig config = new AutoLibrarianConfig(
            targets,
            moduleSettings.searchRadius.get(),
            defaults.movementArrivalRadius(),
            moduleSettings.maximumEmeraldPrice.get(),
            moduleSettings.professionTimeout.get(),
            defaults.tradeScreenTimeoutTicks(),
            defaults.tradeSyncTimeoutTicks(),
            defaults.movementTimeoutTicks(),
            moduleSettings.actionDelay.get(),
            moduleSettings.resetDelay.get(),
            moduleSettings.removeTargetOnFound.get(),
            moduleSettings.debugMode.get(),
            moduleSettings.playNotificationSound.get()
        );
        ModuleLogger moduleLogger = new ModuleLogger();
        orchestrator = new AutoLibrarianOrchestrator(
            config,
            context,
            new FabricVillagerSearchService(moduleLogger, moduleSettings.debugMode::get),
            stationService,
            new FabricBaritoneMovementService(moduleLogger),
            lecternService,
            tradeService,
            new FabricInventoryService(),
            enchantmentService,
            moduleLogger,
            new ModuleSound()
        );
        orchestrator.start();
        reportStartupInfo(targets);
    }

    /**
     * 开机自检，收集全部缺项。
     *
     * 收集全部而不是遇到第一个就返回，这样用户一次就能看到还差什么，
     * 配好一项下次启动就少一条，不用反复开关模块试错。
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();

        // 目标附魔检测
        if (moduleSettings.targetEnchantments.get().isEmpty()) {
            missing.add("§d目标附魔§f·未勾选");
        }

        // Baritone 检测（移动服务依赖）
        if (!baritoneAvailable()) {
            missing.add("§bBaritone§f·未安装或未启用");
        }

        // 背包物料检测：讲台 / 书 / 绿宝石
        int lectern = 0, book = 0, emerald = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.is(Items.LECTERN)) lectern += stack.getCount();
            if (stack.is(Items.BOOK)) book += stack.getCount();
            if (stack.is(Items.EMERALD)) emerald += stack.getCount();
        }
        if (lectern == 0) missing.add("§a讲台§f·背包没有");
        if (book == 0) missing.add("§a书§f·背包没有");
        if (emerald == 0) missing.add("§a绿宝石§f·背包没有");

        return missing;
    }

    /** 判断 Baritone 是否可用（移动服务依赖），任何异常都视为不可用 */
    private boolean baritoneAvailable() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 启动播报：合并为一条多行消息块，只带一次模块前缀。
     *
     * 只报会影响本次结果的关键项（目标附魔、搜索半径、最高价格、命中策略），
     * 正文统一「标签 §8▸ 值」，与自动农场/自动挖矿的启动报告风格一致。
     */
    private void reportStartupInfo(List<EnchantmentTarget> targets) {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 自动图书管理员 · 启动报告");

        // 目标附魔（最多显示前 3 个，其余折叠）
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(3, targets.size()); i++) {
            if (i > 0) names.append("§f、");
            names.append(highlightText(targets.get(i).displayName() + " Lv." + targets.get(i).level()));
        }
        if (targets.size() > 3) {
            names.append("§f 等 ").append(highlightText(String.valueOf(targets.size()))).append("§f 种");
        }
        report.append("\n§7目标附魔　§8▸ ").append(names).append("§r");

        // 搜索半径
        report.append("\n§7搜索半径　§8▸ ").append(highlightNumber(moduleSettings.searchRadius.get() + " 格")).append("§r");

        // 最高绿宝石价格
        report.append("\n§7最高价格　§8▸ ").append(highlightNumber(moduleSettings.maximumEmeraldPrice.get() + " 绿宝石")).append("§r");

        // 命中策略
        report.append("\n§7命中策略　§8▸ ").append(highlightFunction(moduleSettings.removeTargetOnFound.get() ? "找到后移除" : "找到后保留")).append("§r");

        notify(report.toString());
    }

    @Override
    public void onDeactivate() {
        if (orchestrator != null) orchestrator.stop();
        orchestrator = null;
        services = null;
        context = null;
        paused = false;
        if (mc.player != null) {
            notify("§c§l已关闭");
        }
    }

    @Override
    public String getInfoString() {
        if (paused) return "已暂停";
        return orchestrator == null ? "未初始化" : orchestrator.getState().name();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (orchestrator == null) return;
        // 检测快捷键：按下直接强制结束，关闭模块
        if (moduleSettings.pauseKeybind.get().isPressed()) {
            if (mc.player != null) {
                notify("§c§l已强制结束");
            }
            toggle();
            return;
        }
        if (paused) return;
        orchestrator.tick();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null) return;
        // 静默交易：运行中打开村民交易界面/箱子屏幕时取消显示（不抢鼠标），
        // 交易界面数据仍由 mc.player.containerMenu 同步，SelectTrade/取成品照常发包。
        // 排除背包(InventoryScreen)与创造模式背包(CreativeModeInventoryScreen)：
        // 玩家手动按 E 打开背包必须放行，避免影响正常操作。
        if (isActive() && event.screen instanceof AbstractContainerScreen<?>
            && !(event.screen instanceof InventoryScreen)
            && !(event.screen instanceof CreativeModeInventoryScreen)) {
            event.setCancelled(true);
        }
    }

    public void setServices(AutoLibrarianServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public AutoLibrarianContext getContext() {
        return context;
    }

    public AutoLibrarianServices getServices() {
        return services;
    }

    public AutoLibrarianSettings getModuleSettings() {
        return moduleSettings;
    }

    private EnchantmentTarget createTarget(
        Registry<Enchantment> enchantments,
        ResourceKey<Enchantment> key
    ) {
        var optEntry = enchantments.get(key);
        int maximumLevel = optEntry
            .<Integer>map(holder -> holder.value().getMaxLevel())
            .orElseThrow(() -> new IllegalArgumentException("无法解析目标附魔: " + key.identifier()));
        // 获取本地化中文名，如"击退"、"精准采集"，回退到命名空间ID
        String displayName = optEntry
            .map(holder -> holder.value().description().getString())
            .orElse(key.identifier().toString());
        return EnchantmentTarget.resolved(
            key.identifier().toString(),
            displayName,
            "minecraft:enchanted_book",
            maximumLevel
        );
    }

    private final class ModuleLogger implements DebugLoggerService {
        private static final long ERROR_THROTTLE_MILLIS = 2000L;
        private String lastErrorMessage;
        private long lastErrorTimeMillis;
        // 状态播报去重锁：同一进度状态不重复播，循环类模块每轮只播一次
        private String lastNotifiedState = "";

        @Override
        public void state(AutoLibrarianState state, AutoLibrarianContext currentContext, MovementStatus movementStatus) {
            if (moduleSettings.debugMode.get()) {
                sendChat("§7[调试] 状态 " + localizeState(state) + " 移动 " + localizeMovement(movementStatus));
            }
            broadcastProgress(state);
        }

        // 状态机进度播报：只播有实质动作的工作状态，寻路/读取等过渡状态不播
        private void broadcastProgress(AutoLibrarianState state) {
            if (!moduleSettings.chatFeedback.get()) return;
            String message = switch (state) {
                case SEARCH_VILLAGER -> "§7正在搜索失业村民...";
                case FIND_LECTERN_POSITION -> "§7正在检测工位...";
                case BREAK_OBSTACLE -> "§7正在清除障碍方块...";
                case PLACE_LECTERN -> "§7正在放置讲台...";
                case WAIT_PROFESSION -> "§7等待村民成为图书管理员...";
                case BREAK_LECTERN -> "§7正在拆除讲台...";
                case RESET -> "§6⚠ 未命中目标 §8▸ 准备刷新交易";
                default -> null;
            };
            if (message == null) return;
            if (message.equals(lastNotifiedState)) return;
            lastNotifiedState = message;
            sendChat(message);
        }

        @Override
        public void info(String message) {
            if (moduleSettings.chatFeedback.get()) sendChat("§f§l" + message);
        }

        @Override
        public void debug(String message) {
            if (moduleSettings.debugMode.get()) sendChat("§7[调试] §f" + message);
        }

        @Override
        public void error(String message) {
            long now = System.currentTimeMillis();
            if (message.equals(lastErrorMessage) && now - lastErrorTimeMillis < ERROR_THROTTLE_MILLIS) return;
            lastErrorMessage = message;
            lastErrorTimeMillis = now;
            sendChat("§c✗ " + message);
        }

        private void sendChat(String message) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                    YiyiaddonModule.formatMessage("自动图书管理员", clean(message))));
            }
        }

        private String clean(String message) {
            return message.replaceAll("[\\p{Punct}，。！？：；、（）【】「」《》“”‘’…]", "");
        }

        private String localizeState(AutoLibrarianState state) {
            return switch (state) {
                case IDLE -> "空闲";
                case START -> "启动";
                case SEARCH_VILLAGER -> "搜索村民";
                case SELECT_TARGET_VILLAGER -> "选择目标村民";
                case MOVE_TO_VILLAGER -> "移动到村民";
                case FIND_LECTERN_POSITION -> "检测工位";
                case MOVE_TO_STAND_POSITION -> "移动到放置位";
                case BREAK_OBSTACLE -> "清除障碍方块";
                case PLACE_LECTERN -> "放置讲台";
                case WAIT_PROFESSION -> "等待成为图书管理员";
                case OPEN_TRADE -> "打开交易界面";
                case WAIT_TRADE_SCREEN -> "等待交易界面";
                case READ_TRADES -> "读取交易";
                case CHECK_ENCHANTMENT -> "检查附魔";
                case RESET -> "准备刷新";
                case BREAK_LECTERN -> "拆除讲台";
                case WAIT_UNEMPLOYED -> "等待村民失业";
                case SUCCESS_FOUND -> "找到目标附魔";
                case TRADE_PROCESS -> "处理交易";
                case SELECT_TRADE -> "选择交易";
                case WAIT_TRADE_SYNC -> "等待交易同步";
                case TAKE_TRADE_OUTPUT -> "领取物品";
                case VERIFY_PURCHASE -> "验证购买";
                case COMPLETE_TARGET -> "完成目标";
                case END_VILLAGER_CYCLE -> "结束村民周期";
                case FINISH -> "全部完成";
                case ERROR -> "错误";
            };
        }

        private String localizeMovement(MovementStatus status) {
            return switch (status) {
                case IDLE -> "空闲";
                case STARTING -> "启动中";
                case PATHING -> "寻路中";
                case ARRIVED -> "已到达";
                case FAILED -> "失败";
                case CANCELED -> "已取消";
                case TIMED_OUT -> "超时";
                case UNAVAILABLE -> "不可用";
            };
        }
    }

    private final class ModuleSound implements DebugSoundService {
        @Override
        public void play(DebugSoundEvent event) {
            if (mc.player == null) return;
            if (!moduleSettings.playNotificationSound.get()) return;
            if (event != DebugSoundEvent.SUCCESS) return;

            SoundEvent sound = switch (moduleSettings.successSound.get()) {
                case BELL                 -> SoundEvents.BELL_BLOCK;
                case ATTACK_CRIT          -> SoundEvents.PLAYER_ATTACK_CRIT;
                case CAT                  -> SoundEvents.CAT_AMBIENT_BABY.value();
                case THUNDER              -> SoundEvents.LIGHTNING_BOLT_THUNDER;
                case EXPERIENCE_ORB       -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case CHALLENGE_COMPLETE   -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
                case PLAYER_LEVELUP       -> SoundEvents.PLAYER_LEVELUP;
                case NOTE_PLING           -> SoundEvents.NOTE_BLOCK_PLING.value();
                case CHEST_OPEN           -> SoundEvents.CHEST_OPEN;
                case FIREWORK_BLAST       -> SoundEvents.FIREWORK_ROCKET_BLAST;
                case VILLAGER_CELEBRATE   -> SoundEvents.VILLAGER_CELEBRATE;
                case ZOMBIE_VILLAGER_CURE -> SoundEvents.ZOMBIE_VILLAGER_CURE;
                case GOAT_SCREAM          -> SoundEvents.GOAT_SCREAMING_AMBIENT;
                case GHAST_SCREAM         -> SoundEvents.GHAST_SCREAM;
                case ALLAY_AMBIENT        -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
                case ENCHANTMENT_TABLE    -> SoundEvents.ENCHANTMENT_TABLE_USE;
                case TRIDENT_THUNDER      -> SoundEvents.TRIDENT_THUNDER.value();
                case PANDA_SNEEZE         -> SoundEvents.PANDA_SNEEZE;
                case WARDEN_ROAR          -> SoundEvents.WARDEN_ROAR;
                case DRAGON_GROWL         -> SoundEvents.ENDER_DRAGON_GROWL;
                case END_PORTAL           -> SoundEvents.END_PORTAL_SPAWN;
                case ELDER_GUARDIAN_CURSE -> SoundEvents.ELDER_GUARDIAN_CURSE;
            };
            mc.player.playSound(sound, 1.0f, 1.0f);
        }
    }
}
