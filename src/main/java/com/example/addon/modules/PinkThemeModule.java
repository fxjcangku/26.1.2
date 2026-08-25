package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

public final class PinkThemeModule extends YiyiaddonModule {
    private final Setting<Palette> palette = settings.getDefaultGroup().add(new EnumSetting.Builder<Palette>()
        .name("选择颜色")
        .description("选择界面和 HUD 使用的粉色配色。")
        .defaultValue(Palette.SAKURA)
        .onChanged(value -> {
            if (isActive()) apply(value);
        })
        .build()
    );

    public PinkThemeModule() {
        super(AddonTemplate.CATEGORY, "粉色主题", "一键把 Meteor 界面和 HUD 换成粉色，并可随时切换配色。");
    }

    @Override
    public void onActivate() {
        apply(palette.get());
    }

    @Override
    public void onDeactivate() {
        restoreDefault();
    }

    private void apply(Palette selected) {
        if (!(GuiThemes.get() instanceof MeteorGuiTheme theme)) {
            notifyError("当前不是 Meteor 默认主题，无法应用粉色配色");
            return;
        }

        theme.accentColor.set(color(selected.accent));
        theme.checkboxColor.set(color(selected.accent));
        theme.plusColor.set(color(selected.light));
        theme.minusColor.set(color(selected.strong));
        theme.favoriteColor.set(color(selected.light));
        theme.textColor.set(color(selected.text));
        theme.textSecondaryColor.set(color(selected.secondaryText));
        theme.textHighlightColor.set(color(selected.light));
        theme.titleTextColor.set(color(selected.title));
        theme.placeholderColor.set(color(selected.secondaryText));
        theme.moduleBackground.set(color(selected.moduleBackground));
        theme.separatorText.set(color(selected.light));
        theme.separatorCenter.set(color(selected.accent));
        theme.separatorEdges.set(color(selected.background));
        theme.sliderLeft.set(color(selected.accent));
        theme.sliderRight.set(color(selected.background));
        theme.backgroundColor.get().set(color(selected.background));
        theme.outlineColor.get().set(color(selected.outline));
        theme.scrollbarColor.get().set(color(selected.accent));
        theme.sliderHandle.get().set(color(selected.light));
        applyHud(selected);
        GuiThemes.save();
    }

    private void applyHud(Palette selected) {
        List<SettingColor> colors = new ArrayList<>();
        colors.add(color(selected.light));
        colors.add(color(selected.accent));
        colors.add(color(selected.strong));
        Hud.get().textColors.set(colors);
    }

    private void restoreDefault() {
        if (!(GuiThemes.get() instanceof MeteorGuiTheme theme)) return;

        theme.accentColor.reset();
        theme.checkboxColor.reset();
        theme.plusColor.reset();
        theme.minusColor.reset();
        theme.favoriteColor.reset();
        theme.textColor.reset();
        theme.textSecondaryColor.reset();
        theme.textHighlightColor.reset();
        theme.titleTextColor.reset();
        theme.placeholderColor.reset();
        theme.moduleBackground.reset();
        theme.separatorText.reset();
        theme.separatorCenter.reset();
        theme.separatorEdges.reset();
        theme.sliderLeft.reset();
        theme.sliderRight.reset();
        Hud.get().textColors.reset();
        GuiThemes.save();
    }

    private void select(Palette selected) {
        palette.set(selected);
        if (isActive()) apply(selected);
    }

