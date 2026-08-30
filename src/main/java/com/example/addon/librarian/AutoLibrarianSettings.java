// 附魔交易所 Meteor 设置
package com.example.addon.librarian;

import com.example.addon.librarian.config.SuccessSound;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnchantmentListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Set;

public final class AutoLibrarianSettings {
    // 分组
    private final SettingGroup grpTarget;
    private final SettingGroup grpBehavior;
    private final SettingGroup grpNotification;
    private final SettingGroup grpDebug;
    private final SettingGroup grpKeybind;

    // 目标
    public final Setting<Set<ResourceKey<Enchantment>>> targetEnchantments;

    // 行为
    public final Setting<Integer> searchRadius;
    public final Setting<Integer> maximumEmeraldPrice;
    public final Setting<Integer> professionTimeout;
    public final Setting<Integer> actionDelay;
    public final Setting<Integer> resetDelay;
    public final Setting<Boolean> removeTargetOnFound;

    // 通知
    public final Setting<Boolean> playNotificationSound;
    public final Setting<SuccessSound> successSound;

    // 调试
    public final Setting<Boolean> debugMode;
    public final Setting<Boolean> chatFeedback;

    // 快捷键
    public final Setting<Keybind> pauseKeybind;

    public AutoLibrarianSettings(Settings settings) {
        grpTarget       = settings.createGroup("目标");
        grpBehavior     = settings.createGroup("行为");
        grpNotification = settings.createGroup("通知");
        grpDebug        = settings.createGroup("调试");
        grpKeybind      = settings.createGroup("快捷键");

        // ── 目标 ─────────────────────────────────────────────
        targetEnchantments = grpTarget.add(new EnchantmentListSetting.Builder()
            .name("目标附魔")
            .description("选择附魔类型，模块自动使用该附魔的最高交易等级。")
            .defaultValue(Enchantments.MENDING)
            .build());

        // ── 行为 ─────────────────────────────────────────────
        searchRadius = grpBehavior.add(new IntSetting.Builder()
            .name("村民搜索半径")
            .description("固定交易站村民搜索半径（格）。")
            .defaultValue(32)
            .min(1)
            .sliderRange(1, 64)
            .build());

        maximumEmeraldPrice = grpBehavior.add(new IntSetting.Builder()
            .name("最高绿宝石价格")
            .description("允许购买的单本附魔书最高绿宝石成本。")
            .defaultValue(64)
            .min(1)
            .sliderRange(1, 64)
            .build());

        professionTimeout = grpBehavior.add(new IntSetting.Builder()
            .name("职业等待超时")
            .description("等待村民职业同步的最大 Tick 数。")
            .defaultValue(200)
            .min(20)
            .sliderRange(20, 1200)
            .build());

        actionDelay = grpBehavior.add(new IntSetting.Builder()
            .name("动作延迟")
            .description("普通业务动作之间的 Tick 间隔。")
            .defaultValue(2)
            .min(1)
            .sliderRange(1, 20)
            .build());

        resetDelay = grpBehavior.add(new IntSetting.Builder()
            .name("刷新延迟")
            .description("拆除与重新放置讲台之间的最小 Tick 间隔。")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 100)
            .build());

        removeTargetOnFound = grpBehavior.add(new BoolSetting.Builder()
            .name("找到后移除目标")
            .description("成交验证成功后，从本次运行目标集合移除已完成目标。")
            .defaultValue(false)
            .build());

        // ── 通知 ─────────────────────────────────────────────
        playNotificationSound = grpNotification.add(new BoolSetting.Builder()
            .name("提示音")
            .description("找到目标和完成目标时播放提示音。")
            .defaultValue(true)
            .build());

        successSound = grpNotification.add(new EnumSetting.Builder<SuccessSound>()
            .name("附魔成功音效")
            .description("找到目标附魔时播放的音效。")
            .defaultValue(SuccessSound.CHALLENGE_COMPLETE)
            .visible(playNotificationSound::get)
            .build());

        // ── 调试 ─────────────────────────────────────────────
        debugMode = grpDebug.add(new BoolSetting.Builder()
            .name("调试模式")
            .description("输出状态与移动诊断信息。")
            .defaultValue(false)
            .build());

        chatFeedback = grpDebug.add(new BoolSetting.Builder()
            .name("聊天反馈")
            .description("在聊天栏输出业务反馈。")
            .defaultValue(true)
            .build());

        // ── 快捷键 ───────────────────────────────────────────
        pauseKeybind = grpKeybind.add(new KeybindSetting.Builder()
            .name("暂停快捷键")
            .description("按下后切换暂停/继续状态，保留当前村民和附魔进度。")
            .defaultValue(Keybind.none())
            .build());
    }
}
