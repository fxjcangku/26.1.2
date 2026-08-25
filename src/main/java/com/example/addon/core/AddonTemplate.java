package com.example.addon.core;

import com.example.addon.commands.CommandExample;
import com.example.addon.commands.NongChangCommand;
import com.example.addon.commands.YiyiaddonUpdateCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.AutoFarmMatrix;
import com.example.addon.modules.BaritoneCommandGuideModule;
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
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("§c§lyiyiaddon §a§l工具");
    public static final Category CATEGORY_AUTOMATION = new Category("§c§lyiyiaddon §e§l自动化");
    public static final Category CATEGORY_TACTICAL = new Category("§c§lyiyiaddon §d§l绕过");
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

        // Modules - 反作弊绕过（三个独立模块）
        Modules.get().add(new FlightBypass());
        Modules.get().add(new AntiKickBypass());
        Modules.get().add(new ServerDetector());

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
        Modules.registerCategory(CATEGORY_TACTICAL);
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
