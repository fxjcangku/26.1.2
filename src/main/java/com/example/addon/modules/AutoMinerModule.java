package com.example.addon.modules;

import com.example.addon.ui.HelpScreen;

import com.example.addon.commands.WKCommand;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mining.*;
import com.example.addon.translations.BaritoneChatTranslations;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Set;

/**
 * AutoMiner Matrix - 全自动挖矿矩阵模块
 * 
 * 核心能力：
 * · Baritone 驱动采掘 - 单目标矿石锁定，深层变种自动支持
 * · 状态机全生命周期管理 - 前往挖矿/返回卸货/前往补给/前往修复点修补/死亡返回
 * · 物流自动化 - 返回卸货箱倒产物，前往食物箱拿食物，垃圾丢弃
 * · 耐久修补 - 联动 KillAura 打怪修装备
 * · 死亡自愈 - 自动复活+死亡返回挂机修复点
 * · 防卡死 - 区块加载检测、掉落虚空检测、指令延迟校验
 */
public final class AutoMinerModule extends YiyiaddonModule {

    private final MinerFSM fsm = new MinerFSM(this);
    private final BaritoneExecutor baritone = new BaritoneExecutor(this);
    private final ContainerHelper container = new ContainerHelper(this);
    private final CommandManager cmdManager = new CommandManager(this);
    private final OrePredictor orePredictor = new OrePredictor();
    private final SoundNotifier soundNotifier = new SoundNotifier();

    // ═══════════════════════════════════════════════════════════════════
    //  UI 配置面板 - 人性化分组
    // ═══════════════════════════════════════════════════════════════════

    private final SettingGroup sgTarget = settings.createGroup("目标选择", true);
    private final SettingGroup sgCommand = settings.createGroup("传送指令", false);
    private final SettingGroup sgThreshold = settings.createGroup("触发条件", false);
    private final SettingGroup sgItems = settings.createGroup("物品管理", false);
    private final SettingGroup sgBaritone = settings.createGroup("Baritone调优", false);

    // ─── 目标选择（互斥） ───
    private final Setting<Block> overworldOreTarget;
    private final Setting<Block> netherOreTarget;
    private final Setting<Block> blockTarget;

    // ─── 种子挖矿 ───
    private final Setting<Boolean> seedMiningEnabled;
    private final Setting<String> worldSeed;
    private final Setting<Integer> renderRange;
    private final Setting<SettingColor> oreRenderColor;
    private final Setting<Boolean> pulseEffect;
    
    // 假矿检测结果缓存
    private boolean lastCheckFoundFake = false;

    // ─── 指令配置 ───
    private final Setting<String> wildCommand;
    private final Setting<Boolean> rtpGuiEnabled;
    private final Setting<String> rtpGuiKeyword;
    private final Setting<String> unloadCommand;
    private final Setting<String> supplyCommand;
    private final Setting<String> afkCommand;
    private final Setting<String> respawnCommand;

    // ─── 阈值设置 ───
    private final Setting<Integer> unloadThreshold;
    private final Setting<Integer> hungerThreshold;
    private final Setting<Integer> durabilityThreshold;
    private final Setting<Integer> teleportDelay;

    // ─── 垃圾丢弃 ───
    private final Setting<List<Block>> trashList;
    
    // ─── 食物白名单 ───
    private final Setting<List<Item>> foodWhitelist;
    
    // ─── 搭路方块白名单 ───
    private final BlockListSetting placeBlocks;

    // ─── Baritone 设置 ───
    private final Setting<Boolean> avoidLava;
    private final Setting<Boolean> mobAvoidance;
    private final Setting<Integer> mobAvoidanceRadius;
    private final Setting<Boolean> allowBreak;
    private final Setting<Boolean> allowPlace;
    private final Setting<Integer> maxFallHeight;
    private final Setting<Boolean> pauseMiningForFallingBlocks;
    private final Setting<Boolean> allowInventory;
    private final Setting<Boolean> autoTool;
    private final Setting<Boolean> sprintAscends;
    private final Setting<Boolean> allowParkour;
    private final Setting<Boolean> allowParkourPlace;
    private final Setting<Boolean> allowDiagonalAscend;
    private final Setting<Boolean> allowDiagonalDescend;
    private final Setting<Boolean> allowOnlyExposedOres;
    private final Setting<Integer> allowOnlyExposedOresDistance;
    private final Setting<Integer> minYLevelWhileMining;
    private final Setting<Integer> maxYLevelWhileMining;
    private final Setting<Integer> mineGoalUpdateInterval;
    private final Setting<Integer> mineMaxOreLocationsCount;
    private final Setting<Boolean> blacklistClosestOnFailure;
    private final Setting<Boolean> legitMine;
    private final Setting<Integer> legitMineYLevel;
    private final Setting<Boolean> legitMineIncludeDiagonals;

    // ─── 显示设置（私有字段，不在界面显示） ───
    private final Setting<Double> espScale;
    private final Setting<SettingColor> mineralChestColor;
    private final Setting<SettingColor> foodChestColor;
    private final Setting<SettingColor> afkPointColor;

