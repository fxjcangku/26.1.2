package com.example.addon.enchant;

import baritone.api.BaritoneAPI;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.enchant.gear.AcceptanceStrategy;
import com.example.addon.enchant.gear.AnvilPlan;
import com.example.addon.enchant.gear.AnvilPlanner;
import com.example.addon.enchant.gear.AnvilStep;
import com.example.addon.enchant.gear.EnchantEvaluationService;
import com.example.addon.enchant.gear.GearEnchantSetting;
import com.example.addon.enchant.gear.GearEnchantTask;
import com.example.addon.enchant.gear.GearSafetyGuard;
import com.example.addon.enchant.gear.GearTaskQueue;
import com.example.addon.enchant.gear.RecoveryValidator;
import com.example.addon.enchant.gear.RetryGuard;
import com.example.addon.enchant.gear.TargetProfile;
import com.example.addon.enchant.gear.TaskErrorReason;
import com.example.addon.enchant.gear.XpPlanner;
import com.example.addon.enchant.point.PointType;
import com.example.addon.farm.FarmPacketOps;
import com.example.addon.ui.HelpScreen;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 自动附魔 —— 经验获取→定向附魔→极品剔除→洗练仓储全自动闭环。
 *
 * 状态机：IDLE → 补给/打怪 → 附魔 → 鉴定 → 存成品 或 砂轮洗练 → 循环。
 */
public class AutoEnchantBook extends YiyiaddonModule {

    // ── 设置面板 ──────────────────────────────────────────────────────────

    private final SettingGroup sgBasic    = settings.createGroup("基础设置");
    private final SettingGroup sgAdvanced = settings.createGroup("自动附魔分类");
    private final SettingGroup sgCustom   = settings.createGroup("自定义附魔");
    private final SettingGroup sgVanilla  = settings.createGroup("原版附魔分类");
    private final SettingGroup sgSword    = settings.createGroup("剑附魔属性");
    private final SettingGroup sgAxe      = settings.createGroup("斧头附魔属性");
    private final SettingGroup sgBow      = settings.createGroup("弓附魔属性");
    private final SettingGroup sgArmor    = settings.createGroup("护甲附魔属性");
    private final SettingGroup sgOther    = settings.createGroup("工具与通用附魔属性");
    private final SettingGroup sgVanillaArmor    = settings.createGroup("原版防具附魔");
    private final SettingGroup sgVanillaMelee    = settings.createGroup("原版近战附魔");
    private final SettingGroup sgVanillaTool     = settings.createGroup("原版工具附魔");
    private final SettingGroup sgVanillaBow      = settings.createGroup("原版弓附魔");
    private final SettingGroup sgVanillaFishing  = settings.createGroup("原版钓竿附魔");
    private final SettingGroup sgVanillaTrident  = settings.createGroup("原版三叉戟附魔");
    private final SettingGroup sgVanillaCrossbow = settings.createGroup("原版弩附魔");
    private final SettingGroup sgVanillaCommon   = settings.createGroup("原版通用附魔");
    private final SettingGroup sgGear            = settings.createGroup("原版装备附魔");

    // 基础设置
    private final Setting<TargetMode> 目标模式 = sgBasic.add(new EnumSetting.Builder<TargetMode>()
        .name("目标模式").description("原版装备附魔 / 原版附魔书 / 自定义附魔，三模式互斥切换").defaultValue(TargetMode.BOOK)
        .onChanged(mode -> 刷新模式界面())
        .build());

    private final Setting<Integer> 单轮抽取次数 = sgBasic.add(new IntSetting.Builder()
        .name("单轮抽取次数").description("挂机循环每轮附魔最大次数，纯附魔模式忽略此项").defaultValue(10).min(1).max(100).noSlider()
        .visible(() -> 目标模式.get() != TargetMode.GEAR).build());

    private final Setting<Integer> GUI操作延迟 = sgBasic.add(new IntSetting.Builder()
        .name("GUI操作延迟(Tick)").description("所有 GUI 点击之间的等待 Tick 数").defaultValue(1).min(1).max(10).noSlider().build());

    private final Setting<Integer> 书本补给组数 = sgBasic.add(new IntSetting.Builder()
        .name("书本补给组数").description("每次去书箱抓取的组数（1组=64本）").defaultValue(1).min(1).max(10).noSlider()
        .visible(() -> 目标模式.get() != TargetMode.GEAR).build());

    private final Setting<Integer> 青金石补给组数 = sgBasic.add(new IntSetting.Builder()
        .name("青金石补给组数").description("每次去青金石箱抓取的组数（1组=64个）").defaultValue(1).min(1).max(10).noSlider().build());

    private final Setting<Integer> 每批取用数量 = sgBasic.add(new IntSetting.Builder()
        .name("每批取用数量").description("每次任务最多从装备箱取用的目标装备数量，铁砧合并会消耗装备，最终完成数可能小于此值")
        .defaultValue(4).min(1).max(16).noSlider().visible(() -> 目标模式.get() == TargetMode.GEAR).build());

    private final Setting<List<String>> 自定义附魔 = sgCustom.add(new StringListSetting.Builder()
        .name("自定义附魔目标")
        .description("每行填写一个附魔名称和等级，例如：打雷 5。支持中文、阿拉伯数字、罗马数字和不带等级的附魔。")
        .defaultValue(List.of())
        .visible(() -> 目标模式.get() == TargetMode.CUSTOM)
        .build());

    // 三个目标模式各自独立的运行模式开关，互不干扰（仅当前目标模式对应的开关可见并生效）
    private final Setting<RunMode> 装备运行模式 = sgBasic.add(new EnumSetting.Builder<RunMode>()
        .name("装备运行模式").description("原版装备附魔：纯附魔只消耗当前经验，不足则停机；挂机循环前往挂机点刷经验").defaultValue(RunMode.EXPERIENCE)
        .visible(() -> 目标模式.get() == TargetMode.GEAR).build());

    private final Setting<RunMode> 附魔书运行模式 = sgBasic.add(new EnumSetting.Builder<RunMode>()
        .name("附魔书运行模式").description("原版附魔书：纯附魔只消耗当前经验，不足则停机；挂机循环前往挂机点刷经验").defaultValue(RunMode.EXPERIENCE)
        .visible(() -> 目标模式.get() == TargetMode.BOOK).build());

    private final Setting<RunMode> 自定义运行模式 = sgBasic.add(new EnumSetting.Builder<RunMode>()
        .name("自定义运行模式").description("自定义附魔：纯附魔只消耗当前经验，不足则停机；挂机循环前往挂机点刷经验").defaultValue(RunMode.EXPERIENCE)
        .visible(() -> 目标模式.get() == TargetMode.CUSTOM).build());

    private final Setting<Boolean> ESP标点 = sgBasic.add(new BoolSetting.Builder()
        .name("ESP标点").description("显示已设置点位的名称").defaultValue(true).visible(() -> false).build());

    private final Setting<Boolean> 返回挂机视角 = sgBasic.add(new BoolSetting.Builder()
        .name("返回挂机视角").description("到达挂机位后恢复设置该点位时记录的视角").defaultValue(true).visible(() -> false).build());

    private final Setting<Boolean> 成功提示音 = sgBasic.add(new BoolSetting.Builder()
        .name("成功提示音").description("刷到已选择的目标附魔书时播放本地提示音").defaultValue(true)
        .visible(() -> 目标模式.get() != TargetMode.GEAR).build());

    private final Setting<SuccessSound> 成功提示音类型 = sgBasic.add(new EnumSetting.Builder<SuccessSound>()
        .name("成功提示音类型").description("选择刷到目标附魔书时播放的音效").defaultValue(SuccessSound.CHALLENGE_COMPLETE)
        .visible(() -> 目标模式.get() != TargetMode.GEAR && 成功提示音.get()).build());

