package com.example.addon.autologin;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/** 自动登入所有配置项 */
public final class AutoLoginSettings {

    // ── 分组（顺序即配置页显示顺序）────────────────────────────────────────
    final SettingGroup grpLogin;        // 1. 自动登录
    final SettingGroup grpRegister;     // 2. 自动注册
    final SettingGroup grpReconnect;    // 3. 自动重连
    final SettingGroup grpCommands;     // 4. 自动执行指令
    final SettingGroup grpSubserver;    // 5. 通用子服路线
    final SettingGroup grpLeyuan;       // 6. 乐源服定制路线
    final SettingGroup grpWorldLoad;    // 7. 进入等待
    final SettingGroup grpAccount;      // 8. 正版检测
    final SettingGroup grpDebug;        // 9. 高级设置

    // ── 自动登录 ─────────────────────────────────────────────────────────────
    public final Setting<Boolean> autoLogin;
    public final Setting<Boolean> noLoginDetection;
    public final Setting<Boolean> detectSubserver;
    public final Setting<String>  loginPassword;
    public final Setting<Integer> loginDelay;

    // ── 自动注册 ─────────────────────────────────────────────────────────────
    public final Setting<Boolean> autoRegister;
    public final Setting<String>  registerPassword;
    public final Setting<Integer> registerDelay;

    // ── 自动重连 ─────────────────────────────────────────────────────────────
    public final Setting<Boolean> autoReconnect;
    public final Setting<Boolean> alwaysReconnect;
    public final Setting<Integer> reconnectDelay;
    public final Setting<Integer> maxReconnectAttempts;
    public final Setting<Boolean> hideReconnectButton;

    // ── 自动执行指令 ─────────────────────────────────────────────────────────
    public final Setting<Boolean> autoCommand;
    public final Setting<String>  autoCommandText;
    public final Setting<Integer> commandDelay;

    public final Setting<Boolean> autoEnterSubserver;
    public final Setting<ServerEntryMode> serverEntryMode;
    public final Setting<Item> menuTool;
    public final Setting<List<String>> menuItemKeywords;
    public final Setting<List<String>> menuClickSteps;
    public final Setting<Integer> menuActionDelay;
    public final Setting<Integer> menuTimeout;
    public final Setting<List<String>> targetAreaKeywords;
    public final Setting<Boolean> requireTargetKeyword;
    public final Setting<Integer> targetStableDelay;
    public final Setting<Boolean> subserverCommand;
    public final Setting<String> subserverCommandText;
    public final Setting<Integer> subserverCommandDelay;
    public final Setting<Boolean> leyuanEnabled;
    public final Setting<Item> leyuanMenuTool;
    public final Setting<List<String>> leyuanMenuItemKeywords;
    public final Setting<String> leyuanLoginSurvivalKeyword;
    public final Setting<LeyuanWelcomeEntryMode> leyuanWelcomeEntryMode;
    public final Setting<String> leyuanWorldTransferKeyword;
    public final Setting<String> leyuanSurvivalFirstKeyword;
    public final Setting<String> leyuanSurvivalSecondKeyword;
    public final Setting<String> leyuanTargetServerKeyword;
    public final Setting<List<String>> leyuanMainCityKeywords;
    public final Setting<Boolean> leyuanAfkRecoveryEnabled;
    public final Setting<Integer> leyuanAfkMenuDelayMinutes;
    public final Setting<List<String>> leyuanAfkAreaKeywords;
    public final Setting<List<String>> leyuanReturnMainCityKeywords;
    public final Setting<List<String>> leyuanTargetAreaKeywords;
    public final Setting<Integer> leyuanStepDelay;
    public final Setting<Integer> leyuanStepTimeout;
    public final Setting<Boolean> leyuanCityMenuFallback;
    public final Setting<Integer> leyuanCityMenuFallbackDelay;
    public final Setting<Integer> leyuanRecoveryWaitMinutes;
    public final Setting<Integer> leyuanRecoveryRetrySeconds;
    public final Setting<Integer> leyuanRecoveryMaxRetries;

    // ── 进入等待 ─────────────────────────────────────────────────────────────
    public final Setting<Integer> worldLoadWait;
    public final Setting<Integer> authDetectTimeout;

