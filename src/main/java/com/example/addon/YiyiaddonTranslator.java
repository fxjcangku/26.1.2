package com.example.addon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class YiyiaddonTranslator {
    private static final Map<String, String> TRANSLATIONS = new HashMap<>();
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

        String direct = TRANSLATIONS.get(text);
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
            case "Profiles" -> "配置档";
            case "Search" -> "搜索";
            case "Favorites" -> "收藏";
            default -> text;
        };
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
