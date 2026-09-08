package com.example.addon.villager;

import baritone.api.BaritoneAPI;
import com.example.addon.villager.command.CunminCommand;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.ui.HelpScreen;
import com.example.addon.villager.fsm.VillagerTradeFSM;
import com.example.addon.villager.data.VillagerProfessionRegistry;
import com.example.addon.villager.data.VillagerTradeTarget;
import com.example.addon.villager.logistics.PipelineTask;
import com.example.addon.villager.render.ContainerESP;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动村民交易模块
 * 
 * 功能：
 * · 原地交易模式（不寻路工作站，直接与身边村民交易，绿宝石不足/背包满自动补给/卸货）
 * · 寻路单点模式（Baritone 寻路到工作站，绿宝石不够自动去绿宝石箱补给、满包自动卸货）
 * · 多任务流水线模式（所有已选择物品的职业自动组成队列，完成 1 再做 2，可为不同村民）
 * · 交易通过真实打开村民交易界面发包（26.1.2 协议，无静默交易）
 * · 附魔书精准匹配（忽略等级）
 * · 价格限制
 * · 启动播报 + 每笔成功/失败提示 + 成功提示音
 * 
 * 使用流程：
 * 1. 选择模式
 * 2. 选择职业
 * 3. 选择目标物品
 * 4. 设置价格上限
 * 5. 启动模块
 */

public class AutoVillagerTradeModule extends YiyiaddonModule {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  职业枚举（用于下拉选择器）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public enum ProfessionChoice {
        盔甲匠("minecraft:armorer"),
        屠夫("minecraft:butcher"),
        制图师("minecraft:cartographer"),
        牧师("minecraft:cleric"),
        农民("minecraft:farmer"),
        渔夫("minecraft:fisherman"),
        制箭师("minecraft:fletcher"),
        皮匠("minecraft:leatherworker"),
        图书管理员("minecraft:librarian"),
        石匠("minecraft:mason"),
        牧羊人("minecraft:shepherd"),
        工具匠("minecraft:toolsmith"),
        武器匠("minecraft:weaponsmith");

        private final String id;

        ProfessionChoice(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模块设置（按功能分组，配置页工整易读）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // 基础组：运行模式 / 目标职业 / 补给 / 搜索 / 挂机 / 停止键
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // 目标物品组：各职业物品选择器 + 图书管理员附魔书
    private final SettingGroup sgTarget = settings.createGroup("目标物品");
    // 价格组：各职业价格上限
    private final SettingGroup sgPrice = settings.createGroup("价格上限");
    // 多任务职业组：勾选哪些职业加入多任务队列（仅多任务模式显示）
    private final SettingGroup sgPipeline = settings.createGroup("多任务职业");

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  通用设置
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("运行模式")
        .description("选择交易模式")
        .defaultValue(Mode.LOCAL)
        .build()
    );

    private final Setting<Integer> emeraldSupplyStacks = sgGeneral.add(new IntSetting.Builder()
        .name("绿宝石补给量(组)")
        .description("去绿宝石箱补给时，背包绿宝石补到「底限32个 + 该组数×64」即返回交易")
        .defaultValue(1)
        .min(1)
        .max(27)
        .noSlider()
        .build()
    );

    private final Setting<Integer> searchRange = sgGeneral.add(new IntSetting.Builder()
        .name("搜索范围(格)")
        .description("寻路模式搜索目标村民的半径，村民密集时可调小避免扫到远处村民")
        .defaultValue(96)
        .min(8)
        .max(256)
        .noSlider()
        .build()
    );

    private final Setting<Boolean> idleLoop = sgGeneral.add(new BoolSetting.Builder()
        .name("挂机循环(按钮)")
        .description("仅寻路单点/多任务模式的榨干模式生效：全部村民榨干后不结束，等待补货倒计时到点自动重新循环交易")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> restockWaitSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("补货等待(秒)")
        .description("挂机循环的补货等待时长，与村民补货冷却(2分钟)一致，默认120秒")
        .defaultValue(120)
        .min(20)
        .max(600)
        .noSlider()
        .build()
    );