    // ── 正版检测 ─────────────────────────────────────────────────────────────
    /** 进服时自动输出账号类型 */
    public final Setting<Boolean> autoCheckAccount;
    /** 伪按钮：立即检测账号类型（改为 true 时触发，回调内自动重置） */
    public final Setting<Boolean> checkAccountNow;

    // ── 高级设置 ─────────────────────────────────────────────────────────────
    public final Setting<Boolean> debugMode;

    public AutoLoginSettings(Settings settings, Minecraft mc) {
        grpLogin        = settings.createGroup("自动登录");
        grpRegister     = settings.createGroup("自动注册");
        grpReconnect    = settings.createGroup("自动重连");
        grpCommands     = settings.createGroup("自动执行指令");
        grpSubserver    = settings.createGroup("通用子服路线");
        grpLeyuan       = settings.createGroup("乐源服定制路线");
        grpWorldLoad    = settings.createGroup("进入等待");
        grpAccount      = settings.createGroup("正版检测");
        grpDebug        = settings.createGroup("高级设置");

        // ── 1. 自动登录 ───────────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        Setting<Boolean>[] registerToggleHolder = new Setting[1];

        autoLogin = grpLogin.add(new BoolSetting.Builder()
            .name("自动登录")
            .description("检测到登录提示时自动发送登录指令。")
            .defaultValue(true)
            .onChanged(v -> {
                if (v && registerToggleHolder[0] != null && registerToggleHolder[0].get()) {
                    registerToggleHolder[0].set(false);
                    notifyChat(mc, "§a§l已开启自动登录§r，§c§l自动注册已关闭§r。");
                }
            })
            .build());

        noLoginDetection = grpLogin.add(new BoolSetting.Builder()
            .name("服务器免登录检测")
            .description("适用于服务器两小时免登录：每次进服先监听成功登录或已登入消息；命中时保持自动登录关闭，未命中或收到登录指令提示时自动开启登录。")
            .defaultValue(false)
            .build());

        detectSubserver = grpLogin.add(new BoolSetting.Builder()
            .name("检测服务器子服")
            .description("大厅完成登录后，进入子服时沿用大厅登录状态，不重复发送登录指令。关闭后每个新服务器连接都重新检测登录。")
            .defaultValue(true)
            .onChanged(v -> notifyChat(mc, v
                ? "§a§l已开启服务器子服检测§r，进入子服将沿用大厅登录状态。"
                : "§c§l已关闭服务器子服检测§r，进入新服务器连接将重新检测登录。"))
            .build());

        loginPassword = grpLogin.add(new StringSetting.Builder()
            .name("登录密码")
            .description("自动登录使用的密码（不会显示在聊天栏）。")
            .defaultValue("")
            .visible(autoLogin::get)
            .build());

        loginDelay = grpLogin.add(new IntSetting.Builder()
            .name("登录延迟（tick）")
            .description("收到登录提示后等待多少 tick 再发送指令（20 tick = 1 秒）。")
            .defaultValue(20)
            .min(0)
            .sliderRange(0, 100)
            .visible(autoLogin::get)
            .build());

        // ── 2. 自动注册 ───────────────────────────────────────────────────────
        autoRegister = grpRegister.add(new BoolSetting.Builder()
            .name("自动注册")
            .description("检测到注册提示时自动发送注册指令。")
            .defaultValue(false)
            .onChanged(v -> {
                if (v && autoLogin.get()) {
                    autoLogin.set(false);
                    notifyChat(mc, "§a§l已开启自动注册§r，§c§l自动登录已关闭§r。");
                }
            })
            .build());
        registerToggleHolder[0] = autoRegister;

        registerPassword = grpRegister.add(new StringSetting.Builder()
            .name("注册密码")
            .description("自动注册使用的密码（不会显示在聊天栏）。")
            .defaultValue("")
            .visible(autoRegister::get)
            .build());

        registerDelay = grpRegister.add(new IntSetting.Builder()
            .name("注册延迟（tick）")
            .description("收到注册提示后等待多少 tick 再发送指令（20 tick = 1 秒）。")
            .defaultValue(20)
            .min(0)
            .sliderRange(0, 100)
            .visible(autoRegister::get)
            .build());

        // ── 3. 自动重连 ───────────────────────────────────────────────────────
        autoReconnect = grpReconnect.add(new BoolSetting.Builder()
            .name("自动重连")
            .description("断线后在主菜单或断线界面等待指定时间，再自动连接上一次服务器。")
            .defaultValue(true)
            .build());

        reconnectDelay = grpReconnect.add(new IntSetting.Builder()
            .name("重连等待（tick）")
            .description("断线后等待多少 tick 再重连（20 tick = 1 秒）。")
            .defaultValue(100)
            .min(20)
            .sliderRange(20, 1200)
            .visible(autoReconnect::get)
            .build());

        alwaysReconnect = grpReconnect.add(new BoolSetting.Builder()
            .name("无限重连")
            .description("开启后忽略最大重连次数，持续尝试连接，直到成功进服或关闭自动重连。")
            .defaultValue(false)
            .visible(autoReconnect::get)
            .build());

        maxReconnectAttempts = grpReconnect.add(new IntSetting.Builder()
            .name("最大重连次数")
            .description("连续失败达到该次数后停止重连。")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .visible(() -> autoReconnect.get() && !alwaysReconnect.get())
            .build());

        hideReconnectButton = grpReconnect.add(new BoolSetting.Builder()
            .name("隐藏断线页按钮")
            .description("开启后断线界面不显示「重新连接」和「切换自动重连」按钮，仅保留后台自动重连。")
            .defaultValue(false)
            .visible(autoReconnect::get)
            .build());

        // ── 4. 自动执行指令 ───────────────────────────────────────────────────
        autoCommand = grpCommands.add(new BoolSetting.Builder()
            .name("登录后执行指令")
            .description("仅在自动登录成功或认证检测超时后执行自定义指令；登录和注册均关闭时不会执行。")
            .defaultValue(false)
            .build());

        autoCommandText = grpCommands.add(new StringSetting.Builder()
            .name("执行指令")
            .description("登录完成后执行的指令（含斜杠，如 /home）。")
            .defaultValue("/home")
            .visible(() -> autoCommand.get() && (autoLogin.get() || autoRegister.get() || noLoginDetection.get()))
            .build());

        commandDelay = grpCommands.add(new IntSetting.Builder()
            .name("指令延迟（tick）")
            .description("登录完成后等待多少 tick 再执行指令（20 tick = 1 秒）。")
            .defaultValue(60)
            .min(0)
            .sliderRange(0, 400)
            .visible(() -> autoCommand.get() && (autoLogin.get() || autoRegister.get() || noLoginDetection.get()))
            .build());

        autoEnterSubserver = grpSubserver.add(new BoolSetting.Builder()
            .name("自动进入目标区域")
            .description("认证完成后按照选择的进入方式到达目标区域。")
            .defaultValue(true)
            .build());

        serverEntryMode = grpSubserver.add(new EnumSetting.Builder<ServerEntryMode>()
            .name("进入方式")
            .description("直接进入、菜单传送、子服网络或乐源服专用多阶段回服路线，四种模式互斥运行。")
            .defaultValue(ServerEntryMode.LEYUAN_CUSTOM)
            .visible(autoEnterSubserver::get)
            .build());

        menuTool = grpSubserver.add(new ItemSetting.Builder()
            .name("菜单工具")
            .description("选择用于打开服务器菜单的快捷栏物品。")
            .defaultValue(Items.CLOCK)
            .visible(() -> usesStandardMenuRoute())
            .build());

        menuItemKeywords = grpSubserver.add(new StringListSetting.Builder()
            .name("菜单物品关键词")
            .description("辅助匹配改名物品的名称或 Lore；列表可用加号和减号编辑。")
            .defaultValue(List.of("服务器选择"))
            .visible(() -> usesStandardMenuRoute())
            .build());

        menuClickSteps = grpSubserver.add(new StringListSetting.Builder()
            .name("菜单点击顺序")
            .description("每一项是一步，按从上到下的顺序匹配物品名称或 Lore；使用加号新增、减号删除。")
            .defaultValue(List.of("生存区", "生存二区"))
            .visible(() -> usesStandardMenuRoute())
            .build());

        menuActionDelay = grpSubserver.add(new IntSetting.Builder()
            .name("每步等待（tick）")
            .description("使用菜单物品或点击按钮后等待的时间。")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 100)
            .visible(() -> usesStandardMenuRoute())
            .build());

