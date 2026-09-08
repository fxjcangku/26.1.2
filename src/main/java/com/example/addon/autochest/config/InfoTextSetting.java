package com.example.addon.autochest.config;

import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Supplier;

/**
 * 纯展示信息设置：在配置面板里渲染一行实时文字（不参与持久化）。
 *
 * <p>用于「当前模式：玩家控制模式」等随其它设置实时变化的提示。文字由
 * {@code textSupplier} 动态产出；当关联设置变化导致面板重建时自动刷新。</p>
 */
public final class InfoTextSetting extends Setting<String> {

    /** 实时文字提供器（每次面板构建时读取一次） */
    private final Supplier<String> textSupplier;

    public InfoTextSetting(String name, String description, Supplier<String> textSupplier) {
        this(name, description, textSupplier, null);
    }

    public InfoTextSetting(String name, String description, Supplier<String> textSupplier, IVisible visible) {
        super(name, description, "", null, null, visible);
        this.textSupplier = textSupplier;
    }

    /** 当前应展示的文字 */
    public String currentText() {
        return textSupplier.get();
    }

    // ── 纯展示：不做解析校验，不写存档 ──

    @Override
    protected String parseImpl(String str) {
        return str;
    }

    @Override
    protected boolean isValueValid(String value) {
        return true;
    }

    @Override
    protected CompoundTag save(CompoundTag tag) {
        return tag;
    }

    @Override
    protected String load(CompoundTag tag) {
        return "";
    }

    /** 注册控件工厂（AddonTemplate 启动时调用一次） */
    public static void register() {
        SettingsWidgetFactory.registerCustomFactory(InfoTextSetting.class,
            theme -> (table, setting) -> {
                InfoTextSetting s = (InfoTextSetting) setting;
                // 值文字与设置标题（title label）保持同一垂直对齐：Meteor 的标题
                // 走 top().marginTop(6)，这里不跟上就会比标题上移 6px 错位。
                table.add(theme.label(s.currentText())).top().marginTop(6).expandCellX();
            });
    }
}
