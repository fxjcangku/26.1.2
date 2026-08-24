package com.example.addon.core;

import com.example.addon.commands.CommandExample;
import com.example.addon.commands.NongChangCommand;
import com.example.addon.commands.YiyiaddonUpdateCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.AutoFarmMatrix;
import com.example.addon.modules.BaritoneCommandGuideModule;
import com.example.addon.modules.YiyiaddonTranslationModule;
import com.example.addon.tactical.TacticalBypass;
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
import org.slf4j.Logger;

import static com.example.addon.tactical.TacticalCategory.TACTICAL;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("§c§lyiyiaddon §a§l工具");
    public static final Category CATEGORY_AUTOMATION = new Category("§c§lyiyiaddon §e§l自动化");
    public static final HudGroup HUD_GROUP = new HudGroup("示例");

    @Override
    public void onInitialize() {
        LOG.info("Initializing yiyiaddon");

        // Modules - 工具类
        YiyiaddonTranslationModule translationModule = new YiyiaddonTranslationModule();
        Modules.get().add(translationModule);
        translationModule.enable();

        BaritoneCommandGuideModule baritoneCommandGuideModule = new BaritoneCommandGuideModule();
        Modules.get().add(baritoneCommandGuideModule);

        // Modules - 自动化类
        AutoFarmMatrix autoFarmMatrix = new AutoFarmMatrix();
        Modules.get().add(autoFarmMatrix);

        // Modules - 反作弊绕过（统一入口）
        Modules.get().add(new TacticalBypass());

        // Commands
        Commands.add(new CommandExample());
        Commands.add(new NongChangCommand());
        Commands.add(new YiyiaddonUpdateCommand());

        // HUD
        Hud.get().register(HudExample.INFO);

        YiyiaddonWelcomeService.register();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(CATEGORY_AUTOMATION);
        Modules.registerCategory(TACTICAL);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
