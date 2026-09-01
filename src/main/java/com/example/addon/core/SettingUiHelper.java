package com.example.addon.core;

import com.example.addon.autochest.InfoTextSetting;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.client.Minecraft;

/**
 * 设置界面通用 UI 辅助（下拉框「当前值」实时显示模式）。
 *
 * <p>统一「下拉框 + 下方绿色当前值」的交互：下拉切换后触发界面重建，
 * 下方 {@link InfoTextSetting} 随之上屏刷新。所有模块的枚举下拉框统一走
 * {@link #currentValueLine} 与 {@link #reloadScreen}，避免各模块重复造轮子。</p>
 */
public final class SettingUiHelper {

    private SettingUiHelper() {
    }

    /**
     * 触发当前设置界面重建（延迟到下一 tick）。
     * 下拉框 action 回调栈中直接 reload 会清掉正在交互的控件，必须延后执行。
     */
    public static void reloadScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof WidgetScreen screen) screen.reload();
        });
    }

    /**
     * 给下拉框附加「当前值」绿色实时显示行。
     *
     * @param group   下拉框所在的设置分组
     * @param name    显示行标题（如「当前模式」「当前值」）
     * @param setting 下拉框设置（其 {@code get().toString()} 即当前选中值）
     * @param <T>     枚举类型
     * @return 已添加的展示行（一般无需保存返回值，仅为兼容现有引用）
     */
    public static <T> InfoTextSetting currentValueLine(SettingGroup group, String name, Setting<T> setting) {
        return currentValueLine(group, name, setting, null);
    }

    /**
     * 给下拉框附加「当前值」绿色实时显示行（带可见性，跟随下拉框同步显隐）。
     */
    public static <T> InfoTextSetting currentValueLine(SettingGroup group, String name, Setting<T> setting, IVisible visible) {
        return group.add(new InfoTextSetting(
            name,
            "当前选中的值（随上方下拉切换实时显示）。",
            () -> "§a§l" + setting.get().toString(),
            visible));
    }
}
