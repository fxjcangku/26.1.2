package com.example.addon.translations;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;

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
        Map.entry("Failed", "失败"),
        Map.entry("No waypoints found", "未找到路径点"),
        Map.entry("No waypoints found by that tag", "未找到具有该标签的路径点"),
        Map.entry("Multiple waypoints were found", "找到了多个路径点"),
        Map.entry("Multiple waypoints were found:", "找到了多个路径点："),
        Map.entry("All waypoints:", "全部路径点："),
        Map.entry("Waypoint added: ", "已添加路径点："),
        Map.entry("Click to show a command to recreate this waypoint", "点击显示重新创建此路径点的命令"),
        Map.entry("That waypoint has successfully been deleted, click to restore it", "路径点已删除，点击恢复"),
        Map.entry("Set pos1 first before using pos2", "使用 pos2 前请先设置 pos1"),
        Map.entry("Invalid type", "类型无效"),
        Map.entry("Invalid value", "值无效"),
        Map.entry("Invalid position", "位置无效"),
        Map.entry("Invalid block", "方块无效"),
        Map.entry("Invalid item", "物品无效"),
        Map.entry("Invalid entity", "实体无效"),
        Map.entry("Unknown error", "未知错误"),
        Map.entry("Death position saved.", "死亡位置已保存"),
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
        Map.entry("Please specify 'all' as an argument to reset to confirm you'd really like to do this", "如需确认重置全部设置，请指定参数 'all'"),
        Map.entry("ALL settings will be reset. Use the 'set modified' or 'modified' commands to see what will be reset.", "全部设置都将被重置。使用 'set modified' 或 'modified' 命令查看将被重置的项目。"),
        Map.entry("Specify a setting name instead of 'all' to only reset one setting", "指定设置名称而不是 'all' 可仅重置单项设置"),
        Map.entry("Click to set the setting back to this value", "点击将设置恢复为此值"),
        Map.entry("Warning: Chat commands will no longer work. If you want to revert this change, use prefix control (if enabled) or click the old value listed above.", "警告：聊天命令将不再可用。如需撤销，请使用带前缀命令（若已启用）或点击上方旧值。"),
        Map.entry("Warning: Prefixed commands will no longer work. If you want to revert this change, use chat control (if enabled) or click the old value listed above.", "警告：带前缀命令将不再可用。如需撤销，请使用聊天命令（若已启用）或点击上方旧值。"),
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
        Map.entry("Reset state but still flying to same goal", "已重置状态，仍将飞向同一目标"),
        Map.entry("Queued all loaded chunks for repacking", "已将所有已加载区块加入重新整理队列"),
        Map.entry("Only works in the nether", "仅可在下界使用"),
        Map.entry("Invalid action", "操作无效"),
        Map.entry("yes", "是"),
        Map.entry("Nether seed changed, recalculating path", "下界种子已更改，正在重新计算路径"),
        Map.entry("elytraPredictTerrain setting changed, recalculating path", "elytraPredictTerrain 设置已更改，正在重新计算路径"),
        Map.entry("Emergency landing - almost out of elytra durability or fireworks", "紧急降落——鞘翅耐久或烟花即将耗尽"),
        Map.entry("almost out of elytra durability or fireworks, but I'm going to continue since elytraAllowEmergencyLand is false", "鞘翅耐久或烟花即将耗尽，但 elytraAllowEmergencyLand 已关闭，将继续飞行"),
        Map.entry("Path complete, picking a nearby safe landing spot...", "航线完成，正在选择附近的安全降落点……"),
        Map.entry("Above the landing spot, landing...", "已到达降落点上方，正在降落……"),
        Map.entry("bad landing spot, trying again...", "降落点不安全，正在重试……"),
        Map.entry("Landed, but still moving, waiting for velocity to die down... ", "已着陆但仍在移动，正在等待速度降低……"),
        Map.entry("Done :)", "完成 :)"),
        Map.entry("Not taking off, because elytra durability or fireworks are so low that I would immediately emergency land anyway.", "未起飞：鞘翅耐久或烟花过低，起飞后会立即紧急降落。"),
        Map.entry("Failed to compute path to destination", "无法计算到目的地的航线"),
        Map.entry("Failed to recompute segment", "无法重新计算航线分段"),
        Map.entry("Failed to compute next segment", "无法计算下一航线分段"),
        Map.entry("no fireworks", "没有烟花"),
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
        template("All waypoints by tag (.+):", match -> "标签 " + match.group(1) + " 下的全部路径点："),
        template("Cleared (\\d+) waypoints, click to restore them", match -> "已清除 " + match.group(1) + " 个路径点，点击恢复"),
        template("Invalid tag, \"(.+)\"", match -> "无效标签：\"" + match.group(1) + "\""),
        template("Setting (.+) can only be used via the api\\.", match -> "设置 " + match.group(1) + " 只能通过 API 使用。"),
        template("Toggled setting (.+) to (.+)", match -> "已将设置 " + match.group(1) + " 切换为 " + match.group(2)),
        template("Successfully set (.+) to (.+)", match -> "已将 " + match.group(1) + " 设置为 " + match.group(2)),
        template("Successfully reset (.+) to (.+)", match -> "已将 " + match.group(1) + " 重置为 " + match.group(2)),
        template("All modified settings containing the string '(.+)':", match -> "名称包含 '" + match.group(1) + "' 的全部已修改设置："),
        template("All settings containing the string '(.+)':", match -> "名称包含 '" + match.group(1) + "' 的全部设置："),
        template("All modified settings:", match -> "全部已修改设置："),
        template("All settings:", match -> "全部设置："),
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
        template("Mining (.+)", match -> "正在挖掘 " + translateBlockName(match.group(1))),
        template("mine (.+)", match -> "挖掘 " + translateBlockName(match.group(1))),
        template("> mine (.+)", match -> "> 挖掘 " + translateBlockName(match.group(1))),
        template("#mine\\s+(.+)", match -> "#挖掘 " + translateBlockName(match.group(1))),
        template(".*BlockOptionalMeta\\{block=Block\\{([^,}]+).*", match -> "目标方块：" + translateBlockName(match.group(1))),
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
        template("Failed loading native library\\. Your CPU is (.+) and your operating system is (.+)\\. Supported architectures are 64 bit x86, and 64 bit ARM\\. Supported operating systems are Windows, Linux, and Mac", match -> "无法加载原生库。CPU 架构为 " + match.group(1) + "，操作系统为 " + match.group(2) + "。支持 64 位 x86、64 位 ARM，以及 Windows、Linux 和 Mac。"),
        template("unable to land at (.+)", match -> "无法在 " + match.group(1) + " 降落"),
        template("Starting to search for path from (.+) to (.+)", match -> "开始搜索从 " + match.group(1) + " 到 " + match.group(2) + " 的路径"),
        template("Finished finding a path from (.+) to (.+)\\. (.+) nodes considered", match -> "已找到从 " + match.group(1) + " 到 " + match.group(2) + " 的路径，共检查 " + match.group(3) + " 个节点"),
        template("Found path segment from (.+) towards (.+)\\. (.+) nodes considered", match -> "已找到从 " + match.group(1) + " 前往 " + match.group(2) + " 的路径分段，共检查 " + match.group(3) + " 个节点"),
        template("Pathing exception: (.+)", match -> "寻路异常：" + match.group(1)),
        template("Successfully loaded schematic for building", match -> "已成功加载待建造的原理图"),
        template("Origin: (.+)", match -> "原点：" + match.group(1)),
        template("Explore filter applied\\. Inverted: (.+)", match -> "探索过滤器已应用，反转：" + match.group(1)),
        template("Unable to find any path to (.+), blacklisting presumably unreachable closest instance\\.\\.\\.", match -> "无法找到前往 " + match.group(1) + " 的路径，正在将可能无法到达的最近目标加入黑名单……"),
        template("Unable to find any path to (.+), canceling mine", match -> "无法找到前往 " + match.group(1) + " 的路径，已取消挖掘"),
        template("No locations for (.+) known, cancelling", match -> "没有 " + match.group(1) + " 的已知位置，已取消"),
        template("Creating a tunnel (.+) block\\(s\\) high, (.+) block\\(s\\) wide, and (.+) block\\(s\\) deep", match -> "正在创建高 " + match.group(1) + "、宽 " + match.group(2) + "、深 " + match.group(3) + " 格的隧道")
    );

    private BaritoneChatTranslations() {}

    /**
     * 翻译方块名称（使用 Minecraft 内置翻译）
     * 例如：acacia_fence_gate -> 金合欢木栅栏门
     */
    public static String translateBlockId(String blockId) {
        if (blockId == null || blockId.isEmpty()) return blockId;
        
        try {
            // 遍历所有已注册的方块，查找匹配的方块
            for (Block block : BuiltInRegistries.BLOCK) {
                String registeredId = BuiltInRegistries.BLOCK.getKey(block).toString();
                String path = registeredId.contains(":") ? registeredId.split(":")[1] : registeredId;
                
                // 匹配方块ID（支持带命名空间和不带命名空间）
                if (path.equals(blockId) || registeredId.equals(blockId)) {
                    // 使用 Minecraft 内置翻译获取中文名
                    String translated = block.getName().getString();
                    // 如果翻译成功，返回翻译结果
                    if (translated != null && !translated.isEmpty() && !translated.equals(blockId)) {
                        return translated;
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // 解析失败，返回原始文本
        }
        
        return blockId;
    }

    private static String translateBlockName(String blockId) {
        return translateBlockId(blockId);
    }

    public static String translate(String text) {
        if (!YiyiaddonTranslator.enabled() || text == null || text.isEmpty()) return text;
        try {
            Matcher blockMatcher = Pattern.compile("BlockOptionalMeta(?:Lookup)?(?:\\{|\\[)block=Block\\{([^,}]+)(?:,properties=\\{\\})?(?:\\}|\\])").matcher(text);
            StringBuffer translatedBlocks = new StringBuffer();
            while (blockMatcher.find()) {
                blockMatcher.appendReplacement(translatedBlocks,
                    Matcher.quoteReplacement("目标方块：" + translateBlockName(blockMatcher.group(1))));
            }
            blockMatcher.appendTail(translatedBlocks);
            text = translatedBlocks.toString();
            text = text.replace(",properties={}", "");
            text = text.replace("BlockOptionalMetaLookup", "");
            text = text.replace("BlockOptionalMeta", "");
        } catch (RuntimeException ignored) {
            return text;
        }
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
        String fullText = component.getString();
        String translatedText = translate(fullText);
        if (!translatedText.equals(fullText)) {
            return Component.literal(translatedText).withStyle(component.getStyle());
        }
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