        menuTimeout = grpSubserver.add(new IntSetting.Builder()
            .name("菜单超时（tick）")
            .description("等待菜单或目标关键词的最长时间，超时后停止，避免乱点。")
            .defaultValue(200)
            .min(20)
            .sliderRange(20, 1200)
            .visible(() -> usesStandardMenuRoute())
            .build());

        targetAreaKeywords = grpSubserver.add(new StringListSetting.Builder()
            .name("目标区域关键词")
            .description("扫描侧边栏确认到达目标区域；列表可用加号和减号编辑。")
            .defaultValue(List.of("生存二区"))
            .visible(() -> usesStandardMenuRoute())
            .build());

        requireTargetKeyword = grpSubserver.add(new BoolSetting.Builder()
            .name("必须命中目标关键词")
            .description("开启后只有侧边栏命中目标区域关键词才确认到达。")
            .defaultValue(true)
            .visible(() -> usesStandardMenuRoute())
            .build());

        targetStableDelay = grpSubserver.add(new IntSetting.Builder()
            .name("到达稳定等待（tick）")
            .description("确认目标区域后等待多少 tick 再执行指令。")
            .defaultValue(60)
            .min(0)
            .sliderRange(0, 400)
            .visible(autoEnterSubserver::get)
            .build());

        subserverCommand = grpSubserver.add(new BoolSetting.Builder()
            .name("到达目标后执行指令")
            .description("确认进入目标区域并等待稳定后执行一次指令。")
            .defaultValue(false)
            .visible(autoEnterSubserver::get)
            .build());

