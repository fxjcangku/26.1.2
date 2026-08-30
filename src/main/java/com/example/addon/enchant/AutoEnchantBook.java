package com.example.addon.enchant;

import baritone.api.BaritoneAPI;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.farm.FarmPacketOps;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 * 扩展附魔 —— 经验获取→定向附魔→极品剔除→洗练仓储全自动闭环。
 *
 * 状态机：IDLE → 补给/打怪 → 附魔 → 鉴定 → 存成品 或 砂轮洗练 → 循环。
 */
public class AutoEnchantBook extends YiyiaddonModule {

    // ── 设置面板 ──────────────────────────────────────────────────────────

    private final SettingGroup sgBasic    = settings.createGroup("基础设置");
    private final SettingGroup sgAdvanced = settings.createGroup("扩展附魔分类");
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

    // 基础设置
    private final Setting<Integer> 单轮抽取次数 = sgBasic.add(new IntSetting.Builder()
        .name("单轮抽取次数").description("挂机循环每轮附魔最大次数，纯附魔模式忽略此项").defaultValue(10).min(1).max(100).sliderMax(50).build());

    private final Setting<Integer> GUI操作延迟 = sgBasic.add(new IntSetting.Builder()
        .name("GUI操作延迟(Tick)").description("所有 GUI 点击之间的等待 Tick 数").defaultValue(3).min(1).max(10).sliderMax(10).build());

    private final Setting<Integer> 书本补给组数 = sgBasic.add(new IntSetting.Builder()
        .name("书本补给组数").description("每次去书箱抓取的组数（1组=64本）").defaultValue(1).min(1).max(10).sliderMax(5).build());

    private final Setting<Integer> 青金石补给组数 = sgBasic.add(new IntSetting.Builder()
        .name("青金石补给组数").description("每次去青金石箱抓取的组数（1组=64个）").defaultValue(1).min(1).max(10).sliderMax(5).build());

    private final Setting<List<String>> 自定义附魔 = sgCustom.add(new StringListSetting.Builder()
        .name("自定义附魔目标")
        .description("每行填写一个附魔名称和等级，例如：打雷 5。支持中文、阿拉伯数字、罗马数字和不带等级的附魔。")
        .defaultValue(List.of())
        .build());

    private final Setting<RunMode> 运行模式 = sgBasic.add(new EnumSetting.Builder<RunMode>()
        .name("运行模式").description("纯附魔模式只使用当前经验，经验低于30级时停止；挂机循环会前往挂机点刷经验").defaultValue(RunMode.EXPERIENCE).build());

    private final Setting<Boolean> ESP标点 = sgBasic.add(new BoolSetting.Builder()
        .name("ESP标点").description("显示已设置点位的名称").defaultValue(true).visible(() -> false).build());

    private final Setting<Boolean> 返回挂机视角 = sgBasic.add(new BoolSetting.Builder()
        .name("返回挂机视角").description("到达挂机位后恢复设置该点位时记录的视角").defaultValue(true).visible(() -> false).build());

    private final Setting<Boolean> 成功提示音 = sgBasic.add(new BoolSetting.Builder()
        .name("成功提示音").description("刷到已选择的目标附魔书时播放本地提示音").defaultValue(true).build());

    private final Setting<SuccessSound> 成功提示音类型 = sgBasic.add(new EnumSetting.Builder<SuccessSound>()
        .name("成功提示音类型").description("选择刷到目标附魔书时播放的音效").defaultValue(SuccessSound.CHALLENGE_COMPLETE).visible(成功提示音::get).build());

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
        WALK_TO_RESTOCK("前往补给"), RESTOCKING("补给");

