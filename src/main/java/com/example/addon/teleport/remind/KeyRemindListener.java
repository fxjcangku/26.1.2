package com.example.addon.teleport.remind;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * 按键提醒监听器：模块未开启时按下三个功能键之一 → 回调模块播报「请先开启模块」。
 *
 * <p>常驻事件总线、与模块订阅生命周期完全解耦：模块自身的 onActivate/onDeactivate
 * 订阅模式保持原样，检测与节流逻辑全部收在本类，不对模块事件接线做任何侵入。
 * 是否真的提醒由回调方（模块）按 isActive() 裁决。</p>
 */
public final class KeyRemindListener {

    /** 命中功能键时的回调：参数为命中的功能键名称 */
    @FunctionalInterface
    public interface Callback {
        void onTrigger(String keyName);
    }

    /** 三个功能按键（只读取绑定值，不触发 action） */
    private final KeybindSetting ground;
    private final KeybindSetting wall;
    private final KeybindSetting coord;

    /** 命中回调：由模块实现播报（前缀/高亮统一走基类） */
    private final Callback callback;

    /** 提醒节流（毫秒） */
    private long lastRemindMs;

    public KeyRemindListener(KeybindSetting ground, KeybindSetting wall, KeybindSetting coord, Callback callback) {
        this.ground = ground;
        this.wall = wall;
        this.coord = coord;
        this.callback = callback;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    /** 键盘松开：匹配三个功能键之一才回调 */
    @EventHandler(priority = EventPriority.LOW)
    private void onKeyInput(KeyInputEvent event) {
        if (event.action != KeyAction.Release) return;
        match(event.input);
    }

    /** 鼠标松开：匹配三个功能键之一才回调 */
    @EventHandler(priority = EventPriority.LOW)
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Release) return;
        match(event.input);
    }

    /** 按输入类型区分键/鼠绑定并定位命中的功能键 */
    private void match(Object input) {
        if (input instanceof KeyEvent keyEvent) {
            if (ground.get().matches(keyEvent)) fire(ground.name);
            else if (wall.get().matches(keyEvent)) fire(wall.name);
            else if (coord.get().matches(keyEvent)) fire(coord.name);
            return;
        }
        if (input instanceof MouseButtonInfo buttonInfo) {
            if (ground.get().matches(buttonInfo)) fire(ground.name);
            else if (wall.get().matches(buttonInfo)) fire(wall.name);
            else if (coord.get().matches(buttonInfo)) fire(coord.name);
        }
    }

    /** 节流后回调：3 秒内同键不重复提醒 */
    private void fire(String keyName) {
        long now = System.currentTimeMillis();
        if (now - lastRemindMs < 3000) return;
        lastRemindMs = now;
        callback.onTrigger(keyName);
    }
}