        subserverCommandText = grpSubserver.add(new StringSetting.Builder()
            .name("目标区域指令")
            .description("进入目标区域后执行的指令，例如 /home。")
            .defaultValue("/home")
            .visible(() -> autoEnterSubserver.get() && subserverCommand.get())
            .build());

        subserverCommandDelay = grpSubserver.add(new IntSetting.Builder()
            .name("额外指令延迟（tick）")
            .description("到达稳定等待结束后额外等待多少 tick 再执行指令。")
            .defaultValue(60)
            .min(0)
            .sliderRange(0, 400)
            .visible(() -> autoEnterSubserver.get() && subserverCommand.get())
            .build());

        leyuanEnabled = grpLeyuan.add(new BoolSetting.Builder()
            .name("启用乐源服功能")
            .description("乐源服总开关：控制认证完成后的首次进服路线；关闭后仍可单独保留挂机区自动回服。")
            .defaultValue(true)
            .visible(() -> autoEnterSubserver.get() && serverEntryMode.get() == ServerEntryMode.LEYUAN_CUSTOM)
            .build());

        leyuanMenuTool = grpLeyuan.add(new ItemSetting.Builder()
            .name("乐源服菜单工具")
            .description("仅在快捷栏识别到该物品时，主城才会转向空气并右键打开菜单。")
            .defaultValue(Items.CLOCK)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanMenuItemKeywords = grpLeyuan.add(new StringListSetting.Builder()
            .name("菜单工具关键词")
            .description("菜单工具名称或 Lore 的辅助关键词。")
            .defaultValue(List.of("进入服务器", "菜单"))
            .visible(this::usesLeyuanMode)
            .build());

        leyuanLoginSurvivalKeyword = grpLeyuan.add(new StringSetting.Builder()
            .name("登录服按钮")
            .description("登录服菜单中传送到欢迎页的按钮关键词。")
            .defaultValue("生存服")
            .visible(this::usesLeyuanMode)
            .build());

        leyuanWelcomeEntryMode = grpLeyuan.add(new EnumSetting.Builder<LeyuanWelcomeEntryMode>()
            .name("欢迎页入口")
            .description("选择扫描乐源城入口，或点击欢迎菜单中可直达主城的无名书本。")
            .defaultValue(LeyuanWelcomeEntryMode.BOOK_DIRECT)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanWorldTransferKeyword = grpLeyuan.add(new StringSetting.Builder()
            .name("主城世界传送按钮")
            .description("到达主城后重新打开菜单并点击的按钮关键词。")
            .defaultValue("世界传送")
            .visible(this::usesLeyuanMode)
            .build());

        leyuanSurvivalFirstKeyword = grpLeyuan.add(new StringSetting.Builder()
            .name("第一层生存按钮")
            .description("世界传送页面第一层资源大区按钮关键词。")
            .defaultValue("资源大区")
            .visible(this::usesLeyuanMode)
            .build());

        leyuanSurvivalSecondKeyword = grpLeyuan.add(new StringSetting.Builder()
            .name("第二层生存按钮")
            .description("下一页面再次出现的资源大区按钮关键词。")
            .defaultValue("资源大区")
            .visible(this::usesLeyuanMode)
            .build());

        leyuanTargetServerKeyword = grpLeyuan.add(new StringSetting.Builder()
            .name("最终目标子服按钮")
            .description("最后选择资源一区或资源二区的按钮关键词。")
            .defaultValue("资源二区")
            .visible(this::usesLeyuanMode)
            .build());

        leyuanMainCityKeywords = grpLeyuan.add(new StringListSetting.Builder()
            .name("主城到达关键词")
            .description("欢迎页点击后仅通过侧边栏确认已到达主城，避免误把其他子服当作主城。")
            .defaultValue(List.of("当前位于主城大区", "主城大区"))
            .visible(this::usesLeyuanMode)
            .build());

        leyuanAfkAreaKeywords = grpLeyuan.add(new StringListSetting.Builder()
            .name("挂机区识别关键词")
            .description("侧边栏命中任一关键词时识别为挂机区；是否自动回服由「挂机区自动回服」单独控制。")
            .defaultValue(List.of("挂机区自动发放金币/经验"))
            .visible(this::usesLeyuanMode)
            .build());

        leyuanAfkRecoveryEnabled = grpLeyuan.add(new BoolSetting.Builder()
            .name("挂机区自动回服")
            .description("挂机区独立开关：检测到挂机区后执行返回主城大区、世界传送、资源大区、资源大区、资源二区；不受乐源服总开关影响。")
            .defaultValue(true)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanAfkMenuDelayMinutes = grpLeyuan.add(new IntSetting.Builder()
            .name("挂机区菜单延迟（分钟）")
            .description("检测到挂机区后等待多久再打开菜单，避免服务器重启后菜单尚未准备好。默认 10 分钟，设置为 0 立即打开。")
            .defaultValue(10)
            .min(0)
            .max(120)
            .sliderRange(0, 60)
            .visible(() -> usesLeyuanMode() && leyuanAfkRecoveryEnabled.get())
            .build());

        leyuanReturnMainCityKeywords = grpLeyuan.add(new StringListSetting.Builder()
            .name("返回主城按钮关键词")
            .description("挂机区 Shift＋F 菜单中返回主城大区按钮的关键词。")
            .defaultValue(List.of("返回主城大区"))
            .visible(this::usesLeyuanMode)
            .build());

        leyuanTargetAreaKeywords = grpLeyuan.add(new StringListSetting.Builder()
            .name("到达确认关键词")
            .description("最终进入目标子服后，侧边栏用于确认成功；应与最终目标子服按钮一致。")
            .defaultValue(List.of("资源一区", "资源二区", "生存一区", "生存二区"))
            .visible(this::usesLeyuanMode)
            .build());

        leyuanStepDelay = grpLeyuan.add(new IntSetting.Builder()
            .name("每步等待（tick）")
            .description("菜单打开、按钮点击和页面切换之间的等待时间。")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 120)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanStepTimeout = grpLeyuan.add(new IntSetting.Builder()
            .name("单步超时（tick）")
            .description("每个路线阶段的最长等待时间，超时后停止以避免乱点。")
            .defaultValue(600)
            .min(100)
            .sliderRange(100, 2400)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanCityMenuFallback = grpLeyuan.add(new BoolSetting.Builder()
            .name("主城钟快捷键兜底（Shift＋F）")
            .description("主城快捷栏未识别到钟时，直接尝试 Shift＋F 快捷菜单。")
            .defaultValue(true)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanCityMenuFallbackDelay = grpLeyuan.add(new IntSetting.Builder()
            .name("主城钟兜底等待（tick）")
            .description("已识别到钟但右键未打开菜单时，等待多久再触发 Shift＋F。20 tick = 1 秒。")
            .defaultValue(80)
            .min(20)
            .sliderRange(20, 400)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanRecoveryWaitMinutes = grpLeyuan.add(new IntSetting.Builder()
            .name("异常恢复等待上限（分钟）")
            .description("乐源路线断线后最多等待多久继续恢复当前步骤，按分钟设置，最大 30 分钟。")
            .defaultValue(20)
            .min(1)
            .max(30)
            .sliderRange(1, 30)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanRecoveryRetrySeconds = grpLeyuan.add(new IntSetting.Builder()
            .name("异常恢复初始重试（秒）")
            .description("乐源路线异常恢复的首次重试等待，后续按次数递增退避。")
            .defaultValue(30)
            .min(5)
            .max(300)
            .sliderRange(5, 300)
            .visible(this::usesLeyuanMode)
            .build());

        leyuanRecoveryMaxRetries = grpLeyuan.add(new IntSetting.Builder()
            .name("异常恢复最大重试次数")
            .description("乐源路线异常恢复期间允许的最多连接尝试次数。")
            .defaultValue(30)
            .min(1)
            .max(60)
            .sliderRange(1, 60)
            .visible(this::usesLeyuanMode)
            .build());

        // ── 5. 进入等待 ───────────────────────────────────────────────────────
        worldLoadWait = grpWorldLoad.add(new IntSetting.Builder()
            .name("世界加载等待（tick）")
            .description("进入服务器后等待多少 tick 再开始认证流程（20 tick = 1 秒）。")
            .defaultValue(200)
            .min(0)
            .sliderRange(0, 600)
            .visible(() -> autoLogin.get() || autoRegister.get() || noLoginDetection.get())
            .build());

        authDetectTimeout = grpWorldLoad.add(new IntSetting.Builder()
            .name("认证检测超时（tick）")
            .description("开始监听后等待多少 tick 仍未收到登录/注册提示，则判定该服务器无需登录，直接进入就绪状态（20 tick = 1 秒）。")
            .defaultValue(200)
            .min(20)
            .sliderRange(20, 1200)
            .visible(() -> autoLogin.get() || autoRegister.get() || noLoginDetection.get())
            .build());

        // ── 8. 正版检测 ───────────────────────────────────────────────────────
        autoCheckAccount = grpAccount.add(new BoolSetting.Builder()
            .name("进服时自动检测")
            .description("每次进入服务器后自动输出账号类型（正版 / 离线）；与自动登录、自动注册和自动指令相互独立。")
            .defaultValue(false)
            .build());

        // 用数组持有者避免 lambda 中引用未初始化的 final 字段
        @SuppressWarnings("unchecked")
        Setting<Boolean>[] checkHolder = new Setting[1];
        checkHolder[0] = grpAccount.add(new BoolSetting.Builder()
            .name("立即检测账号")
            .description("点击后立即在聊天栏输出当前账号的检测结果（不发送任何网络请求）。结果仅供参考，不参与登录判断。")
            .defaultValue(false)
            .onChanged(v -> {
                if (!v) return;
                checkHolder[0].set(false);
                notifyChat(mc, "§f§l" + AccountChecker.describe(mc));
            })
            .build());
        checkAccountNow = checkHolder[0];

        // ── 9. 高级设置 ───────────────────────────────────────────────────────
        debugMode = grpDebug.add(new BoolSetting.Builder()
            .name("调试模式")
            .description("在聊天栏输出当前状态、识别到的指令等调试信息。")
            .defaultValue(false)
            .build());
    }

    boolean usesStandardMenuRoute() {
        return autoEnterSubserver.get()
            && serverEntryMode.get() != ServerEntryMode.DIRECT
            && serverEntryMode.get() != ServerEntryMode.LEYUAN_CUSTOM;
    }

    boolean usesLeyuanRoute() {
        return autoEnterSubserver.get()
            && serverEntryMode.get() == ServerEntryMode.LEYUAN_CUSTOM
            && leyuanEnabled.get();
    }

    boolean usesLeyuanMode() {
        return autoEnterSubserver.get()
            && serverEntryMode.get() == ServerEntryMode.LEYUAN_CUSTOM;
    }

    /** 统一使用 yiyiaddon 前缀输出到聊天栏 */
    static void notifyChat(Minecraft mc, String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            YiyiaddonModule.formatMessage("自动登入", message)));
    }
}