        private final String cn;
        State(String cn) { this.cn = cn; }
        /** 状态中文名（用于状态播报） */
        public String cn() { return cn; }
    }

    private State state = State.IDLE;
    private String lastNotifiedState = "";
    private int   remainingAttempts = 0;
    private int   guiTick           = 0;
    private int   guiPhase          = 0;
    private static final int GUI_OPEN_PENDING = 20;
    private static final int GUI_OPEN_TIMEOUT = 40;

    private String hitTask = null;
    private int enchantedBookSlot = -1;
    private boolean hangoutViewRestored;

    private final List<String> activeTasks = new ArrayList<>();

    private final EnchantmentSelectSetting 剑附魔选择;
    private final EnchantmentSelectSetting 斧头附魔选择;
    private final EnchantmentSelectSetting 弓附魔选择;
    private final EnchantmentSelectSetting 护甲附魔选择;
    private final EnchantmentSelectSetting 工具通用附魔选择;

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
        super(AddonTemplate.CATEGORY_AUTOMATION, "扩展附魔",
            "经验获取→定向附魔→极品剔除→洗练仓储全自动闭环。详细参考下面使用说明。");
        剑附魔选择 = addSelector("剑附魔属性", sgSword, sgAdvanced);
        斧头附魔选择 = addSelector("斧头附魔属性", sgAxe, sgAdvanced);
        弓附魔选择 = addSelector("弓附魔属性", sgBow, sgAdvanced);
        护甲附魔选择 = addSelector("护甲附魔属性", sgArmor, sgAdvanced);
        工具通用附魔选择 = addSelector("工具与通用附魔属性", sgOther, sgAdvanced);
        addTargets(sgVanillaArmor, "保护 IV", "火焰保护 IV", "摔落缓冲 IV", "爆炸保护 IV", "弹射物保护 IV", "水下呼吸 III", "水下速掘", "荆棘 II", "深海探索者 III");
        addTargets(sgVanillaMelee, "锋利 IV", "亡灵杀手 IV", "节肢杀手 IV", "击退 II", "火焰附加 II", "抢夺 III", "横扫之刃 III");
        addTargets(sgVanillaTool, "效率 IV", "精准采集", "时运 III");
        addTargets(sgVanillaBow, "力量 IV", "冲击 II", "火矢", "无限");
        addTargets(sgVanillaFishing, "海之眷顾 III", "饵钓 III");
        addTargets(sgVanillaTrident, "忠诚 III", "穿刺 V", "激流 III", "引雷");
        addTargets(sgVanillaCrossbow, "多重射击", "快速装填 III", "穿透 IV");
        addTargets(sgVanillaCommon, "耐久 III");
        addSelector("原版防具附魔", sgVanillaArmor, sgVanilla);
        addSelector("原版近战附魔", sgVanillaMelee, sgVanilla);
        addSelector("原版工具附魔", sgVanillaTool, sgVanilla);
        addSelector("原版弓附魔", sgVanillaBow, sgVanilla);
        addSelector("原版钓竿附魔", sgVanillaFishing, sgVanilla);
        addSelector("原版三叉戟附魔", sgVanillaTrident, sgVanilla);
        addSelector("原版弩附魔", sgVanillaCrossbow, sgVanilla);
        addSelector("原版通用附魔", sgVanillaCommon, sgVanilla);
        sgAdvanced.sectionExpanded = true;
        sgVanilla.sectionExpanded = false;
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

    private EnchantmentSelectSetting addSelector(String name, SettingGroup source, SettingGroup target) {
        List<BoolSetting> values = new ArrayList<>();
        for (Setting<?> setting : source) if (setting instanceof BoolSetting boolSetting) values.add(boolSetting);
        List<String> names = new ArrayList<>();
        for (BoolSetting setting : values) names.add(setting.name);
        EnchantmentSelectSetting selector = new EnchantmentSelectSetting(name, names, values);
        target.add(selector);
        return selector;
    }

    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§l扩展附魔 · 使用说明" },
            new String[]{
                "§e§l▌ 首次配置",
                "§f  1. 升级到本版本后，先在要使用的服务器和维度执行 §e.fumo clear",
                "§f  2. 准星对准对应方块，依次执行：",
                "§f     §e.fumo set 书§f / §e青晶石§f / §e成品箱§f / §e附魔台§f / §e砂轮",
                "§f  3. 挂机循环模式站在刷怪点并调整好杀怪视角，再执行 §e.fumo set 挂机位§f（纯附魔模式不需要）",
                "§f  4. 在扩展附魔分类或原版附魔分类中选择需要收集的目标词条",
                "§f  5. 将带“横扫之刃”的剑放在背包或快捷栏任意位置",
                "§f  6. 书箱放空白书，青金石箱放青金石，成品箱预留空间"
            },
            new String[]{
                "§a§l▌ 运行流程",
                "§f  · 物资不足时自动前往对应补给箱取书或青金石",
                "§f  · 经验不足时，挂机循环返回挂机位；纯附魔模式低于30级后停止",
                "§f  · 横扫之刃剑在主背包时会自动换入当前快捷栏并选中",
                "§f  · 达到目标等级后自动前往附魔台执行 30 级附魔",
                "§f  · 未命中目标词条时前往砂轮洗练，再继续下一次附魔",
                "§f  · 命中目标词条时播放提示音并将附魔书存入成品箱"
            },
            new String[]{
                "§b§l▌ 参数说明",
                "§f  · §e单轮抽取次数§f — 普通模式每轮计划执行的附魔次数",
                "§f  · §eGUI操作延迟§f — 服务器卡顿或吞点击时适当调大",
                "§f  · §e书本/青金石补给组数§f — 每次补给希望保有的组数",
                "§f  · §e自定义附魔目标§f — 每行填写一个附魔名称，可带等级；例如“打雷5”，无需输入空格",
                "§f  · §e运行模式§f — 纯附魔模式只消耗当前经验，低于 30 级提示停止；挂机循环会前往挂机点补经验",
                "§f  · §eESP标点§f — 开关六个已设置点位的固定 0.6 字号名称",
                "§f  · §e返回挂机视角§f — 回到挂机位后恢复设置点位时的视角",
                "§f  · §e成功提示音§f — 命中目标词条时播放所选音效"
            },
            new String[]{
                "§6§l▌ 原版附魔分类",
                "§f  · 仅收录普通书通过附魔台随机附魔可以获得的词条",
                "§f  · 每种词条只提供附魔台实际能刷出的最高等级",
                "§f  · 锋利、效率、力量等书本附魔最高为 IV，不显示无法直接刷出的 V",
                "§f  · 不包含经验修补、冰霜行者、灵魂疾行、迅捷潜行和诅咒",
                "§f  · 原版与服务器扩展词条可以同时选择并自动收集"
            },
            new String[]{
                "§d§l▌ 换服、换维度与指令",
                "§f  · 一套点位只能用于设置它时所在的服务器和维度",
                "§f  · 去其他服务器、下界或末地时，旧点位不会运行，也不会乱跑",
                "§f  · 要在新地点使用：先执行 §e.fumo clear§f，再重新设置六个点位",
                "§f  · 回到原来的服务器和维度时，旧点位仍可直接使用，不必重设",
                "§f  · §e.fumo status§f — 查看坐标和当前地点是否匹配",
                "§f  · §e.fumo remove <节点>§f — 仅删除一个点位，方便在同一地点修改",
                "§f  · 重设挂机位会同时更新站立坐标和挂机视角"
            },
            new String[]{
                "§c§l▌ 注意",
                "§f  · 单人世界与服务器均可使用",
                "§f  · 至少选择一个目标词条，否则模块不会启动",
                "§f  · 找不到横扫之刃剑时仍会启用 KillAura，但无法保证刷怪效率",
                "§f  · 成品箱满、书箱或青金石箱空时会提示并自动停机",
                "§f  · 自动换入横扫之刃剑会占用当前选中的快捷栏格"
            }
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
        if (!reportSelfCheck(selfCheck())) return;
        if (pointServer == null || pointDimension == null) {
            notifyError("旧版点位没有服务器和维度信息，请执行 .fumo clear 后重新设置！");
            toggle();
            return;
        }
        if (运行模式.get() == RunMode.DRAIN && posHangout != null) {
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
        guiTick = 0;
        guiPhase = 0;
        hangoutViewRestored = false;
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
        report.append("§a§l✓ 扩展附魔 · 启动报告");

        // 运行模式：纯附魔 / 挂机循环
        report.append("\n§7当前模式　§8▸ ").append(highlightFunction(运行模式.get().toString())).append("§r");

        // 目标词条统计：总量 + 原版/扩展拆分
        report.append("\n§7目标词条　§8▸ ").append(highlightNumber(totalTasks + " 本")).append("§r");
        report.append("\n§7原版词条　§8▸ ").append(highlightNumber(vanillaTasks + " 本")).append("§r");
        report.append("\n§7扩展词条　§8▸ ").append(highlightNumber(extensionTasks + " 本")).append("§r");

        // 单轮抽取只在挂机循环生效，纯附魔模式忽略
        if (运行模式.get() == RunMode.EXPERIENCE) {
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
            guiPhase = 0;
            guiTick = GUI操作延迟.get();
        }
    }

    /**
     * 是否静默取消容器屏幕。
     * 只在模块激活且处于容器操作状态时生效，避免干扰玩家手动开箱。
     */
    private boolean shouldSuppressScreen(Screen screen) {
        if (!isActive() || !(screen instanceof AbstractContainerScreen<?>)) return false;
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

        if (isPathing()) {
            if (!state.name().startsWith("WALK_") && state != State.FARMING) return;
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
        }
    }

    // ── 状态处理器 ────────────────────────────────────────────────────────

    private void tickIdle() {
        if (needRestock()) {
            setState(State.WALK_TO_RESTOCK);
            return;
        }
        int xpLevel = mc.player.experienceLevel;
        if (运行模式.get() == RunMode.DRAIN) {
            if (xpLevel >= 30) {
                setState(State.WALK_TO_ENCHANT);
            } else {
                notify("§e纯附魔模式已完成，当前经验低于30级，停止自动化。请切换到挂机循环后重新启动。");
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
        int targetLevel = 30 + 3 * (单轮抽取次数.get() - 1);
        if (mc.player.experienceLevel >= targetLevel) {
            stopKillAura();
            remainingAttempts = 单轮抽取次数.get();
            setState(State.WALK_TO_ENCHANT);
        }
    }

    private void tickWalkToEnchant() {
        if (!isBlockAt(posEnchant, Blocks.ENCHANTING_TABLE)) {
            stopBaritone();
            notifyError("附魔台不存在或已被挖掉，自动化已停止。请重新设置附魔台点位。");
            toggle();
            return;
        }
        if (arrivedAt(posEnchant)) {
            if (needRestock() || countInInventory(Items.LAPIS_LAZULI) < 3) { setState(State.WALK_TO_RESTOCK); return; }
            guiTick = 0; guiPhase = 0;
            setState(State.ENCHANTING);
        } else {
            walkTo(posEnchant);
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
                mc.gameMode.handleContainerInput(syncId, invSlot, 0, ContainerInput.PICKUP, mc.player);
                guiTick = GUI操作延迟.get();
                guiPhase = 1;
            }
            case 1 -> {
                if (!mc.player.containerMenu.getCarried().is(Items.BOOK)) return;
                mc.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.PICKUP, mc.player);
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
                if (运行模式.get() == RunMode.EXPERIENCE) remainingAttempts--;
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
        if (arrivedAt(posGrindstone)) {
            guiTick = 0; guiPhase = 0;
            setState(State.GRINDING);
        } else {
            walkTo(posGrindstone);
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
                mc.player.closeContainer();
                guiTick = GUI操作延迟.get();
                setState(State.IDLE);
            }
        }
    }

    private void tickWalkToStore() {
        if (arrivedAt(posOutput)) {
            guiTick = 0; guiPhase = 0;
            setState(State.STORING);
        } else {
            walkTo(posOutput);
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
            if (arrivedAt(posBook)) {
                guiTick = 0; guiPhase = 0;
                setState(State.RESTOCKING);
            } else {
                walkTo(posBook);
            }
        } else if (needLapisRestock()) {
            if (arrivedAt(posLapis)) {
                setState(State.RESTOCKING);
                guiPhase = 10;
            } else {
                walkTo(posLapis);
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
                guiTick = GUI操作延迟.get();
                return;
            }
        }

        String name = isLapis ? "青金石" : "空白书";
        mc.player.closeContainer();
        notifyError(name + "补给箱已空！自动停机。");
        toggle();
    }

    // ── 2D 渲染 ────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!ESP标点.get() || !matchesCurrentPointContext()) return;
        renderLabel(event, posBook,       "📚 §a§l[书本箱]",   new Color(80,  230, 160, 200));
        renderLabel(event, posLapis,      "💎 §9§l[青金石箱]", new Color(70,  130, 255, 200));
        renderLabel(event, posOutput,     "📦 §6§l[成品箱]",   new Color(255, 200, 50,  200));
        renderLabel(event, posEnchant,    "✨ §d§l[附魔台]",   new Color(200, 100, 255, 200));
        renderLabel(event, posGrindstone, "⚙ §7§l[砂轮]",      new Color(160, 160, 160, 200));
        renderLabel(event, posHangout,    "⚔ §c§l[挂机位]",    new Color(255, 80,  80,  200));
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

    public String currentServer() {
        if (mc.getCurrentServer() == null || mc.getCurrentServer().ip == null) return null;
        return mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
    }

    public String currentDimension() {
        return mc.level == null ? null : mc.level.dimension().identifier().toString();
    }

    public boolean matchesCurrentPointContext() {
        return Objects.equals(pointServer, currentServer()) && Objects.equals(pointDimension, currentDimension());
    }

    public boolean hasAnyPosition() {
        return posBook != null || posLapis != null || posOutput != null || posEnchant != null || posGrindstone != null || posHangout != null;
    }

    public void clearPoints() {
        posBook = null;
        posLapis = null;
        posOutput = null;
        posEnchant = null;
        posGrindstone = null;
        posHangout = null;
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
     * 启动自检：收集所有未绑定的点位，交给 reportSelfCheck 一次性多行播报。
     * 缺项文案「§颜色点位名§f·未绑定」，与 AutoMinerModule 自检风格保持一致。
     */
    private List<String> selfCheck() {
        List<String> missing = new ArrayList<>();
        if (posBook       == null) missing.add("§6书本箱§f·未绑定");
        if (posLapis      == null) missing.add("§2青金石箱§f·未绑定");
        if (posOutput     == null) missing.add("§6成品箱§f·未绑定");
        if (posEnchant    == null) missing.add("§d附魔台§f·未绑定");
        if (posGrindstone == null) missing.add("§d砂轮§f·未绑定");
        if (运行模式.get() == RunMode.EXPERIENCE && posHangout == null) missing.add("§d挂机位§f·未绑定");
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

    private boolean arrivedAt(BlockPos pos) {
        if (pos == null) return false;
        double dist = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
        return dist <= 4.0;
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

    private void walkTo(BlockPos pos) {
        if (pos == null) return;
        if (!isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone()
                .getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalNear(pos, 2));
        }
    }

    private void interactBlock(BlockPos pos) {
        if (pos == null) return;
        if (!arrivedAt(pos)) return;
        // 直接发包开箱/开附魔台/开砂轮，带 sequence 预测处理。
        // 窗口失焦时 mc.gameMode.useItemOn 会被吞导致开箱失败，发包方式不受影响。
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
        // 状态播报：只播有实质动作的工作状态，寻路过渡（WALK_TO_*）不播防刷屏
        // 带去重锁，循环类流程每轮每个工作状态只播一次
        if (isWorkState(newState) && !newState.cn().equals(lastNotifiedState)) {
            notify("§7正在" + newState.cn() + "...");
            lastNotifiedState = newState.cn();
        } else if (newState == State.IDLE) {
            lastNotifiedState = "";
        }
    }

    /** 是否为需要播报进度的工作状态（排除寻路过渡与待机） */
    private boolean isWorkState(State s) {
        return s == State.FARMING || s == State.ENCHANTING || s == State.CHECKING
            || s == State.GRINDING || s == State.STORING || s == State.RESTOCKING;
    }
}
