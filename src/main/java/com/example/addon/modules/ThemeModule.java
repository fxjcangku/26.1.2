package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面主题模块
 * 一键切换 Meteor 界面与 HUD 的整套配色方案，支持樱花粉、雾霾蓝、鼠尾草等 11 种主题，
 * 关闭模块时恢复 Meteor 默认配色。
 */
public final class ThemeModule extends YiyiaddonModule {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Palette> palette = sgGeneral.add(new EnumSetting.Builder<Palette>()
        .name("选择配色")
        .description("选择界面和 HUD 使用的配色方案。")
        .defaultValue(Palette.SAKURA)
        .onChanged(value -> {
            if (isActive()) apply(value);
        })
        .build()
    );

    public ThemeModule() {
        super(AddonTemplate.CATEGORY, "界面主题", "一键切换 Meteor 界面和 HUD 的配色方案，支持多种主题并可随时切换。");
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
            notifyError("当前不是 Meteor 默认主题，无法应用配色方案");
            return;
        }

        // 普通颜色
        theme.accentColor.set(color(selected.accent));
        theme.checkboxColor.set(color(selected.accent));
        theme.plusColor.set(color(selected.light));
        theme.minusColor.set(color(selected.strong));
        theme.favoriteColor.set(color(selected.light));
        theme.textColor.set(color(selected.text));
        theme.textSecondaryColor.set(color(selected.secondaryText));
        theme.textHighlightColor.set(color(selected.light));
        theme.titleTextColor.set(color(selected.title));
        theme.loggedInColor.set(color(selected.light));
        theme.placeholderColor.set(color(selected.secondaryText));
        theme.moduleBackground.set(color(selected.moduleBackground));
        theme.separatorText.set(color(selected.light));
        theme.separatorCenter.set(color(selected.accent));
        theme.separatorEdges.set(color(selected.background));
        theme.sliderLeft.set(color(selected.accent));
        theme.sliderRight.set(color(selected.background));

        // 三态颜色（normal/hovered/pressed）：不补全 hovered/pressed 时，
        // 滑块把手、背景、边框、滚动条在悬停/按下会残留默认紫色
        applyThreeState(theme, "Background", "background", color(selected.background), color(selected.moduleBackground), color(selected.moduleBackground));
        applyThreeState(theme, "Outline", "outline", color(selected.outline), color(selected.outline), color(selected.outline));
        applyThreeState(theme, "Scrollbar", "Scrollbar", color(selected.accent), color(selected.accent), color(selected.accent));
        applyThreeState(theme, "Slider", "slider-handle", color(selected.accent), color(selected.light), color(selected.strong));

        applyHud(selected);
        GuiThemes.save();
        invalidateScreen();
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

        // 普通颜色设置（SettingColor）
        theme.accentColor.reset();
        theme.checkboxColor.reset();
        theme.plusColor.reset();
        theme.minusColor.reset();
        theme.favoriteColor.reset();
        theme.textColor.reset();
        theme.textSecondaryColor.reset();
        theme.textHighlightColor.reset();
        theme.titleTextColor.reset();
        theme.loggedInColor.reset();
        theme.placeholderColor.reset();
        theme.moduleBackground.reset();
        theme.separatorText.reset();
        theme.separatorCenter.reset();
        theme.separatorEdges.reset();
        theme.sliderLeft.reset();
        theme.sliderRight.reset();

        // 三态颜色：用 reset() 还原 Meteor 真实默认值（normal/hovered/pressed 三态一起恢复）
        resetThreeState(theme, "Background", "background");
        resetThreeState(theme, "Outline", "outline");
        resetThreeState(theme, "Scrollbar", "Scrollbar");
        resetThreeState(theme, "Slider", "slider-handle");

        // HUD 文字颜色
        Hud.get().textColors.reset();

