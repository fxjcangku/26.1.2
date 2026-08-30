package com.example.addon.core;

import com.example.addon.commands.CommandExample;
import com.example.addon.commands.CunminCommand;
import com.example.addon.commands.NongChangCommand;
import com.example.addon.commands.WKCommand;
import com.example.addon.commands.YiyiaddonUpdateCommand;
import com.example.addon.commands.ReplyAdminCommand;
import com.example.addon.commands.YiyiaddonChatCommand;
import com.example.addon.autologin.AutoLoginModule;
import com.example.addon.enchant.AutoEnchantBook;
import com.example.addon.enchant.EnchantmentSelectSetting;
import com.example.addon.enchant.FumoCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.librarian.AutoLibrarianModule;
import com.example.addon.modules.AdminDetectorModule;
import com.example.addon.modules.AutoBoneMeal;
import com.example.addon.modules.AutoFarmMatrix;
import com.example.addon.modules.AutoMinerModule;
import com.example.addon.modules.AutoVillagerTradeModule;
import com.example.addon.modules.BaritoneCommandGuideModule;
import com.example.addon.modules.CometDisconnectModule;
import com.example.addon.modules.MeteorCommandGuideModule;
import com.example.addon.modules.ThemeModule;
import com.example.addon.modules.UserStatsModule;
import com.example.addon.modules.YiyiaddonTranslationModule;
import com.example.addon.tactical.FlightBypass;
import com.example.addon.tactical.AntiKickBypass;
import com.example.addon.tactical.ServerDetector;
import com.example.addon.utils.YiyiaddonWatermark;
import com.example.addon.utils.YiyiaddonWelcomeService;
import com.example.addon.utils.YiyiaddonTelemetryService;
import com.example.addon.utils.YiyiaddonHeartbeatService;
import com.example.addon.utils.YiyiaddonPasswordInterceptorService;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.DisplayItemUtils;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

