package com.example.addon.translations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class BaritoneChatTranslations {
    private static final Map<String, String> EXACT = Map.ofEntries(
        Map.entry("Paused", "已暂停"),
        Map.entry("Resumed", "已恢复"),
        Map.entry("Baritone is paused", "Baritone 已暂停"),
        Map.entry("Baritone is not paused", "Baritone 未暂停"),
        Map.entry("ok canceled", "已取消"),
        Map.entry("ok force canceled", "已强制取消"),
        Map.entry("Cleared goal", "已清除目标"),
        Map.entry("There was no goal to clear", "没有可清除的目标"),
        Map.entry("No goal set", "尚未设置目标"),
        Map.entry("No goal has been set", "尚未设置目标"),
        Map.entry("No process in control", "当前没有控制中的进程"),
        Map.entry("Not currently pathing", "当前未在寻路"),
        Map.entry("No waypoints found", "未找到路径点"),
        Map.entry("Multiple waypoints were found", "找到了多个路径点"),
        Map.entry("Set pos1 first before using pos2", "使用 pos2 前请先设置 pos1"),
        Map.entry("Invalid type", "类型无效"),
        Map.entry("Invalid value", "值无效"),
        Map.entry("Invalid position", "位置无效"),
        Map.entry("Invalid block", "方块无效"),
        Map.entry("Invalid item", "物品无效"),
        Map.entry("Invalid entity", "实体无效"),
        Map.entry("Unknown error", "未知错误"),
        Map.entry("Now pathing", "开始寻路"),
        Map.entry("Coming", "正在前往"),
        Map.entry("Already at surface", "已经位于地表"),
        Map.entry("No higher location found", "未找到更高的位置"),
        Map.entry("No positions known, are you sure the blocks are cached?", "没有已知位置，请确认目标方块已被缓存"),
        Map.entry("Blacklisted closest instances", "已将最近的目标加入黑名单"),
        Map.entry("Farming", "开始耕作"),
        Map.entry("Picking up all items", "正在拾取所有物品"),
        Map.entry("Picking up these items:", "正在拾取以下物品："),
        Map.entry("Settings saved", "设置已保存"),
        Map.entry("All settings have been reset to their default values", "所有设置已恢复默认值"),
        Map.entry("Reloaded", "已重新加载"),
        Map.entry("Saved", "已保存"),
        Map.entry("Done", "已完成"),
        Map.entry("Position 1 has been set", "位置 1 已设置"),
        Map.entry("Selection added", "选区已添加"),
        Map.entry("Selection copied", "选区已复制"),
        Map.entry("Filling now", "开始填充"),
        Map.entry("Building now", "开始建造"),
        Map.entry("Pathing complete", "寻路完成"),
        Map.entry("Done building", "建造完成"),
        Map.entry("Exploration failed", "探索失败"),
        Map.entry("Explored all chunks", "已探索全部区块"),
        Map.entry("Farm failed", "耕作失败"),
        Map.entry("No path found =(", "未找到路径 =("),
        Map.entry("Click to set goal to this position", "点击将目标设为此位置"),
        Map.entry("Click to rerun command", "点击重新执行命令"),
        Map.entry("Click to select", "点击选择"),
        Map.entry("Click to delete this waypoint", "点击删除此路径点"),
        Map.entry("Click to set goal to this waypoint", "点击将目标设为此路径点"),
        Map.entry("Click to return to the waypoints list", "点击返回路径点列表"),
        Map.entry("Old value: ", "旧值：")
    );

    private static final List<Template> TEMPLATES = List.of(
        template("Goal: (.+)", match -> "目标：" + match.group(1)),
        template("Command not found: (.+)", match -> "找不到命令：" + match.group(1)),
        template("Not enough arguments \\(expected at least (\\d+)\\)", match -> "参数不足（至少需要 " + match.group(1) + " 个）"),
        template("Too many arguments \\(expected at most (\\d+)\\)", match -> "参数过多（最多允许 " + match.group(1) + " 个）"),
        template("Error at argument #(.+): (.+)", match -> "第 " + match.group(1) + " 个参数出错：" + match.group(2)),
        template("Expected (.+), but got (.+) instead", match -> "应为 " + match.group(1) + "，但实际得到 " + match.group(2)),
        template("Expected (.+)", match -> "应为 " + match.group(1)),
        template("Could not find a handler for type (.+)", match -> "找不到类型 " + match.group(1) + " 对应的参数处理器"),
        template("Invalid (.+): (.+)", match -> "无效的" + match.group(1) + "：" + match.group(2)),
        template("Going to: (.+)", match -> "正在前往：" + match.group(1)),
        template("Exploring from (.+)", match -> "从 " + match.group(1) + " 开始探索"),
        template("Mining (.+)", match -> "正在挖掘 " + match.group(1)),
        template("Following all (.+)", match -> "正在跟随所有" + match.group(1)),
        template("Queued (\\d+) chunks for repacking", match -> "已将 " + match.group(1) + " 个区块加入重新整理队列"),
        template("Removed (\\d+) selections", match -> "已移除 " + match.group(1) + " 个选区"),
        template("Transformed (\\d+) selections", match -> "已变换 " + match.group(1) + " 个选区"),
        template("Restored (\\d+) waypoints", match -> "已恢复 " + match.group(1) + " 个路径点"),
        template("Position: (.+)", match -> "位置：" + match.group(1)),
        template("Old value: (.+)", match -> "旧值：" + match.group(1)),
        template("You are running Baritone v(.+)", match -> "当前运行 Baritone v" + match.group(1)),
        template("Value of setting (.+):", match -> "设置 " + match.group(1) + " 的值："),
        template("Settings reloaded from (.+)", match -> "已从 " + match.group(1) + " 重新加载设置"),
        template("Successfully loaded schematic for building", match -> "已成功加载待建造的原理图"),
        template("Origin: (.+)", match -> "原点：" + match.group(1)),
        template("Explore filter applied\\. Inverted: (.+)", match -> "探索过滤器已应用，反转：" + match.group(1)),
        template("Unable to find any path to (.+), blacklisting presumably unreachable closest instance\\.\\.\\.", match -> "无法找到前往 " + match.group(1) + " 的路径，正在将可能无法到达的最近目标加入黑名单……"),
        template("Unable to find any path to (.+), canceling mine", match -> "无法找到前往 " + match.group(1) + " 的路径，已取消挖掘"),
        template("No locations for (.+) known, cancelling", match -> "没有 " + match.group(1) + " 的已知位置，已取消"),
        template("Creating a tunnel (.+) block\\(s\\) high, (.+) block\\(s\\) wide, and (.+) block\\(s\\) deep", match -> "正在创建高 " + match.group(1) + "、宽 " + match.group(2) + "、深 " + match.group(3) + " 格的隧道")
    );

    private BaritoneChatTranslations() {}

    public static String translate(String text) {
        if (!YiyiaddonTranslator.enabled() || text == null || text.isEmpty()) return text;
        String exact = EXACT.get(text);
        if (exact != null) return exact;
        for (Template template : TEMPLATES) {
            var matcher = template.pattern().matcher(text);
            if (matcher.matches()) return template.translation().apply(matcher.toMatchResult());
        }
        return text;
    }

    public static Component translate(Component component) {
        if (!YiyiaddonTranslator.enabled() || component == null) return component;
        MutableComponent translated = Component.empty();
        component.visit((style, text) -> {
            translated.append(Component.literal(translate(text)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return translated;
    }

    public static Component[] translate(Component[] components) {
        if (!YiyiaddonTranslator.enabled() || components == null) return components;
        Component[] translated = new Component[components.length];
        for (int i = 0; i < components.length; i++) translated[i] = translate(components[i]);
        return translated;
    }

    private static Template template(String pattern, Function<MatchResult, String> translation) {
        return new Template(Pattern.compile(pattern, Pattern.DOTALL), translation);
    }

    private record Template(Pattern pattern, Function<MatchResult, String> translation) {}
}
