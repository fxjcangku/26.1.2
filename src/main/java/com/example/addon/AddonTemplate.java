package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.commands.NongChangCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.AutoFarmMatrix;
import com.example.addon.modules.BaritoneCommandGuideModule;
import com.example.addon.modules.YiyiaddonTranslationModule;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("§c§lyiyiaddon §a§l工具");
    public static final HudGroup HUD_GROUP = new HudGroup("示例");

    @Override
    public void onInitialize() {
        LOG.info("Initializing yiyiaddon");

        // Modules
        YiyiaddonTranslationModule translationModule = new YiyiaddonTranslationModule();
        Modules.get().add(translationModule);
        translationModule.enable();

        BaritoneCommandGuideModule baritoneCommandGuideModule = new BaritoneCommandGuideModule();
        Modules.get().add(baritoneCommandGuideModule);

        AutoFarmMatrix autoFarmMatrix = new AutoFarmMatrix();
        Modules.get().add(autoFarmMatrix);

        // Commands
        Commands.add(new CommandExample());
        Commands.add(new NongChangCommand());

        // HUD
        Hud.get().register(HudExample.INFO);

        YiyiaddonWelcomeService.register();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
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
