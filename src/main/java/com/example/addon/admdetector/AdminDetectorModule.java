package com.example.addon.admdetector;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.autodisconnect.CometDisconnectModule;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 管理员检测模块
 *
 * 独立监测附近玩家，识别管理员视察的典型形态（旁观/创造/隐身/隐藏），
 * 命中即聊天栏提示并调用自动断线保命；危险玩家离开范围同样提示。
 * 白名单玩家来了不退出，黑名单玩家来了立即退出。
 */
public final class AdminDetectorModule extends YiyiaddonModule {

    // ─── 设置分组 ───
    private final SettingGroup sgDetect = settings.createGroup("检测", true);
    private final SettingGroup sgList = settings.createGroup("名单", false);

    // ─── 检测设置 ───
    private final Setting<Integer> detectRange;
    private final Setting<Boolean> detectSpectator;
    private final Setting<Boolean> detectCreative;
    private final Setting<Boolean> detectInvisible;
    private final Setting<Boolean> detectHidden;

    // ─── 名单设置 ───
    private final Setting<List<String>> whitelist;
    private final Setting<List<String>> blacklist;

    // 断线防重锁：断线后 onTick 仍在跑，防止重复触发断线
    private boolean disconnecting = false;

    // 当前范围内已提示过的危险玩家（UUID -> 玩家名），用于进入/离开边沿判断
    private final Map<UUID, String> nearbyThreats = new HashMap<>();

    public AdminDetectorModule() {
        super(AddonTemplate.CATEGORY_TACTICAL, "管理员检测",
            "监测附近玩家，识别旁观/创造/隐身/隐藏的管理员，命中即断线保命。点击按钮查看说明。");

        detectRange = sgDetect.add(new IntSetting.Builder()
            .name("检测范围（格）")
            .description("危险玩家进入多少格内触发检测")
            .defaultValue(10)
            .min(1)
            .max(64)
            .noSlider()
            .build());

        detectSpectator = sgDetect.add(new BoolSetting.Builder()
            .name("检测旁观者")
            .description("旁观模式玩家靠近即命中（管理员视察常见形态）")
            .defaultValue(true)
            .build());

        detectCreative = sgDetect.add(new BoolSetting.Builder()
            .name("检测创造")
            .description("创造模式玩家靠近即命中")
            .defaultValue(true)
            .build());

        detectInvisible = sgDetect.add(new BoolSetting.Builder()
            .name("检测隐身")
            .description("隐身玩家靠近即命中（管理员隐身视察）")
            .defaultValue(true)
            .build());

        detectHidden = sgDetect.add(new BoolSetting.Builder()
            .name("检测隐藏玩家")
            .description("不在 Tab 列表的玩家靠近即命中（vanish 插件隐藏的管理员典型特征）")
            .defaultValue(true)
            .build());

        whitelist = sgList.add(new StringListSetting.Builder()
            .name("白名单（来了不退出）")
            .description("白名单内玩家接近不检测不提示，逗号分隔多个名字")
            .defaultValue(new ArrayList<>())
            .build());

        blacklist = sgList.add(new StringListSetting.Builder()
            .name("黑名单（来了就退出）")
            .description("黑名单内玩家接近立即断线，无视危险特征，逗号分隔多个名字")
            .defaultValue(new ArrayList<>())
            .build());
    }

    @Override
    public void onActivate() {
        // 重置状态：重新武装检测，清空历史进出记录
        disconnecting = false;
        nearbyThreats.clear();
    }

    @Override
    public void onDeactivate() {
        disconnecting = false;
        nearbyThreats.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        int range = detectRange.get();
        List<String> wl = whitelist.get();
        List<String> bl = blacklist.get();

        // 本次 tick 在范围内的危险玩家
        Set<UUID> currentThreats = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String name = player.getName().getString();

            // 白名单豁免：来了不退出，也不提示
            if (containsName(wl, name)) continue;

            // 距离判断：超出范围不处理
            if (mc.player.distanceTo(player) > range) continue;

            // 命中危险特征
            String reason = getThreatReason(player, name, bl);
            if (reason == null) continue;

            currentThreats.add(player.getUUID());

            // 首次进入：聊天栏提示 + 记录
            if (!nearbyThreats.containsKey(player.getUUID())) {
                nearbyThreats.put(player.getUUID(), name);
                notify("§c✗ 检测到危险玩家 §8▸ " + highlightText(name) + " §f· " + highlightFunction(reason));
            }
        }

