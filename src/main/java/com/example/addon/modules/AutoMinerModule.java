package com.example.addon.modules;

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
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AutoMiner Matrix - 全自动挖矿矩阵模块
 * 
 * 核心能力：
 * · Baritone 驱动采掘 - 单目标矿石锁定，深层变种自动支持
 * · 状态机全生命周期管理 - 去野外/卸货/补给/挂机修复点修补/死亡复活
 * · 物流自动化 - 卸货箱倒产物，食物箱拿食物，垃圾丢弃
 * · 耐久修补 - 联动 KillAura 打怪修装备
 * · 死亡自愈 - 自动复活+回归挂机修复点
 * · 防卡死 - 区块加载检测、掉落虚空检测、指令延迟校验
 */
public final class AutoMinerModule extends YiyiaddonModule {

    private final MinerFSM fsm = new MinerFSM(this);
    private final BaritoneExecutor baritone = new BaritoneExecutor(this);
    private final ContainerHelper container = new ContainerHelper(this);
    private final CommandManager cmdManager = new CommandManager(this);
    private final OrePredictor orePredictor = new OrePredictor();

    // ═══════════════════════════════════════════════════════════════════
    //  UI 配置面板
    // ═══════════════════════════════════════════════════════════════════

    private final SettingGroup sgTarget = settings.createGroup("目标选择", false);
    private final SettingGroup sgSeedMining = settings.createGroup("种子挖矿", false);
    private final SettingGroup sgThresholds = settings.createGroup("阈值设置", false);
    private final SettingGroup sgCommands = settings.createGroup("指令配置", false);
    private final SettingGroup sgBaritone = settings.createGroup("Baritone设置", false);
    private final SettingGroup sgTrash = settings.createGroup("垃圾丢弃", false);
    private final SettingGroup sgDisplay = settings.createGroup("显示设置", false);

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
    private final Setting<String> unloadCommand;
    private final Setting<String> supplyCommand;
    private final Setting<String> afkCommand;
    private final Setting<String> respawnCommand;

    // ─── 阈值设置 ───
    private final Setting<Integer> unloadThreshold;
    private final Setting<Integer> hungerThreshold;
    private final Setting<Integer> durabilityThreshold;

    // ─── 垃圾丢弃 ───
    private final Setting<List<Block>> trashList;

    // ─── Baritone 设置 ───
    private final Setting<Boolean> avoidLava;
    private final Setting<Boolean> mobAvoidance;
    private final Setting<Integer> mobAvoidanceRadius;
    private final Setting<Boolean> allowBreak;
    private final Setting<Boolean> allowPlace;
    private final Setting<Integer> maxFallHeight;
    private final Setting<Boolean> pauseMiningForFallingBlocks;
    private final Setting<Boolean> itemSaver;
    private final Setting<Integer> itemSaverThreshold;
    private final Setting<Boolean> allowOnlyExposedOres;
    private final Setting<Integer> allowOnlyExposedOresDistance;
    private final Setting<Integer> minYLevelWhileMining;
    private final Setting<Integer> maxYLevelWhileMining;
    private final Setting<Integer> mineMaxOreLocationsCount;
    private final Setting<Boolean> blacklistClosestOnFailure;
    private final Setting<Boolean> legitMine;
    private final Setting<Integer> legitMineYLevel;
    private final Setting<Boolean> legitMineIncludeDiagonals;

    // ─── 显示设置 ───
    private final Setting<Double> espScale;
    private final Setting<SettingColor> mineralChestColor;
    private final Setting<SettingColor> foodChestColor;
    private final Setting<SettingColor> afkPointColor;