    private final Setting<ProfessionChoice> profession = sgGeneral.add(new EnumSetting.Builder<ProfessionChoice>()
        .name("目标职业")
        .description("选择村民职业（多任务模式下由「多任务职业」勾选决定，此选项隐藏）")
        .defaultValue(ProfessionChoice.图书管理员)
        .onChanged(value -> updateItemSettings())
        .visible(() -> mode.get() != Mode.PIPELINE)
        .build()
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  目标物品设置（为每个职业生成独立选择器 + 补给数量 + 价格上限）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // 职业物品选择器（存储每个职业的选择器）
    private final Map<String, ItemListSetting> professionItemSettings = new HashMap<>();
    
    // 职业补给数量设置
    private final Map<String, Setting<Integer>> professionSupplySettings = new HashMap<>();
    
    // 职业价格上限设置
    private final Map<String, Setting<Integer>> professionPriceSettings = new HashMap<>();

    // 多任务职业勾选（仅多任务模式显示，勾选的职业才进队列）
    private final Map<String, Setting<Boolean>> pipelineProfessionToggles = new HashMap<>();
    
    // 图书管理员附魔书选择器（使用 Meteor 原生弹窗）
    private EnchantmentListSetting librarianEnchantments;
    
    // 快速停止键
    private Setting<Keybind> stopKey;

    /**
     * 初始化动态物品选择器
     * 顺序：选择器 → 附魔书(仅图书管理员) → 滑块×2
     */
    private void initializeItemSettings() {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  第一步：为 13 个职业生成多任务勾选框（放「多任务职业」组，仅多任务模式显示）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        for (ProfessionChoice profChoice : ProfessionChoice.values()) {
            String profName = profChoice.name();
            Setting<Boolean> toggle = sgPipeline.add(new BoolSetting.Builder()
                .name(profName)
                .description("勾选后该职业加入多任务队列（需在目标物品组先选好该职业的物品）")
                .defaultValue(false)
                .visible(() -> mode.get() == Mode.PIPELINE)
                .build()
            );
            pipelineProfessionToggles.put(profName, toggle);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  第二步：为13个职业生成完整配置
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        for (ProfessionChoice profChoice : ProfessionChoice.values()) {
            String profName = profChoice.name();
            
            // 1. 物品选择器
            ItemListSetting itemSetting = sgTarget.add(new ItemListSetting.Builder()
                .name(profName + "交易")
                .description("点击选择要购买的物品")
                .defaultValue(new ArrayList<>())
                .filter(item -> {
                    VillagerProfession prof = VillagerProfessionRegistry.getProfessionByDisplayName(profName);
                    if (prof == null) return false;
                    Set<Item> targets = VillagerProfessionRegistry.getPurchaseTargets(prof);
                    return targets.contains(item);
                })
                .visible(() -> isProfessionVisible(profName))
                .build()
            );
            professionItemSettings.put(profName, itemSetting);

            // 2. 图书管理员的附魔书：Meteor 原生注册表多选器，弹窗支持搜索与加减
            if (profChoice == ProfessionChoice.图书管理员) {
                librarianEnchantments = sgTarget.add(new EnchantmentListSetting.Builder()
                    .name("图书管理员附魔书")
                    .description("只选择村民能够刷出的附魔类型，交易时忽略附魔等级")
                    .defaultValue()
                    .visible(() -> isProfessionVisible("图书管理员"))
                    .build()
                );
            }

            // 3. 购买总量（组）：默认榨干，购买量不再参与退出判定，直接隐藏
            Setting<Integer> supplySetting = sgTarget.add(new IntSetting.Builder()
                .name(profName + " 购买量(组)")
                .description("已改为默认榨干模式，购买量不再生效")
                .defaultValue(1)
                .min(1)
                .max(64)
                .noSlider()
                .visible(() -> false)
                .build()
            );
            professionSupplySettings.put(profName, supplySetting);
            
            // 4. 价格上限：加减按钮 + 可点框手输
            Setting<Integer> priceSetting = sgPrice.add(new IntSetting.Builder()
                .name(profName + " 价格上限")
                .description("该职业物品的绿宝石价格上限（超过此价格不购买）")
                .defaultValue(32)
                .min(1)
                .max(64)
                .noSlider()
                .visible(() -> isProfessionVisible(profName))
                .build()
            );
            professionPriceSettings.put(profName, priceSetting);
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  快捷键设置（放在最后）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        stopKey = sgGeneral.add(new KeybindSetting.Builder()
            .name("快速停止键")
            .description("按此键立即停止交易")
            .defaultValue(Keybind.none())
            .build()
        );
    }
    
    /**
     * 更新物品设置可见性（职业变化时调用）
     */
    private void updateItemSettings() {
        // visible() 会自动根据当前职业重新计算
        // 无需手动操作，Meteor 会自动刷新 UI
    }

    /**
     * 判断某职业的设置（物品选择器/价格上限/附魔书）是否可见：
     * 多任务模式按「多任务职业勾选」显示；其余模式按「当前目标职业」显示。
     */
    private boolean isProfessionVisible(String profName) {
        if (mode.get() == Mode.PIPELINE) {
            Setting<Boolean> toggle = pipelineProfessionToggles.get(profName);
            return toggle != null && toggle.get();
        }
        ProfessionChoice current = profession.get();
        return current != null && current.name().equals(profName);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  核心逻辑
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final VillagerTradeFSM fsm;
    private final ContainerESP containerESP;

    public AutoVillagerTradeModule() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动村民交易", "自动与村民交易，支持原地和寻路模式。点击按钮查看说明。");
        
        this.fsm = new VillagerTradeFSM();
        this.fsm.setLogger(this::info);
        
        this.containerESP = new ContainerESP();
        
        // 初始化动态物品选择器
        initializeItemSettings();
    }

    @Override
    public void onActivate() {
        // 自检（按模式差异化，缺项会列出并自动关闭）
        if (!reportSelfCheck(selfCheck())) return;

        VillagerProfession prof = getProfessionEnum();
        List<VillagerTradeTarget> targets = buildTargets();

        // 多任务模式：所有「已选择物品」的职业自动组成队列（顺序=职业枚举顺序）
        List<PipelineTask> pipelineTasks = mode.get() == Mode.PIPELINE ? buildPipelineTasks() : null;

        String profName = profession.get().name();
        int maxPrice = professionPriceSettings.get(profName).get();
        int quantity = professionSupplySettings.get(profName).get() * 64;

        // 完成 / 出错时自动关闭模块（结果日志由状态机 logger 已输出，这里只负责关模块）
        fsm.setCompleteHandler(summary -> {
            chatFeedback = false;
            mc.execute(() -> {
                if (isActive()) toggle();
                chatFeedback = true;
            });
        });
        fsm.setErrorHandler(reason -> {
            chatFeedback = false;
            mc.execute(() -> {
                if (isActive()) toggle();
                chatFeedback = true;
            });
        });

        fsm.configure(prof, targets, maxPrice, 32, quantity);
        fsm.setSupplyStacks(emeraldSupplyStacks.get());
        fsm.setSearchRange(searchRange.get());
        fsm.setIdleLoop(idleLoop.get());
        fsm.setRestockWaitTicks(restockWaitSeconds.get() * 20);

        // 启动播报：模式 / 职业 / 目标物品 / 价格上限 / 购买总量（三种模式都要）
        announceStartup(pipelineTasks, quantity);

        boolean started;
        switch (mode.get()) {
            case LOCAL -> started = fsm.start(VillagerTradeFSM.Mode.LOCAL);
            case SINGLE_PATH -> started = fsm.start(VillagerTradeFSM.Mode.SINGLE_PATH);
            case PIPELINE -> started = fsm.startPipeline(pipelineTasks);
            default -> started = false;
        }

        if (!started) {
            error("启动失败（状态机未就绪）");
            chatFeedback = false;
            mc.execute(() -> {
                if (isActive()) toggle();
                chatFeedback = true;
            });
        }
    }

    /**
     * 启动播报：把本次运行参数完整打印到聊天栏，三个模式通用。
     *
     * 整份报告合并成一个消息块输出（一条 info 多行），只带一次模块前缀，
     * 正文统一「标签 + §8▸ + 值」结构。多任务模式逐条列出每个任务；原地模式附带自行管理提示。
     */
    private void announceStartup(List<PipelineTask> pipelineTasks, int quantity) {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 自动村民交易 · 启动报告");

        // 默认榨干：始终展示，覆盖购买总量的语义
        report.append("\n§7交易模式　§8▸ ").append(highlightText("榨干模式"))
            .append("§r§f（无视购买总量，售罄/锁死才收工）");

        if (mode.get() == Mode.PIPELINE) {
            // 多任务模式：逐条列出每个任务的职业与参数
            report.append("\n§7执行模式　§8▸ ").append(highlightText(mode.get().toString())).append("§r")
                .append("§f（共 ").append(highlightText(String.valueOf(pipelineTasks.size())))
                .append("§r§f 个任务，按顺序执行）");
            for (int i = 0; i < pipelineTasks.size(); i++) {
                PipelineTask task = pipelineTasks.get(i);
                List<String> names = new ArrayList<>();
                for (VillagerTradeTarget t : task.getTargets()) names.add(t.getDisplayName());
                report.append("\n§7任务 ").append(String.valueOf(i + 1)).append("　§8▸ §f职业=")
                    .append(highlightText(VillagerProfessionRegistry.getDisplayName(task.getProfession()))).append("§r")
                    .append("§f · 目标=").append(highlightText(String.join(",", names))).append("§r")
                    .append("§f · 价格≤").append(highlightText(String.valueOf(task.getMaxPrice()))).append("§r")
                    .append("§f · 总量=").append(highlightText("不限(榨干)")).append("§r");
            }
        } else {
            // 单职业模式：职业 / 目标 / 价格 / 总量 一行一项
            List<String> names = new ArrayList<>();
            for (VillagerTradeTarget t : buildTargets()) names.add(t.getDisplayName());
            report.append("\n§7执行模式　§8▸ ").append(highlightText(mode.get().toString())).append("§r");
            report.append("\n§7交易职业　§8▸ ").append(highlightText(profession.get().name())).append("§r");
            report.append("\n§7目标物品　§8▸ ").append(highlightText(String.join(",", names))).append("§r");
            report.append("\n§7价格上限　§8▸ ").append(highlightText(String.valueOf(professionPriceSettings.get(profession.get().name()).get()))).append("§r");
            report.append("\n§7购买总量　§8▸ ").append(highlightText("不限(榨干)")).append("§r");
            if (mode.get() == Mode.LOCAL) {
                report.append("\n§e⚠ 原地模式　§8▸ 请靠近").append(highlightText(profession.get().name())).append("§r§f村民（约 3 格内）")
                    .append("\n§e⚠ 玩家自主　§8▸ 只自动交易，绿宝石/背包由你手动管理");
            }
            if (idleLoop.get() && mode.get() != Mode.LOCAL) {
                report.append("\n§d⏳ 挂机循环　§8▸ 榨干后等待补货 ").append(highlightText(restockWaitSeconds.get() + " 秒")).append("§r§f 自动循环");
            }
        }

        info(report.toString());
    }

    @Override
    public void onDeactivate() {
        fsm.stop();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isActive()) return;
        containerESP.render(event);
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null) return;
        // 静默容器：交易运行中打开村民交易界面/箱子屏幕时取消显示（不抢鼠标），
        // 交易界面数据仍由 mc.player.containerMenu 同步，SelectTrade/取绿宝石照常发包。
        // 排除背包(InventoryScreen)与创造模式背包(CreativeModeInventoryScreen)：
        // 玩家手动按 E 打开背包必须放行，不能被模块拦掉。
        // 创造模式下按 E 会先开 InventoryScreen、init 检测创造模式后转 CreativeModeInventoryScreen，
        // 若后者被拦掉会导致 RecipeBookComponent.book 未初始化（init 未走 super）而 NPE 闪退。
        if (isActive() && event.screen instanceof AbstractContainerScreen<?>
            && !(event.screen instanceof InventoryScreen)
            && !(event.screen instanceof CreativeModeInventoryScreen)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;

        // 检查快速停止键
        if (stopKey.get().isPressed()) {
            info("§e检测到快速停止键，停止交易");
            toggle();
            return;
        }

        // 检查异常情况
        if (mc.player == null) {
            fsm.handleException("玩家无效");
            toggle();
            return;
        }
        
        if (mc.level == null) {
            fsm.handleException("世界无效");
            toggle();
            return;
        }

        fsm.tick();
    }

    /**
     * 自检（参考 AutoMinerModule）
     * 检查：职业、目标物品、容器绑定、背包绿宝石
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();

        // 1. 职业验证
        VillagerProfession prof = getProfessionEnum();
        if (prof == null) {
            missing.add("§e目标职业§f·未选择");
        }

        // 2. 目标物品验证（多任务模式校验整个队列 13 个职业，而不是当前职业）
        List<PipelineTask> pipelineTasks = mode.get() == Mode.PIPELINE ? buildPipelineTasks() : null;
        if (mode.get() == Mode.PIPELINE) {
            if (pipelineTasks == null || pipelineTasks.isEmpty()) {
                missing.add("§e多任务§f·未勾选任何职业（请在「多任务职业」组勾选并选好物品）");
            }
        } else if (buildTargets().isEmpty()) {
            missing.add("§e目标物品§f·未选择");
        }

        // 3. 容器绑定检测（三种模式都需要：绿宝石不足/背包满时自动补给/卸货）
        if (CunminCommand.getEmeraldChestPos() == null) {
            missing.add("§a绿宝石箱§f·未绑定");
        }
        if (CunminCommand.getUnloadChestPos() == null) {
            missing.add("§b成品交易箱§f·未绑定");
        }

        // 5. 目标职业村民检测：搜索半径内必须存在目标职业村民
        //    原地模式用实际交易交互距离 3.2 格（与状态机 isValidTarget 一致）；
        //    寻路/多任务模式与状态机一致，使用可配置的搜索范围。多任务模式对队列里每个职业都检测一遍
        List<VillagerProfession> checkProfessions = new ArrayList<>();
        if (mode.get() == Mode.PIPELINE && pipelineTasks != null) {
            for (PipelineTask task : pipelineTasks) {
                if (!checkProfessions.contains(task.getProfession())) {
                    checkProfessions.add(task.getProfession());
                }
            }
        } else if (prof != null) {
            checkProfessions.add(prof);
        }

        if (mc.player != null && mc.level != null) {
            double radius = mode.get() == Mode.LOCAL ? 3.2 : searchRange.get();
            for (VillagerProfession p : checkProfessions) {
                boolean found = !mc.level.getEntitiesOfClass(Villager.class,
                    mc.player.getBoundingBox().inflate(radius),
                    v -> isProfessionMatch(v, p)
                ).isEmpty();
                if (!found) {
                    missing.add(String.format("§e%s村民§f·%.0f 格内未找到%s",
                        VillagerProfessionRegistry.getDisplayName(p), radius,
                        mode.get() == Mode.LOCAL ? "，请靠近村民（原地模式需站在村民旁约 3 格）" : ""));
                }
            }
        }

        // 6. Baritone 验证（补给/卸货寻路都需要，三种模式通用）
        try {
            if (BaritoneAPI.getProvider().getPrimaryBaritone() == null) {
                missing.add("§cBaritone§f·未安装或未启用");
            }
        } catch (Throwable e) {
            missing.add("§cBaritone§f·未安装或未启用");
        }

        return missing;
    }

    /**
     * 村民职业是否匹配目标职业（职业数据异常时视为不匹配）
     */
    private static boolean isProfessionMatch(Villager villager, VillagerProfession prof) {
        if (villager == null || !villager.isAlive() || prof == null) return false;
        try {
            // 26.1.2：VillagerProfession 是 Record，常量是 ResourceKey，
            // 必须用注册表 Identifier 比较，value().equals() 会把傻子/失业村民误匹配进来
            Identifier targetId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
            return targetId != null && villager.getVillagerData().profession().is(targetId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取当前选择的职业枚举
     */
    private VillagerProfession getProfessionEnum() {
        ProfessionChoice choice = profession.get();
        return VillagerProfessionRegistry.getProfessionByDisplayName(choice.name());
    }

    /**
     * 构建当前职业的目标列表
     */
    private List<VillagerTradeTarget> buildTargets() {
        return buildTargetsFor(profession.get());
    }

    /**
     * 构建指定职业的目标列表（多任务队列生成时复用）
     */
    private List<VillagerTradeTarget> buildTargetsFor(ProfessionChoice choice) {
        List<VillagerTradeTarget> targets = new ArrayList<>();

        VillagerProfession prof = VillagerProfessionRegistry.getProfessionByDisplayName(choice.name());
        if (prof == null) return targets;

        String profName = choice.name();
        boolean isLibrarian = profName.equals("图书管理员");

        // 获取该职业的物品选择器
        ItemListSetting itemSetting = professionItemSettings.get(profName);
        if (itemSetting != null) {
            List<Item> selectedItems = itemSetting.get();
            for (Item item : selectedItems) {
                // 物品显示名走本地化（跟随客户端语言），不再用英文注册表 ID
                String displayName = item.getDefaultInstance().getHoverName().getString();
                targets.add(new VillagerTradeTarget(item, displayName));
            }
        }

        if (isLibrarian && librarianEnchantments != null) {
            // 附魔是动态注册表，必须走 level 的 registryAccess 解析本地化名（回退到命名空间 ID）
            var enchantRegistry = (mc.level != null)
                ? mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                : null;
            for (ResourceKey<Enchantment> enchantment : librarianEnchantments.get()) {
                String enchantmentId = enchantment.identifier().toString();
                if (!VillagerProfessionRegistry.getLibrarianEnchantments().contains(
                    enchantment.identifier().getPath())) continue;

                String enchantName = (enchantRegistry != null)
                    ? enchantRegistry.get(enchantment)
                        .map(holder -> holder.value().description().getString())
                        .orElse(enchantment.identifier().getPath())
                    : enchantment.identifier().getPath();

                VillagerTradeTarget target = new VillagerTradeTarget(
                    Items.ENCHANTED_BOOK,
                    "附魔书·" + enchantName
                );
                target.setEnchantmentId(enchantmentId);
                targets.add(target);
            }
        }

        return targets;
    }

    /**
     * 构建多任务队列：只遍历「多任务职业组」里勾选的职业（且该职业已选物品）。
     * 顺序 = 职业枚举顺序（盔甲匠 → 武器匠）；每个任务独立价格上限与购买总量，
     * 由状态机 NEXT_TASK 逐个推进：完成任务 1 后自动开始任务 2，可为不同职业村民。
     */
    private List<PipelineTask> buildPipelineTasks() {
        List<PipelineTask> tasks = new ArrayList<>();
        for (ProfessionChoice choice : ProfessionChoice.values()) {
            String profName = choice.name();
            // 未在「多任务职业」勾选的职业直接跳过
            Setting<Boolean> toggle = pipelineProfessionToggles.get(profName);
            if (toggle == null || !toggle.get()) continue;

            List<VillagerTradeTarget> targets = buildTargetsFor(choice);
            if (targets.isEmpty()) continue;

            VillagerProfession prof = VillagerProfessionRegistry.getProfessionByDisplayName(profName);
            if (prof == null) continue;

            int price = professionPriceSettings.get(profName).get();
            int qty = professionSupplySettings.get(profName).get() * 64;
            tasks.add(new PipelineTask(prof, targets, price, qty));
        }
        return tasks;
    }

    /**
     * 运行模式
     */
    public enum Mode {
        LOCAL("原地交易"),
        SINGLE_PATH("寻路单点"),
        PIPELINE("多任务");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  GUI 点位设置卡片（底部按钮区域）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            // ═══════════════════════════════════════════════════════════════════
            //  使用说明按钮（置顶显眼位置，参考自动挖矿模块）
            // ═══════════════════════════════════════════════════════════════════
            WButton helpBtn = theme.button("§e查看使用说明");
            helpBtn.action = () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();

            // 多任务模式说明（全宽一行）
            table.add(theme.label("§7多任务模式 = 依次执行所有「已选择物品」的职业，"
                + "顺序为 盔甲匠→…→武器匠，完成一个再下一个")).expandX().center();
            table.row();

            // 点位设置卡片区（两列布局）
            WTable cardRow = theme.table();
            
            // 绿宝石箱卡片
            buildLocationCard(theme, cardRow, "绿宝石箱", "emerald_chest");
            
            // 成品交易箱卡片
            buildLocationCard(theme, cardRow, "成品交易箱", "unload_chest");
            
            // 将两列容器加入主表格
            table.add(cardRow).expandX();
            table.row();
        });
    }

    /**
     * 构建使用说明内容（HelpScreen 风格，参考 AutoMinerModule）
     */
    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("模块定位",
                "  §8├─ §f交易通过真实打开村民交易界面发包 §7(26.1.2 协议，无静默交易)",
                "  §8├─ §f面向固定村民交易所：村民提前手动解锁至大师级",
                "  §8└─ §f自检通过才能启动，缺项一次性列全"
            ),
            new HelpScreen.HelpSection("三种模式",
                "  §b▸ §e原地交易 §8- §f不寻路工作站，直接与身边村民交易",
                "    §7绿宝石不足/背包满时自动去箱子补给/卸货",
                "  §b▸ §e寻路单点 §8- §fBaritone 寻路到工作站自动交易",
                "    §7绿宝石不足自动去绿宝石箱补给，背包满自动卸货",
                "  §b▸ §e多任务 §8- §f所有已选择物品的职业组成队列依次执行",
                "    §7顺序 盔甲匠→…→武器匠，完成 1 再做 2，可为不同村民"
            ),
            new HelpScreen.HelpSection("默认榨干 §7(已内置，无需开关)",
                "  §d▸ §f交易默认就是榨干模式，一路买到目标交易全部「售罄/锁死」才收工",
                "  §d▸ §f补给卸货循环照常，直到目标交易榨干/锁死为止",
                "  §d▸ §f买不到、村民消失或绿宝石箱也空了才停"
            ),
            new HelpScreen.HelpSection("使用流程",
                "  §8> §e1§8. §f选择模式、目标职业、目标物品",
                "  §8> §e2§8. §f图书管理员可同时勾选附魔书 §7(自动忽略等级)",
                "  §8> §e3§8. §f设置价格上限，可调绿宝石补给量",
                "  §8> §e4§8. §f先绑定绿宝石箱与成品交易箱再开模块",
                "  §8> §e5§8. §f自检通过即开始；快速停止键可随时终止"
            ),
            new HelpScreen.HelpSection("点位设置 §7(三种模式通用)",
                "  §8> §3.cunmin set 绿宝石箱 §8— §7准星对准箱子绑定",
                "  §8> §3.cunmin set 成品交易箱 §8— §7准星对准箱子绑定",
                "  §8> §3.cunmin status §8— §7查看绑定状态",
                "  §8> §3.cunmin remove 绿宝石箱 §8— §7解绑",
                "  §7§o也可直接点击配置页底部卡片中的「设置」按钮"
            ),
            new HelpScreen.HelpSection("状态反馈",
                "  §a✓ §f每笔交易成功播报 + 村民交易提示音",
                "  §e✗ §f确认失败自动重发 1 次，连败自动跳过",
                "  §7启动播报：模式/职业/物品/价格/总量，一目了然"
            ),
            new HelpScreen.HelpSection("注意事项",
                "  §c⚠ §f原地模式交易距离仅约 3 格，请站在村民旁边",
                "  §c⚠ §f多任务模式至少给一个职业选择物品，否则无法启动"
            )
        );
    }

    /**
     * 构建点位设置卡片（参考 AutoMinerModule）
     */
    private void buildLocationCard(GuiTheme theme, WTable parentTable, String title, String key) {
        // 创建卡片容器（垂直布局）
        WTable card = theme.table();
        
        // 绑定状态
        var pos = key.equals("emerald_chest") ? CunminCommand.getEmeraldChestPos() : CunminCommand.getUnloadChestPos();
        var dim = key.equals("emerald_chest") ? CunminCommand.getEmeraldChestDimension() : CunminCommand.getUnloadChestDimension();
        boolean isBound = pos != null;
        
        // 获取对应的颜色
        String titleColor = key.equals("emerald_chest") ? "§a" : "§6";
        
        // 标题
        card.add(theme.label(titleColor + title)).expandX().center();
        card.row();
        
        // 状态显示（固定两行，保持高度一致）
        if (isBound) {
            String coords = String.format("§7X§f%d §7Y§f%d §7Z§f%d", 
                pos.getX(), pos.getY(), pos.getZ());
            card.add(theme.label(coords)).expandX().center();
            card.row();
            
            // 维度显示（灰色）
            String dimName = getDimensionDisplayName(dim);
            card.add(theme.label("§7" + dimName)).expandX().center();
            card.row();
        } else {
            card.add(theme.label("§8暂未绑定")).expandX().center();
            card.row();
            card.add(theme.label("§8-")).expandX().center();  // 占位符
            card.row();
        }
        
        // 设置按钮（已绑定=亮绿色，未绑定=暗灰色）
        String setBtnColor = isBound ? "§a" : "§8";
        WButton setBtn = theme.button(setBtnColor + "设置");
        setBtn.action = () -> {
            CunminCommand.setBinding(key);
            mc.setScreen(null);
        };
        card.add(setBtn).expandX().center();
        card.row();
        
        // 删除按钮（红色）
        WButton delBtn = theme.button("§c删除");
        delBtn.action = () -> {
            if (isBound) {
                CunminCommand.removeBinding(key);
                mc.setScreen(null);
            }
        };
        card.add(delBtn).expandX().center();
        
        // 将卡片加入父表格（横向排列）
        parentTable.add(card).expandX();
    }

    /**
     * 获取维度显示名称
     */
    private String getDimensionDisplayName(ResourceKey<Level> dimension) {
        if (dimension == null) return "未知";

        // 按字符串匹配维度 ID：ResourceKey 不可靠 == 比较（世界数据重新解析的实例），
        // 与 CunminCommand 持久化的 dimension.toString() 格式保持一致
        String id = dimension.toString();
        if (id.contains("minecraft:overworld")) return "主世界";
        if (id.contains("minecraft:the_nether")) return "下界";
        if (id.contains("minecraft:the_end")) return "末地";
        return id;
    }
}