        GuiThemes.save();
        invalidateScreen();
    }

    /**
     * 设置三态颜色（normal/hovered/pressed）。
     * ThreeStateColorSetting 内部三个状态是 private，只能通过 SettingGroup 按名字访问；
     * 名字由 Meteor 生成规则决定：baseName + "-color"、hovered-/pressed- + baseName + "-color"。
     */
    private static void applyThreeState(MeteorGuiTheme theme, String groupName, String baseName, SettingColor normal, SettingColor hovered, SettingColor pressed) {
        SettingGroup group = theme.settings.getGroup(groupName);
        setColor(group, baseName + "-color", normal);
        setColor(group, "hovered-" + baseName + "-color", hovered);
        setColor(group, "pressed-" + baseName + "-color", pressed);
    }

    private static void resetThreeState(MeteorGuiTheme theme, String groupName, String baseName) {
        SettingGroup group = theme.settings.getGroup(groupName);
        resetColor(group, baseName + "-color");
        resetColor(group, "hovered-" + baseName + "-color");
        resetColor(group, "pressed-" + baseName + "-color");
    }

    @SuppressWarnings("unchecked")
    private static void setColor(SettingGroup group, String name, SettingColor color) {
        Setting<?> setting = group.get(name);
        if (setting != null) ((Setting<SettingColor>) setting).set(color);
    }

    private static void resetColor(SettingGroup group, String name) {
        Setting<?> setting = group.get(name);
        if (setting != null) setting.reset();
    }

    /** 切换配色后立即重绘当前界面，保证颜色实时生效、不残留旧色 */
    private void invalidateScreen() {
        if (mc.screen instanceof WidgetScreen screen) screen.invalidate();
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
                // 独立按钮表：避免按钮与下方说明文字共享列，防止列宽被说明文字撑大导致按钮大小不一
                WTable buttons = theme.table();
                addButton(theme, buttons, "樱花粉", Palette.SAKURA);
                addButton(theme, buttons, "蜜桃粉", Palette.PEACH);
                addButton(theme, buttons, "玫瑰粉", Palette.ROSE);
                buttons.row();
                addButton(theme, buttons, "莓果粉", Palette.BERRY);
                addButton(theme, buttons, "粉紫夜", Palette.PINK_NIGHT);
                addButton(theme, buttons, "薄荷灰", Palette.MINT_GRAY);
                buttons.row();
                addButton(theme, buttons, "雾霾蓝", Palette.MIST_BLUE);
                addButton(theme, buttons, "暖沙灰", Palette.WARM_SAND);
                addButton(theme, buttons, "鼠尾草", Palette.SAGE);
                buttons.row();
                addButton(theme, buttons, "灰紫夜", Palette.DUSK_LAVENDER);
                addButton(theme, buttons, "森林雾", Palette.FOREST_MIST);
                addButton(theme, buttons, "青碧", Palette.TEAL);
                buttons.row();
                addButton(theme, buttons, "薰衣草", Palette.LAVENDER);
                addButton(theme, buttons, "珊瑚", Palette.CORAL);
                addButton(theme, buttons, "天蓝", Palette.SKY);
                buttons.row();
                addButton(theme, buttons, "奶油", Palette.CREAM);
                addButton(theme, buttons, "石墨", Palette.GRAPHITE);
                addUniformButton(theme, buttons, "恢复默认", this::restoreDefault);
                buttons.row();
                table.add(buttons);
                table.row();
            },
            new String[]{ "§l界面主题 · 使用说明" },
            new String[]{
                "§e§l▌ 使用方法",
                "§f  1. 打开模块，立即应用当前选择的配色",
                "§f  2. 点击上面的颜色按钮，可直接切换整套配色",
                "§f  3. 关闭模块，会恢复 Meteor 默认颜色"
            },
            new String[]{
                "§a§l▌ 会修改什么",
                "§f  · Meteor 菜单主色、按钮、勾选框、滑块和高亮文字",
                "§f  · 模块背景、窗口背景、边框、分隔线和滚动条",
                "§f  · HUD 全局文字颜色，按三种配色自动渐变"
            },
            new String[]{
                "§b§l▌ 当前配色",
                "§f  · " + palette.get().displayName,
                "§f  · 配色选择会自动保存，下次打开继续使用"
            },
            new String[]{
                "§c§l▌ 注意",
                "§f  · 只支持 Meteor 默认主题",
                "§f  · 点击恢复默认只恢复颜色，不会关闭本模块"
            }
        );
    }

    private void addButton(GuiTheme theme, WTable table, String title, Palette selected) {
        addUniformButton(theme, table, title, () -> select(selected));
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
        FOREST_MIST("森林雾", 0x789B8CFF, 0xA9C2B5FF, 0x527464FF, 0xEDF5F0FF, 0xAFC3B8FF, 0xFFFFFFFF, 0x17221DFF, 0x29382FEE, 0x668979FF),
        TEAL("青碧", 0x2BB3A3FF, 0x8AD8CCFF, 0x1B7F73FF, 0xEFF9F7FF, 0xB0CDC6FF, 0xFFFFFFFF, 0x14211FFF, 0x20332FEE, 0x1F8F81FF),
        LAVENDER("薰衣草", 0xA78BFAFF, 0xCDBCFDFF, 0x7C5EE0FF, 0xF7F4FFFF, 0xC9BEDDFF, 0xFFFFFFFF, 0x1E1A28FF, 0x2E283AEE, 0x8466D4FF),
        CORAL("珊瑚", 0xFF7A6BFF, 0xFFB4ABFF, 0xD95041FF, 0xFFF6F4FF, 0xDBB9B4FF, 0xFFFFFFFF, 0x261916FF, 0x3A2521EE, 0xCF5A4CFF),
        SKY("天蓝", 0x5CB3E8FF, 0xA5D2F2FF, 0x3A84B8FF, 0xF1F8FDFF, 0xB4CCDAFF, 0xFFFFFFFF, 0x16222AFF, 0x22343EEE, 0x4289BAFF),
        CREAM("奶油", 0xE8C97AFF, 0xF3DFAFFF, 0xB8964BFF, 0xFDFAF2FF, 0xD8CCB0FF, 0xFFFFFFFF, 0x262116FF, 0x3A3320EE, 0xC09A4EFF),
        GRAPHITE("石墨", 0x9AA0A6FF, 0xC6CBCFFF, 0x6E7378FF, 0xF4F5F6FF, 0xB9BDC1FF, 0xFFFFFFFF, 0x1B1D1FFF, 0x2A2D30EE, 0x7A7F84FF);

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