    public AutoMinerModule() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动挖矿",
            "Baritone驱动全自动挖矿，物流循环，耐久修补，死亡自愈。详细参考下面使用说明。");

        // ═══════════════════════════════════════════════════════════
        //  目标选择 - 挖什么
        // ═══════════════════════════════════════════════════════════
        
        overworldOreTarget = sgTarget.add(new BlockSetting.Builder()
            .name("主世界矿石")
            .description("选择主世界矿石（含深层变种）")
            .defaultValue(Blocks.AIR)
            .filter(block -> {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                return id.contains("_ore") && !id.contains("nether") && !id.contains("ancient");
            })
            .build());

        netherOreTarget = sgTarget.add(new BlockSetting.Builder()
            .name("下界矿石")
            .description("选择下界矿石（下界金矿、下界石英矿、远古残骸）")
            .defaultValue(Blocks.AIR)
            .filter(block -> {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                return id.contains("nether") && (id.contains("ore") || id.contains("quartz")) 
                    || id.contains("ancient_debris");
            })
            .build());

        blockTarget = sgTarget.add(new BlockSetting.Builder()
            .name("普通方块")
            .description("选择普通方块（石头、泥土、原木等）")
            .defaultValue(Blocks.AIR)
            .build());

        // ═══════════════════════════════════════════════════════════
        //  传送指令 - 去哪里
        // ═══════════════════════════════════════════════════════════
        
        wildCommand = sgCommand.add(new StringSetting.Builder()
            .name("前往挖矿指令")
            .description("传送到挖矿区域的指令（支持带/或不带/）")
            .defaultValue("")
            .build());

        rtpGuiEnabled = sgCommand.add(new BoolSetting.Builder()
            .name("RTP需要GUI选择")
            .description("指令后自动扫描GUI点击匹配按钮")
            .defaultValue(false)
            .build());

        rtpGuiKeyword = sgCommand.add(new StringSetting.Builder()
            .name("GUI按钮关键词")
            .description("输入纯文本（如'主世界'会匹配'§a主 §e世 §b界'），自动忽略颜色和空格")
            .defaultValue("主世界")
            .visible(rtpGuiEnabled::get)
            .build());

        unloadCommand = sgCommand.add(new StringSetting.Builder()
            .name("返回卸货指令")
            .description("传送到卸货箱的指令")
            .defaultValue("")
            .build());

        supplyCommand = sgCommand.add(new StringSetting.Builder()
            .name("前往补给指令")
            .description("传送到食物箱的指令")
            .defaultValue("")
            .build());

        afkCommand = sgCommand.add(new StringSetting.Builder()
            .name("前往修复指令")
            .description("传送到挂机修补点")
            .defaultValue("")
            .build());

        respawnCommand = sgCommand.add(new StringSetting.Builder()
            .name("死亡返回指令")
            .description("复活后返回挂机点")
            .defaultValue("")
            .build());

        teleportDelay = sgCommand.add(new IntSetting.Builder()
            .name("传送等待时长")
            .description("执行传送指令后等待秒数")
            .defaultValue(8)
            .min(1)
            .sliderMax(30)
            .build());

        // ═══════════════════════════════════════════════════════════
        //  触发条件 - 什么时候做什么
        // ═══════════════════════════════════════════════════════════
        
        unloadThreshold = sgThreshold.add(new IntSetting.Builder()
            .name("满载组数")
            .description("背包矿物达到多少组时触发卸货")
            .defaultValue(20)
            .min(1)
            .sliderMax(36)
            .build());

        hungerThreshold = sgThreshold.add(new IntSetting.Builder()
            .name("食物阈值")
            .description("背包食物少于此数量时触发补给")
            .defaultValue(32)
            .min(1)
            .sliderMax(64)
            .build());

        durabilityThreshold = sgThreshold.add(new IntSetting.Builder()
            .name("耐久阈值")
            .description("工具剩余耐久低于此值时前往挂机点修补")
            .defaultValue(100)
            .min(1)
            .sliderMax(500)
            .build());

        // ═══════════════════════════════════════════════════════════
        //  物品管理 - 拿什么扔什么
        // ═══════════════════════════════════════════════════════════
        
        trashList = sgItems.add(new BlockListSetting.Builder()
            .name("垃圾丢弃名单")
            .description("挖矿时自动丢弃这些方块（点击选择添加，推荐：圆石、石头、泥土、闪长岩、花岗岩、安山岩等）")
            .defaultValue(List.of(
                Blocks.COBBLESTONE,
                Blocks.COBBLED_DEEPSLATE,
                Blocks.STONE,
                Blocks.DEEPSLATE,
                Blocks.DIRT,
                Blocks.GRAVEL,
                Blocks.DIORITE,
                Blocks.GRANITE,
                Blocks.ANDESITE,
                Blocks.TUFF
            ))
            .build());

        foodWhitelist = sgItems.add(new ItemListSetting.Builder()
            .name("食物白名单")
            .description("从食物箱只拿这些食物（推荐：熟牛肉、熟猪排、金胡萝卜、面包）")
            .defaultValue(List.of(
                Items.COOKED_BEEF,
                Items.COOKED_PORKCHOP,
                Items.GOLDEN_CARROT,
                Items.BREAD
            ))
            .filter(item -> {
                ItemStack stack = new ItemStack(item);
                return stack.has(DataComponents.FOOD);
            })
            .build());

        placeBlocks = sgItems.add(new BlockListSetting.Builder()
            .name("搭路方块白名单")
            .description("Baritone搭桥/填坑时使用这些方块（点击选择添加，推荐：圆石、深层圆石、泥土、石头等）")
            .defaultValue(List.of(
                Blocks.COBBLESTONE,
                Blocks.COBBLED_DEEPSLATE,
                Blocks.DIRT,
                Blocks.STONE,
                Blocks.NETHERRACK
            ))
            .onChanged(blocks -> baritone.updatePlaceBlocks(blocks))
            .build());

        // ═══════════════════════════════════════════════════════════
        //  Baritone调优 - 寻路与挖掘参数
        // ═══════════════════════════════════════════════════════════
        
        // ─── 种子挖矿 ───
        seedMiningEnabled = sgBaritone.add(new BoolSetting.Builder()
            .name("启用种子挖矿")
            .description("根据世界种子预测真实矿石位置，只挖真矿，无视假矿")
            .defaultValue(false)
            .onChanged(value -> {
                if (value) {
                    updateOrePredictor();
                } else {
                    orePredictor.invalidateCache();
                }
            })
            .build());

        worldSeed = sgBaritone.add(new StringSetting.Builder()
            .name("世界种子")
            .description("服务器的世界种子（Long类型，支持负数，如 -8913466909937400889）")
            .defaultValue("")
            .visible(seedMiningEnabled::get)
            .onChanged(s -> updateOrePredictor())
            .build());

        renderRange = sgBaritone.add(new IntSetting.Builder()
            .name("渲染范围")
            .description("渲染玩家周围多少格内的预测矿石")
            .defaultValue(128)
            .min(32)
            .sliderMax(256)
            .visible(seedMiningEnabled::get)
            .build());

        oreRenderColor = sgBaritone.add(new ColorSetting.Builder()
            .name("矿石渲染颜色")
            .description("预测矿石方块框线的颜色")
            .defaultValue(new SettingColor(255, 215, 0, 180))
            .visible(seedMiningEnabled::get)
            .build());

        pulseEffect = sgBaritone.add(new BoolSetting.Builder()
            .name("脉冲效果")
            .description("矿石框线启用呼吸灯效果")
            .defaultValue(true)
            .visible(seedMiningEnabled::get)
            .build());

        // ─── Baritone参数 ───
        
        // ────────────── 开关类设置 ──────────────
        allowBreak = sgBaritone.add(new BoolSetting.Builder()
            .name("破坏阻挡方块")
            .description("允许破坏阻挡路径的方块（石头、泥土等）")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("allowBreak", value))
            .build());

        allowPlace = sgBaritone.add(new BoolSetting.Builder()
            .name("放置方块")
            .description("允许搭桥或填坑（需要背包里有方块）")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("allowPlace", value))
            .build());

        allowInventory = sgBaritone.add(new BoolSetting.Builder()
            .name("自动整理物品栏")
            .description("允许Baritone自动将物品从背包移到快捷栏（工具、方块等）")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("allowInventory", value))
            .build());

        autoTool = sgBaritone.add(new BoolSetting.Builder()
            .name("自动切换工具")
            .description("挖掘时自动选择最佳工具（镐子挖石头、铲子挖土等）")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("autoTool", value))
            .build());

        avoidLava = sgBaritone.add(new BoolSetting.Builder()
            .name("避开岩浆")
            .description("禁止 Baritone 将岩浆作为正常寻路路径")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("avoidLava", value))
            .build());

        mobAvoidance = sgBaritone.add(new BoolSetting.Builder()
            .name("怪物规避")
            .description("提高怪物附近路径代价，尽量绕开危险区域")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("avoidance", value))
            .build());

        pauseMiningForFallingBlocks = sgBaritone.add(new BoolSetting.Builder()
            .name("掉落方块暂停")
            .description("遇到沙子、沙砾等掉落方块时暂停挖掘")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("pauseMiningForFallingBlocks", value))
            .build());

        sprintAscends = sgBaritone.add(new BoolSetting.Builder()
            .name("疾跑上坡")
            .description("上坡时提前一格疾跑+跳跃，提升速度")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("sprintAscends", value))
            .build());

        allowParkour = sgBaritone.add(new BoolSetting.Builder()
            .name("允许跑酷")
            .description("允许跨越1-4格的跑酷跳跃（有一定风险）")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowParkour", value))
            .build());

        allowParkourPlace = sgBaritone.add(new BoolSetting.Builder()
            .name("跑酷搭桥")
            .description("跑酷跳跃中途放置方块来延长距离（需开启放置方块）")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowParkourPlace", value))
            .build());

        allowDiagonalAscend = sgBaritone.add(new BoolSetting.Builder()
            .name("对角线上升")
            .description("允许斜向上跳跃，速度更快但消耗更多饥饿值")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowDiagonalAscend", value))
            .build());

        allowDiagonalDescend = sgBaritone.add(new BoolSetting.Builder()
            .name("对角线下降")
            .description("允许斜向下降，速度更快但有一定风险（地狱慎用）")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowDiagonalDescend", value))
            .build());

        allowOnlyExposedOres = sgBaritone.add(new BoolSetting.Builder()
            .name("仅挖暴露矿石")
            .description("只挖掘能从指定距离看到的矿石，减少无效挖掘")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowOnlyExposedOres", value))
            .build());

        blacklistClosestOnFailure = sgBaritone.add(new BoolSetting.Builder()
            .name("失败目标暂时跳过")
            .description("矿点无法到达时跳过最近目标，避免反复卡住")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("blacklistClosestOnFailure", value))
            .build());

        legitMine = sgBaritone.add(new BoolSetting.Builder()
            .name("合法挖掘模式")
            .description("启用合法挖掘限制（关闭可提启效率但可能被检测）")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("legitMine", value))
            .build());

        legitMineIncludeDiagonals = sgBaritone.add(new BoolSetting.Builder()
            .name("合法挖掘检测对角矿石")
            .description("合法挖掘时检测与已发现矿石对角相邻的矿石")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("legitMineIncludeDiagonals", value))
            .build());

        // ────────────── 滑块类设置 ──────────────
        mineGoalUpdateInterval = sgBaritone.add(new IntSetting.Builder()
            .name("矿点刷新间隔")
            .description("每隔多少tick重新扫描矿点（值越小越优先挖近矿，默认10tick约0.5秒）")
            .defaultValue(10)
            .min(1)
            .sliderMax(100)
            .onChanged(value -> baritone.updateSetting("mineGoalUpdateInterval", value))
            .build());

        mineMaxOreLocationsCount = sgBaritone.add(new IntSetting.Builder()
            .name("矿点缓存数量")
            .description("Baritone一次缓存的最大矿点数量")
            .defaultValue(128)
            .min(1)
            .sliderMax(256)
            .onChanged(value -> baritone.updateSetting("mineMaxOreLocationsCount", value))
            .build());

        mobAvoidanceRadius = sgBaritone.add(new IntSetting.Builder()
            .name("怪物规避半径")
            .description("计算怪物危险区域的半径")
            .defaultValue(8)
            .min(1)
            .sliderMax(16)
            .onChanged(value -> baritone.updateSetting("mobAvoidanceRadius", value))
            .visible(mobAvoidance::get)
            .build());

        maxFallHeight = sgBaritone.add(new IntSetting.Builder()
            .name("最大坠落高度")
            .description("允许从多高的地方跳下（超过会绕路）")
            .defaultValue(3)
            .min(0)
            .sliderMax(20)
            .onChanged(value -> baritone.updateSetting("maxFallHeightNoWater", value))
            .build());

        allowOnlyExposedOresDistance = sgBaritone.add(new IntSetting.Builder()
            .name("暴露矿石检测距离")
            .description("判断矿石是否暴露时使用的检测距离")
            .defaultValue(1)
            .min(1)
            .sliderMax(8)
            .onChanged(value -> baritone.updateSetting("allowOnlyExposedOresDistance", value))
            .visible(allowOnlyExposedOres::get)
            .build());

        minYLevelWhileMining = sgBaritone.add(new IntSetting.Builder()
            .name("最低挖掘高度")
            .description("Baritone 挖矿时不会低于此高度")
            .defaultValue(-64)
            .min(-64)
            .sliderMax(320)
            .onChanged(value -> baritone.updateSetting("minYLevelWhileMining", value))
            .build());

        maxYLevelWhileMining = sgBaritone.add(new IntSetting.Builder()
            .name("最高挖掘高度")
            .description("Baritone 挖矿时不会高于此高度")
            .defaultValue(320)
            .min(-64)
            .sliderMax(320)
            .onChanged(value -> baritone.updateSetting("maxYLevelWhileMining", value))
            .build());

        legitMineYLevel = sgBaritone.add(new IntSetting.Builder()
            .name("合法挖掘高度")
            .description("合法挖掘模式进行条带探索时使用的高度")
            .defaultValue(12)
            .min(-64)
            .sliderMax(320)
            .onChanged(value -> baritone.updateSetting("legitMineYLevel", value))
            .visible(legitMine::get)
            .build());

        // ═══════════════════════════════════════════════════════════
        //  语音播报和可视化设置（代码默认值，不显示在界面）
        // ═══════════════════════════════════════════════════════════
        
        // 语音播报默认启用，音量1.0
        soundNotifier.setEnabled(true);
        soundNotifier.setVolume(1.0f);
        
        // ESP字体大小默认2.0（两倍），不添加到界面
        espScale = settings.getDefaultGroup().add(new DoubleSetting.Builder()
            .name("_esp_scale_internal")
            .description("")
            .defaultValue(2.0)
            .visible(() -> false)
            .build());
        
        // ESP颜色默认值，不添加到界面
        mineralChestColor = settings.getDefaultGroup().add(new ColorSetting.Builder()
            .name("_mineral_color_internal")
            .description("")
            .defaultValue(new SettingColor(255, 215, 0))
            .visible(() -> false)
            .build());
            
        foodChestColor = settings.getDefaultGroup().add(new ColorSetting.Builder()
            .name("_food_color_internal")
            .description("")
            .defaultValue(new SettingColor(100, 255, 100))
            .visible(() -> false)
            .build());
            
        afkPointColor = settings.getDefaultGroup().add(new ColorSetting.Builder()
            .name("_afk_color_internal")
            .description("")
            .defaultValue(new SettingColor(255, 100, 255))
            .visible(() -> false)
            .build());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  模块生命周期
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        // 重新加载当前服务器的点位配置
        WKCommand.reloadForCurrentServer();
        
        // 启动自检：缺项一次列全，配好一项下次就少一条
        if (!reportSelfCheck(selfCheck())) return;

        // 更新种子预测器
        updateOrePredictor();

        // 应用 Baritone 设置
        baritone.applySettings(
            avoidLava.get(),
            mobAvoidance.get(),
            mobAvoidanceRadius.get(),
            allowBreak.get(),
            allowPlace.get(),
            maxFallHeight.get(),
            pauseMiningForFallingBlocks.get(),
            allowInventory.get(),
            autoTool.get(),
            sprintAscends.get(),
            allowParkour.get(),
            allowParkourPlace.get(),
            allowDiagonalAscend.get(),
            allowDiagonalDescend.get(),
            allowOnlyExposedOres.get(),
            allowOnlyExposedOresDistance.get(),
            minYLevelWhileMining.get(),
            maxYLevelWhileMining.get(),
            mineMaxOreLocationsCount.get(),
            blacklistClosestOnFailure.get(),
            legitMine.get(),
            legitMineYLevel.get(),
            legitMineIncludeDiagonals.get()
        );

        // 重置状态机
        fsm.reset();
        container.reset();
        cmdManager.reset();

        reportStartupInfo();
    }

    /**
     * 启动播报：让用户一眼确认这次跑的是什么配置
     *
     * 只报会影响本次结果的关键项（目标矿、种子模式、阈值），
     * 不把整个设置面板念一遍，否则聊天栏刷屏反而看不清。
     * 
     * 维度不匹配时不仅警告，还会直接停止模块，避免浪费时间挖空气。
     */
    private void reportStartupInfo() {
        notify("§a已启动，开始挖矿循环");

        // 当前维度
        String dimension = getDimensionName();
        notify("§f当前维度：" + highlightText(dimension));

        // 目标矿物
        Block target = getTargetBlock();
        String targetName = BaritoneChatTranslations.translateBlockId(
            BuiltInRegistries.BLOCK.getKey(target).toString());
        notify("§f目标矿物：" + highlightText(targetName));

        // 维度匹配检查（末地什么矿都没有，主世界矿/下界矿要对应维度）
        if (mc.level != null) {
            ResourceKey<Level> dim = mc.level.dimension();
            Block overworld = overworldOreTarget.get();
            Block nether = netherOreTarget.get();
            boolean isOverworldOre = overworld != null && !overworld.equals(Blocks.AIR);
            boolean isNetherOre = nether != null && !nether.equals(Blocks.AIR);

            // 末地没有任何矿石
            if (dim == Level.END) {
                notifyError("§c§l末地没有任何矿石，换个维度再启动！");
                toggle();  // 直接停止模块
                return;
            }
            // 选了主世界矿但在下界
            else if (isOverworldOre && dim == Level.NETHER) {
                notifyError("§c§l选了主世界矿但在下界，传送到主世界再启动！");
                toggle();  // 直接停止模块
                return;
            }
            // 选了下界矿但在主世界
            else if (isNetherOre && dim == Level.OVERWORLD) {
                notifyError("§c§l选了下界矿但在主世界，传送到下界再启动！");
                toggle();  // 直接停止模块
                return;
            }
        }

        // 挖矿模式
        if (seedMiningEnabled.get()) {
            notify("§f挖矿模式：" + highlightText("种子模式") + "§f（只挖预测真矿，无视假矿）");
            notify("§f世界种子：" + highlightServer(worldSeed.get().trim()));
            notify("§f渲染范围：" + highlightText(renderRange.get() + " 格"));
        } else {
            notify("§f挖矿模式：" + highlightText("普通模式") + "§f（挖视野内所有目标矿）");
        }

        // 触发阈值
        notify("§f触发阈值：满载 " + highlightText(unloadThreshold.get() + " 组")
            + "§f · 饥饿 " + highlightText(String.valueOf(hungerThreshold.get()))
            + "§f · 耐久 " + highlightText(String.valueOf(durabilityThreshold.get())));
    }

    /**
     * 获取当前维度中文名
     */
    private String getDimensionName() {
        if (mc.level == null) return "未知";
        ResourceKey<Level> dim = mc.level.dimension();
        if (dim == Level.OVERWORLD) return "主世界";
        if (dim == Level.NETHER) return "下界";
        if (dim == Level.END) return "末地";
        return "自定义维度";
    }

    @Override
    public void onDeactivate() {
        baritone.stop();
        fsm.reset();
        container.reset();
        cmdManager.reset();
        orePredictor.invalidateCache();
        // 不再显示"已停止"，因为基类 toggle() 已经显示"已关闭"
    }

    /**
     * 更新矿石预测器配置
     * 当种子或目标矿石改变时调用
     */
    private void updateOrePredictor() {
        if (!seedMiningEnabled.get()) return;
        
        String seedStr = worldSeed.get().trim();
        if (seedStr.isEmpty()) return;
        
        try {
            long seed = Long.parseLong(seedStr);
            Block target = getTargetBlock();
            if (target != null && target != Blocks.AIR) {
                orePredictor.configure(seed, target);
            }
        } catch (NumberFormatException e) {
            notifyError("种子格式错误，必须是Long类型数字");
        }
    }

    /**
     * 启动自检：目标单选、三个WK坐标、五条指令、装备检测
     *
     * 收集全部缺项而不是遇到第一个就返回，这样用户一次就能看到还差什么，
     * 配好一项下次启动就少一条，不用反复试错。
     * 
     * 种子挖矿是可选功能，不强制检测
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();

        // 目标选择检测
        Block overworld = overworldOreTarget.get();
        Block nether = netherOreTarget.get();
        Block block = blockTarget.get();

        int selectedCount = 0;
        if (overworld != null && !overworld.equals(Blocks.AIR)) selectedCount++;
        if (nether != null && !nether.equals(Blocks.AIR)) selectedCount++;
        if (block != null && !block.equals(Blocks.AIR)) selectedCount++;

        if (selectedCount == 0) {
            missing.add("§e目标§f·未选择");
        } else if (selectedCount > 1) {
            missing.add("§e目标§f·选了" + selectedCount + "个（只能选1个）");
        }

        // 点位绑定检测
        if (WKCommand.getMineralChest() == null) missing.add("§6矿物箱§f·未绑定");
        if (WKCommand.getFoodChest() == null) missing.add("§2食物箱§f·未绑定");
        if (WKCommand.getAFKPoint() == null) missing.add("§d挂机点§f·未绑定");

        // 指令配置检测
        if (wildCommand.get().isEmpty()) missing.add("§b前往挖矿指令§f·未填写");
        if (unloadCommand.get().isEmpty()) missing.add("§b返回卸货指令§f·未填写");
        if (supplyCommand.get().isEmpty()) missing.add("§b前往补给指令§f·未填写");
        if (afkCommand.get().isEmpty()) missing.add("§b前往修复指令§f·未填写");
        if (respawnCommand.get().isEmpty()) missing.add("§b死亡返回指令§f·未填写");

        // 装备检测
        if (mc.player != null) {
            boolean hasPickaxe = false;
            boolean hasWeapon = false;
            int foodCount = 0;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;

                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                
                if (itemId.contains("pickaxe")) hasPickaxe = true;
                if (itemId.contains("sword") || itemId.contains("axe") || itemId.contains("shovel")) hasWeapon = true;
                if (stack.has(DataComponents.FOOD)) foodCount += stack.getCount();
            }

            if (!hasPickaxe) missing.add("§7镐子§f·背包里没有");
            if (!hasWeapon) missing.add("§7武器§f·背包里没有");
            if (foodCount < 32) missing.add("§7食物§f·只有" + foodCount + "个（建议32+）");
        }

        // 种子挖矿检测
        if (seedMiningEnabled.get()) {
            String seedStr = worldSeed.get().trim();
            if (seedStr.isEmpty()) {
                missing.add("§e种子挖矿§f·已启用但未填种子");
            } else {
                try {
                    Long.parseLong(seedStr);
                } catch (NumberFormatException e) {
                    missing.add("§e种子挖矿§f·格式错误（需Long数字）");
                }
            }
        }

        return missing;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  事件处理
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // 垃圾丢弃
        container.tickTrashDisposal(trashList.get(), placeBlocks.get());

        // 状态机推进
        fsm.tick();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        WKCommand.WKData mineral = WKCommand.getMineralChest();
        WKCommand.WKData food = WKCommand.getFoodChest();
        WKCommand.WKData afk = WKCommand.getAFKPoint();
        
        if (mineral != null && mineral.inCurrentDimension()) {
            String label = String.format("§6[矿物箱] §7(%s)", mineral.dimensionName());
            AutoMinerModule_ESP.renderLabel(event, mineral.pos, label, 
                mineralChestColor.get(), espScale.get().floatValue());
        }
        if (food != null && food.inCurrentDimension()) {
            String label = String.format("§2[食物箱] §7(%s)", food.dimensionName());
            AutoMinerModule_ESP.renderLabel(event, food.pos, label, 
                foodChestColor.get(), espScale.get().floatValue());
        }
        if (afk != null && afk.inCurrentDimension()) {
            String label = String.format("§d[挂机修复点] §7(%s)", afk.dimensionName());
            AutoMinerModule_ESP.renderLabel(event, afk.pos, label, 
                afkPointColor.get(), espScale.get().floatValue());
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!seedMiningEnabled.get()) return;

        // 获取玩家周围的预测矿石位置
        Set<BlockPos> predictedOres = orePredictor.getPredictedOresInRange(
            mc.player.blockPosition(), 
            renderRange.get()
        );

        if (!predictedOres.isEmpty()) {
            AutoMinerModule_ESP.renderPredictedOres(
                event, 
                predictedOres, 
                oreRenderColor.get(), 
                2.0
            );
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  配置访问器（供子组件调用）
    // ═══════════════════════════════════════════════════════════════════

    public Block getTargetBlock() {
        Block overworld = overworldOreTarget.get();
        if (overworld != null && !overworld.equals(Blocks.AIR)) return overworld;
        
        Block nether = netherOreTarget.get();
        if (nether != null && !nether.equals(Blocks.AIR)) return nether;
        
        Block block = blockTarget.get();
        if (block != null && !block.equals(Blocks.AIR)) return block;
        
        return Blocks.AIR;
    }

    public String getWildCommand() { return wildCommand.get(); }
    public boolean isRtpGuiEnabled() { return rtpGuiEnabled.get(); }
    public String getRtpGuiKeyword() { return rtpGuiKeyword.get(); }
    public String getUnloadCommand() { return unloadCommand.get(); }
    public String getSupplyCommand() { return supplyCommand.get(); }
    public String getAFKCommand() { return afkCommand.get(); }
    public String getRespawnCommand() { return respawnCommand.get(); }

    public int getUnloadThreshold() { return unloadThreshold.get(); }
    public int getFullLoadStacks() { return unloadThreshold.get(); }
    public int getHungerThreshold() { return hungerThreshold.get(); }
    public int getDurabilityThreshold() { return durabilityThreshold.get(); }
    public int getTeleportDelay() { return teleportDelay.get(); }
    public int getMineGoalUpdateInterval() { return mineGoalUpdateInterval.get(); }
    
    public List<Item> getFoodWhitelist() { return foodWhitelist.get(); }

    public BaritoneExecutor getBaritone() { return baritone; }
    public ContainerHelper getContainer() { return container; }
    public CommandManager getCmdManager() { return cmdManager; }
    public OrePredictor getOrePredictor() { return orePredictor; }
    public SoundNotifier getSoundNotifier() { return soundNotifier; }

    
    public boolean isSeedMiningEnabled() { return seedMiningEnabled.get(); }

    // 公开消息方法供子组件调用
    public void info(String msg) { notify(msg); }
    public void error(String msg) { notifyError(msg); }
    
    // 传送超时重试标志
    private boolean needRetryTeleport = false;
    
    public void requestRetryTeleport() {
        needRetryTeleport = true;
    }
    
    public boolean shouldRetryTeleport() {
        if (needRetryTeleport) {
            needRetryTeleport = false;
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  假矿检测
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 检测周围是否存在假矿
     * 
     * 触发方式：
     * · 配置页面「检测假矿」按钮
     * · .wk checkfake 指令
     * 
     * 检测逻辑：
     * 1. 检查种子挖矿是否启用 + 种子是否填写 + 格式是否正确
     * 2. 扫描玩家周围的目标矿石
     * 3. 对比种子预测位置
     * 4. 只要发现一个不在预测列表的矿 = 检测到假矿
     */
    public void checkFakeOres() {
        if (mc.player == null || mc.level == null) {
            notifyError("无法检测，玩家未加载");
            return;
        }

        // 1. 检查种子挖矿是否启用
        if (!seedMiningEnabled.get()) {
            notifyError("无法检测假矿！请先启用「种子挖矿」功能");
            return;
        }

        // 2. 检查种子是否填写
        String seedStr = worldSeed.get().trim();
        if (seedStr.isEmpty()) {
            notifyError("无法检测假矿！请先在「种子挖矿」设置中填入世界种子");
            return;
        }

        // 3. 检查种子格式是否正确
        try {
            Long.parseLong(seedStr);
        } catch (NumberFormatException e) {
            notifyError("种子格式错误！必须是Long类型数字（支持负数）");
            return;
        }

        // 4. 确保预测器已配置
        updateOrePredictor();

        // 5. 验证种子有效性：检测周围是否有真实矿石可供验证
        Block targetBlock = getTargetBlock();
        if (targetBlock == null || targetBlock == Blocks.AIR) {
            notifyError("未选择目标矿石，请先在「目标选择」中选择矿物");
            return;
        }

        notify("§e正在扫描周围矿石...");

        int scanRadius = 64; // 扫描范围 64 格
        BlockPos playerPos = mc.player.blockPosition();
        boolean foundFake = false;
        int realOreCount = 0; // 真实矿石数量

        // 扫描立方体区域
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -scanRadius; y <= scanRadius; y++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    Block block = mc.level.getBlockState(checkPos).getBlock();

                    // 检查是否是目标矿石（含深层变种）
                    String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                    String targetId = BuiltInRegistries.BLOCK.getKey(targetBlock).toString();
                    
                    boolean isTargetOre = blockId.equals(targetId) || 
                                         (blockId.contains(targetId.replace("minecraft:", "")) && 
                                          blockId.contains("deepslate"));

                    if (isTargetOre) {
                        realOreCount++;
                        // 对比预测位置
                        if (!orePredictor.isPredictedOreAt(checkPos)) {
                            foundFake = true;
                            break;
                        }
                    }
                }
                if (foundFake) break;
            }
            if (foundFake) break;
        }

        // 6. 验证种子有效性：至少需要3个矿石样本
        if (realOreCount < 3) {
            notifyError("§c周围矿石样本不足（需要至少3个），无法验证种子有效性");
            notifyError("§7提示：靠近矿脉或使用 X-Ray 找到更多矿石后再检测");
            return;
        }

        // 7. 播报结果
        lastCheckFoundFake = foundFake;
        if (foundFake) {
            notifyError("§c检测到假矿！种子错误或服务器使用了假矿");
        } else {
            notify("§a扫描 " + realOreCount + " 个矿石，全部匹配预测位置，种子验证通过");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  使用说明面板
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            // ═══════════════════════════════════════════════════════════════════
            //  使用说明按钮（置顶显眼位置）
            // ═══════════════════════════════════════════════════════════════════
            WButton helpBtn = theme.button("查看使用说明");
            helpBtn.action = () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();
            
            // ═══════════════════════════════════════════════════════════════════
            //  假矿检测按钮
            // ═══════════════════════════════════════════════════════════════════
            WButton checkFakeBtn = theme.button("检测假矿");
            checkFakeBtn.action = this::checkFakeOres;
            table.add(checkFakeBtn).expandX().minWidth(200);
            table.row();
            
            // ═══════════════════════════════════════════════════════════════════
            //  点位设置卡片区（三列布局）
            // ═══════════════════════════════════════════════════════════════
            
            // 创建三列容器
            WTable cardRow = theme.table();
            
            // 矿物箱卡片
            buildLocationCard(theme, cardRow, "矿物箱", "mineral");
            
            // 食物箱卡片
            buildLocationCard(theme, cardRow, "食物箱", "food");
            
            // 挂机修复点卡片
            buildLocationCard(theme, cardRow, "挂机修复点", "afk");
            
            // 将三列容器加入主表格
            table.add(cardRow).expandX();
            table.row();
        },
            new String[]{
                "§l自动挖矿 · 使用说明"
            },
            new String[]{
                "§e§l准备工作",
                "§f  1. 准备好挖掘工具（推荐附魔耐久、效率）",
                "§f  2. 准备好武器（修补耐久时用）",
                "§f  3. 放置矿物箱、食物箱（装满食物）",
                "§f  4. 选好挂机修复点（安全区域，怪物可到达）",
                "§f  5. 配置页面顶部点击卡片按钮设置三个点位"
            },
            new String[]{
                "§6§l点位设置（两种方式）",
                "§f  · " + highlightText("方式1：配置页面按钮") + " — 打开配置页面 → 点击卡片中的设置按钮",
                "§f    · 箱子类点位：准星对准箱子后自动绑定",
                "§f    · 挂机修复点：站在目标位置后自动绑定（含视角）",
                "§f    · " + highlightText("颜色联动") + "：已设置=绿色按钮，未设置=红色按钮",
                "§f  · " + highlightText("方式2：指令设置") + " — 使用 .wk 指令系统",
                "§f    · " + highlightCommand(".wk set 矿物箱") + " — 准星对准箱子，绑定矿物卸货箱",
                "§f    · " + highlightCommand(".wk set 食物箱") + " — 准星对准箱子，绑定食物补给箱",
                "§f    · " + highlightCommand(".wk set 挂机修复点") + " — 当前位置绑定为挂机修复点（含视角）",
                "§f  · " + highlightText("容器检测") + "：箱子类点位会自动检测目标是否为容器",
                "§f    · 不是容器 → 自动关闭GUI并提示重新设置"
            },
            new String[]{
                "§6§l指令系统",
                "§f  · " + highlightCommand(".wk status") + " — 查看绑定状态",
                "§f  · " + highlightCommand(".wk checkfake") + " — 检测周围是否存在假矿",
                "§f  · " + highlightCommand(".wk remove <目标>") + " — 解绑单个坐标",
                "§f  · " + highlightCommand(".wk clear") + " — 清空所有绑定"
            },
            new String[]{
                "§a§l状态机流程",
                "§f  1. " + highlightText("前往挖矿") + " — 发送挖矿指令，等区块加载完成",
                "§f  2. " + highlightText("采掘") + " — Baritone 自动挖矿，满载/饥饿/耐久触发转换",
                "§f  3. " + highlightText("卸货循环") + " — 传送到矿物箱，倒货，返回野外",
                "§f  4. " + highlightText("补给循环") + " — 传送到箱，拿食物，吃饱，返回",
                "§f  5. " + highlightText("修补循环") + " — 传送到挂机修复点，KillAura打怪修工具（需修补附魔）",
                "§f  6. " + highlightText("死亡处理") + " — 自动复活，执行死亡返回指令，恢复挖矿"
            },
            new String[]{
                "§b§l参数建议",
                "§f  · " + highlightText("满载组数") + "：默认20组，矿物达到此数量触发卸货",
                "§f  · " + highlightText("饥饿阈值") + "：默认12，饥饿值低于此值触发补给",
                "§f  · " + highlightText("耐久阈值") + "：默认50，工具剩余耐久低于此值触发修补"
            },
            new String[]{
                "§d§l种子挖矿",
                "§f  · " + highlightText("应对假矿") + "：服务器手动放置的假矿无法骗过种子预测",
                "§f  · " + highlightText("填入种子") + "：从服主获取或使用工具反推世界种子",
                "§f  · " + highlightText("自动渲染") + "：周围128格内的真实矿石位置会显示方块框",
                "§f  · " + highlightText("智能过滤") + "：Baritone只挖预测位置的矿，假矿直接无视",
                "§f  · " + highlightText("支持深层变种") + "：选钻石矿会预测钻石矿+深层钻石矿",
                "§f  · " + highlightText("假矿检测") + "：使用 " + highlightCommand(".wk checkfake") + " 扫描周围假矿"
            },
            new String[]{
                "§c§l注意事项",
                "§f  · " + highlightText("必须单选目标") + "：矿石和方块只能选一个",
                "§f  · " + highlightText("维度匹配检查") + "：启动时自动检测，选主世界矿别跑下界",
                "§f  · " + highlightText("深层变种自动支持") + "：选钻石矿会自动挖深层钻石矿",
                "§f  · " + highlightText("垃圾自动丢弃") + "：圆石、深层圆石等默认已勾选",
                "§f  · " + highlightText("修补需要经验") + "：挂机修复点附近必须有怪物刷新",
                "§f  · " + highlightText("死亡自愈") + "：复活后自动执行死亡返回指令并恢复挖矿",
                "§f  · " + highlightText("防卡死机制") + "：区块加载检测、掉落检测、指令延迟校验"
            }
        );
    }

    /**
     * 构建点位设置卡片
     * 卡片式布局，包含标题、设置按钮、删除按钮、状态显示
     * 
     * @param theme Meteor GUI 主题
     * @param parentTable 父表格（横向三列排列）
     * @param title 卡片标题（如"矿物箱"）
     * @param key 绑定键名（"mineral"/"food"/"afk"）
     */
    private void buildLocationCard(GuiTheme theme, WTable parentTable, String title, String key) {
        // 创建卡片容器（垂直布局）
        WTable card = theme.table();
        
        // 获取当前绑定状态
        boolean isBound = WKCommand.hasBinding(key);
        WKCommand.WKData data = WKCommand.getBinding(key);
        
        // 获取对应的ESP颜色并转换为Minecraft颜色代码
        String titleColor = switch (key) {
            case "mineral" -> "§6";  // 金色
            case "food" -> "§2";     // 绿色
            case "afk" -> "§d";      // 粉色
            default -> "§f";         // 白色
        };
        
        // 标题（使用简化的颜色代码）
        card.add(theme.label(titleColor + title)).expandX().center();
        card.row();
        
        // 状态显示（固定两行，保持高度一致）
        if (isBound && data != null) {
            String coords = String.format("§7X§f%d §7Y§f%d §7Z§f%d", 
                data.pos.getX(), data.pos.getY(), data.pos.getZ());
            card.add(theme.label(coords)).expandX().center();
            card.row();
            
            String dimName = data.dimensionName();
            card.add(theme.label("§7" + dimName)).expandX().center();
            card.row();
        } else {
            // 未绑定时也占两行，保持高度一致
            card.add(theme.label("§8暂未绑定")).expandX().center();
            card.row();
            card.add(theme.label("§8-")).expandX().center();  // 占位符
            card.row();
        }
        
        // 设置按钮（已绑定=亮绿色，未绑定=暗灰色）
        String setBtnColor = isBound ? "§a" : "§8";
        WButton setBtn = theme.button(setBtnColor + "设置");
        setBtn.action = () -> {
            WKCommand.setBinding(key);
            mc.setScreen(null);
        };
        card.add(setBtn).expandX().center();
        card.row();
        
        // 删除按钮（红色）
        WButton delBtn = theme.button("§c删除");
        delBtn.action = () -> {
            if (isBound) {
                WKCommand.removeBinding(key);
                mc.setScreen(null);
            }
        };
        card.add(delBtn).expandX().center();
        
        // 将卡片加入父表格（横向排列，均匀分配）
        parentTable.add(card).expandX();
    }

    /**
     * 构建使用说明内容
     * 采用黑客终端风格，颜色方案遵循个人习惯
     */
    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("准备工作",
                "  §8├─ §f准备好挖矿工具 §7(推荐附魔耐久、效率)",
                "  §8├─ §f准备好武器 §7(修补耐久时用)",
                "  §8├─ §f放置矿物箱、食物箱 §7(装满食物)",
                "  §8├─ §f选好挂机修复点 §7(安全区域，怪物可到达)",
                "  §8└─ §f配置页面顶部点击卡片按钮设置三个点位"
            ),
            new HelpScreen.HelpSection("点位设置 §7(两种方式)",
                "  §b▸ §e方式1 §8- §f配置页面按钮",
                "    §7准星对准箱子 §8→ §f点击卡片中的设置按钮",
                "    §7箱子类型：矿物箱、食物箱自动检测容器",
                "    §7挂机修复点：直接站在目标位置即可绑定",
                "",
                "  §b▸ §e方式2 §8- §f指令系统",
                "    §8> §3.wk set 矿物箱 §8— §7准星对准箱子，绑定矿物贮箱",
                "    §8> §3.wk set 食物箱 §8— §7准星对准箱子，绑定食物补给箱",
                "    §8> §3.wk set 挂机修复点 §8— §7站在目标位置后自动绑定 §7(含视角)",
                "",
                "  §7§o容器检测：箱子类点位会自动检测目标方块是否为容器",
                "  §7§o不是容器 §8→ §7自动拒绝并提示重新设置，避免卡死"
            ),
            new HelpScreen.HelpSection("指令系统",
                "  §8> §3.wk status §8— §7查看绑定状态 §7(含坐标、维度、视角)",
                "  §8> §3.wk checkfake §8— §7检测周围假矿 §7(需启用种子挖矿)",
                "  §8> §3.wk remove §c<目标> §8— §7解绑单个坐标",
                "  §8> §3.wk clear §8— §7清空所有绑定"
            ),
            new HelpScreen.HelpSection("状态机流程",
                "  §a[1] §f前往挖矿 §8→ §7发送挖矿指令，等区块加载完成",
                "  §a[2] §f采掘 §8→ §7Baritone自动挖矿，满载/饿/耐久触发转换",
                "  §a[3] §f卸货循环 §8→ §7传送到矿物箱，卸货，返回野外",
                "  §a[4] §f补给循环 §8→ §7传送到箱，拿食物，吃饱，返回"
            ),
            new HelpScreen.HelpSection("参数建议",
                "  §6▸ §f满载组数 §8= §e36 §7(标准背包容量)",
                "  §6▸ §f食物阈值 §8= §e14 §7(7格肉约14饱食度)",
                "  §6▸ §f耐久阈值 §8= §e50 §7(低于50时自动修复)",
                "  §6▸ §f传送等待 §8= §e10秒 §7(RTP加载缓冲)"
            ),
            new HelpScreen.HelpSection("种子挖矿 §7(可选)",
                "  §d▸ §f启用后可预测矿石位置 §7(需填入世界种子)",
                "  §d▸ §f检测假矿：对准可疑方块 §8→ §f点击「检测假矿」按钮",
                "  §d▸ §f假矿判定：预测无矿但显示有矿 §8= §c假矿",
                "  §d▸ §f适用场景：防止挖到管理员放置的诱饵矿"
            ),
            new HelpScreen.HelpSection("注意事项",
                "  §c⚠ §f模块运行中无法修改点位，必须先关闭模块",
                "  §c⚠ §f已绑定点位不允许覆盖，必须先删除再重新设置",
                "  §c⚠ §f传送指令需服务器支持，否则无法自动返回",
                "  §c⚠ §f挂机修复点会记录视角，用于精准对准修补工作台"
            )
        );
    }
}