    private static SettingColor color(int rgba) {
        return new SettingColor(
            rgba >> 24 & 0xFF,
            rgba >> 16 & 0xFF,
            rgba >> 8 & 0xFF,
            rgba & 0xFF
        );
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            table -> {
                addButton(theme, table, "樱花粉", Palette.SAKURA);
                addButton(theme, table, "蜜桃粉", Palette.PEACH);
                addButton(theme, table, "玫瑰粉", Palette.ROSE);
                table.row();
                addButton(theme, table, "莓果粉", Palette.BERRY);
                addButton(theme, table, "粉紫夜", Palette.PINK_NIGHT);
                table.row();
                addButton(theme, table, "薄荷灰", Palette.MINT_GRAY);
                addButton(theme, table, "雾霾蓝", Palette.MIST_BLUE);
                addButton(theme, table, "暖沙灰", Palette.WARM_SAND);
                table.row();
                addButton(theme, table, "鼠尾草", Palette.SAGE);
                addButton(theme, table, "灰紫夜", Palette.DUSK_LAVENDER);
                addButton(theme, table, "森林雾", Palette.FOREST_MIST);
                WButton restore = theme.button("恢复默认");
                restore.action = this::restoreDefault;
                table.add(restore).expandX();
                table.row();
            },
            new String[]{ "§l粉色主题 · 使用说明" },
            new String[]{
                "§e§l▌ 使用方法",
                "§f  1. 打开模块，立即应用当前选择的颜色",
                "§f  2. 点击上面的颜色按钮，可直接切换整套配色",
                "§f  3. 关闭模块，会恢复 Meteor 默认颜色"
            },
            new String[]{
                "§a§l▌ 会修改什么",
                "§f  · Meteor 菜单主色、按钮、勾选框、滑块和高亮文字",
                "§f  · 模块背景、窗口背景、边框、分隔线和滚动条",
                "§f  · HUD 全局文字颜色，按三种粉色自动渐变"
            },
            new String[]{
                "§b§l▌ 当前配色",
                "§f  · " + palette.get().displayName,
                "§f  · 颜色选择会自动保存，下次打开继续使用"
            },
            new String[]{
                "§c§l▌ 注意",
                "§f  · 只支持 Meteor 默认主题",
                "§f  · 点击恢复默认只恢复颜色，不会关闭本模块"
            }
        );
    }

    private void addButton(GuiTheme theme, meteordevelopment.meteorclient.gui.widgets.containers.WTable table, String title, Palette selected) {
        WButton button = theme.button(title);
        button.action = () -> select(selected);
        table.add(button).expandX();
    }

    public enum Palette {
        SAKURA("樱花粉", 0xFF69B4FF, 0xFFB7D5FF, 0xD9368FFF, 0xFFF5FAFF, 0xD8B7C7FF, 0xFFFFFFFF, 0x24151EFF, 0x351F2BEE, 0xB8417AFF),
        PEACH("蜜桃粉", 0xFF8FA3FF, 0xFFD0C4FF, 0xF06F8EFF, 0xFFF7F3FF, 0xD9BDB5FF, 0xFFFFFFFF, 0x271A18FF, 0x3B2723EE, 0xC65F72FF),
        ROSE("玫瑰粉", 0xE84A83FF, 0xFF91B6FF, 0xB91F5DFF, 0xFFF2F7FF, 0xD1AAB9FF, 0xFFFFFFFF, 0x25131BFF, 0x391D29EE, 0xA92E60FF),
        BERRY("莓果粉", 0xD94F9DFF, 0xF59BC9FF, 0xA52A78FF, 0xFFF1FAFF, 0xD2A8C2FF, 0xFFFFFFFF, 0x21131EFF, 0x351D30EE, 0x96256DFF),
        PINK_NIGHT("粉紫夜", 0xC95CFFFF, 0xF0A1FFFF, 0x9136C7FF, 0xFAEEFFFF, 0xC7A7D2FF, 0xFFFFFFFF, 0x17121FFF, 0x271C36EE, 0x7D3AA2FF),
        MINT_GRAY("薄荷灰", 0x8FB9A8FF, 0xB9D5C9FF, 0x668F80FF, 0xF1F7F3FF, 0xB7C9C0FF, 0xFFFFFFFF, 0x18211EFF, 0x293630EE, 0x729B88FF),
        MIST_BLUE("雾霾蓝", 0x829FB5FF, 0xB4C8D5FF, 0x5C788DFF, 0xEFF5F8FF, 0xB6C5CDFF, 0xFFFFFFFF, 0x182027FF, 0x29343BEE, 0x6E8DA1FF),
        WARM_SAND("暖沙灰", 0xB5A58FFF, 0xD4C8B7FF, 0x897966FF, 0xF7F3ECFF, 0xCDC3B5FF, 0xFFFFFFFF, 0x25211BFF, 0x393229EE, 0x9D8C73FF),
        SAGE("鼠尾草", 0x9BAF8FFF, 0xC3D0B6FF, 0x718264FF, 0xF2F6EDFF, 0xBEC8B6FF, 0xFFFFFFFF, 0x1D231AFF, 0x30392BEE, 0x849873FF),
        DUSK_LAVENDER("灰紫夜", 0x9D96B2FF, 0xC5BED1FF, 0x746D88FF, 0xF3F0F7FF, 0xC2BBCBFF, 0xFFFFFFFF, 0x211E28FF, 0x342F3DEE, 0x88809EFF),
        FOREST_MIST("森林雾", 0x789B8CFF, 0xA9C2B5FF, 0x527464FF, 0xEDF5F0FF, 0xAFC3B8FF, 0xFFFFFFFF, 0x17221DFF, 0x29382FEE, 0x668979FF);

        private final String displayName;
        private final int accent;
        private final int light;
        private final int strong;
        private final int text;
        private final int secondaryText;
        private final int title;
        private final int background;
        private final int moduleBackground;
        private final int outline;

        Palette(String displayName, int accent, int light, int strong, int text, int secondaryText, int title, int background, int moduleBackground, int outline) {
            this.displayName = displayName;
            this.accent = accent;
            this.light = light;
            this.strong = strong;
            this.text = text;
            this.secondaryText = secondaryText;
            this.title = title;
            this.background = background;
            this.moduleBackground = moduleBackground;
            this.outline = outline;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
