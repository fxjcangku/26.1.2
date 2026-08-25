package com.example.addon.modules;

import com.example.addon.commands.WKCommand;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.mining.*;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoMiner Matrix - 全自动挖矿矩阵模块
 * 
 * 核心能力：
 * · Baritone 驱动采掘 - 单目标矿石锁定，深层变种自动支持
 * · 状态机全生命周期管理 - 去野外/卸货/补给/挂机修补/死亡复活
 * · 物流自动化 - 卸货箱倒产物，食物箱拿食物，垃圾丢弃
 * · 耐久修补 - 联动 KillAura 打怪修装备
 * · 死亡自愈 - 自动复活+回归挂机点
 * · 防卡死 - 区块加载检测、掉落虚空检测、指令延迟校验
 */
public final class AutoMinerModule extends YiyiaddonModule {

    private final MinerFSM fsm = new MinerFSM(this);
    private final BaritoneExecutor baritone = new BaritoneExecutor(this);
    private final ContainerHelper container = new ContainerHelper(this);
    private final CommandManager cmdManager = new CommandManager(this);

    // ═══════════════════════════════════════════════════════════════════
    //  UI 配置面板
    // ═══════════════════════════════════════════════════════════════════

    private final SettingGroup sgTarget = settings.createGroup("目标选择", false);
    private final SettingGroup sgThresholds = settings.createGroup("阈值设置", false);
    private final SettingGroup sgCommands = settings.createGroup("指令配置", false);
    private final SettingGroup sgBaritone = settings.createGroup("Baritone设置", false);
    private final SettingGroup sgTrash = settings.createGroup("垃圾丢弃", false);
    private final SettingGroup sgDisplay = settings.createGroup("显示设置", false);

    // ─── 目标选择（互斥） ───
    private final Setting<Block> overworldOreTarget;
    private final Setting<Block> netherOreTarget;
    private final Setting<Block> blockTarget;

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
            })
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
            .name("挂机点颜色")
            .description("挂机点ESP标签的颜色")
            .defaultValue(new SettingColor(255, 100, 255))
            .build());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  模块生命周期
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        String error = selfCheck();
        if (error != null) {
            notifyError("启动失败：" + error);
            if (isActive()) toggle();
            return;
        }

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

        notify("§a已启动，开始挖矿循环");
    }

    @Override
    public void onDeactivate() {
        baritone.stop();
        fsm.reset();
        container.reset();
        cmdManager.reset();
        notify("§c已停止");
    }

    /**
     * 启动自检：目标单选、三个WK坐标、五条指令
     */
    private String selfCheck() {
        // 1. 目标单选
        Block overworld = overworldOreTarget.get();
        Block nether = netherOreTarget.get();
        Block block = blockTarget.get();
        
        int selectedCount = 0;
        if (overworld != null && !overworld.equals(Blocks.AIR)) selectedCount++;
        if (nether != null && !nether.equals(Blocks.AIR)) selectedCount++;
        if (block != null && !block.equals(Blocks.AIR)) selectedCount++;
        
        if (selectedCount == 0) {
            return "必须选择一个目标（主世界矿石/下界矿石/普通方块）";
        }
        if (selectedCount > 1) {
            return "只能选择一个目标，当前选择了 " + selectedCount + " 个";
        }

        // 2. 三个 WK 坐标
        if (WKCommand.getMineralChest() == null) {
            return "矿物箱未绑定，请用 .wk set 矿物箱 绑定";
        }
        if (WKCommand.getFoodChest() == null) {
            return "食物箱未绑定，请用 .wk set 食物箱 绑定";
        }
        if (WKCommand.getAFKPoint() == null) {
            return "挂机点未绑定，请用 .wk set 挂机点 绑定";
        }

        // 3. 五条指令
        if (wildCommand.get().isEmpty()) return "去野外指令未填写";
        if (unloadCommand.get().isEmpty()) return "满载卸货指令未填写";
        if (supplyCommand.get().isEmpty()) return "补给指令未填写";
        if (afkCommand.get().isEmpty()) return "挂机点指令未填写";
        if (respawnCommand.get().isEmpty()) return "死亡重返指令未填写";

        return null;
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
            AutoMinerModule_ESP.renderLabel(event, afk.pos, "[挂机点]", 
                afkPointColor.get(), espScale.get().floatValue());
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

    // 公开消息方法供子组件调用
    public void info(String msg) { notify(msg); }
    public void error(String msg) { notifyError(msg); }

    // ═══════════════════════════════════════════════════════════════════
    //  使用说明面板
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{
                "⛏️ §l自动挖矿 · 使用说明"
            },
            new String[]{
                "§e§l▌ 准备工作",
                "§f  1. 准备好挖掘工具（推荐附魔耐久、效率）",
                "§f  2. 准备好武器（修补耐久时用）",
                "§f  3. 放置矿物箱、食物箱（装满食物）",
                "§f  4. 选好挂机点（安全区域，怪物可到达）",
                "§f  5. 使用 .wk 指令绑定三个坐标"
            },
            new String[]{
                "§6§l▌ 指令系统",
                "§f  · " + highlightCommand(".wk set 矿物箱") + " — 准星对准箱子，绑定矿物卸货箱",
                "§f  · " + highlightCommand(".wk set 食物箱") + " — 准星对准箱子，绑定食物补给箱",
                "§f  · " + highlightCommand(".wk set 挂机点") + " — 当前位置绑定为挂机点（含视角）",
                "§f  · " + highlightCommand(".wk remove <目标>") + " — 解绑单个坐标",
                "§f  · " + highlightCommand(".wk clear") + " — 清空所有绑定",
                "§f  · " + highlightCommand(".wk status") + " — 查看绑定状态"
            },
            new String[]{
                "§a§l▌ 状态机流程",
                "§f  1. " + highlightText("去野外") + " — 发送野外指令，等区块加载完成",
                "§f  2. " + highlightText("采掘") + " — Baritone 自动挖矿，满载/饥饿/耐久触发转换",
                "§f  3. " + highlightText("卸货循环") + " — 传送到矿物箱，倒货，返回野外",
                "§f  4. " + highlightText("补给循环") + " — 传送到食物箱，拿食物，吃饱，返回",
                "§f  5. " + highlightText("修补循环") + " — 工具切副手，武器切主手，KillAura打怪修装备",
                "§f  6. " + highlightText("死亡处理") + " — 自动复活，执行死亡重返指令，恢复挖矿"
            },
            new String[]{
                "§b§l▌ 参数建议",
                "§f  · " + highlightText("满载组数") + "：默认20组，矿物达到此数量触发卸货",
                "§f  · " + highlightText("饥饿阈值") + "：默认12，饥饿值低于此值触发补给",
                "§f  · " + highlightText("耐久阈值") + "：默认50，工具剩余耐久低于此值触发修补"
            },
            new String[]{
                "§c§l▌ 注意事项",
                "§f  · " + highlightText("必须单选目标") + "：矿石和方块只能选一个",
                "§f  · " + highlightText("深层变种自动支持") + "：选钻石矿会自动挖深层钻石矿",
                "§f  · " + highlightText("垃圾自动丢弃") + "：圆石、深层圆石等默认已勾选",
                "§f  · " + highlightText("修补需要经验") + "：挂机点附近必须有怪物刷新",
                "§f  · " + highlightText("死亡自愈") + "：复活后自动执行重返指令并恢复挖矿",
                "§f  · " + highlightText("防卡死机制") + "：区块加载检测、掉落检测、指令延迟校验"
            }
        );
    }
}
