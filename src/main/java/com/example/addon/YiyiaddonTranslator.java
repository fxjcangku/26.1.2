package com.example.addon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class YiyiaddonTranslator {
    private static final Map<String, String> TRANSLATIONS = new HashMap<>();
    private static final Map<Module, String> MODULE_NAMES = new IdentityHashMap<>();
    private static final Map<Module, String> MODULE_DESCRIPTIONS = new IdentityHashMap<>();
    private static final Map<SettingGroup, String> GROUP_NAMES = new IdentityHashMap<>();
    private static final Map<Setting<?>, String> SETTING_NAMES = new IdentityHashMap<>();
    private static final Map<Setting<?>, String> SETTING_TITLES = new IdentityHashMap<>();
    private static final Map<Setting<?>, String> SETTING_DESCRIPTIONS = new IdentityHashMap<>();
    private static boolean loaded;

    private YiyiaddonTranslator() {}

    public static boolean enabled() {
        Modules modules = Modules.get();
        if (modules == null) return true;
        return modules.getOptional(com.example.addon.modules.YiyiaddonTranslationModule.class)
            .map(module -> module.isActive() && module.simplifiedChinese.get())
            .orElse(true);
    }

    public static String translate(String key, String fallback) {
        if (!enabled()) return fallback;
        load();
        return TRANSLATIONS.getOrDefault(key, fallback);
    }

    public static String translateVisible(String text) {
        if (!enabled() || text == null) return text;
        load();

        String normalized = text.trim();
        if (normalized.matches("\\(\\d+ selected\\)")) {
            return "（已选择 " + normalized.substring(1, normalized.indexOf(' ')) + " 项）";
        }
        if (normalized.equals("Bind") || normalized.startsWith("Bind:")) return "绑定";
        if (normalized.equals("Toggle on bind release") || normalized.startsWith("Toggle on bind release:")) {
            return "按键释放时切换：";
        }
        if (normalized.equals("Chat Feedback") || normalized.equals("Chat feedback") || normalized.startsWith("Chat Feedback:")) {
            return "聊天反馈：";
        }
        if (normalized.equals("Active") || normalized.startsWith("Active:")) return "激活：";
        if (normalized.equals("Logged in as")) return "登录身份";
        if (normalized.startsWith("Logged in as:")) return "登录身份：" + normalized.substring("Logged in as:".length());
        if (normalized.startsWith("Logged in as ")) return "登录身份：" + normalized.substring("Logged in as ".length());

        String direct = TRANSLATIONS.get(normalized);
        if (direct != null) return direct;

        String moduleKey = "module." + text.trim().toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        String moduleTitle = TRANSLATIONS.get(moduleKey);
        if (moduleTitle != null) return moduleTitle;

        return switch (text) {
            case "Modules" -> "模块";
            case "Config" -> "配置";
            case "GUI" -> "界面";
            case "HUD" -> "HUD";
            case "Friends" -> "好友";
            case "Macros" -> "宏";
            case "Multiplayer" -> "多人游戏";
            case "Proxies" -> "代理";
            case "Accounts" -> "账户";
            case "Using proxy" -> "正在使用代理";
            case "Not using a proxy" -> "未使用代理";
            case "Logged in as" -> "登录身份";
            case "Combat" -> "战斗";
            case "Player" -> "玩家";
            case "Movement" -> "移动";
            case "Render" -> "渲染";
            case "World" -> "世界";
            case "Rain Level" -> "降雨强度";
            case "Thunder Level" -> "雷暴强度";
            case "Rotation Hold" -> "旋转保持";
            case "Use Team Color" -> "使用队伍颜色";
            case "Scrollbar Color" -> "滚动条颜色";
            case "Hovered Scrollbar Color" -> "悬停滚动条颜色";
            case "Pressed Scrollbar Color" -> "按下滚动条颜色";
            case "Slider Handle Color" -> "滑块手柄颜色";
            case "Hovered Slider Handle Color" -> "悬停滑块手柄颜色";
            case "Pressed Slider Handle Color" -> "按下滑块手柄颜色";
            case "Slider Left Color" -> "滑块左侧颜色";
            case "Slider Right Color" -> "滑块右侧颜色";
            case "Starscript Text Color" -> "星语法文本颜色";
            case "Starscript Braces Color" -> "星语法花括号颜色";
            case "Starscript Parenthesis Color" -> "星语法圆括号颜色";
            case "Starscript Dots Color" -> "星语法点号颜色";
            case "Starscript Commas Color" -> "星语法逗号颜色";
            case "Starscript Operators Color" -> "星语法运算符颜色";
            case "Starscript Strings Color" -> "星语法字符串颜色";
            case "Starscript Numbers Color" -> "星语法数字颜色";
            case "Starscript Keywords Color" -> "星语法关键字颜色";
            case "Starscript Accessed Objects Color" -> "星语法已访问对象颜色";
            case "Profiles" -> "配置档";
            case "Search" -> "搜索";
            case "Favorites" -> "收藏";
            case "Bind" -> "绑定";
            case "Bind: " -> "绑定： ";
            case "Reset" -> "重置";
            case "Active" -> "激活";
            case "Toggle on bind release", "Toggle on bind release:" -> "按键释放时切换：";
            case "Chat feedback", "Chat Feedback:" -> "聊天反馈：";
            case "Copy config" -> "复制配置";
            case "Paste config" -> "粘贴配置";
            case "Visual" -> "视觉效果";
            case "Chat" -> "聊天";
            case "Misc" -> "杂项";
            case "Theme" -> "主题";
            case "Reset Layout" -> "重置布局";
            case "Reset Colors" -> "重置颜色";
            case "General" -> TRANSLATIONS.getOrDefault("setting.group.general", "常规");
            case "Colors" -> TRANSLATIONS.getOrDefault("setting.group.colors", "颜色");
            case "Text" -> "文本";
            case "Starscript" -> "星语法";
            case "Output" -> "输出";
            case "ClosestAngle" -> "视角最近";
            case "Adult" -> "成年";
            case "Both" -> "两者";
            case "Always" -> "始终";
            case "Background" -> "背景";
            case "Outline" -> "轮廓";
            case "Separator" -> "分隔线";
            case "Scrollbar" -> "滚动条";
            case "Slider" -> "滑块";
            case "Scale", "Text Scale" -> "缩放";
            case "Module Alignment" -> "模块对齐方式";
            case "Category Icons" -> "类别图标";
            case "Hide HUD" -> "隐藏 HUD";
            case "Hide In Menus" -> "在菜单中隐藏";
            case "Accent Color" -> "强调色";
            case "Checkbox Color" -> "复选框颜色";
            case "Plus Color" -> "加号颜色";
            case "Minus Color" -> "减号颜色";
            case "Favorite Color" -> "收藏颜色";
            case "Text Color" -> "文本颜色";
            case "Text Colors" -> "文本颜色";
            case "Text Secondary Text Color" -> "次要文本颜色";
            case "Text Highlight Color" -> "高亮文本颜色";
            case "Title Text Color" -> "标题文本颜色";
            case "Logged In Text Color" -> "已登录文本颜色";
            case "Placeholder Color" -> "占位符颜色";
            case "Background Color" -> "背景颜色";
            case "Hovered Background Color" -> "悬停背景颜色";
            case "Pressed Background Color" -> "按下背景颜色";
            case "Module Background Color" -> "模块背景颜色";
            case "Outline Color" -> "轮廓颜色";
            case "Hovered Outline Color" -> "悬停轮廓颜色";
            case "Pressed Outline Color" -> "按下轮廓颜色";
            case "Separator Text Color" -> "分隔线文本颜色";
            case "Separator Center Color" -> "分隔线中心颜色";
            case "Separator Edges Color" -> "分隔线边缘颜色";
            case "Border" -> "边框";
            case "Snapping Range" -> "吸附范围";
            case "Editor" -> "编辑器";
            case "Edit" -> "编辑";
            case "Clear" -> "清除";
            case "Reset to default elements" -> "重置为默认元素";
            case "Active:" -> "激活：";
            case "Custom Font" -> "自定义字体";
            case "Rainbow Speed" -> "彩虹速度";
            case "Title Screen Credits" -> "标题屏幕鸣谢";
            case "Title Screen Splashes" -> "标题屏幕标语";
            case "Custom Window Title" -> "自定义窗口标题";
            case "Friend Color" -> "好友颜色";
            case "Sync List Setting Widths" -> "同步列表设置宽度";
            case "Accounts Button" -> "账户按钮";
            case "Account Status" -> "账户状态";
            case "Proxies Button" -> "代理按钮";
            case "Proxy Status" -> "代理状态";
            case "Hidden Modules" -> "隐藏模块";
            case "Module Search Count" -> "模块搜索数量";
            case "Search Module Aliases" -> "搜索模块别名";
            case "Prefix" -> "前缀";
            case "Delete Chat Feedback" -> "删除聊天反馈";
            case "TopRight" -> "右上角";
            case "Select" -> "选择";
            case "None" -> "无";
            case "(0 selected)" -> "（未选择）";
            default -> text;
        };
    }

    public static void localizeModule(Module module) {
        if (module == null) return;
        load();
        String originalModuleName = MODULE_NAMES.computeIfAbsent(module, ignored -> module.name);
        String originalModuleDescription = MODULE_DESCRIPTIONS.computeIfAbsent(module, ignored -> module.description);
        String modulePrefix = "module." + key(originalModuleName);

        if (!enabled()) {
            ((ModuleTranslationAccess) module).yiyiaddon$setTitle(originalModuleName);
            ((ModuleTranslationAccess) module).yiyiaddon$setDescription(originalModuleDescription);
            for (SettingGroup group : module.settings) {
                String originalGroupName = GROUP_NAMES.computeIfAbsent(group, ignored -> group.name);
                ((SettingGroupTranslationAccess) group).yiyiaddon$setName(originalGroupName);
                for (Setting<?> setting : group) {
                    restoreSetting(setting);
                }
            }
            return;
        }

        ((ModuleTranslationAccess) module).yiyiaddon$setTitle(
            TRANSLATIONS.getOrDefault(modulePrefix, originalModuleName)
        );
        ((ModuleTranslationAccess) module).yiyiaddon$setDescription(
            TRANSLATIONS.getOrDefault(modulePrefix + ".description", originalModuleDescription)
        );
        for (SettingGroup group : module.settings) {
            String originalGroupName = GROUP_NAMES.computeIfAbsent(group, ignored -> group.name);
            String groupKey = key(originalGroupName);
            ((SettingGroupTranslationAccess) group).yiyiaddon$setName(
                TRANSLATIONS.getOrDefault("setting.group." + groupKey, originalGroupName)
            );
            for (Setting<?> setting : group) {
                String originalSettingName = SETTING_NAMES.computeIfAbsent(setting, ignored -> setting.name);
                String originalSettingTitle = SETTING_TITLES.computeIfAbsent(setting, ignored -> setting.title);
                String originalSettingDescription = SETTING_DESCRIPTIONS.computeIfAbsent(setting, ignored -> setting.description);
                if (originalModuleName.equalsIgnoreCase("Baritone") && !originalSettingName.isEmpty()) {
                    String baritoneKey = Character.toLowerCase(originalSettingName.charAt(0)) + originalSettingName.substring(1);
                    BaritoneSettingTranslations.Translation translation = BaritoneSettingTranslations.get(baritoneKey);
                    ((SettingTranslationAccess) setting).yiyiaddon$setTitle(translation.name());
                    ((SettingTranslationAccess) setting).yiyiaddon$setDescription(translation.description());
                    continue;
                }
                String prefix = modulePrefix + "." + groupKey + "." + key(originalSettingName);
                ((SettingTranslationAccess) setting).yiyiaddon$setTitle(
                    TRANSLATIONS.getOrDefault(prefix, originalSettingTitle)
                );
                ((SettingTranslationAccess) setting).yiyiaddon$setDescription(
                    TRANSLATIONS.getOrDefault(prefix + ".description", originalSettingDescription)
                );
            }
        }
    }

    public static void localizeBaritoneSettings(meteordevelopment.meteorclient.settings.Settings settings) {
        if (settings == null) return;
        load();

        for (SettingGroup group : settings) {
            String originalGroupName = GROUP_NAMES.computeIfAbsent(group, ignored -> group.name);
            ((SettingGroupTranslationAccess) group).yiyiaddon$setName(
                enabled() ? TRANSLATIONS.getOrDefault("setting.group." + key(originalGroupName), originalGroupName) : originalGroupName
            );

            for (Setting<?> setting : group) {
                String originalSettingName = SETTING_NAMES.computeIfAbsent(setting, ignored -> setting.name);
                String originalSettingTitle = SETTING_TITLES.computeIfAbsent(setting, ignored -> setting.title);
                String originalSettingDescription = SETTING_DESCRIPTIONS.computeIfAbsent(setting, ignored -> setting.description);
                if (!enabled()) {
                    ((SettingTranslationAccess) setting).yiyiaddon$setTitle(originalSettingTitle);
                    ((SettingTranslationAccess) setting).yiyiaddon$setDescription(originalSettingDescription);
                    continue;
                }

                BaritoneSettingTranslations.Translation translation = BaritoneSettingTranslations.get(originalSettingName);
                ((SettingTranslationAccess) setting).yiyiaddon$setTitle(translation.name());
                ((SettingTranslationAccess) setting).yiyiaddon$setDescription(translation.description());
            }
        }
    }

    private static void restoreSetting(Setting<?> setting) {
        String originalTitle = SETTING_TITLES.get(setting);
        String originalDescription = SETTING_DESCRIPTIONS.get(setting);
        if (originalTitle != null && originalDescription != null) {
            ((SettingTranslationAccess) setting).yiyiaddon$setTitle(originalTitle);
            ((SettingTranslationAccess) setting).yiyiaddon$setDescription(originalDescription);
        }
    }

    public static String localizeCategory(String name) {
        if (!enabled()) return name;
        load();
        String cleanName = name.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (cleanName.contains("yiyiaddon")) return name;
        String standard = TRANSLATIONS.get("category." + key(cleanName));
        if (standard != null) return standard;
        return TRANSLATIONS.getOrDefault("category." + key(name), name);
    }

    private static String key(String value) {
        return value.trim().toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;

        try (InputStream stream = YiyiaddonTranslator.class.getResourceAsStream(
            "/assets/yalu/lang/zh_cn.json"
        )) {
            if (stream == null) return;

            JsonObject json = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            json.entrySet().forEach(entry ->
                TRANSLATIONS.put(entry.getKey(), entry.getValue().getAsString())
            );
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