    public AutoMinerModule() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动挖矿",
            "Baritone驱动全自动挖矿，物流循环，耐久修补，死亡自愈。详细参考下面使用说明。");

        // ─── 目标选择 ───
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

        overworldOreTarget = sgTarget.add(new BlockSetting.Builder()
            .name("主世界矿石")
            .description("选择主世界矿石（含深层变种）")
            .defaultValue(Blocks.DIAMOND_ORE)
            .filter(block -> {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                return id.contains("_ore") && !id.contains("nether") && !id.contains("ancient");
            })
            .onChanged(block -> {
                if (!block.equals(Blocks.AIR)) {
                    netherOreTarget.set(Blocks.AIR);
                    blockTarget.set(Blocks.AIR);
                }
                updateOrePredictor();
            })
            .build());

        // ─── 种子挖矿 ───
        seedMiningEnabled = sgSeedMining.add(new BoolSetting.Builder()
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

        worldSeed = sgSeedMining.add(new StringSetting.Builder()
            .name("世界种子")
            .description("服务器的世界种子（Long类型，支持负数，如 -8913466909937400889）")
            .defaultValue("")
            .visible(seedMiningEnabled::get)
            .onChanged(s -> updateOrePredictor())
            .build());

        renderRange = sgSeedMining.add(new IntSetting.Builder()
            .name("渲染范围")
            .description("渲染玩家周围多少格内的预测矿石")
            .defaultValue(128)
            .min(32)
            .sliderMax(256)
            .visible(seedMiningEnabled::get)
            .build());

        oreRenderColor = sgSeedMining.add(new ColorSetting.Builder()
            .name("矿石渲染颜色")
            .description("预测矿石方块框线的颜色")
            .defaultValue(new SettingColor(255, 215, 0, 180))
            .visible(seedMiningEnabled::get)
            .build());

        pulseEffect = sgSeedMining.add(new BoolSetting.Builder()
            .name("脉冲效果")
            .description("矿石框线启用呼吸灯效果")
            .defaultValue(true)
            .visible(seedMiningEnabled::get)
            .build());

        // ─── 指令配置 ───
        wildCommand = sgCommands.add(new StringSetting.Builder()
            .name("去野外指令")
            .description("传送到挖矿区域的指令（如 /rtp）")
            .defaultValue("")
            .build());

        unloadCommand = sgCommands.add(new StringSetting.Builder()
            .name("满载卸货指令")
            .description("传送到卸货箱的指令（如 /home kuang）")
            .defaultValue("")
            .build());

        supplyCommand = sgCommands.add(new StringSetting.Builder()
            .name("补给指令")
            .description("传送到食物箱的指令（如 /home shiwu）")
            .defaultValue("")
            .build());

        afkCommand = sgCommands.add(new StringSetting.Builder()
            .name("挂机点指令")
            .description("传送到挂机修补点的指令（如 /home guaji）")
            .defaultValue("")
            .build());

        respawnCommand = sgCommands.add(new StringSetting.Builder()
            .name("死亡重返指令")
            .description("复活后返回挂机点的指令")
            .defaultValue("")
            .build());

        // ─── 阈值设置 ───
        unloadThreshold = sgThresholds.add(new IntSetting.Builder()
            .name("满载组数")
            .description("背包矿物达到多少组时触发卸货")
            .defaultValue(20)
            .min(1)
            .sliderMax(36)
            .build());

        hungerThreshold = sgThresholds.add(new IntSetting.Builder()
            .name("饥饿阈值")
            .description("饥饿值低于此值时触发补给")
            .defaultValue(12)
            .min(1)
            .sliderMax(20)
            .build());

        durabilityThreshold = sgThresholds.add(new IntSetting.Builder()
            .name("耐久阈值")
            .description("工具剩余耐久低于此值时触发修补")
            .defaultValue(50)
            .min(1)
            .sliderMax(500)
            .build());

        // ─── 垃圾丢弃（预设并默认勾选） ───
        trashList = sgTrash.add(new BlockListSetting.Builder()
            .name("垃圾丢弃名单")
            .description("挖矿时自动丢弃这些方块")
            .defaultValue(List.of(
                Blocks.COBBLESTONE,
                Blocks.COBBLED_DEEPSLATE,
                Blocks.DIRT,
                Blocks.NETHERRACK,
                Blocks.DIORITE,
                Blocks.GRANITE,
                Blocks.ANDESITE,
                Blocks.GRAVEL,
                Blocks.TUFF
            ))
            .build());

        // ─── Baritone 设置 ───
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

        mobAvoidanceRadius = sgBaritone.add(new IntSetting.Builder()
            .name("怪物规避半径")
            .description("计算怪物危险区域的半径")
            .defaultValue(8)
            .min(1)
            .sliderMax(16)
            .onChanged(value -> baritone.updateSetting("mobAvoidanceRadius", value))
            .build());

        allowBreak = sgBaritone.add(new BoolSetting.Builder()
            .name("破坏阻挡方块")
            .description("允许破坏阻挡路径的方块（石头、泥土等）")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("allowBreak", value))
            .build());

        allowPlace = sgBaritone.add(new BoolSetting.Builder()
            .name("放置方块")
            .description("允许搭桥或填坑（需要背包里有方块）")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowPlace", value))
            .build());

        maxFallHeight = sgBaritone.add(new IntSetting.Builder()
            .name("最大坠落高度")
            .description("允许从多高的地方跳下（超过会绕路）")
            .defaultValue(3)
            .min(0)
            .sliderMax(20)
            .onChanged(value -> baritone.updateSetting("maxFallHeightNoWater", value))
            .build());

        pauseMiningForFallingBlocks = sgBaritone.add(new BoolSetting.Builder()
            .name("掉落方块暂停")
            .description("遇到沙子、沙砾等掉落方块时暂停挖掘")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("pauseMiningForFallingBlocks", value))
            .build());

        itemSaver = sgBaritone.add(new BoolSetting.Builder()
            .name("工具保护")
            .description("工具耐久不足时避免继续使用该工具")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("itemSaver", value))
            .build());

        itemSaverThreshold = sgBaritone.add(new IntSetting.Builder()
            .name("Baritone工具耐久阈值")
            .description("Baritone 停止使用工具的剩余耐久")
            .defaultValue(50)
            .min(1)
            .sliderMax(500)
            .onChanged(value -> baritone.updateSetting("itemSaverThreshold", value))
            .build());

        allowOnlyExposedOres = sgBaritone.add(new BoolSetting.Builder()
            .name("仅挖暴露矿石")
            .description("只挖掘能从指定距离看到的矿石，减少无效挖掘")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("allowOnlyExposedOres", value))
            .build());

        allowOnlyExposedOresDistance = sgBaritone.add(new IntSetting.Builder()
            .name("暴露矿石检测距离")
            .description("判断矿石是否暴露时使用的检测距离")
            .defaultValue(1)
            .min(1)
            .sliderMax(8)
            .onChanged(value -> baritone.updateSetting("allowOnlyExposedOresDistance", value))
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

        mineMaxOreLocationsCount = sgBaritone.add(new IntSetting.Builder()
            .name("矿点缓存数量")
            .description("Baritone 一次缓存的最大矿点数量")
            .defaultValue(64)
            .min(1)
            .sliderMax(256)
            .onChanged(value -> baritone.updateSetting("mineMaxOreLocationsCount", value))
            .build());

        blacklistClosestOnFailure = sgBaritone.add(new BoolSetting.Builder()
            .name("失败目标暂时跳过")
            .description("矿点无法到达时跳过最近目标，避免反复卡住")
            .defaultValue(true)
            .onChanged(value -> baritone.updateSetting("blacklistClosestOnFailure", value))
            .build());

        legitMine = sgBaritone.add(new BoolSetting.Builder()
            .name("合法挖掘模式")
            .description("启用合法挖掘限制（关闭可提升效率但可能被检测）")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("legitMine", value))
            .build());

        legitMineYLevel = sgBaritone.add(new IntSetting.Builder()
            .name("合法挖掘高度")
            .description("合法挖掘模式进行条带探索时使用的高度")
            .defaultValue(12)
            .min(-64)
            .sliderMax(320)
            .onChanged(value -> baritone.updateSetting("legitMineYLevel", value))
            .build());

        legitMineIncludeDiagonals = sgBaritone.add(new BoolSetting.Builder()
            .name("合法挖掘检测对角矿石")
            .description("合法挖掘时检测与已发现矿石对角相邻的矿石")
            .defaultValue(false)
            .onChanged(value -> baritone.updateSetting("legitMineIncludeDiagonals", value))
            .build());

        // ─── 显示设置 ───
        espScale = sgDisplay.add(new DoubleSetting.Builder()
            .name("ESP字体大小")
            .description("三个坐标点悬浮标签的字体缩放倍数")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(3.0)
            .build());

        mineralChestColor = sgDisplay.add(new ColorSetting.Builder()
            .name("矿物箱颜色")
            .description("矿物箱ESP标签的颜色")
            .defaultValue(new SettingColor(255, 215, 0))
            .build());

        foodChestColor = sgDisplay.add(new ColorSetting.Builder()
            .name("食物箱颜色")
            .description("食物箱ESP标签的颜色")
            .defaultValue(new SettingColor(100, 255, 100))
            .build());

        afkPointColor = sgDisplay.add(new ColorSetting.Builder()
            .name("挂机修复点颜色")
            .description("挂机修复点ESP标签的颜色")
            .defaultValue(new SettingColor(255, 100, 255))
            .build());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  模块生命周期
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
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
            itemSaver.get(),
            itemSaverThreshold.get(),
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
     * 启动自检：目标单选、三个WK坐标、五条指令
     *
     * 收集全部缺项而不是遇到第一个就返回，这样用户一次就能看到还差什么，
     * 配好一项下次启动就少一条，不用反复试错。
     * 
     * 种子挖矿是可选功能，不强制检测
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();

        // 1. 目标单选
        Block overworld = overworldOreTarget.get();
        Block nether = netherOreTarget.get();
        Block block = blockTarget.get();

        int selectedCount = 0;
        if (overworld != null && !overworld.equals(Blocks.AIR)) selectedCount++;
        if (nether != null && !nether.equals(Blocks.AIR)) selectedCount++;
        if (block != null && !block.equals(Blocks.AIR)) selectedCount++;

        if (selectedCount == 0) {
            missing.add("未选择目标 — 在「主世界矿石/下界矿石/普通方块」里选一个");
        } else if (selectedCount > 1) {
            missing.add("目标选了 " + selectedCount + " 个 — 只能选一个，取消多余的");
        }

        // 2. 三个 WK 坐标
        if (WKCommand.getMineralChest() == null) {
            missing.add("矿物箱未绑定 — 准星对准箱子，输入 " + highlightCommand(".wk set 矿物箱"));
        }
        if (WKCommand.getFoodChest() == null) {
            missing.add("食物箱未绑定 — 准星对准箱子，输入 " + highlightCommand(".wk set 食物箱"));
        }
        if (WKCommand.getAFKPoint() == null) {
            missing.add("挂机点未绑定 — 站到挂机位置，输入 " + highlightCommand(".wk set 挂机点"));
        }

        // 3. 五条指令
        if (wildCommand.get().isEmpty()) missing.add("「去野外指令」未填写 — 填传送到矿区的服务器指令");
        if (unloadCommand.get().isEmpty()) missing.add("「满载卸货指令」未填写 — 填传送到矿物箱的指令");
        if (supplyCommand.get().isEmpty()) missing.add("「补给指令」未填写 — 填传送到食物箱的指令");
        if (afkCommand.get().isEmpty()) missing.add("「挂机点指令」未填写 — 填传送到挂机点的指令");
        if (respawnCommand.get().isEmpty()) missing.add("「死亡重返指令」未填写 — 填复活后回矿区的指令");

        // 4. 种子挖矿（可选功能，仅在启用时检测）
        if (seedMiningEnabled.get()) {
            String seedStr = worldSeed.get().trim();
            if (seedStr.isEmpty()) {
                missing.add("「种子挖矿」已启用但未填写种子 — 填入世界种子或关闭该功能");
            } else {
                try {
                    Long.parseLong(seedStr);
                } catch (NumberFormatException e) {
                    missing.add("「种子挖矿」种子格式错误 — 必须是Long类型数字（支持负数）");
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
        container.tickTrashDisposal(trashList.get());

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
            AutoMinerModule_ESP.renderLabel(event, mineral.pos, "[矿物箱]", 
                mineralChestColor.get(), espScale.get().floatValue());
        }
        if (food != null && food.inCurrentDimension()) {
            AutoMinerModule_ESP.renderLabel(event, food.pos, "[食物箱]", 
                foodChestColor.get(), espScale.get().floatValue());
        }
        if (afk != null && afk.inCurrentDimension()) {
            AutoMinerModule_ESP.renderLabel(event, afk.pos, "[挂机修复点]", 
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
    public String getUnloadCommand() { return unloadCommand.get(); }
    public String getSupplyCommand() { return supplyCommand.get(); }
    public String getAFKCommand() { return afkCommand.get(); }
    public String getRespawnCommand() { return respawnCommand.get(); }

    public int getUnloadThreshold() { return unloadThreshold.get(); }
    public int getHungerThreshold() { return hungerThreshold.get(); }
    public int getDurabilityThreshold() { return durabilityThreshold.get(); }

    public BaritoneExecutor getBaritone() { return baritone; }
    public ContainerHelper getContainer() { return container; }
    public CommandManager getCmdManager() { return cmdManager; }
    public OrePredictor getOrePredictor() { return orePredictor; }
    
    public boolean isSeedMiningEnabled() { return seedMiningEnabled.get(); }

    // 公开消息方法供子组件调用
    public void info(String msg) { notify(msg); }
    public void error(String msg) { notifyError(msg); }

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

        // 5. 扫描周围矿石
        Block targetBlock = getTargetBlock();
        if (targetBlock == null || targetBlock == Blocks.AIR) {
            notifyError("未选择目标矿石，请先在「目标选择」中选择矿物");
            return;
        }

        notify("§e正在扫描周围矿石...");

        int scanRadius = 64; // 扫描范围 64 格
        BlockPos playerPos = mc.player.blockPosition();
        boolean foundFake = false;

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

        // 6. 播报结果
        lastCheckFoundFake = foundFake;
        if (foundFake) {
            notifyError("§c检测到假矿！建议启用种子挖矿模式避开假矿");
        } else {
            notify("§a周围未发现假矿，当前区域安全");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  使用说明面板
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            // ═══════════════════════════════════════════════════════════════════
            //  假矿检测按钮（置顶显眼位置）
            // ═══════════════════════════════════════════════════════════════════
            WButton checkFakeBtn = theme.button("§e§l🔍 检测假矿");
            checkFakeBtn.action = this::checkFakeOres;
            table.add(checkFakeBtn).expandX().minWidth(200);
            table.row();
            
            // ═══════════════════════════════════════════════════════════════════
            //  点位设置卡片区（三列布局）
            // ═══════════════════════════════════════════════════════════════════
            
            // 矿物箱卡片
            buildLocationCard(theme, table, "矿物箱", "mineral");
            
            // 食物箱卡片
            buildLocationCard(theme, table, "食物箱", "food");
            
            // 挂机修复点卡片
            buildLocationCard(theme, table, "挂机修复点", "afk");
            
            table.row();
        },
            new String[]{
                "§l自动挖矿 · 使用说明"
            },
            new String[]{
                "§e§l▌ 准备工作",
                "§f  1. 准备好挖掘工具（推荐附魔耐久、效率）",
                "§f  2. 准备好武器（修补耐久时用）",
                "§f  3. 放置矿物箱、食物箱（装满食物）",
                "§f  4. 选好挂机修复点（安全区域，怪物可到达）",
                "§f  5. 配置页面顶部点击卡片按钮设置三个点位"
            },
            new String[]{
                "§6§l▌ 点位设置（两种方式）",
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
                "§6§l▌ 指令系统",
                "§f  · " + highlightCommand(".wk status") + " — 查看绑定状态",
                "§f  · " + highlightCommand(".wk checkfake") + " — 检测周围是否存在假矿",
                "§f  · " + highlightCommand(".wk remove <目标>") + " — 解绑单个坐标",
                "§f  · " + highlightCommand(".wk clear") + " — 清空所有绑定"
            },
            new String[]{
                "§a§l▌ 状态机流程",
                "§f  1. " + highlightText("去野外") + " — 发送野外指令，等区块加载完成",
                "§f  2. " + highlightText("采掘") + " — Baritone 自动挖矿，满载/饥饿/耐久触发转换",
                "§f  3. " + highlightText("卸货循环") + " — 传送到矿物箱，倒货，返回野外",
                "§f  4. " + highlightText("补给循环") + " — 传送到箱，拿食物，吃饱，返回",
                "§f  5. " + highlightText("修补循环") + " — 传送到挂机修复点，KillAura打怪修工具（需修补附魔）",
                "§f  6. " + highlightText("死亡处理") + " — 自动复活，执行死亡重返指令，恢复挖矿"
            },
            new String[]{
                "§b§l▌ 参数建议",
                "§f  · " + highlightText("满载组数") + "：默认20组，矿物达到此数量触发卸货",
                "§f  · " + highlightText("饥饿阈值") + "：默认12，饥饿值低于此值触发补给",
                "§f  · " + highlightText("耐久阈值") + "：默认50，工具剩余耐久低于此值触发修补"
            },
            new String[]{
                "§d§l▌ 种子挖矿",
                "§f  · " + highlightText("应对假矿") + "：服务器手动放置的假矿无法骗过种子预测",
                "§f  · " + highlightText("填入种子") + "：从服主获取或使用工具反推世界种子",
                "§f  · " + highlightText("自动渲染") + "：周围128格内的真实矿石位置会显示方块框",
                "§f  · " + highlightText("智能过滤") + "：Baritone只挖预测位置的矿，假矿直接无视",
                "§f  · " + highlightText("支持深层变种") + "：选钻石矿会预测钻石矿+深层钻石矿",
                "§f  · " + highlightText("假矿检测") + "：使用 " + highlightCommand(".wk checkfake") + " 扫描周围假矿"
            },
            new String[]{
                "§c§l▌ 注意事项",
                "§f  · " + highlightText("必须单选目标") + "：矿石和方块只能选一个",
                "§f  · " + highlightText("维度匹配检查") + "：启动时自动检测，选主世界矿别跑下界",
                "§f  · " + highlightText("深层变种自动支持") + "：选钻石矿会自动挖深层钻石矿",
                "§f  · " + highlightText("垃圾自动丢弃") + "：圆石、深层圆石等默认已勾选",
                "§f  · " + highlightText("修补需要经验") + "：挂机修复点附近必须有怪物刷新",
                "§f  · " + highlightText("死亡自愈") + "：复活后自动执行重返指令并恢复挖矿",
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
        
        // 标题
        card.add(theme.label("§l" + title)).expandX();
        card.row();
        
        // 设置按钮（根据绑定状态改变颜色）
        WButton setBtn = theme.button(WKCommand.hasBinding(key) ? "§a设置" : "§c设置");
        setBtn.action = () -> {
            boolean success = WKCommand.setBinding(key);
            if (!success) {
                // 设置失败（目标不是容器），关闭GUI
                if (mc.screen != null) {
                    mc.screen.onClose();
                }
            } else {
                notify("§a设置成功，重新打开配置页面可看到更新");
            }
        };
        card.add(setBtn).minWidth(80).expandWidgetX();
        card.row();
        
        // 删除按钮
        WButton delBtn = theme.button("§7删除");
        delBtn.action = () -> {
            WKCommand.removeBinding(key);
            notify("§e已删除 " + title + " 绑定");
        };
        card.add(delBtn).minWidth(80).expandWidgetX();
        card.row();
        
        // 状态显示
        String status = WKCommand.hasBinding(key) ? "§a已设置" : "§c未设置";
        card.add(theme.label(status)).expandX();
        
        // 将卡片加入父表格（横向排列）
        parentTable.add(card).minWidth(100);
    }
}