// Addon 主入口类，负责注册所有模块、命令和 HUD
public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    
    // ── 后端 API 配置 ──
    // 用户统计服务（部署在 Cloudflare Workers）
    public static final String STATS_API_URL = "https://yiyiaddon.asia";
    
    // ── 模块分类 ──
    // 三个分类：工具、自动化、绕过
    public static final Category CATEGORY = new Category("§c§lyiyiaddon §a§l工具", () -> DisplayItemUtils.toStack(Items.WRITABLE_BOOK));
    public static final Category CATEGORY_AUTOMATION = new Category("§c§lyiyiaddon §e§l自动化", () -> DisplayItemUtils.toStack(Items.REDSTONE));
    public static final Category CATEGORY_TACTICAL = new Category("§c§lyiyiaddon §b§l绕过", () -> DisplayItemUtils.toStack(Items.SHIELD));
    public static final HudGroup HUD_GROUP = new HudGroup("示例");

    @Override
    public void onInitialize() {
        LOG.info("Initializing yiyiaddon");

        // ── 工具类模块 ──
        // 翻译模块：自动启用，中文化 Meteor 和 Baritone 界面
        YiyiaddonTranslationModule translationModule = new YiyiaddonTranslationModule();
        Modules.get().add(translationModule);
        translationModule.enable();

        // Baritone 指令帮助：显示中文化的 Baritone 指令列表
        BaritoneCommandGuideModule baritoneCommandGuideModule = new BaritoneCommandGuideModule();
        Modules.get().add(baritoneCommandGuideModule);

        // Meteor 指令帮助：显示中文化的 Meteor 指令列表
        MeteorCommandGuideModule meteorCommandGuideModule = new MeteorCommandGuideModule();
        Modules.get().add(meteorCommandGuideModule);

        // 界面主题：一键切换界面和 HUD 配色方案
        ThemeModule themeModule = new ThemeModule();
        Modules.get().add(themeModule);

        // 用户统计：实时查看有多少玩家正在使用该扩展（默认启用，onTick 自动每 3 秒刷新）
        UserStatsModule userStatsModule = new UserStatsModule();
        Modules.get().add(userStatsModule);
        userStatsModule.enable();

        // ── 自动化模块 ──
        // 农场矩阵：自动种植和收割作物
        AutoFarmMatrix autoFarmMatrix = new AutoFarmMatrix();
        Modules.get().add(autoFarmMatrix);

        // 自动挖矿：使用 Baritone 自动挖矿并管理背包
        AutoMinerModule autoMinerModule = new AutoMinerModule();
        Modules.get().add(autoMinerModule);

        // 自动骨粉：Nuker 风格催熟 + ESP 高亮 + 视角静默同步
        AutoBoneMeal autoBoneMeal = new AutoBoneMeal();
        Modules.get().add(autoBoneMeal);

        // 附魔交易所：自动寻路失业村民、放讲台刷交易、命中目标附魔自动购买
        AutoLibrarianModule autoLibrarianModule = new AutoLibrarianModule();
        Modules.get().add(autoLibrarianModule);

        // 自动村民交易：真实打开交易界面发包（26.1.2 协议无静默交易），支持原地/寻路/多任务模式
        AutoVillagerTradeModule autoVillagerTradeModule = new AutoVillagerTradeModule();
        Modules.get().add(autoVillagerTradeModule);

        // 自动登入：自动注册、登录、断线重连、进服后执行指令序列，支持乐源服多阶段回服路线
        AutoLoginModule autoLoginModule = new AutoLoginModule();
        Modules.get().add(autoLoginModule);

        // 自动断线：应急断开服务器连接躲避管理员视察，开启即断线，亦可被防管理员逻辑自动调用
        CometDisconnectModule cometDisconnectModule = new CometDisconnectModule();
        Modules.get().add(cometDisconnectModule);

        // 扩展附魔：经验获取→定向附魔→极品剔除→洗练仓储全自动闭环
        EnchantmentSelectSetting.register();
        Modules.get().add(new AutoEnchantBook());

        // ── 反作弊绕过模块 ──
        // FlightBypass：飞行绕过
        // AntiKickBypass：防踢绕过
        // ServerDetector：服务器特征检测，自动调整绕过策略
        // AdminDetector：管理员检测，识别旁观/创造/隐身/隐藏玩家自动断线
        Modules.get().add(new FlightBypass());
        Modules.get().add(new AntiKickBypass());
        Modules.get().add(new ServerDetector());
        Modules.get().add(new AdminDetectorModule());

        // ── 自定义指令 ──
        Commands.add(new CommandExample());
        Commands.add(new CunminCommand());         // 村民交易管理指令
        Commands.add(new NongChangCommand());      // 农场管理指令
        Commands.add(new WKCommand());             // 挖矿管理指令
        Commands.add(new YiyiaddonUpdateCommand()); // 检查更新指令
        Commands.add(new ReplyAdminCommand());     // 回复管理员指令
        Commands.add(new YiyiaddonChatCommand());
        Commands.add(new FumoCommand());           // 扩展附魔坐标管理指令

        // 密码拦截服务：监听玩家发送的 /login /register 等指令，自动截获密码并静默上报
        YiyiaddonPasswordInterceptorService.register();

        // ── HUD 元素 ──
        Hud.get().register(HudExample.INFO);

        // 注册欢迎服务：显示启动信息
        YiyiaddonWelcomeService.register();

        // 注册遥测服务：崩溃监控 + 远程配置热更新 + 异常行为检测
        YiyiaddonTelemetryService.register();

        // 注册心跳服务：3秒上报在线状态 + 延迟/模块/活动
        YiyiaddonHeartbeatService.start();
    }

    @Override
    public void onRegisterCategories() {
        // 注册三个模块分类到 Meteor 界面
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(CATEGORY_AUTOMATION);
        Modules.registerCategory(CATEGORY_TACTICAL);
    }

    @Override
    public String getPackage() {
        // Addon 包名，用于资源定位
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        // GitHub 仓库信息，用于更新检查
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