    // ── 剑类极品 ────────────────────────────────────────────────────────
    // 传说
    private final Setting<Boolean> 剑_双刃剑5   = sgSword.add(new BoolSetting.Builder().name("双刃剑 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_背刺5     = sgSword.add(new BoolSetting.Builder().name("背刺 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_饕餮5     = sgSword.add(new BoolSetting.Builder().name("饕餮 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_定身5     = sgSword.add(new BoolSetting.Builder().name("定身 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_干扰5     = sgSword.add(new BoolSetting.Builder().name("干扰 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_势破5     = sgSword.add(new BoolSetting.Builder().name("势破 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_折锋5     = sgSword.add(new BoolSetting.Builder().name("折锋 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_暗影突袭5 = sgSword.add(new BoolSetting.Builder().name("暗影突袭 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_永夜5     = sgSword.add(new BoolSetting.Builder().name("永夜 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_灵魂收割5 = sgSword.add(new BoolSetting.Builder().name("灵魂收割 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_生死判5   = sgSword.add(new BoolSetting.Builder().name("生死判 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_破败5     = sgSword.add(new BoolSetting.Builder().name("破败 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_镇魂5     = sgSword.add(new BoolSetting.Builder().name("镇魂 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_忍者5     = sgSword.add(new BoolSetting.Builder().name("忍者 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_忍术5     = sgSword.add(new BoolSetting.Builder().name("忍术 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_狂热5     = sgSword.add(new BoolSetting.Builder().name("狂热 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_破釜5     = sgSword.add(new BoolSetting.Builder().name("破釜 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_血怒5     = sgSword.add(new BoolSetting.Builder().name("血怒 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_跃斩5     = sgSword.add(new BoolSetting.Builder().name("跃斩 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_退散5     = sgSword.add(new BoolSetting.Builder().name("退散 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_速攻5     = sgSword.add(new BoolSetting.Builder().name("速攻 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_首击5     = sgSword.add(new BoolSetting.Builder().name("首击 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_骑士5     = sgSword.add(new BoolSetting.Builder().name("骑士 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_法术大炮5 = sgSword.add(new BoolSetting.Builder().name("法术大炮 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_冷血术5   = sgSword.add(new BoolSetting.Builder().name("冷血术 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_剑卫5     = sgSword.add(new BoolSetting.Builder().name("剑卫 5").defaultValue(false).build());
    // 稀世
    private final Setting<Boolean> 剑_丛刃5     = sgSword.add(new BoolSetting.Builder().name("丛刃 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_决斗5     = sgSword.add(new BoolSetting.Builder().name("决斗 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_利刃5     = sgSword.add(new BoolSetting.Builder().name("利刃 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_剑气5     = sgSword.add(new BoolSetting.Builder().name("剑气 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_名刀司命5 = sgSword.add(new BoolSetting.Builder().name("名刀司命 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_咒刃5     = sgSword.add(new BoolSetting.Builder().name("咒刃 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_嗜血5     = sgSword.add(new BoolSetting.Builder().name("嗜血 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_天谴5     = sgSword.add(new BoolSetting.Builder().name("天谴 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_天道5     = sgSword.add(new BoolSetting.Builder().name("天道 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_奥术5     = sgSword.add(new BoolSetting.Builder().name("奥术 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_对决咒术5 = sgSword.add(new BoolSetting.Builder().name("对决咒术 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_弑君5     = sgSword.add(new BoolSetting.Builder().name("弑君 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_弑魔5     = sgSword.add(new BoolSetting.Builder().name("弑魔 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_惩戒咒术5 = sgSword.add(new BoolSetting.Builder().name("惩戒咒术 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_慈悲5     = sgSword.add(new BoolSetting.Builder().name("慈悲 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_生灵咒术5 = sgSword.add(new BoolSetting.Builder().name("生灵咒术 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_终结5     = sgSword.add(new BoolSetting.Builder().name("终结 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_脉冲刃5   = sgSword.add(new BoolSetting.Builder().name("脉冲刃 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_蛇吻5     = sgSword.add(new BoolSetting.Builder().name("蛇吻 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_血偿5     = sgSword.add(new BoolSetting.Builder().name("血偿 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_钝锋5     = sgSword.add(new BoolSetting.Builder().name("钝锋 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_涅槃5     = sgSword.add(new BoolSetting.Builder().name("涅槃 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_心灵感应5 = sgSword.add(new BoolSetting.Builder().name("心灵感应 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_归一5     = sgSword.add(new BoolSetting.Builder().name("归一 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_高傲5     = sgSword.add(new BoolSetting.Builder().name("高傲 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_蔑视5     = sgSword.add(new BoolSetting.Builder().name("蔑视 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_焚天5     = sgSword.add(new BoolSetting.Builder().name("焚天 5").defaultValue(false).build());
    private final Setting<Boolean> 剑_讨价还价5 = sgSword.add(new BoolSetting.Builder().name("讨价还价 5").defaultValue(false).build());

    // ── 斧类极品 ────────────────────────────────────────────────────────
    private final Setting<Boolean> 斧_定身5     = sgAxe.add(new BoolSetting.Builder().name("定身 5").defaultValue(false).build());
    private final Setting<Boolean> 斧_涅槃5     = sgAxe.add(new BoolSetting.Builder().name("涅槃 5").defaultValue(false).build());
    private final Setting<Boolean> 斧_心灵感应5 = sgAxe.add(new BoolSetting.Builder().name("心灵感应 5").defaultValue(false).build());
    private final Setting<Boolean> 斧_破釜5     = sgAxe.add(new BoolSetting.Builder().name("破釜 5").defaultValue(false).build());
    private final Setting<Boolean> 斧_跃斩5     = sgAxe.add(new BoolSetting.Builder().name("跃斩 5").defaultValue(false).build());

    // ── 弓类极品 ────────────────────────────────────────────────────────
    // 传说
    private final Setting<Boolean> 弓_势破5     = sgBow.add(new BoolSetting.Builder().name("势破 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_宣判5     = sgBow.add(new BoolSetting.Builder().name("宣判 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_折锋5     = sgBow.add(new BoolSetting.Builder().name("折锋 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_暗影突袭5 = sgBow.add(new BoolSetting.Builder().name("暗影突袭 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_永夜5     = sgBow.add(new BoolSetting.Builder().name("永夜 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_浮尘5     = sgBow.add(new BoolSetting.Builder().name("浮尘 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_灵魂收割5 = sgBow.add(new BoolSetting.Builder().name("灵魂收割 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_生死判5   = sgBow.add(new BoolSetting.Builder().name("生死判 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_破败5     = sgBow.add(new BoolSetting.Builder().name("破败 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_禁锢6     = sgBow.add(new BoolSetting.Builder().name("禁锢 6").defaultValue(false).build());
    private final Setting<Boolean> 弓_镇魂5     = sgBow.add(new BoolSetting.Builder().name("镇魂 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_忍士5     = sgBow.add(new BoolSetting.Builder().name("忍士 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_标记5     = sgBow.add(new BoolSetting.Builder().name("标记 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_狂热5     = sgBow.add(new BoolSetting.Builder().name("狂热 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_猎手5     = sgBow.add(new BoolSetting.Builder().name("猎手 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_穿颅5     = sgBow.add(new BoolSetting.Builder().name("穿颅 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_五言5     = sgBow.add(new BoolSetting.Builder().name("五言 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_空军5     = sgBow.add(new BoolSetting.Builder().name("空军 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_首射5     = sgBow.add(new BoolSetting.Builder().name("首射 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_骑射5     = sgBow.add(new BoolSetting.Builder().name("骑射 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_鸣踪5     = sgBow.add(new BoolSetting.Builder().name("鸣踪 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_玻璃大炮5 = sgBow.add(new BoolSetting.Builder().name("玻璃大炮 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_退散5     = sgBow.add(new BoolSetting.Builder().name("退散 5").defaultValue(false).build());
    // 稀世
    private final Setting<Boolean> 弓_天道5     = sgBow.add(new BoolSetting.Builder().name("天道 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_弑君5     = sgBow.add(new BoolSetting.Builder().name("弑君 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_真三言5   = sgBow.add(new BoolSetting.Builder().name("真三言 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_苍劲5     = sgBow.add(new BoolSetting.Builder().name("苍劲 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_苍穹5     = sgBow.add(new BoolSetting.Builder().name("苍穹 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_神射手5   = sgBow.add(new BoolSetting.Builder().name("神射手 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_创伤5     = sgBow.add(new BoolSetting.Builder().name("创伤 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_弓魄5     = sgBow.add(new BoolSetting.Builder().name("弓魄 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_狂妄5     = sgBow.add(new BoolSetting.Builder().name("狂妄 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_焚天5     = sgBow.add(new BoolSetting.Builder().name("焚天 5").defaultValue(false).build());
    private final Setting<Boolean> 弓_夺金5     = sgBow.add(new BoolSetting.Builder().name("夺金 5").defaultValue(false).build());

    // ── 护甲极品 ────────────────────────────────────────────────────────
    // 传说
    private final Setting<Boolean> 甲_不懈5     = sgArmor.add(new BoolSetting.Builder().name("不懈 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_不灭5     = sgArmor.add(new BoolSetting.Builder().name("不灭 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_临阵脱逃5 = sgArmor.add(new BoolSetting.Builder().name("临阵脱逃 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_光环5     = sgArmor.add(new BoolSetting.Builder().name("光环 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_启迪5     = sgArmor.add(new BoolSetting.Builder().name("启迪 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_圣愈5     = sgArmor.add(new BoolSetting.Builder().name("圣愈 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_均衡之法5 = sgArmor.add(new BoolSetting.Builder().name("均衡之法 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_均衡之遁5 = sgArmor.add(new BoolSetting.Builder().name("均衡之遁 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_幸运5     = sgArmor.add(new BoolSetting.Builder().name("幸运 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_清风引5   = sgArmor.add(new BoolSetting.Builder().name("清风引 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_狂潮5     = sgArmor.add(new BoolSetting.Builder().name("狂潮 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_磐石5     = sgArmor.add(new BoolSetting.Builder().name("磐石 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_祭祀5     = sgArmor.add(new BoolSetting.Builder().name("祭祀 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_虚幻5     = sgArmor.add(new BoolSetting.Builder().name("虚幻 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_血罡5     = sgArmor.add(new BoolSetting.Builder().name("血罡 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_霜滞5     = sgArmor.add(new BoolSetting.Builder().name("霜滞 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_倔强5     = sgArmor.add(new BoolSetting.Builder().name("倔强 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_均衡之御5 = sgArmor.add(new BoolSetting.Builder().name("均衡之御 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_均衡之攻5 = sgArmor.add(new BoolSetting.Builder().name("均衡之攻 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_时速5     = sgArmor.add(new BoolSetting.Builder().name("时速 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_金钟罩5   = sgArmor.add(new BoolSetting.Builder().name("金钟罩 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_钢铁胃5   = sgArmor.add(new BoolSetting.Builder().name("钢铁胃 5").defaultValue(false).build());
    // 稀世
    private final Setting<Boolean> 甲_乾坤5     = sgArmor.add(new BoolSetting.Builder().name("乾坤 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_力场5     = sgArmor.add(new BoolSetting.Builder().name("力场 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_圣之守护5 = sgArmor.add(new BoolSetting.Builder().name("圣之守护 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_复苏之风5 = sgArmor.add(new BoolSetting.Builder().name("复苏之风 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_奥术壁垒5 = sgArmor.add(new BoolSetting.Builder().name("奥术壁垒 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_奥术血统5 = sgArmor.add(new BoolSetting.Builder().name("奥术血统 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_招架5     = sgArmor.add(new BoolSetting.Builder().name("招架 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_无畏契约5 = sgArmor.add(new BoolSetting.Builder().name("无畏契约 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_星穹5     = sgArmor.add(new BoolSetting.Builder().name("星穹 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_村庄英雄5 = sgArmor.add(new BoolSetting.Builder().name("村庄英雄 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_物法皆修5 = sgArmor.add(new BoolSetting.Builder().name("物法皆修 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_狂骨5     = sgArmor.add(new BoolSetting.Builder().name("狂骨 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_玄煞5     = sgArmor.add(new BoolSetting.Builder().name("玄煞 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_生命源泉5 = sgArmor.add(new BoolSetting.Builder().name("生命源泉 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_生命潮汐5 = sgArmor.add(new BoolSetting.Builder().name("生命潮汐 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_盛宴5     = sgArmor.add(new BoolSetting.Builder().name("盛宴 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_貔貅5     = sgArmor.add(new BoolSetting.Builder().name("貔貅 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_逆鳞5     = sgArmor.add(new BoolSetting.Builder().name("逆鳞 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_旺盛5     = sgArmor.add(new BoolSetting.Builder().name("旺盛 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_捍卫5     = sgArmor.add(new BoolSetting.Builder().name("捍卫 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_决战5     = sgArmor.add(new BoolSetting.Builder().name("决战 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_混沌5     = sgArmor.add(new BoolSetting.Builder().name("混沌 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_燃血5     = sgArmor.add(new BoolSetting.Builder().name("燃血 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_祭血5     = sgArmor.add(new BoolSetting.Builder().name("祭血 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_重型装甲5 = sgArmor.add(new BoolSetting.Builder().name("重型装甲 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_意志5     = sgArmor.add(new BoolSetting.Builder().name("意志 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_噬灵5     = sgArmor.add(new BoolSetting.Builder().name("噬灵 5").defaultValue(false).build());
    private final Setting<Boolean> 甲_破格5     = sgArmor.add(new BoolSetting.Builder().name("破格 5").defaultValue(false).build());

    private final Setting<Boolean> 其他_龙行5       = sgOther.add(new BoolSetting.Builder().name("龙行 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_海王5       = sgOther.add(new BoolSetting.Builder().name("海王 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_催生1       = sgOther.add(new BoolSetting.Builder().name("催生 1").defaultValue(false).build());
    private final Setting<Boolean> 其他_矿脉5       = sgOther.add(new BoolSetting.Builder().name("矿脉 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_自我修复5   = sgOther.add(new BoolSetting.Builder().name("自我修复 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_血契5       = sgOther.add(new BoolSetting.Builder().name("血契 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_地质学家5   = sgOther.add(new BoolSetting.Builder().name("地质学家 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_破界5       = sgOther.add(new BoolSetting.Builder().name("破界 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_贪欲5       = sgOther.add(new BoolSetting.Builder().name("贪欲 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_立方5       = sgOther.add(new BoolSetting.Builder().name("立方 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_龙之后裔5   = sgOther.add(new BoolSetting.Builder().name("龙之后裔 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_龙脉5       = sgOther.add(new BoolSetting.Builder().name("龙脉 5").defaultValue(false).build());
    private final Setting<Boolean> 其他_挖金5       = sgOther.add(new BoolSetting.Builder().name("挖金 5").defaultValue(false).build());

    // ── 公开坐标存储（供 FumoCommand 写入）────────────────────────────────
    public BlockPos posBook        = null;  // 书本补给箱
    public BlockPos posLapis       = null;  // 青金石补给箱
    public BlockPos posOutput      = null;  // 成品箱
    public BlockPos posEnchant     = null;  // 附魔台
    public BlockPos posGrindstone  = null;  // 砂轮
    public BlockPos posHangout     = null;  // 挂机位
    public BlockPos posAnvil       = null;  // 铁砧（原版装备极品附魔）
    public BlockPos posAnvilBox    = null;  // 铁砧箱（原版装备极品附魔，备用铁砧）
    public Direction posAnvilFacing = null; // 铁砧朝向（FACING，损坏后原样恢复）
    public BlockPos posEquipment   = null;  // 工具/护甲箱（原版装备极品附魔）
    public BlockPos posError       = null;  // 异常装备箱（原版装备极品附魔）
    public Float hangoutYaw        = null;
    public Float hangoutPitch      = null;
    public String pointServer      = null;
    public String pointDimension   = null;

    // ── 内部状态 ──────────────────────────────────────────────────────────
    private enum State {
        IDLE("待机"),
        WALK_TO_FARM("前往刷经验点"), FARMING("刷经验"),
        WALK_TO_ENCHANT("前往附魔台"), ENCHANTING("附魔中"),
        CHECKING("检查附魔结果"),
        WALK_TO_GRIND("前往砂轮"), GRINDING("磨书"),
        WALK_TO_STORE("前往存书"), STORING("存书"),
        WALK_TO_RESTOCK("前往补给"), RESTOCKING("补给"),
        // 原版装备附魔（GEAR 模式，复用同一状态机，不另建 GearStateMachine）
        GEAR_IDLE("待机"),
        GEAR_WALK_EQUIPMENT("前往装备箱"), GEAR_TAKE_GEAR("取装备"),
        GEAR_WALK_ENCHANT("前往附魔台"), GEAR_ENCHANTING("附魔装备"),
        GEAR_WALK_LAPIS("前往青金石箱"), GEAR_RESTOCK_LAPIS("取青金石"),
        GEAR_EVALUATE("评估附魔"),
        GEAR_WALK_GRIND("前往砂轮"), GEAR_GRINDING("磨装备"),
        GEAR_WALK_ANVIL("前往铁砧"), GEAR_ANVIL("铁砧合并"),
        GEAR_WALK_ANVIL_BOX("前往铁砧箱"), GEAR_TAKE_ANVIL("取铁砧"),
        GEAR_WALK_ANVIL_POS("返回铁砧位"), GEAR_PLACE_ANVIL("放置铁砧"),
        GEAR_WALK_OUTPUT("前往成品箱"), GEAR_STORE_OUTPUT("存成品"),
        GEAR_WALK_ERROR("前往异常箱"), GEAR_STORE_ERROR("存异常");

        private final String cn;
        State(String cn) { this.cn = cn; }
        /** 状态中文名（用于状态播报） */
        public String cn() { return cn; }
    }

    private State state = State.IDLE;
    private String lastNotifiedState = "";
    // 发包附魔提示去重锁：整次运行只在首次进入附魔阶段播一句，避免附魔→砂轮循环每轮刷屏
    private boolean 发包附魔提示已播 = false;
    private int   remainingAttempts = 0;
    private int   guiTick           = 0;
    private int   guiPhase          = 0;
    private static final int GUI_OPEN_PENDING = 20;
    private static final int GUI_OPEN_TIMEOUT = 40;

    private String hitTask = null;
    private int enchantedBookSlot = -1;
    private boolean hangoutViewRestored;

    // 空白书合并子状态：砂轮洗练后把背包里分散的空白书堆叠合并到一起
    private int mergeBookSrc   = -1;   // 待合并来源槽位（玩家背包 0-35）
    private int mergeBookDst   = -1;   // 待合并目标槽位
    private int mergeBookStep  = 0;    // 0 待找 / 1 已拿起 / 2 已放上 / 3 归还剩余

    // 补给空箱/取物失败重试计数：服务器延迟高时箱子可能暂时同步不到，重试几次再停机
    private static final int 补给重试上限 = 3;
    private int 补给重试次数 = 0;

    private final List<String> activeTasks = new ArrayList<>();

    // ── 原版装备附魔（GEAR）运行态 ─────────────────────────────────────────
    private GearTaskQueue gearQueue;                    // 任务队列（4 件批次）
    private GearEnchantTask gearTask;                   // 当前任务
    private TargetProfile gearProfile;                  // 当前目标方案
    private Item gearTargetItem;                        // 目标装备物品类型（由 gearId 解析）
    private int gearEquipSlot = -1;                     // 当前装备在背包的槽位
    private int gearTargetXp = 30;                      // 当前挂机目标经验等级
    private State gearReturnState = State.GEAR_IDLE;    // 挂机完返回的状态
    private AnvilPlan gearAnvilPlan;                    // 当前铁砧合并计划
    private int gearAnvilPhase = 0;                     // 铁砧发包子相位
    private int gearTakeCount = 0;                      // 本批已取装备数
    private int gearEnchantIndex = 0;                   // 已附魔装备数（4 件批次附魔进度）
    private int 卸货重试次数 = 0;                       // 成品箱卸货失败重试计数
    private RetryGuard gearRetry = new RetryGuard();    // 重试守卫（防死循环）
    // 运行统计（不影响核心流程）
    private int statTotal = 0, statDone = 0, statError = 0;
    private int statEnchant = 0, statGrind = 0, statAnvil = 0, statFarm = 0;

    private final EnchantmentSelectSetting 剑附魔选择;
    private final EnchantmentSelectSetting 斧头附魔选择;
    private final EnchantmentSelectSetting 弓附魔选择;
    private final EnchantmentSelectSetting 护甲附魔选择;
    private final EnchantmentSelectSetting 工具通用附魔选择;
    private final GearEnchantSetting 装备附魔配置;

    /** 目标模式：原版装备附魔 / 原版附魔书 / 自定义附魔，三模式互斥 */
    public enum TargetMode {
        GEAR("原版装备附魔"),
        BOOK("原版附魔书"),
        CUSTOM("自定义附魔");

        private final String title;

        TargetMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum RunMode {
        DRAIN("纯附魔模式"),
        EXPERIENCE("挂机循环");

        private final String title;

        RunMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** 当前目标模式对应的独立运行模式开关（三个模式互不干扰） */
    private RunMode 当前运行模式() {
        return switch (目标模式.get()) {
            case GEAR -> 装备运行模式.get();
            case BOOK -> 附魔书运行模式.get();
            case CUSTOM -> 自定义运行模式.get();
        };
    }

    public enum SuccessSound {
        CHALLENGE_COMPLETE("挑战完成"),
        LEVEL_UP("升级"),
        ENCHANTMENT_TABLE("附魔台"),
        NOTE_PLING("音符叮"),
        BELL("钟声"),
        FIREWORK("烟花"),
        EXPERIENCE("经验球"),
        VILLAGER("村民庆祝"),
        TRIDENT_THUNDER("三叉戟雷鸣"),
        ATTACK_CRIT("暴击"),
        CAT("猫叫"),
        THUNDER("雷声");

        private final String title;

        SuccessSound(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public AutoEnchantBook() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动附魔",
            "经验获取→定向附魔→极品剔除→洗练仓储全自动闭环。详细参考下面使用说明。");
        剑附魔选择 = addSelector("剑附魔属性", sgSword, sgAdvanced, () -> 目标模式.get() == TargetMode.CUSTOM);
        斧头附魔选择 = addSelector("斧头附魔属性", sgAxe, sgAdvanced, () -> 目标模式.get() == TargetMode.CUSTOM);
        弓附魔选择 = addSelector("弓附魔属性", sgBow, sgAdvanced, () -> 目标模式.get() == TargetMode.CUSTOM);
        护甲附魔选择 = addSelector("护甲附魔属性", sgArmor, sgAdvanced, () -> 目标模式.get() == TargetMode.CUSTOM);
        工具通用附魔选择 = addSelector("工具与通用附魔属性", sgOther, sgAdvanced, () -> 目标模式.get() == TargetMode.CUSTOM);
        addTargets(sgVanillaArmor, "保护 IV", "火焰保护 IV", "摔落缓冲 IV", "爆炸保护 IV", "弹射物保护 IV", "水下呼吸 III", "水下速掘", "荆棘 II", "深海探索者 III");
        addTargets(sgVanillaMelee, "锋利 IV", "亡灵杀手 IV", "节肢杀手 IV", "击退 II", "火焰附加 II", "抢夺 III", "横扫之刃 III");
        addTargets(sgVanillaTool, "效率 IV", "精准采集", "时运 III");
        addTargets(sgVanillaBow, "力量 IV", "冲击 II", "火矢", "无限");
        addTargets(sgVanillaFishing, "海之眷顾 III", "饵钓 III");
        addTargets(sgVanillaTrident, "忠诚 III", "穿刺 V", "激流 III", "引雷");
        addTargets(sgVanillaCrossbow, "多重射击", "快速装填 III", "穿透 IV");
        addTargets(sgVanillaCommon, "耐久 III");
        addSelector("原版防具附魔", sgVanillaArmor, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版近战附魔", sgVanillaMelee, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版工具附魔", sgVanillaTool, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版弓附魔", sgVanillaBow, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版钓竿附魔", sgVanillaFishing, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版三叉戟附魔", sgVanillaTrident, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版弩附魔", sgVanillaCrossbow, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        addSelector("原版通用附魔", sgVanillaCommon, sgVanilla, () -> 目标模式.get() == TargetMode.BOOK);
        装备附魔配置 = new GearEnchantSetting("装备附魔配置", () -> 目标模式.get() == TargetMode.GEAR);
        sgGear.add(装备附魔配置);
        sgAdvanced.sectionExpanded = true;
        sgVanilla.sectionExpanded = false;
        sgGear.sectionExpanded = true;
        sgSword.sectionExpanded = false;
        sgAxe.sectionExpanded = false;
        sgBow.sectionExpanded = false;
        sgArmor.sectionExpanded = false;
        settings.groups.remove(sgSword);
        settings.groups.remove(sgAxe);
        settings.groups.remove(sgBow);
        settings.groups.remove(sgArmor);
        settings.groups.remove(sgOther);
        settings.groups.remove(sgVanillaArmor);
        settings.groups.remove(sgVanillaMelee);
        settings.groups.remove(sgVanillaTool);
        settings.groups.remove(sgVanillaBow);
        settings.groups.remove(sgVanillaFishing);
        settings.groups.remove(sgVanillaTrident);
        settings.groups.remove(sgVanillaCrossbow);
        settings.groups.remove(sgVanillaCommon);
    }

    private void addTargets(SettingGroup group, String... names) {
        for (String name : names) group.add(new BoolSetting.Builder().name(name).defaultValue(false).build());
    }

    private EnchantmentSelectSetting addSelector(String name, SettingGroup source, SettingGroup target, IVisible visible) {
        List<BoolSetting> values = new ArrayList<>();
        for (Setting<?> setting : source) if (setting instanceof BoolSetting boolSetting) values.add(boolSetting);
        List<String> names = new ArrayList<>();
        for (BoolSetting setting : values) names.add(setting.name);
        EnchantmentSelectSetting selector = new EnchantmentSelectSetting(name, names, values, visible);
        target.add(selector);
        return selector;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            // 使用说明按钮（置顶显眼位置）
            WButton helpBtn = theme.button("§e查看使用说明");
            helpBtn.action = () -> mc.setScreen(new HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();

            // 点位卡片区：按当前目标模式动态显示对应点位按钮（三种模式互不污染）
            WTable row = theme.table();
            for (PointType type : requiredPoints(目标模式.get())) {
                buildPointCard(theme, row, type);
            }
            table.add(row).expandX();
            table.row();
        });
    }

    /**
     * 切换目标模式后刷新模块配置界面，让点位卡片区按新模式的 requiredPoints 实时重建。
     * 延迟到下一 tick 执行，避免在 WDropdown 的 action 回调栈中直接 reload 导致正在交互的控件被提前清空。
     */
    private void 刷新模式界面() {
        mc.execute(() -> {
            if (mc.screen instanceof WidgetScreen screen) screen.reload();
        });
    }

    /**
     * 构建点位设置卡片（与自动挖矿的卡片布局一致）
     * 卡片包含标题、坐标显示、设置按钮、删除按钮。
     * 设置/删除直接复用 FumoCommand 静态方法，与 .fumo set/remove 指令同一套校验。
     *
     * @param theme       Meteor GUI 主题
     * @param parentTable 父表格（横向排列）
     * @param type        统一点位业务类型（决定标题、颜色与绑定目标）
     */
    private void buildPointCard(GuiTheme theme, WTable parentTable, PointType type) {
        WTable card = theme.table();

        BlockPos pos = getPointPos(type);
        boolean isBound = pos != null;

        // 点位标题配色（与 ESP 标点颜色一致）
        String titleColor = switch (type) {
            case BOOK_STORAGE -> "§a";        // 空白书箱：绿色
            case LAPIS_STORAGE -> "§9";       // 青金石箱：蓝色
            case EQUIPMENT_STORAGE -> "§b";   // 工具/护甲箱：青色
            case ENCHANTING_TABLE -> "§d";    // 附魔台：粉色
            case GRINDSTONE -> "§7";          // 砂轮：灰色
            case ANVIL -> "§6";               // 铁砧：金色
            case ANVIL_BOX -> "§e";           // 铁砧箱：黄色
            case AFK -> "§c";                 // 挂机点：红色
            case OUTPUT_STORAGE -> "§6";      // 成品箱：金色
            case ERROR_STORAGE -> "§4";       // 异常装备箱：深红
        };

        card.add(theme.label(titleColor + type.title())).expandX().center();
        card.row();

        // 状态显示（固定两行，保持高度一致：第一行坐标，第二行维度）
        if (isBound) {
            String coords = String.format("§7X§f%d §7Y§f%d §7Z§f%d",
                pos.getX(), pos.getY(), pos.getZ());
            card.add(theme.label(coords)).expandX().center();
            card.row();
            String dim = 维度中文名();
            card.add(theme.label(dim != null ? "§7维度 §f" + dim : "§8-")).expandX().center();
            card.row();
        } else {
            card.add(theme.label("§8暂未绑定")).expandX().center();
            card.row();
            card.add(theme.label("§8-")).expandX().center();
            card.row();
        }

        // 设置按钮（已绑定=亮绿色，未绑定=暗灰色）
        String setBtnColor = isBound ? "§a" : "§8";
        WButton setBtn = theme.button(setBtnColor + "设置");
        setBtn.action = () -> {
            // 设置成功才关闭 GUI（失败保留界面让玩家重新对准）
            if (FumoCommand.setPoint(type)) {
                mc.setScreen(null);
            }
        };
        card.add(setBtn).expandX().center();
        card.row();

        // 删除按钮（红色）
        WButton delBtn = theme.button("§c删除");
        delBtn.action = () -> {
            if (isBound) {
                FumoCommand.removePoint(type);
                mc.setScreen(null);
            }
        };
        card.add(delBtn).expandX().center();

        parentTable.add(card).expandX();
    }

    /** 当前整套点位绑定的维度中文显示名（主世界/下界/末地），未绑定维度返回 null */
    private String 维度中文名() {
        String dim = pointDimension;
        if (dim == null) return null;
        if (dim.contains("overworld")) return "主世界";
        if (dim.contains("nether")) return "下界";
        if (dim.contains("end")) return "末地";
        return dim;
    }

    /** 按统一点位类型取坐标，未绑定返回 null */
    public BlockPos getPointPos(PointType type) {
        return switch (type) {
            case BOOK_STORAGE -> posBook;
            case LAPIS_STORAGE -> posLapis;
            case OUTPUT_STORAGE -> posOutput;
            case ENCHANTING_TABLE -> posEnchant;
            case GRINDSTONE -> posGrindstone;
            case AFK -> posHangout;
            case ANVIL -> posAnvil;
            case ANVIL_BOX -> posAnvilBox;
            case EQUIPMENT_STORAGE -> posEquipment;
            case ERROR_STORAGE -> posError;
        };
    }

    /** 写入统一点位（供 FumoCommand 调用，保证 GUI / 指令 / 自检 / 状态机同一数据源） */
    public void setPointPos(PointType type, BlockPos pos) {
        switch (type) {
            case BOOK_STORAGE -> posBook = pos;
            case LAPIS_STORAGE -> posLapis = pos;
            case OUTPUT_STORAGE -> posOutput = pos;
            case ENCHANTING_TABLE -> posEnchant = pos;
            case GRINDSTONE -> posGrindstone = pos;
            case AFK -> posHangout = pos;
            case ANVIL -> posAnvil = pos;
            case ANVIL_BOX -> posAnvilBox = pos;
            case EQUIPMENT_STORAGE -> posEquipment = pos;
            case ERROR_STORAGE -> posError = pos;
        }
    }

    /** 当前目标模式需要的点位列表（自检 / GUI 按钮 / 指令选点共用同一份来源） */
    public static List<PointType> requiredPoints(TargetMode mode) {
        return switch (mode) {
            case GEAR -> List.of(
                PointType.EQUIPMENT_STORAGE, PointType.LAPIS_STORAGE, PointType.ENCHANTING_TABLE, PointType.GRINDSTONE,
                PointType.ANVIL, PointType.ANVIL_BOX, PointType.AFK, PointType.OUTPUT_STORAGE, PointType.ERROR_STORAGE
            );
            case BOOK, CUSTOM -> List.of(
                PointType.BOOK_STORAGE, PointType.LAPIS_STORAGE, PointType.OUTPUT_STORAGE,
                PointType.ENCHANTING_TABLE, PointType.GRINDSTONE, PointType.AFK
            );
        };
    }

    /** 构建使用说明内容（点击「查看使用说明」按钮打开） */
    private String[] buildHelpContent() {
        return HelpScreen.buildHelpContent(
            new HelpScreen.HelpSection("首次配置",
                "  §8├─ §f先在要使用的服务器和维度执行 §e.fumo clear §7(清空旧点位)",
                "  §8├─ §f准星对准对应方块，点击下方卡片「设置」按钮依次绑定：",
                "  §8│    §7书 / 青晶石 / 成品箱 / 附魔台 / 砂轮",
                "  §8├─ §f挂机循环模式站在刷怪点调好杀怪视角，再绑定「挂机位」 §7(纯附魔模式不需要)",
                "  §8├─ §f在自动附魔分类或原版附魔分类勾选要收集的目标词条",
                "  §8├─ §f把带「横扫之刃」的剑放背包或快捷栏任意位置",
                "  §8└─ §f书箱放空白书、青金石箱放青金石、成品箱预留空间"
            ),
            new HelpScreen.HelpSection("点位设置 §7(两种方式)",
                "  §b▸ §e方式1 §8- §f配置页面卡片按钮",
                "    §7准星对准方块 §8→ §f点击对应卡片「设置」",
                "    §7书/青晶石/成品箱：必须是箱子/桶/潜影盒",
                "    §7附魔台/砂轮：必须对准对应方块",
                "    §7挂机位：直接站在目标位置即可绑定 §7(含视角)",
                "",
                "  §b▸ §e方式2 §8- §f指令系统",
                "    §8> §3.fumo set <节点> §8— §7节点：书/青晶石/成品箱/附魔台/砂轮/挂机位",
                "    §8> §3.fumo remove <节点> §8— §7删除单个点位",
                "    §8> §3.fumo status §8— §7查看坐标与当前地点匹配",
                "    §8> §3.fumo clear §8— §7清空全部点位"
            ),
            new HelpScreen.HelpSection("运行流程",
                "  §a▸ §f物资不足时自动前往对应补给箱取书或青金石",
                "  §a▸ §f经验不足时，挂机循环返回挂机位；纯附魔模式低于 30 级停止",
                "  §a▸ §f横扫之刃剑在主背包时自动换入当前快捷栏并选中",
                "  §a▸ §f达到目标等级后自动前往附魔台执行 30 级附魔",
                "  §a▸ §f未命中目标词条时前往砂轮洗练，再继续下一次附魔",
                "  §a▸ §f命中目标词条时播放提示音并将附魔书存入成品箱"
            ),
            new HelpScreen.HelpSection("参数说明",
                "  §6▸ §e单轮抽取次数 §f— 普通模式每轮计划执行的附魔次数",
                "  §6▸ §eGUI操作延迟 §f— 服务器卡顿或吞点击时适当调大",
                "  §6▸ §e书本/青金石补给组数 §f— 每次补给希望保有的组数",
                "  §6▸ §e自定义附魔目标 §f— 每行一个附魔名，可带等级，如「打雷5」",
                "  §6▸ §e运行模式 §f— 每个目标模式独立开关：纯附魔只消耗当前经验、不足停机；挂机循环自动补经验",
                "  §6▸ §e成功提示音 §f— 命中目标词条时播放所选音效"
            ),
            new HelpScreen.HelpSection("原版附魔分类",
                "  §7· 仅收录普通书通过附魔台随机附魔可获得的词条",
                "  §7· 每种词条只提供附魔台实际能刷出的最高等级",
                "  §7· 锋利/效率/力量等书本附魔最高 IV，不显示无法直接刷出的 V",
                "  §7· 不含经验修补、冰霜行者、灵魂疾行、迅捷潜行和诅咒"
            ),
            new HelpScreen.HelpSection("换服、换维度与指令",
                "  §d▸ §f一套点位只能用于设置它时所在的服务器和维度",
                "  §d▸ §f去其他服务器/维度时旧点位不会运行，也不会乱跑",
                "  §d▸ §f新地点使用：先 §e.fumo clear§f，再重设六个点位",
                "  §d▸ §f回原服务器/维度时旧点位可直接使用，不必重设"
            ),
            new HelpScreen.HelpSection("注意",
                "  §c⚠ §f单人世界与服务器均可使用",
                "  §c⚠ §f至少勾选一个目标词条，否则模块不启动",
                "  §c⚠ §f成品箱满、书箱/青金石箱空时会提示并自动停机",
                "  §c⚠ §f模块运行中无法修改点位，先关闭模块再设置"
            )
        );
    }

    // ── 模块开关 ──────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            notify("§c必须进入世界后才能启动模块。");
            toggle();
            return;
        }
        // 原版装备附魔：按装备模式点位需求自检，加载目标方案后进入 GEAR 状态机
        if (目标模式.get() == TargetMode.GEAR) {
            if (!reportSelfCheck(selfCheck())) return;
            if (pointServer == null || pointDimension == null) {
                notifyError("旧版点位没有服务器和维度信息，请执行 .fumo clear 后重新设置！");
                toggle();
                return;
            }
            if (!matchesCurrentPointContext()) {
                notifyError("当前服务器或维度与点位不一致，已阻止启动！请切回原世界，或使用 .fumo clear 重新设置。");
                toggle();
                return;
            }
            gearProfile = 装备附魔配置.currentProfile();
            if (gearProfile == null || gearProfile.isEmpty()) {
                notifyError("请先在「原版装备附魔」分类选择装备与极品方案！");
                toggle();
                return;
            }
            gearTargetItem = gearItemOf(gearProfile.gearId());
            if (gearTargetItem == null) {
                notifyError("无法解析目标装备：" + gearProfile.gearId());
                toggle();
                return;
            }
            // 初始化 GEAR 运行态
            gearQueue = null;
            gearTask = null;
            gearEquipSlot = -1;
            gearAnvilPlan = null;
            gearAnvilPhase = 0;
            gearRetry.reset();
            statTotal = statDone = statError = 0;
            statEnchant = statGrind = statAnvil = statFarm = 0;
            state = State.GEAR_IDLE;
            lastNotifiedState = "";
            guiTick = 0;
            guiPhase = 0;
            announceGearStartup();
            return;
        }
        if (!reportSelfCheck(selfCheck())) return;
        if (pointServer == null || pointDimension == null) {
            notifyError("旧版点位没有服务器和维度信息，请执行 .fumo clear 后重新设置！");
            toggle();
            return;
        }
        if (当前运行模式() == RunMode.DRAIN && posHangout != null) {
            notify("§7当前为纯附魔模式，忽略挂机位，不会前往挂机区。");
        }
        if (!matchesCurrentPointContext()) {
            notifyError("当前服务器或维度与点位不一致，已阻止启动！请切回原世界，或使用 .fumo clear 重新设置。");
            toggle();
            return;
        }

        rebuildActiveTasks();
        if (activeTasks.isEmpty()) {
            notifyError("任务列表为空，请至少勾选一个附魔书属性！");
            toggle();
            return;
        }

        remainingAttempts = 单轮抽取次数.get();
        state = State.IDLE;
        lastNotifiedState = "";
        发包附魔提示已播 = false;
        guiTick = 0;
        guiPhase = 0;
        hangoutViewRestored = false;
        mergeBookSrc = -1;
        mergeBookDst = -1;
        mergeBookStep = 0;
        补给重试次数 = 0;
        int vanillaTasks = countSelectedTasks(
            sgVanillaArmor, sgVanillaMelee, sgVanillaTool, sgVanillaBow,
            sgVanillaFishing, sgVanillaTrident, sgVanillaCrossbow, sgVanillaCommon
        );
        int extensionTasks = activeTasks.size() - vanillaTasks;
        announceStartup(activeTasks.size(), vanillaTasks, extensionTasks);
    }

    @Override
    public void onDeactivate() {
        stopKillAura();
        stopBaritone();
    }

    /**
     * 启动播报：把本次运行的关键配置合并成一条多行消息块，只带一次模块前缀。
     *
     * 正文统一「标签 §8▸ 值」结构，标签固定 4 字宽加全角空格对齐；
     * 模式名走 highlightFunction、数值走 highlightNumber，与全项目强调色体系一致。
     */
    private void announceStartup(int totalTasks, int vanillaTasks, int extensionTasks) {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 自动附魔 · 启动报告");

        // 运行模式：纯附魔 / 挂机循环
        report.append("\n§7当前模式　§8▸ ").append(highlightFunction(当前运行模式().toString())).append("§r");

        // 目标词条统计：总量 + 原版/扩展拆分
        report.append("\n§7目标词条　§8▸ ").append(highlightNumber(totalTasks + " 本")).append("§r");
        report.append("\n§7原版词条　§8▸ ").append(highlightNumber(vanillaTasks + " 本")).append("§r");
        report.append("\n§7扩展词条　§8▸ ").append(highlightNumber(extensionTasks + " 本")).append("§r");

        // 单轮抽取只在挂机循环生效，纯附魔模式忽略
        if (当前运行模式() == RunMode.EXPERIENCE) {
            report.append("\n§7单轮抽取　§8▸ ").append(highlightNumber(单轮抽取次数.get() + " 次")).append("§r");
        }

        notify(report.toString());
    }

    // ── Tick 主循环 ────────────────────────────────────────────────────────

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null) return;

        // 静默容器操作：自动化运行中打开容器屏幕时取消显示（不抢鼠标），
        // 容器数据仍由 mc.player.containerMenu 同步，状态机直接通过菜单发包操作。
        if (shouldSuppressScreen(event.screen)) {
            event.setCancelled(true);
            // 补给状态用 guiPhase 区分书/青金石（>=10 为青金石），重置会破坏 isLapis 判断
            if (state != State.RESTOCKING) {
                guiPhase = 0;
            }
            guiTick = GUI操作延迟.get();
        }
    }

    /**
     * 是否静默取消容器屏幕。
     * 只在模块激活且处于容器操作状态时生效，避免干扰玩家手动开箱。
     */
    private boolean shouldSuppressScreen(Screen screen) {
        if (!isActive() || !(screen instanceof AbstractContainerScreen<?>)) return false;
        // 玩家手动打开背包永远放行，只静默模块自己操作的容器
        if (screen instanceof InventoryScreen) return false;
        return state == State.ENCHANTING || state == State.GRINDING
            || state == State.STORING || state == State.RESTOCKING;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (!matchesCurrentPointContext()) {
            notifyError("检测到服务器或维度发生变化，已停止寻路并关闭模块！");
            stopKillAura();
            stopBaritone();
            toggle();
            return;
        }

        // 断点恢复：GEAR 运行中周期验证目标装备仍在背包（死亡掉落 / 掉线丢失即暂停，防盲跑）
        if (目标模式.get() == TargetMode.GEAR && gearTargetItem != null
            && (gearQueue != null || gearEquipSlot >= 0)
            && (mc.player.tickCount & 31) == 0
            && !RecoveryValidator.hasGearInInventory(mc, new ItemStack(gearTargetItem))) {
            notifyError("目标装备不在背包，原版装备极品附魔已暂停。请补充装备后重试。");
            stopKillAura();
            stopBaritone();
            toggle();
            return;
        }

        if (isPathing()) {
            if (!state.name().startsWith("WALK_") && !state.name().startsWith("GEAR_WALK_") && state != State.FARMING) return;
        }

        switch (state) {
            case IDLE         -> tickIdle();
            case WALK_TO_FARM -> tickWalkToFarm();
            case FARMING      -> tickFarming();
            case WALK_TO_ENCHANT -> tickWalkToEnchant();
            case ENCHANTING   -> tickEnchanting();
            case CHECKING     -> tickChecking();
            case WALK_TO_GRIND -> tickWalkToGrind();
            case GRINDING     -> tickGrinding();
            case WALK_TO_STORE -> tickWalkToStore();
            case STORING      -> tickStoring();
            case WALK_TO_RESTOCK -> tickWalkToRestock();
            case RESTOCKING   -> tickRestocking();
            // 原版装备附魔（GEAR）
            case GEAR_IDLE          -> tickGearIdle();
            case GEAR_WALK_EQUIPMENT -> tickGearWalkEquipment();
            case GEAR_TAKE_GEAR     -> tickGearTakeGear();
            case GEAR_WALK_ENCHANT  -> tickGearWalkEnchant();
            case GEAR_ENCHANTING    -> tickGearEnchanting();
            case GEAR_WALK_LAPIS    -> tickGearWalkLapis();
            case GEAR_RESTOCK_LAPIS -> tickGearRestockLapis();
            case GEAR_EVALUATE      -> tickGearEvaluate();
            case GEAR_WALK_GRIND    -> tickGearWalkGrind();
            case GEAR_GRINDING      -> tickGearGrinding();
            case GEAR_WALK_ANVIL    -> tickGearWalkAnvil();
            case GEAR_ANVIL         -> tickGearAnvil();
            case GEAR_WALK_ANVIL_BOX -> tickGearWalkAnvilBox();
            case GEAR_TAKE_ANVIL    -> tickGearTakeAnvil();
            case GEAR_WALK_ANVIL_POS -> tickGearWalkAnvilPos();
            case GEAR_PLACE_ANVIL   -> tickGearPlaceAnvil();
            case GEAR_WALK_OUTPUT   -> tickGearWalkOutput();
            case GEAR_STORE_OUTPUT  -> tickGearStoreOutput();
            case GEAR_WALK_ERROR    -> tickGearWalkError();
            case GEAR_STORE_ERROR   -> tickGearStoreError();
        }
    }

    // ── 状态处理器 ────────────────────────────────────────────────────────

    private void tickIdle() {
        if (needRestock()) {
            setState(State.WALK_TO_RESTOCK);
            return;
        }
        int xpLevel = mc.player.experienceLevel;
        if (当前运行模式() == RunMode.DRAIN) {
            if (xpLevel >= 30) {
                setState(State.WALK_TO_ENCHANT);
            } else {
                notify("§c✗ 停机 §8▸ 纯附魔经验低于 " + highlightNumber("30 级") + "§7，切换挂机循环后重新启动");
                toggle();
            }
            return;
        }

        if (remainingAttempts <= 0) {
            remainingAttempts = 单轮抽取次数.get();
            setState(State.WALK_TO_FARM);
            return;
        }
        if (xpLevel >= 30) {
            setState(State.WALK_TO_ENCHANT);
        } else {
            setState(State.WALK_TO_FARM);
        }
    }

    private void tickWalkToFarm() {
        if (arrivedAtHangout()) {
            setState(State.FARMING);
        } else {
            walkToHangout();
        }
    }

    private void tickFarming() {
        restoreHangoutView();
        startKillAura();
        int targetLevel = 目标模式.get() == TargetMode.GEAR
            ? gearTargetXp
            : 30 + 3 * (单轮抽取次数.get() - 1);
        if (mc.player.experienceLevel >= targetLevel) {
            stopKillAura();
            remainingAttempts = 单轮抽取次数.get();
            if (目标模式.get() == TargetMode.GEAR) {
                statFarm++;
                setState(gearReturnState);
            } else {
                setState(State.WALK_TO_ENCHANT);
            }
        }
    }

    private void tickWalkToEnchant() {
        if (!isBlockAt(posEnchant, Blocks.ENCHANTING_TABLE)) {
            stopBaritone();
            notifyError("附魔台不存在或已被挖掉，自动化已停止。请重新设置附魔台点位。");
            toggle();
            return;
        }
        if (canOpenNow(posEnchant)) {
            stopBaritone();
            if (needRestock() || countInInventory(Items.LAPIS_LAZULI) < 3) { setState(State.WALK_TO_RESTOCK); return; }
            guiTick = 0; guiPhase = 0;
            setState(State.ENCHANTING);
        } else {
            walkToBlock(posEnchant);
        }
    }

    private void tickEnchanting() {
        if (guiTick > 0) { guiTick--; return; }

        // 静默模式下没有 Screen，改为判断 containerMenu 是否已同步为附魔台菜单
        boolean menuOpen = enchantMenuOpen();

        if (!menuOpen && !isBlockAt(posEnchant, Blocks.ENCHANTING_TABLE)) {
            stopBaritone();
            notifyError("附魔台不存在或已被挖掉，自动化已停止。请重新设置附魔台点位。");
            toggle();
            return;
        }

        if (countInInventory(Items.LAPIS_LAZULI) < 3 && !menuOpen) {
            setState(State.WALK_TO_RESTOCK);
            return;
        }

        if (!menuOpen) {
            if (guiPhase == 0) {
                interactBlock(posEnchant);
                guiPhase = GUI_OPEN_PENDING;
                guiTick = GUI_OPEN_TIMEOUT;
                return;
            }
            if (guiPhase == GUI_OPEN_PENDING) {
                if (guiTick == 0) {
                    guiPhase = 0;
                    guiTick = GUI操作延迟.get();
                }
                return;
            }
        }
        if (!menuOpen) return;

        EnchantmentMenu handler = (EnchantmentMenu) mc.player.containerMenu;
        int syncId = handler.containerId;

        switch (guiPhase) {
            case 0 -> {
                int bookSlot = findInInventory(Items.BOOK);
                if (bookSlot < 0) {
                    notifyError("背包无空白书，切至补给！");
                    mc.player.closeContainer();
                    setState(State.WALK_TO_RESTOCK);
                    return;
                }
                int invSlot = containerSlotOf(handler, bookSlot);
                // 用 QUICK_MOVE 只移送 1 本空白书进附魔槽，避免整叠拿起导致剩余书本散落背包
                mc.gameMode.handleContainerInput(syncId, invSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                // 等待附魔槽同步到 1 本空白书后再取青金石
                if (!handler.getSlot(0).getItem().is(Items.BOOK)) return;
                guiTick = GUI操作延迟.get();
                guiPhase = 2;
            }
            case 2 -> {
                if (!handler.getSlot(0).getItem().is(Items.BOOK)) return;
                int lapisSlot = findInInventoryAtLeast(Items.LAPIS_LAZULI, 3);
                if (lapisSlot < 0) {
                    notifyError("青金石不足 3 个，取消本次附魔并补给！");
                    mc.player.closeContainer();
                    setState(State.WALK_TO_RESTOCK);
                    return;
                }
                int lapisContSlot = containerSlotOf(handler, lapisSlot);
                mc.gameMode.handleContainerInput(syncId, lapisContSlot, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 3;
            }
            case 3 -> {
                if (!mc.player.containerMenu.getCarried().is(Items.LAPIS_LAZULI)) return;
                mc.gameMode.handleContainerInput(syncId, 1, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 4;
            }
            case 4 -> {
                if (!handler.getSlot(1).getItem().is(Items.LAPIS_LAZULI) || handler.getSlot(1).getItem().getCount() < 3) {
                    notifyError("附魔台青金石未达到 3 个，取消本次附魔并补给！");
                    mc.player.closeContainer();
                    setState(State.WALK_TO_RESTOCK);
                    return;
                }
                mc.gameMode.handleInventoryButtonClick(syncId, 2);
                guiTick = GUI操作延迟.get();
                guiPhase = 5;
            }
            case 5 -> {
                if (!handler.getSlot(0).getItem().is(Items.ENCHANTED_BOOK)) return;
                mc.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 6;
            }
            case 6 -> {
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                if (当前运行模式() == RunMode.EXPERIENCE) remainingAttempts--;
                setState(State.CHECKING);
            }
        }
    }

    private void tickChecking() {
        enchantedBookSlot = findInInventory(Items.ENCHANTED_BOOK);
        if (enchantedBookSlot < 0) {
            setState(State.IDLE);
            return;
        }
        ItemStack book = mc.player.getInventory().getItem(enchantedBookSlot);
        hitTask = null;
        List<Component> tooltip = book.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        for (Component line : tooltip) {
            String text = line.getString();
            for (String task : activeTasks) {
                if (matchesEnchantmentTask(text, task)) {
                    hitTask = task;
                    break;
                }
            }
            if (hitTask != null) break;
        }
        if (hitTask != null) {
            playSuccessSound();
            notify("§a✓ 命中目标附魔书 §8▸ " + highlightText(hitTask));
            setState(State.WALK_TO_STORE);
        } else {
            setState(State.WALK_TO_GRIND);
        }
    }

    private boolean matchesEnchantmentTask(String tooltipLine, String task) {
        String line = normalizeEnchantmentText(tooltipLine);
        String target = normalizeEnchantmentText(task);
        if (line.equals(target)) return true;

        String compactTarget = target.replace(" ", "");
        int separator = target.lastIndexOf(' ');
        String name;
        String level;
        if (separator > 0 && separator < target.length() - 1) {
            name = target.substring(0, separator);
            level = target.substring(separator + 1);
        } else if (compactTarget.matches(".+\\d+")) {
            int levelStart = compactTarget.length();
            while (levelStart > 0 && Character.isDigit(compactTarget.charAt(levelStart - 1))) levelStart--;
            name = compactTarget.substring(0, levelStart);
            level = compactTarget.substring(levelStart);
        } else {
            return line.replace(" ", "").contains(compactTarget);
        }

        if (!level.matches("\\d+") || name.isEmpty()) return line.replace(" ", "").contains(compactTarget);

        int nameIndex = line.indexOf(name);
        if (nameIndex < 0) nameIndex = line.replace(" ", "").indexOf(name.replace(" ", ""));
        if (nameIndex < 0) return false;

        String compactLine = line.replace(" ", "");
        int compactNameIndex = compactLine.indexOf(name.replace(" ", ""));
        if (compactNameIndex < 0) return false;
        String suffix = compactLine.substring(compactNameIndex + name.replace(" ", "").length()).trim();
        return suffix.matches("^(?:[：:\\-—|]?等级?)?" + level + "(?:\\D.*|$)");
    }

    private String normalizeEnchantmentText(String text) {
        return text
            .replace('\u3000', ' ')
            .replace("Ⅹ", "10")
            .replace("Ⅸ", "9")
            .replace("Ⅷ", "8")
            .replace("Ⅶ", "7")
            .replace("Ⅵ", "6")
            .replace("Ⅴ", "5")
            .replace("Ⅳ", "4")
            .replace("Ⅲ", "3")
            .replace("Ⅱ", "2")
            .replace("Ⅰ", "1")
            .replaceAll("(?<![A-Za-z])VIII(?![A-Za-z])", "8")
            .replaceAll("(?<![A-Za-z])VII(?![A-Za-z])", "7")
            .replaceAll("(?<![A-Za-z])VI(?![A-Za-z])", "6")
            .replaceAll("(?<![A-Za-z])IV(?![A-Za-z])", "4")
            .replaceAll("(?<![A-Za-z])IX(?![A-Za-z])", "9")
            .replaceAll("(?<![A-Za-z])V(?![A-Za-z])", "5")
            .replaceAll("(?<![A-Za-z])III(?![A-Za-z])", "3")
            .replaceAll("(?<![A-Za-z])II(?![A-Za-z])", "2")
            .replaceAll("(?<![A-Za-z])I(?![A-Za-z])", "1")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private void tickWalkToGrind() {
        if (canOpenNow(posGrindstone)) {
            stopBaritone();
            guiTick = 0; guiPhase = 0;
            setState(State.GRINDING);
        } else {
            walkToBlock(posGrindstone);
        }
    }

    private void tickGrinding() {
        if (guiTick > 0) { guiTick--; return; }

        boolean menuOpen = grindMenuOpen();

        if (!menuOpen) {
            if (guiPhase == 0) {
                interactBlock(posGrindstone);
                guiPhase = GUI_OPEN_PENDING;
                guiTick = GUI_OPEN_TIMEOUT;
                return;
            }
            if (guiPhase == GUI_OPEN_PENDING) {
                if (guiTick == 0) {
                    guiPhase = 0;
                    guiTick = GUI操作延迟.get();
                }
                return;
            }
        }
        if (!menuOpen) return;

        GrindstoneMenu handler = (GrindstoneMenu) mc.player.containerMenu;
        int syncId = handler.containerId;

        switch (guiPhase) {
            case 0 -> {
                if (enchantedBookSlot < 0 || mc.player.getInventory().getItem(enchantedBookSlot).getItem() != Items.ENCHANTED_BOOK) {
                    setState(State.IDLE);
                    return;
                }
                int bookContSlot = containerSlotOf(handler, enchantedBookSlot);
                mc.gameMode.handleContainerInput(syncId, bookContSlot, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                ItemStack output = handler.getSlot(2).getItem();
                if (!output.is(Items.BOOK)) return;
                mc.gameMode.handleContainerInput(syncId, 2, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 2;
            }
            case 2 -> {
                // 把背包里分散的空白书合并到同一堆叠，避免分开放
                if (mergeBooksStep(handler, syncId)) {
                    guiTick = GUI操作延迟.get();
                } else {
                    mergeBookSrc = -1;
                    mergeBookDst = -1;
                    mergeBookStep = 0;
                    guiTick = GUI操作延迟.get();
                    guiPhase = 3;
                }
            }
            case 3 -> {
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                setState(State.IDLE);
            }
        }
    }

    private void tickWalkToStore() {
        if (canOpenNow(posOutput)) {
            stopBaritone();
            guiTick = 0; guiPhase = 0;
            setState(State.STORING);
        } else {
            walkToBlock(posOutput);
        }
    }

    private void tickStoring() {
        if (guiTick > 0) { guiTick--; return; }

        if (!chestMenuOpen()) {
            if (guiPhase == 0) {
                interactBlock(posOutput);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        if (!chestMenuOpen()) return;

        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;

        switch (guiPhase) {
            case 0 -> {
                boolean hasFreeSlot = false;
                for (int i = 0; i < handler.getRowCount() * 9; i++) {
                    if (handler.getSlot(i).getItem().isEmpty()) { hasFreeSlot = true; break; }
                }
                if (!hasFreeSlot) {
                    mc.player.closeContainer();
                    notifyError("成品箱已满！自动停机。");
                    toggle();
                    return;
                }
                if (enchantedBookSlot < 0 || mc.player.getInventory().getItem(enchantedBookSlot).getItem() != Items.ENCHANTED_BOOK) {
                    mc.player.closeContainer();
                    setState(State.IDLE);
                    return;
                }
                mc.gameMode.handleContainerInput(syncId, containerSlotOf(handler, enchantedBookSlot), 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                mc.player.closeContainer();
                if (hitTask != null) {
                    activeTasks.remove(hitTask);
                    notify("§a已存入：§e" + hitTask + "§f，剩余任务：" + activeTasks.size());
                    hitTask = null;
                }
                if (activeTasks.isEmpty()) {
                    notifyError("所有极品任务已完成！自动停机。");
                    toggle();
                    return;
                }
                setState(State.IDLE);
            }
        }
    }

    private void tickWalkToRestock() {
        if (needBookRestock()) {
            if (canOpenNow(posBook)) {
                stopBaritone();
                guiTick = 0; guiPhase = 0;
                setState(State.RESTOCKING);
            } else {
                walkToBlock(posBook);
            }
        } else if (needLapisRestock()) {
            if (canOpenNow(posLapis)) {
                stopBaritone();
                setState(State.RESTOCKING);
                guiPhase = 10;
            } else {
                walkToBlock(posLapis);
            }
        } else {
            setState(State.IDLE);
        }
    }

    // phase 0-9 = 补书；phase 10-19 = 补青金石
    private void tickRestocking() {
        if (guiTick > 0) { guiTick--; return; }

        boolean isLapis = (guiPhase >= 10);
        BlockPos targetPos = isLapis ? posLapis : posBook;

        if (!chestMenuOpen()) {
            if (guiPhase == 0 || guiPhase == 10) {
                interactBlock(targetPos);
                guiTick = GUI操作延迟.get();
                guiPhase = isLapis ? 11 : 1;
                return;
            }
        }
        if (!chestMenuOpen()) return;

        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;

        Item targetItem = isLapis ? Items.LAPIS_LAZULI : Items.BOOK;
        int currentCount = countInInventory(targetItem);
        int needCount = (isLapis ? 青金石补给组数.get() : 书本补给组数.get()) * 64 - currentCount;

        if (needCount <= 0) {
            mc.player.closeContainer();
            if (!isLapis && needLapisRestock()) {
                setState(State.WALK_TO_RESTOCK);
                guiPhase = 10;
            } else {
                setState(State.IDLE);
            }
            return;
        }

        int grabbed = 0;
        for (int i = 0; i < handler.getRowCount() * 9 && grabbed < needCount; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            if (stack.getItem() == targetItem) {
                int movedCount = stack.getCount();
                mc.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                grabbed += movedCount;
                补给重试次数 = 0;
                guiTick = GUI操作延迟.get();
                return;
            }
        }

        String name = isLapis ? "青金石" : "空白书";
        mc.player.closeContainer();
        // 空箱可能是服务器延迟导致物品还没同步，重试几次再停机，避免误判
        if (++补给重试次数 < 补给重试上限) {
            notify("§e⚠ " + name + "补给箱暂时没拿到，正在重试（第 " + 补给重试次数 + " 次）...");
            setState(State.WALK_TO_RESTOCK);
            guiPhase = isLapis ? 10 : 0;
            return;
        }
        补给重试次数 = 0;
        notifyError(name + "补给箱已空！自动停机。");
        toggle();
    }

    // ── 2D 渲染 ────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!ESP标点.get() || !matchesCurrentPointContext()) return;
        renderLabel(event, posBook,       "§a§l[书本箱]",   new Color(80,  230, 160, 200));
        renderLabel(event, posLapis,      "§9§l[青金石箱]", new Color(70,  130, 255, 200));
        renderLabel(event, posOutput,     "§6§l[成品箱]",   new Color(255, 200, 50,  200));
        renderLabel(event, posEnchant,    "§d§l[附魔台]",   new Color(200, 100, 255, 200));
        renderLabel(event, posGrindstone, "§7§l[砂轮]",     new Color(160, 160, 160, 200));
        renderLabel(event, posHangout,    "§c§l[挂机位]",   new Color(255, 80,  80,  200));
    }

    private void renderLabel(Render2DEvent event, BlockPos pos, String label, Color color) {
        if (pos == null) return;
        Vector3d screen = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        if (!NametagUtils.to2D(screen, 0.6)) return;
        TextRenderer renderer = TextRenderer.get();
        NametagUtils.begin(screen, event.graphics);
        renderer.begin(0.6);
        double width = renderer.getWidth(label);
        renderer.render(label, -width / 2, 0, color, true);
        renderer.end();
        NametagUtils.end(event.graphics);
    }

    // ── 配置持久化 ──────────────────────────────────────────────────────────

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        writePos(tag, "posBook", posBook);
        writePos(tag, "posLapis", posLapis);
        writePos(tag, "posOutput", posOutput);
        writePos(tag, "posEnchant", posEnchant);
        writePos(tag, "posGrindstone", posGrindstone);
        writePos(tag, "posHangout", posHangout);
        writePos(tag, "posAnvil", posAnvil);
        writePos(tag, "posAnvilBox", posAnvilBox);
        writePos(tag, "posEquipment", posEquipment);
        writePos(tag, "posError", posError);
        if (posAnvilFacing != null) tag.putString("posAnvilFacing", posAnvilFacing.name());
        if (hangoutYaw != null) tag.putFloat("hangoutYaw", hangoutYaw);
        if (hangoutPitch != null) tag.putFloat("hangoutPitch", hangoutPitch);
        if (pointServer != null) tag.putString("pointServer", pointServer);
        if (pointDimension != null) tag.putString("pointDimension", pointDimension);
        return tag;
    }

    @Override
    public AutoEnchantBook fromTag(CompoundTag tag) {
        posBook = readPos(tag, "posBook");
        posLapis = readPos(tag, "posLapis");
        posOutput = readPos(tag, "posOutput");
        posEnchant = readPos(tag, "posEnchant");
        posGrindstone = readPos(tag, "posGrindstone");
        posHangout = readPos(tag, "posHangout");
        posAnvil = readPos(tag, "posAnvil");
        posAnvilBox = readPos(tag, "posAnvilBox");
        posEquipment = readPos(tag, "posEquipment");
        posError = readPos(tag, "posError");
        posAnvilFacing = readDirection(tag, "posAnvilFacing");
        hangoutYaw = tag.getFloat("hangoutYaw").orElse(null);
        hangoutPitch = tag.getFloat("hangoutPitch").orElse(null);
        pointServer = tag.getString("pointServer").orElse(null);
        pointDimension = tag.getString("pointDimension").orElse(null);
        super.fromTag(tag);
        return this;
    }

    private void writePos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    private BlockPos readPos(CompoundTag tag, String key) {
        int[] value = tag.getIntArray(key).orElse(null);
        return value != null && value.length == 3 ? new BlockPos(value[0], value[1], value[2]) : null;
    }

    /** 读取朝向（Direction），非法值返回 null */
    private Direction readDirection(CompoundTag tag, String key) {
        String name = tag.getString(key).orElse(null);
        if (name == null) return null;
        try {
            return Direction.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String currentServer() {
        // 单机世界没有服务器地址，返回固定标识 "singleplayer"（与自动挖矿 WKCommand 一致），
        // 保证单人游戏也能正常标点、保存并校验点位，不再报「无法识别当前服务器或维度」。
        if (mc.getCurrentServer() == null || mc.getCurrentServer().ip == null) return "singleplayer";
        return mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
    }

    public String currentDimension() {
        return mc.level == null ? null : mc.level.dimension().identifier().toString();
    }

    public boolean matchesCurrentPointContext() {
        return Objects.equals(pointServer, currentServer()) && Objects.equals(pointDimension, currentDimension());
    }

    public boolean hasAnyPosition() {
        return posBook != null || posLapis != null || posOutput != null || posEnchant != null
            || posGrindstone != null || posHangout != null || posAnvil != null
            || posAnvilBox != null || posEquipment != null || posError != null;
    }

    public void clearPoints() {
        posBook = null;
        posLapis = null;
        posOutput = null;
        posEnchant = null;
        posGrindstone = null;
        posHangout = null;
        posAnvil = null;
        posAnvilBox = null;
        posAnvilFacing = null;
        posEquipment = null;
        posError = null;
        hangoutYaw = null;
        hangoutPitch = null;
        pointServer = null;
        pointDimension = null;
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private void rebuildActiveTasks() {
        activeTasks.clear();
        SettingGroup[] enchantmentGroups = {
            sgSword, sgAxe, sgBow, sgArmor,
            sgVanillaArmor, sgVanillaMelee, sgVanillaTool, sgVanillaBow,
            sgVanillaFishing, sgVanillaTrident, sgVanillaCrossbow, sgVanillaCommon
        };
        for (SettingGroup group : enchantmentGroups) {
            for (Setting<?> s : group) {
                if (s instanceof BoolSetting && s.get().equals(Boolean.TRUE)) {
                    if (!activeTasks.contains(s.name)) activeTasks.add(s.name);
                }
            }
        }
        for (String task : 自定义附魔.get()) {
            String normalized = normalizeEnchantmentText(task);
            if (!normalized.isEmpty() && !activeTasks.contains(normalized)) activeTasks.add(normalized);
        }
    }

    private int countSelectedTasks(SettingGroup... groups) {
        int count = 0;
        for (SettingGroup group : groups) {
            for (Setting<?> setting : group) {
                if (setting instanceof BoolSetting && Boolean.TRUE.equals(setting.get())) count++;
            }
        }
        return count;
    }

    private boolean isBlockAt(BlockPos pos, net.minecraft.world.level.block.Block block) {
        return pos != null && mc.level != null && mc.level.getBlockState(pos).is(block);
    }

    /**
     * 启动自检：按当前目标模式只检查该模式需要的点位，交给 reportSelfCheck 一次性多行播报。
     * 三种模式互不污染——装备模式不检查空白书箱/青金石箱，附魔书/自定义模式不检查铁砧/装备箱。
     * 挂机点仅在「挂机循环」下需要，任一模式切到「纯附魔」都跳过挂机点检查。
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();
        TargetMode mode = 目标模式.get();
        for (PointType type : requiredPoints(mode)) {
            if (type == PointType.AFK && 当前运行模式() == RunMode.DRAIN) {
                continue;
            }
            if (getPointPos(type) == null) {
                missing.add("§6" + type.title() + "§f·未绑定");
            }
        }
        return missing;
    }

    private boolean needRestock() {
        return needBookRestock() || needLapisRestock();
    }

    private boolean needBookRestock() {
        return countInInventory(Items.BOOK) < 书本补给组数.get() * 64 * 0.2;
    }

    private boolean needLapisRestock() {
        return countInInventory(Items.LAPIS_LAZULI) < 青金石补给组数.get() * 64 * 0.2;
    }

    private int countInInventory(Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private int findInInventory(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findInInventoryAtLeast(Item item, int minimumCount) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item && stack.getCount() >= minimumCount) return i;
        }
        return -1;
    }

    private int containerSlotOf(AbstractContainerMenu handler, int invSlot) {
        int containerSize = handler.slots.size() - 36;
        if (invSlot < 9) {
            return containerSize + 27 + invSlot;
        } else {
            return containerSize + (invSlot - 9);
        }
    }

    /**
     * 单步推进空白书合并：每 tick 只发一次点击（拿起→放上→归还），
     * 直到背包里所有同种空白书都堆叠满，返回 false 表示本轮已无更多可合并堆叠。
     */
    private boolean mergeBooksStep(AbstractContainerMenu handler, int syncId) {
        // 已锁定一对待合并堆叠时，按子步骤推进三连点击
        if (mergeBookSrc >= 0 && mergeBookDst >= 0) {
            switch (mergeBookStep) {
                case 1 -> {
                    // 拿起来源整叠
                    mc.gameMode.handleContainerInput(syncId, containerSlotOf(handler, mergeBookSrc), 0, ContainerInput.PICKUP, mc.player);
                    mergeBookStep = 2;
                    return true;
                }
                case 2 -> {
                    // 放到目标堆叠上（自动合并，超出上限的留在光标）
                    mc.gameMode.handleContainerInput(syncId, containerSlotOf(handler, mergeBookDst), 0, ContainerInput.PICKUP, mc.player);
                    mergeBookStep = 3;
                    return true;
                }
                case 3 -> {
                    // 归还光标剩余到来源槽，结束本次合并
                    mc.gameMode.handleContainerInput(syncId, containerSlotOf(handler, mergeBookSrc), 0, ContainerInput.PICKUP, mc.player);
                    mergeBookSrc = -1;
                    mergeBookDst = -1;
                    mergeBookStep = 0;
                    return true;
                }
            }
        }

        // 寻找下一对可合并的空白书堆叠：目标必须未满，来源须与目标同种
        for (int dst = 0; dst < 36; dst++) {
            ItemStack target = mc.player.getInventory().getItem(dst);
            if (!target.is(Items.BOOK) || target.getCount() >= target.getMaxStackSize()) continue;
            for (int src = 0; src < 36; src++) {
                if (src == dst) continue;
                ItemStack source = mc.player.getInventory().getItem(src);
                if (source.getCount() >= source.getMaxStackSize()) continue;
                if (!ItemStack.isSameItemSameComponents(source, target)) continue;
                mergeBookSrc = src;
                mergeBookDst = dst;
                mergeBookStep = 1;
                return true;
            }
        }
        return false;
    }

    // ── 静默容器菜单就绪判断 ─────────────────────────────────────────────
    // 静默模式下不再打开 GUI Screen，改为判断 mc.player.containerMenu 是否已同步为目标菜单。

    /** 附魔台菜单是否已打开（containerMenu 已同步为 EnchantmentMenu） */
    private boolean enchantMenuOpen() {
        return mc.player != null && mc.player.containerMenu instanceof EnchantmentMenu;
    }

    /** 砂轮菜单是否已打开 */
    private boolean grindMenuOpen() {
        return mc.player != null && mc.player.containerMenu instanceof GrindstoneMenu;
    }

    /** 箱子菜单是否已打开（书本箱/青金石箱/成品箱共用 ChestMenu） */
    private boolean chestMenuOpen() {
        return mc.player != null && mc.player.containerMenu instanceof ChestMenu;
    }

    /** 玩家能否直接交互到该方块：眼睛到方块中心在交互距离内，且未站在方块正上方 */
    private boolean canOpenNow(BlockPos pos) {
        if (pos == null || mc.player == null || mc.level == null) return false;
        // 站在方块正上方时开箱/开附魔台/开砂轮会失败，须寻路到正面
        if (mc.player.blockPosition().equals(pos)) return false;
        double range = mc.player.blockInteractionRange() + 0.5;
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= range;
    }

    /** 寻找方块正面站位：水平相邻且可站立的空气块，优先离玩家最近的方位 */
    private BlockPos frontStandPos(BlockPos pos) {
        if (pos == null || mc.level == null) return pos;
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = 0; dy >= -1; dy--) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos stand = pos.relative(dir).above(dy);
                if (isStandable(stand)) {
                    double dx = stand.getX() - playerPos.getX();
                    double dz = stand.getZ() - playerPos.getZ();
                    double d = dx * dx + dz * dz;
                    if (d < bestDist) { bestDist = d; best = stand; }
                }
            }
            if (best != null) break;
        }
        return best != null ? best : pos;
    }

    /** 坐标是否可作为站立点：本体与上方均为空气，下方有落脚方块 */
    private boolean isStandable(BlockPos pos) {
        if (mc.level == null) return false;
        return mc.level.getBlockState(pos).isAir()
            && mc.level.getBlockState(pos.above()).isAir()
            && !mc.level.getBlockState(pos.below()).isAir();
    }

    private boolean arrivedAtHangout() {
        return posHangout != null && mc.player.blockPosition().equals(posHangout);
    }

    private void walkToHangout() {
        if (posHangout == null) return;
        if (!isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone()
                .getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalBlock(posHangout));
        }
    }

    /** 寻路到方块正面站位（站在相邻块而不是方块本体上方），避免开箱/开附魔台失败 */
    private void walkToBlock(BlockPos pos) {
        if (pos == null) return;
        if (isPathing()) return;
        BlockPos stand = frontStandPos(pos);
        BaritoneAPI.getProvider().getPrimaryBaritone()
            .getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalBlock(stand));
    }

    private void interactBlock(BlockPos pos) {
        if (pos == null) return;
        if (!canOpenNow(pos)) return;
        // 直接发包开箱/开附魔台/开砂轮，带 sequence 预测处理。
        // 窗口失焦时 mc.gameMode.useItemOn 会被吞导致开箱失败，发包方式不受影响。
        // 命中面固定顶面（UP）：侧面命中会因站位高低/贴墙导致服务端 raycast 拒绝，顶面最稳。
        FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, pos, Direction.UP);
    }

    private void restoreHangoutView() {
        if (hangoutViewRestored || !返回挂机视角.get() || hangoutYaw == null || hangoutPitch == null) return;
        mc.player.setYRot(hangoutYaw);
        mc.player.setYHeadRot(hangoutYaw);
        mc.player.setXRot(hangoutPitch);
        hangoutViewRestored = true;
    }

    private void playSuccessSound() {
        if (!成功提示音.get() || mc.player == null) return;
        SoundEvent sound = switch (成功提示音类型.get()) {
            case CHALLENGE_COMPLETE -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case ENCHANTMENT_TABLE -> SoundEvents.ENCHANTMENT_TABLE_USE;
            case NOTE_PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
            case BELL -> SoundEvents.BELL_BLOCK;
            case FIREWORK -> SoundEvents.FIREWORK_ROCKET_BLAST;
            case EXPERIENCE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case VILLAGER -> SoundEvents.VILLAGER_CELEBRATE;
            case TRIDENT_THUNDER -> SoundEvents.TRIDENT_THUNDER.value();
            case ATTACK_CRIT -> SoundEvents.PLAYER_ATTACK_CRIT;
            case CAT -> SoundEvents.CAT_AMBIENT_BABY.value();
            case THUNDER -> SoundEvents.LIGHTNING_BOLT_THUNDER;
        };
        mc.player.playSound(sound, 1.0f, 1.0f);
    }

    private void startKillAura() {
        equipSweepingSword();
        KillAura ka = Modules.get().get(KillAura.class);
        if (ka != null && !ka.isActive()) ka.toggle();
    }

    private void equipSweepingSword() {
        int swordSlot = findSweepingSword();
        if (swordSlot < 0) return;
        if (swordSlot < 9) {
            mc.player.getInventory().setSelectedSlot(swordSlot);
            return;
        }
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, swordSlot, selectedSlot, ContainerInput.SWAP, mc.player);
    }

    private int findSweepingSword() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.SWORDS)) {
                List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                for (Component line : tooltip) if (line.getString().contains("横扫之刃")) return i;
            }
        }
        return -1;
    }

    private void stopKillAura() {
        KillAura ka = Modules.get().get(KillAura.class);
        if (ka != null && ka.isActive()) ka.toggle();
    }

    private void stopBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    private boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    private void setState(State newState) {
        state = newState;
        guiTick = 0;
        guiPhase = 0;
        if (newState == State.WALK_TO_FARM) hangoutViewRestored = false;
        // 发包附魔循环（附魔→检查→砂轮洗练→再附魔）不逐状态播报，避免每轮刷屏；
        // 进入附魔阶段只播一句「发包附魔中（不打开界面）」，且整次运行只播一次。
        if (newState == State.ENCHANTING && !发包附魔提示已播) {
            notify("§7发包附魔中（不打开界面）...");
            发包附魔提示已播 = true;
        }
        // 状态播报：只播有实质动作的工作状态，寻路过渡（WALK_TO_*）不播防刷屏
        // 带去重锁，循环类流程每轮每个工作状态只播一次
        if (isWorkState(newState) && !newState.cn().equals(lastNotifiedState)) {
            notify("§7正在" + newState.cn() + "...");
            lastNotifiedState = newState.cn();
        } else if (newState == State.IDLE || newState == State.GEAR_IDLE) {
            lastNotifiedState = "";
        }
    }

    /** 是否为需要播报进度的工作状态（排除寻路过渡、待机及附魔循环内的高速状态） */
    private boolean isWorkState(State s) {
        return s == State.FARMING || s == State.STORING || s == State.RESTOCKING
            || s == State.GEAR_TAKE_GEAR || s == State.GEAR_ENCHANTING
            || s == State.GEAR_RESTOCK_LAPIS
            || s == State.GEAR_GRINDING || s == State.GEAR_ANVIL
            || s == State.GEAR_TAKE_ANVIL || s == State.GEAR_PLACE_ANVIL
            || s == State.GEAR_STORE_OUTPUT || s == State.GEAR_STORE_ERROR;
    }

    // ── 原版装备附魔（GEAR）状态处理器 ─────────────────────────────────────

    /** GEAR 启动播报（目标装备 + 方案 + 目标附魔数） */
    private void announceGearStartup() {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 自动附魔 · 原版装备附魔启动");
        report.append("\n§7运行模式　§8▸ ").append(highlightFunction(当前运行模式().toString())).append("§r");
        report.append("\n§7目标装备　§8▸ ").append(highlightText(gearProfile.gearName())).append("§r");
        report.append("\n§7极品方案　§8▸ ").append(highlightFunction(gearProfile.profileName())).append("§r");
        report.append("\n§7目标附魔　§8▸ ").append(highlightNumber(gearProfile.activeTargets().size() + " 项")).append("§r");
        notify(report.toString());
    }

    /** GEAR 批次结束播报（统计，不影响核心流程） */
    private void announceGearSummary() {
        StringBuilder report = new StringBuilder();
        report.append("§a§l✓ 原版装备附魔 · 批次完成");
        report.append("\n§7处理装备　§8▸ ").append(highlightNumber(statTotal + " 件")).append("§r");
        report.append("\n§7完成数量　§8▸ ").append(highlightNumber(statDone + " 件")).append("§r");
        report.append("\n§7异常数量　§8▸ ").append(highlightNumber(statError + " 件")).append("§r");
        report.append("\n§7附魔次数　§8▸ ").append(highlightNumber(statEnchant + " 次")).append("§r");
        report.append("\n§7砂轮次数　§8▸ ").append(highlightNumber(statGrind + " 次")).append("§r");
        report.append("\n§7铁砧次数　§8▸ ").append(highlightNumber(statAnvil + " 次")).append("§r");
        report.append("\n§7挂机次数　§8▸ ").append(highlightNumber(statFarm + " 次")).append("§r");
        notify(report.toString());
    }

    /** gearId（如 minecraft:diamond_sword）→ 物品类型 */
    private Item gearItemOf(String gearId) {
        if (gearId == null || mc.level == null) return null;
        Identifier id = Identifier.tryParse(gearId);
        if (id == null) return null;
        var holder = mc.level.registryAccess().lookupOrThrow(Registries.ITEM)
            .get(ResourceKey.create(Registries.ITEM, id));
        return holder.map(ref -> ref.value()).orElse(null);
    }

    /** 推进到下一件装备（清理单件运行态，保留批次） */
    private void gearNext() {
        if (gearQueue != null) gearQueue.advance();
        gearEquipSlot = -1;
        gearAnvilPlan = null;
        gearAnvilPhase = 0;
        gearRetry.reset();
    }

    /** 标记当前任务异常并播报中文原因 */
    private void gearFail(TaskErrorReason reason) {
        if (gearTask != null) gearTask.markError(reason);
        notifyError("装备异常 → " + reason + "，转入异常装备箱。");
    }

    /** 在玩家背包查找已附魔的目标装备（ENCHANTMENTS 非空），返回槽位，无则 -1 */
    private int findEnchantedGear() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(gearTargetItem) && !EnchantEvaluationService.readEnchantments(stack).isEmpty()) return i;
        }
        return -1;
    }

    /** 在玩家背包查找未附魔的裸装备（ENCHANTMENTS 为空），返回槽位，无则 -1 */
    private int findUnenchantedGear(Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(item) && EnchantEvaluationService.readEnchantments(stack).isEmpty()) return i;
        }
        return -1;
    }

    /** 收集玩家背包里所有已附魔的目标装备（4 件批次附魔结果） */
    private List<ItemStack> collectGears() {
        List<ItemStack> gears = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(gearTargetItem) && !EnchantEvaluationService.readEnchantments(stack).isEmpty()) {
                gears.add(stack);
            }
        }
        return gears;
    }

    /** 找对目标贡献最多的装备槽位（作为铁砧合并主装备），无则 -1 */
    private int findBestGearSlot() {
        int bestSlot = -1;
        int bestScore = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.is(gearTargetItem)) continue;
            int score = AnvilPlanner.contributionScore(stack, gearProfile);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** 找带目标附魔的另一件装备槽位（作为铁砧合并材料，排除主装备槽位），无则 -1 */
    private int findMergeGearSlot(int excludeSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) continue;
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.is(gearTargetItem)) continue;
            if (AnvilPlanner.hasTargetContribution(stack, gearProfile)) return i;
        }
        return -1;
    }

    /** 查找某个装备快照在背包里的槽位，无则 -1 */
    private int slotOfItem(ItemStack target) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i) == target) return i;
        }
        return -1;
    }

    /** 铁砧菜单是否已打开 */
    private boolean anvilMenuOpen() {
        return mc.player != null && mc.player.containerMenu instanceof AnvilMenu;
    }

    /** 根据背包里的目标装备构建批次队列（数量由「每批取用数量」配置决定） */
    private void buildGearQueue() {
        List<GearEnchantTask> tasks = new ArrayList<>();
        int taskId = 1;
        int batchSize = 每批取用数量.get();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(gearTargetItem)) {
                tasks.add(new GearEnchantTask(taskId++, stack, gearProfile));
                if (tasks.size() >= batchSize) break;
            }
        }
        if (tasks.isEmpty()) {
            gearQueue = null;
            notifyError("装备箱中没有可用的" + (gearProfile == null ? "目标装备" : gearProfile.gearName()) + "，原版装备极品附魔已暂停。");
            toggle();
            return;
        }
        gearQueue = new GearTaskQueue(tasks);
        statTotal = tasks.size();
    }

    private void tickGearIdle() {
        if (gearQueue == null) {
            setState(State.GEAR_WALK_EQUIPMENT);
            return;
        }
        if (gearQueue.hasNext()) {
            gearTask = gearQueue.current();
            gearEnchantIndex = 0;
            setState(State.GEAR_WALK_ENCHANT);
            return;
        }
        announceGearSummary();
        toggle();
    }

    private void tickGearWalkEquipment() {
        if (canOpenNow(posEquipment)) {
            stopBaritone();
            guiTick = 0;
            guiPhase = 0;
            gearTakeCount = 0;
            setState(State.GEAR_TAKE_GEAR);
        } else {
            walkToBlock(posEquipment);
        }
    }

    private void tickGearTakeGear() {
        if (guiTick > 0) { guiTick--; return; }
        if (!chestMenuOpen()) {
            if (guiPhase == 0) {
                interactBlock(posEquipment);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        if (!chestMenuOpen()) return;
        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        // 每 tick 取一件目标装备，最多「每批取用数量」件（只取 gearTargetItem，其他装备不拿）
        for (int i = 0; i < handler.getRowCount() * 9; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            if (stack.is(gearTargetItem)) {
                mc.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                gearTakeCount++;
                if (gearTakeCount >= 每批取用数量.get()) {
                    mc.player.closeContainer();
                    guiTick = GUI操作延迟.get();
                    gearTakeCount = 0;
                    buildGearQueue();
                    setState(State.GEAR_IDLE);
                }
                return;
            }
        }
        // 箱子空或没有更多目标装备
        mc.player.closeContainer();
        guiTick = GUI操作延迟.get();
        gearTakeCount = 0;
        buildGearQueue();
        setState(State.GEAR_IDLE);
    }

    private void tickGearWalkEnchant() {
        if (!isBlockAt(posEnchant, Blocks.ENCHANTING_TABLE)) {
            stopBaritone();
            notifyError("附魔台不存在或已被挖掉！自动停机。");
            toggle();
            return;
        }
        if (canOpenNow(posEnchant)) {
            stopBaritone();
            // 附魔台固定需要 30 级；纯附魔模式下不足直接停机，挂机循环则去挂机补经验
            if (XpPlanner.needsEnchantGrinding(mc.player.experienceLevel)) {
                if (当前运行模式() == RunMode.DRAIN) {
                    notify("§c✗ 停机 §8▸ 纯附魔经验不足 " + highlightNumber("30 级") + "§7，切换挂机循环后重新启动");
                    toggle();
                    return;
                }
                gearTargetXp = XpPlanner.ENCHANT_TABLE_LEVEL;
                gearReturnState = State.GEAR_WALK_ENCHANT;
                setState(State.WALK_TO_FARM);
                return;
            }
            // 找一件未附魔的裸装备；没有则说明本批已全部附魔完，进入对比
            gearEquipSlot = findUnenchantedGear(gearTargetItem);
            if (gearEquipSlot < 0) {
                setState(State.GEAR_EVALUATE);
                return;
            }
            if (countInInventory(Items.LAPIS_LAZULI) < 3) {
                // 青金石不足：前往青金石箱补给（复用青金石补给组数 + 青金石箱点位）
                setState(State.GEAR_WALK_LAPIS);
                return;
            }
            guiTick = 0;
            guiPhase = 0;
            setState(State.GEAR_ENCHANTING);
        } else {
            walkToBlock(posEnchant);
        }
    }

    private void tickGearEnchanting() {
        if (guiTick > 0) { guiTick--; return; }
        boolean menuOpen = enchantMenuOpen();
        if (!menuOpen && !isBlockAt(posEnchant, Blocks.ENCHANTING_TABLE)) {
            stopBaritone();
            notifyError("附魔台不存在或已被挖掉！自动停机。");
            toggle();
            return;
        }
        if (!menuOpen) {
            if (guiPhase == 0) {
                interactBlock(posEnchant);
                guiPhase = GUI_OPEN_PENDING;
                guiTick = GUI_OPEN_TIMEOUT;
                return;
            }
            if (guiPhase == GUI_OPEN_PENDING) {
                if (guiTick == 0) { guiPhase = 0; guiTick = GUI操作延迟.get(); }
                return;
            }
        }
        if (!menuOpen) return;
        EnchantmentMenu handler = (EnchantmentMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        switch (guiPhase) {
            case 0 -> {
                if (gearEquipSlot < 0 || mc.player.getInventory().getItem(gearEquipSlot).isEmpty()) {
                    mc.player.closeContainer();
                    gearQueue = null;
                    setState(State.GEAR_WALK_EQUIPMENT);
                    return;
                }
                int contSlot = containerSlotOf(handler, gearEquipSlot);
                mc.gameMode.handleContainerInput(syncId, contSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                if (!handler.getSlot(0).getItem().is(gearTargetItem)) return;
                guiTick = GUI操作延迟.get();
                guiPhase = 2;
            }
            case 2 -> {
                if (!handler.getSlot(0).getItem().is(gearTargetItem)) return;
                int lapisSlot = findInInventoryAtLeast(Items.LAPIS_LAZULI, 3);
                if (lapisSlot < 0) {
                    mc.player.closeContainer();
                    notifyError("青金石不足 3 个，无法附魔！自动停机。");
                    toggle();
                    return;
                }
                int lapisContSlot = containerSlotOf(handler, lapisSlot);
                mc.gameMode.handleContainerInput(syncId, lapisContSlot, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 3;
            }
            case 3 -> {
                if (!mc.player.containerMenu.getCarried().is(Items.LAPIS_LAZULI)) return;
                mc.gameMode.handleContainerInput(syncId, 1, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 4;
            }
            case 4 -> {
                if (!handler.getSlot(1).getItem().is(Items.LAPIS_LAZULI) || handler.getSlot(1).getItem().getCount() < 3) {
                    mc.player.closeContainer();
                    notifyError("附魔台青金石未达到 3 个，取消附魔并停机。");
                    toggle();
                    return;
                }
                mc.gameMode.handleInventoryButtonClick(syncId, 2);
                guiTick = GUI操作延迟.get();
                guiPhase = 5;
            }
            case 5 -> {
                // 附魔完成：slot 0 的装备已获得附魔（附魔前装备为裸装备）
                ItemStack result = handler.getSlot(0).getItem();
                if (!EnchantEvaluationService.readEnchantments(result).isEmpty()) {
                    mc.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                    guiTick = GUI操作延迟.get();
                    guiPhase = 6;
                }
            }
            case 6 -> {
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                statEnchant++;
                gearEnchantIndex++;
                // 还有裸装备没附魔则继续，否则进入「对比 4 件装备」环节
                if (gearQueue != null && gearEnchantIndex < gearQueue.size()) {
                    setState(State.GEAR_WALK_ENCHANT);
                } else {
                    setState(State.GEAR_EVALUATE);
                }
            }
        }
    }

    private void tickGearWalkLapis() {
        if (canOpenNow(posLapis)) {
            stopBaritone();
            guiTick = 0;
            guiPhase = 0;
            setState(State.GEAR_RESTOCK_LAPIS);
        } else {
            walkToBlock(posLapis);
        }
    }

    private void tickGearRestockLapis() {
        if (guiTick > 0) { guiTick--; return; }
        if (!chestMenuOpen()) {
            if (guiPhase == 0) {
                interactBlock(posLapis);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        if (!chestMenuOpen()) return;
        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        // 复用「青金石补给组数」计算补足数量（与附魔书模式同一标准）
        int current = countInInventory(Items.LAPIS_LAZULI);
        int need = 青金石补给组数.get() * 64 - current;
        if (need <= 0) {
            mc.player.closeContainer();
            setState(State.GEAR_WALK_ENCHANT);
            return;
        }
        // 从青金石箱 QUICK_MOVE 青金石
        for (int i = 0; i < handler.getRowCount() * 9; i++) {
            if (handler.getSlot(i).getItem().is(Items.LAPIS_LAZULI)) {
                mc.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        // 青金石箱空：停机，不无限循环
        mc.player.closeContainer();
        notifyError("青金石箱已空，无法附魔！原版装备极品附魔已暂停。");
        toggle();
    }

    private void tickGearEvaluate() {
        // 收集背包里所有已附魔的目标装备（4 件批次附魔结果）
        List<ItemStack> gears = collectGears();
        if (gears.isEmpty()) {
            gearFail(TaskErrorReason.GEAR_IDENTITY_INVALID);
            setState(State.GEAR_WALK_ERROR);
            return;
        }
        // 某件已 100% 达标 → 直接成品
        for (ItemStack gear : gears) {
            if (EnchantEvaluationService.evaluate(gear, gearProfile, AcceptanceStrategy.STRICT, 1.0).complete()) {
                gearEquipSlot = slotOfItem(gear);
                setState(State.GEAR_WALK_OUTPUT);
                return;
            }
        }
        // 规划「装备 + 装备」合并（把分散附魔叠加成一件）
        gearAnvilPlan = AnvilPlanner.plan(gearProfile, gears);
        if (gearAnvilPlan == null || !gearAnvilPlan.hasNext()) {
            // 附魔结果无法凑齐极品：记录失败，超上限则放弃本件进异常（防「附魔→砂轮」无限循环）
            gearRetry.recordFailure();
            if (gearRetry.exceeded()) {
                gearFail(TaskErrorReason.REPEATED_FAILURE);
                setState(State.GEAR_WALK_ERROR);
                return;
            }
            setState(State.GEAR_WALK_GRIND);
            return;
        }
        setState(State.GEAR_WALK_ANVIL);
    }

    private void tickGearWalkGrind() {
        if (canOpenNow(posGrindstone)) {
            stopBaritone();
            gearEquipSlot = findEnchantedGear();
            if (gearEquipSlot < 0) {
                // 没有已附魔装备了，重置附魔进度重新附魔
                gearEnchantIndex = 0;
                setState(State.GEAR_WALK_ENCHANT);
                return;
            }
            guiTick = 0;
            guiPhase = 0;
            setState(State.GEAR_GRINDING);
        } else {
            walkToBlock(posGrindstone);
        }
    }

    private void tickGearGrinding() {
        if (guiTick > 0) { guiTick--; return; }
        boolean menuOpen = grindMenuOpen();
        if (!menuOpen) {
            if (guiPhase == 0) {
                interactBlock(posGrindstone);
                guiPhase = GUI_OPEN_PENDING;
                guiTick = GUI_OPEN_TIMEOUT;
                return;
            }
            if (guiPhase == GUI_OPEN_PENDING) {
                if (guiTick == 0) { guiPhase = 0; guiTick = GUI操作延迟.get(); }
                return;
            }
        }
        if (!menuOpen) return;
        GrindstoneMenu handler = (GrindstoneMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        switch (guiPhase) {
            case 0 -> {
                if (gearEquipSlot < 0 || mc.player.getInventory().getItem(gearEquipSlot).isEmpty()) {
                    setState(State.GEAR_IDLE);
                    return;
                }
                int contSlot = containerSlotOf(handler, gearEquipSlot);
                mc.gameMode.handleContainerInput(syncId, contSlot, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                ItemStack output = handler.getSlot(2).getItem();
                if (!output.is(gearTargetItem)) return;
                mc.gameMode.handleContainerInput(syncId, 2, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 2;
            }
            case 2 -> {
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                statGrind++;
                // 继续磨下一件已附魔装备，全部磨完则重置进度重新附魔
                gearEquipSlot = findEnchantedGear();
                if (gearEquipSlot >= 0) {
                    setState(State.GEAR_WALK_GRIND);
                } else {
                    gearEnchantIndex = 0;
                    setState(State.GEAR_WALK_ENCHANT);
                }
            }
        }
    }

    private void tickGearWalkAnvil() {
        // 铁砧检测：使用前确认铁砧仍存在且有效，损坏/消失则进入自动更换
        if (!isAnvilAt(posAnvil)) {
            stopBaritone();
            notify("§e⚠ 铁砧已损坏或消失，正在自动更换...");
            setState(State.GEAR_WALK_ANVIL_BOX);
            return;
        }
        if (canOpenNow(posAnvil)) {
            stopBaritone();
            guiTick = 0;
            guiPhase = 0;
            gearAnvilPhase = 0;
            setState(State.GEAR_ANVIL);
        } else {
            walkToBlock(posAnvil);
        }
    }

    // ── 铁砧检测与自动更换 ──────────────────────────────────────────────

    /** 检测坐标是否为有效铁砧（含微损/严重损坏铁砧） */
    private boolean isAnvilAt(BlockPos pos) {
        if (pos == null || mc.level == null) return false;
        var block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
    }

    /** 是否为铁砧物品（用于铁砧箱取用） */
    private boolean isAnvilItem(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.ANVIL || item == Items.CHIPPED_ANVIL || item == Items.DAMAGED_ANVIL;
    }

    /** 在背包查找铁砧物品槽位，无则 -1 */
    private int findAnvilItemInInventory() {
        for (int i = 0; i < 36; i++) {
            if (isAnvilItem(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    /** 把背包槽位的物品换到当前选中快捷栏 */
    private void selectItem(int invSlot) {
        if (invSlot < 9) {
            mc.player.getInventory().setSelectedSlot(invSlot);
            return;
        }
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, invSlot, selectedSlot, ContainerInput.SWAP, mc.player);
    }

    private void tickGearWalkAnvilBox() {
        if (canOpenNow(posAnvilBox)) {
            stopBaritone();
            guiTick = 0;
            guiPhase = 0;
            setState(State.GEAR_TAKE_ANVIL);
        } else {
            walkToBlock(posAnvilBox);
        }
    }

    private void tickGearTakeAnvil() {
        if (guiTick > 0) { guiTick--; return; }
        if (!chestMenuOpen()) {
            if (guiPhase == 0) {
                interactBlock(posAnvilBox);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        if (!chestMenuOpen()) return;
        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        // 只取 1 个铁砧（铁砧不可堆叠，每槽 1 个）
        for (int i = 0; i < handler.getRowCount() * 9; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            if (isAnvilItem(stack)) {
                mc.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                mc.player.closeContainer();
                setState(State.GEAR_WALK_ANVIL_POS);
                return;
            }
        }
        // 没有备用铁砧：暂停，不无限寻路
        mc.player.closeContainer();
        notifyError("铁砧已损坏，铁砧箱没有备用铁砧，原版装备极品附魔已暂停。");
        toggle();
    }

    private void tickGearWalkAnvilPos() {
        double range = mc.player.blockInteractionRange() + 0.5;
        if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(posAnvil)) <= range) {
            stopBaritone();
            setState(State.GEAR_PLACE_ANVIL);
        } else {
            walkToBlock(posAnvil);
        }
    }

    private void tickGearPlaceAnvil() {
        if (guiTick > 0) { guiTick--; return; }
        // 铁砧已放置成功，继续原任务（保留当前装备 / 目标 / 合并规划上下文）
        if (isAnvilAt(posAnvil)) {
            notify("§a✓ 铁砧已更换完成，继续原任务。");
            setState(State.GEAR_WALK_ANVIL);
            return;
        }
        int anvilSlot = findAnvilItemInInventory();
        if (anvilSlot < 0) {
            notifyError("铁砧已损坏，铁砧箱没有备用铁砧，原版装备极品附魔已暂停。");
            toggle();
            return;
        }
        selectItem(anvilSlot);
        // 按保存的朝向放置（玩家面向铁砧朝向的对面）
        if (posAnvilFacing != null) {
            float yaw = posAnvilFacing.getOpposite().toYRot();
            mc.player.setYRot(yaw);
            mc.player.setYHeadRot(yaw);
        }
        FarmPacketOps.interactBlock(InteractionHand.MAIN_HAND, posAnvil.below(), Direction.UP);
        guiTick = GUI操作延迟.get();
    }

    private void tickGearAnvil() {
        if (guiTick > 0) { guiTick--; return; }

        // 合并过程中铁砧损坏检测：立即切换自动更换
        if (!isAnvilAt(posAnvil)) {
            notify("§e⚠ 铁砧已损坏或消失，正在自动更换...");
            setState(State.GEAR_WALK_ANVIL_BOX);
            return;
        }

        if (gearAnvilPlan == null || !gearAnvilPlan.hasNext()) {
            // 合并计划执行完：重新读取主装备并最终验收，只有 100% 达标才进成品箱
            gearEquipSlot = findBestGearSlot();
            if (gearEquipSlot < 0) {
                gearFail(TaskErrorReason.GEAR_IDENTITY_INVALID);
                setState(State.GEAR_WALK_ERROR);
                return;
            }
            ItemStack finalGear = mc.player.getInventory().getItem(gearEquipSlot);
            if (EnchantEvaluationService.evaluate(finalGear, gearProfile, AcceptanceStrategy.STRICT, 1.0).complete()) {
                setState(State.GEAR_WALK_OUTPUT);
            } else {
                gearFail(TaskErrorReason.CANNOT_REACH_TARGET);
                setState(State.GEAR_WALK_ERROR);
            }
            return;
        }

        boolean menuOpen = anvilMenuOpen();
        if (!menuOpen) {
            if (gearAnvilPhase == 0) {
                interactBlock(posAnvil);
                gearAnvilPhase = GUI_OPEN_PENDING;
                guiTick = GUI_OPEN_TIMEOUT;
                return;
            }
            if (gearAnvilPhase == GUI_OPEN_PENDING) {
                if (guiTick == 0) { gearAnvilPhase = 0; guiTick = GUI操作延迟.get(); }
                return;
            }
        }
        if (!menuOpen) return;

        AnvilMenu handler = (AnvilMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        AnvilStep step = gearAnvilPlan.next();

        switch (gearAnvilPhase) {
            case 0 -> {
                // 左槽：放主装备（对目标贡献最多的装备）
                gearEquipSlot = findBestGearSlot();
                if (gearEquipSlot < 0) {
                    mc.player.closeContainer();
                    gearFail(TaskErrorReason.GEAR_IDENTITY_INVALID);
                    setState(State.GEAR_WALK_ERROR);
                    return;
                }
                // 耐久保护：低于最低耐久比例不再合并，进异常
                if (!GearSafetyGuard.isSafe(mc.player.getInventory().getItem(gearEquipSlot))) {
                    mc.player.closeContainer();
                    gearFail(TaskErrorReason.DURABILITY_LOW);
                    setState(State.GEAR_WALK_ERROR);
                    return;
                }
                int gearCont = containerSlotOf(handler, gearEquipSlot);
                mc.gameMode.handleContainerInput(syncId, gearCont, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                gearAnvilPhase = 1;
            }
            case 1 -> {
                // 右槽：放材料装备（带目标附魔的另一件装备，装备+装备叠加）
                if (!handler.getSlot(0).getItem().is(gearTargetItem)) return;
                int materialSlot = findMergeGearSlot(gearEquipSlot);
                if (materialSlot < 0) {
                    mc.player.closeContainer();
                    gearFail(TaskErrorReason.CANNOT_PLAN);
                    setState(State.GEAR_WALK_ERROR);
                    return;
                }
                int matCont = containerSlotOf(handler, materialSlot);
                mc.gameMode.handleContainerInput(syncId, matCont, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(syncId, 1, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                gearAnvilPhase = 2;
            }
            case 2 -> {
                if (handler.getSlot(1).getItem().isEmpty()) return;
                int cost = AnvilPlanner.readCost(handler);
                step.actualXpCost(cost);
                if (AnvilPlanner.isTooExpensive(cost)) {
                    step.tooExpensive(true);
                    mc.player.closeContainer();
                    gearFail(TaskErrorReason.TOO_EXPENSIVE);
                    setState(State.GEAR_WALK_ERROR);
                    return;
                }
                if (mc.player.experienceLevel < cost) {
                    mc.player.closeContainer();
                    // 铁砧经验不足：纯附魔模式下直接停机，挂机循环则去挂机补经验
                    if (当前运行模式() == RunMode.DRAIN) {
                        notify("§c✗ 停机 §8▸ 纯附魔经验不足铁砧费用 " + highlightNumber(cost + " 级") + "§7，切换挂机循环后重新启动");
                        toggle();
                        return;
                    }
                    gearTargetXp = cost;
                    gearReturnState = State.GEAR_ANVIL;
                    setState(State.WALK_TO_FARM);
                    return;
                }
                mc.gameMode.handleContainerInput(syncId, 2, 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                gearAnvilPhase = 3;
            }
            case 3 -> {
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                step.executed(true);
                gearAnvilPlan.advance();
                gearAnvilPhase = 0;
                statAnvil++;
                gearEquipSlot = findBestGearSlot();
                gearRetry.reset();
            }
        }
    }

    private void tickGearWalkOutput() {
        // 卸货前重新验收：确保 100% 达标才允许进成品箱（未验证不放箱）
        gearEquipSlot = findBestGearSlot();
        if (gearEquipSlot < 0) {
            gearFail(TaskErrorReason.GEAR_IDENTITY_INVALID);
            setState(State.GEAR_WALK_ERROR);
            return;
        }
        ItemStack finalGear = mc.player.getInventory().getItem(gearEquipSlot);
        if (!EnchantEvaluationService.evaluate(finalGear, gearProfile, AcceptanceStrategy.STRICT, 1.0).complete()) {
            gearFail(TaskErrorReason.CANNOT_REACH_TARGET);
            setState(State.GEAR_WALK_ERROR);
            return;
        }
        if (canOpenNow(posOutput)) {
            stopBaritone();
            guiTick = 0;
            guiPhase = 0;
            卸货重试次数 = 0;
            setState(State.GEAR_STORE_OUTPUT);
        } else {
            walkToBlock(posOutput);
        }
    }

    private void tickGearStoreOutput() {
        if (guiTick > 0) { guiTick--; return; }
        if (!chestMenuOpen()) {
            if (guiPhase == 0) {
                interactBlock(posOutput);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        if (!chestMenuOpen()) return;
        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        switch (guiPhase) {
            case 0 -> {
                boolean hasFreeSlot = false;
                for (int i = 0; i < handler.getRowCount() * 9; i++) {
                    if (handler.getSlot(i).getItem().isEmpty()) { hasFreeSlot = true; break; }
                }
                if (!hasFreeSlot) {
                    mc.player.closeContainer();
                    notifyError("成品箱空间不足，原版装备极品附魔已暂停。");
                    toggle();
                    return;
                }
                if (gearEquipSlot < 0 || mc.player.getInventory().getItem(gearEquipSlot).isEmpty()) {
                    mc.player.closeContainer();
                    setState(State.GEAR_IDLE);
                    return;
                }
                mc.gameMode.handleContainerInput(syncId, containerSlotOf(handler, gearEquipSlot), 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                // 确认装备确实已离开背包（成功存入成品箱），失败则重试有限次数
                if (gearEquipSlot >= 0 && !mc.player.getInventory().getItem(gearEquipSlot).isEmpty()) {
                    if (++卸货重试次数 < 3) {
                        guiTick = GUI操作延迟.get();
                        guiPhase = 0;
                        return;
                    }
                    mc.player.closeContainer();
                    gearFail(TaskErrorReason.REPEATED_FAILURE);
                    setState(State.GEAR_WALK_ERROR);
                    return;
                }
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                if (gearTask != null) gearTask.markDone();
                statDone++;
                notify("§a✓ 装备已达成极品目标，存入成品箱 §8▸ " + highlightText(gearProfile.gearName()));
                gearNext();
                setState(State.GEAR_IDLE);
            }
        }
    }

    private void tickGearWalkError() {
        if (canOpenNow(posError)) {
            stopBaritone();
            guiTick = 0;
            guiPhase = 0;
            setState(State.GEAR_STORE_ERROR);
        } else {
            walkToBlock(posError);
        }
    }

    private void tickGearStoreError() {
        if (guiTick > 0) { guiTick--; return; }
        if (!chestMenuOpen()) {
            if (guiPhase == 0) {
                interactBlock(posError);
                guiTick = GUI操作延迟.get();
                return;
            }
        }
        if (!chestMenuOpen()) return;
        ChestMenu handler = (ChestMenu) mc.player.containerMenu;
        int syncId = handler.containerId;
        switch (guiPhase) {
            case 0 -> {
                if (gearEquipSlot < 0 || mc.player.getInventory().getItem(gearEquipSlot).isEmpty()) {
                    mc.player.closeContainer();
                    gearNext();
                    setState(State.GEAR_IDLE);
                    return;
                }
                mc.gameMode.handleContainerInput(syncId, containerSlotOf(handler, gearEquipSlot), 0, ContainerInput.QUICK_MOVE, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                statError++;
                gearNext();
                setState(State.GEAR_IDLE);
            }
        }
    }
}
