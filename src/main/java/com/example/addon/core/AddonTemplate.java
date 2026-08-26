package com.example.addon.core;

import com.example.addon.commands.CommandExample;
import com.example.addon.commands.NongChangCommand;
import com.example.addon.commands.WKCommand;
import com.example.addon.commands.YiyiaddonUpdateCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.AutoFarmMatrix;
import com.example.addon.modules.AutoMinerModule;
import com.example.addon.modules.BaritoneCommandGuideModule;
import com.example.addon.modules.MeteorCommandGuideModule;
import com.example.addon.modules.PinkThemeModule;
import com.example.addon.modules.YiyiaddonTranslationModule;
import com.example.addon.tactical.FlightBypass;
import com.example.addon.tactical.AntiKickBypass;
import com.example.addon.tactical.ServerDetector;
import com.example.addon.utils.YiyiaddonWatermark;
import com.example.addon.utils.YiyiaddonWelcomeService;
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

        // 粉色主题：应用粉色配色方案
        PinkThemeModule pinkThemeModule = new PinkThemeModule();
        Modules.get().add(pinkThemeModule);

        // ── 自动化模块 ──
        // 农场矩阵：自动种植和收割作物
        AutoFarmMatrix autoFarmMatrix = new AutoFarmMatrix();
        Modules.get().add(autoFarmMatrix);

        // 自动挖矿：使用 Baritone 自动挖矿并管理背包
        AutoMinerModule autoMinerModule = new AutoMinerModule();
        Modules.get().add(autoMinerModule);

        // ── 反作弊绕过模块 ──
        // FlightBypass：飞行绕过
        // AntiKickBypass：防踢绕过
        // ServerDetector：服务器特征检测，自动调整绕过策略
        Modules.get().add(new FlightBypass());
        Modules.get().add(new AntiKickBypass());
        Modules.get().add(new ServerDetector());

        // ── 自定义指令 ──
        Commands.add(new CommandExample());
        Commands.add(new NongChangCommand());      // 农场管理指令
        Commands.add(new WKCommand());             // 挖矿管理指令
        Commands.add(new YiyiaddonUpdateCommand()); // 检查更新指令

        // ── HUD 元素 ──
        Hud.get().register(HudExample.INFO);

        // 注册欢迎服务：显示启动信息
        YiyiaddonWelcomeService.register();
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