        // 离开提示：之前记录但本次已不在范围内的危险玩家
        Iterator<Map.Entry<UUID, String>> it = nearbyThreats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> e = it.next();
            if (!currentThreats.contains(e.getKey())) {
                it.remove();
                notify("§a✓ 危险玩家已离开 §8▸ " + highlightText(e.getValue()));
            }
        }

        // 存在危险玩家且未断线 → 断线保命
        if (!currentThreats.isEmpty() && !disconnecting) {
            triggerDisconnect();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // 断线退出后自动关闭，避免重连后残留开启态误断
        if (isActive()) toggle();
    }

    /**
     * 判定玩家命中的危险特征，未命中返回 null。
     * 黑名单优先级最高，其次按旁观/创造/隐身/隐藏顺序。
     */
    private String getThreatReason(Player player, String name, List<String> blacklist) {
        if (containsName(blacklist, name)) return "黑名单";
        if (detectSpectator.get() && player.isSpectator()) return "旁观者";
        if (detectCreative.get() && isCreative(player)) return "创造模式";
        if (detectInvisible.get() && player.isInvisible()) return "隐身";
        if (detectHidden.get() && isHiddenFromTab(player)) return "隐藏";
        return null;
    }

    /**
     * 触发断线：调用自动断线模块断开服务器连接。
     */
    private void triggerDisconnect() {
        disconnecting = true;
        notify("§c✗ 已触发自动断线，正在退出服务器");
        CometDisconnectModule.disconnect("检测到危险玩家接近");
    }

    /**
     * 判断玩家是否为创造模式，优先 Tab 列表游戏模式，缺失时退回实体能力位。
     */
    private boolean isCreative(Player player) {
        GameType gm = getGameMode(player);
        if (gm != null) return gm == GameType.CREATIVE;
        return player.getAbilities().instabuild;
    }

    /**
     * 判断玩家是否被 vanish 插件隐藏（实体存在但不在 Tab 列表）。
     */
    private boolean isHiddenFromTab(Player player) {
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(player.getUUID()) == null;
    }

    /**
     * 读取玩家游戏模式，连接或 Tab 列表缺失返回 null。
     */
    private GameType getGameMode(Player player) {
        if (mc.getConnection() == null) return null;
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? null : info.getGameMode();
    }

    /**
     * 判断玩家名是否在名单内（忽略大小写与首尾空格）。
     */
    private boolean containsName(List<String> list, String name) {
        for (String entry : list) {
            if (entry != null && entry.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{"§l管理员检测 · 使用说明"},
            new String[]{
                "§e§l▌ 功能",
                "§f  · 监测附近玩家，识别管理员视察典型形态并自动断线保命。",
                "§f  · 检测到危险玩家进入范围 → 聊天栏提示 + 调用自动断线。",
                "§f  · 危险玩家离开范围 → 聊天栏提示。"
            },
            new String[]{
                "§b§l▌ 检测项",
                "§f  · 旁观者：管理员 spectator 视察。",
                "§f  · 创造：Tab 列表游戏模式为创造。",
                "§f  · 隐身：实体带隐身标志。",
                "§f  · 隐藏：不在 Tab 列表（vanish 插件典型特征）。"
            },
            new String[]{
                "§d§l▌ 名单",
                "§f  · 白名单：名单内玩家来了不退出、不提示（豁免）。",
                "§f  · 黑名单：名单内玩家来了立即断线，无视危险特征。"
            },
            new String[]{
                "§c§l▌ 注意",
                "§c⚠ 完全隐身到客户端无实体的管理员无法检测，这是客户端 hack 的固有限制。",
                "§c⚠ 提示仅发到自己的聊天栏，不会暴露给其他玩家。"
            }
        );
    }
}
