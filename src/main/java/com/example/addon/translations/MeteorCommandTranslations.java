package com.example.addon.translations;

import meteordevelopment.meteorclient.commands.Command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MeteorCommandTranslations {
    private static final Map<String, String> COMMAND_NAMES = Map.ofEntries(
        Map.entry("help", "帮助"),
        Map.entry("bind", "绑定"),
        Map.entry("binds", "绑定列表"),
        Map.entry("commands", "命令列表"),
        Map.entry("damage", "伤害"),
        Map.entry("dc", "断连"),
        Map.entry("disconnect", "断开连接"),
        Map.entry("dismount", "下马"),
        Map.entry("drop", "丢弃"),
        Map.entry("dmg", "自伤"),
        Map.entry("ec", "末箱"),
        Map.entry("echest", "末影箱预览"),
        Map.entry("enchant", "附魔"),
        Map.entry("ender-chest", "末影箱"),
        Map.entry("fake-player", "假人"),
        Map.entry("fov", "视野"),
        Map.entry("friends", "好友"),
        Map.entry("gamemode", "游戏模式"),
        Map.entry("give", "给予"),
        Map.entry("gm", "模式"),
        Map.entry("hclip", "水平穿墙"),
        Map.entry("history", "曾用名"),
        Map.entry("input", "输入"),
        Map.entry("inv", "背包预览"),
        Map.entry("inventory", "背包"),
        Map.entry("invsee", "查看背包"),
        Map.entry("loc", "查结构"),
        Map.entry("locate", "定位"),
        Map.entry("macro", "宏"),
        Map.entry("modules", "模块列表"),
        Map.entry("features", "功能列表"),
        Map.entry("name-history", "名称历史"),
        Map.entry("names", "历史名称"),
        Map.entry("nbt", "NBT"),
        Map.entry("notebot", "音符机器人"),
        Map.entry("peek", "窥视"),
        Map.entry("profiles", "配置档"),
        Map.entry("reload", "重载"),
        Map.entry("reset", "重置"),
        Map.entry("rotation", "视角"),
        Map.entry("s", "模块设置"),
        Map.entry("save-map", "保存地图"),
        Map.entry("say", "说"),
        Map.entry("server", "服务器"),
        Map.entry("settings", "设置"),
        Map.entry("sm", "存地图"),
        Map.entry("spectate", "旁观"),
        Map.entry("swarm", "蜂群"),
        Map.entry("t", "开关"),
        Map.entry("toggle", "切换"),
        Map.entry("vclip", "垂直穿墙"),
        Map.entry("wasp", "黄蜂"),
        Map.entry("waypoint", "路径点"),
        Map.entry("wp", "路点"),
        Map.entry("farm", "农场"),
        Map.entry("nc", "农场绑定"),
        Map.entry("nongchang", "农场管理"),
        Map.entry("yiyiaddon", "依依插件"),
        Map.entry("example", "示例")
    );

    private static final Map<String, String> SUBCOMMAND_NAMES = Map.ofEntries(
        Map.entry("add", "添加"), Map.entry("all", "全部"), Map.entry("all_possible", "全部可用"),
        Map.entry("armor", "盔甲"), Map.entry("buried_treasure", "埋藏的宝藏"), Map.entry("cancel", "取消"),
        Map.entry("clear", "清除"), Map.entry("config", "配置"), Map.entry("connections", "连接"),
        Map.entry("confirm", "确认"), Map.entry("copy", "复制"), Map.entry("count", "数量"),
        Map.entry("delete", "删除"), Map.entry("disconnect", "断开"), Map.entry("end_city", "末地城"),
        Map.entry("exec", "执行"), Map.entry("follow", "跟随"), Map.entry("get", "查看"),
        Map.entry("goto", "前往"), Map.entry("hand", "主手"), Map.entry("hotbar", "快捷栏"),
        Map.entry("hud", "界面"), Map.entry("infinity-miner", "无限挖掘"), Map.entry("info", "信息"),
        Map.entry("inventory", "背包"), Map.entry("join", "加入"), Map.entry("level", "等级"),
        Map.entry("list", "列表"), Map.entry("load", "加载"), Map.entry("lodestone", "磁石"),
        Map.entry("logout", "登出"), Map.entry("mansion", "林地府邸"), Map.entry("max", "最大"),
        Map.entry("mine", "挖掘"), Map.entry("monument", "海底神殿"), Map.entry("nether_fortress", "下界要塞"),
        Map.entry("off", "关闭"), Map.entry("offhand", "副手"), Map.entry("on", "开启"),
        Map.entry("one", "单个"), Map.entry("pause", "暂停"), Map.entry("play", "播放"),
        Map.entry("plugins", "插件"), Map.entry("preview", "预览"), Map.entry("randomsong", "随机歌曲"),
        Map.entry("record", "录制"), Map.entry("remove", "移除"), Map.entry("reset", "重置"),
        Map.entry("resume", "恢复"), Map.entry("save", "保存"), Map.entry("scatter", "分散"),
        Map.entry("set", "设置"), Map.entry("settings", "设置"), Map.entry("start", "开始"),
        Map.entry("status", "状态"), Map.entry("stop", "停止"), Map.entry("stronghold", "要塞"),
        Map.entry("toggle", "切换"), Map.entry("tps", "TPS"), Map.entry("walkhome", "回家"),
        Map.entry("help", "帮助")
    );

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("help", "显示命令的帮助信息。"),
        Map.entry("bind", "将模块绑定到按键。"),
        Map.entry("binds", "列出所有按键绑定。"),
        Map.entry("commands", "列出所有命令。"),
        Map.entry("damage", "对自己造成伤害。"),
        Map.entry("disconnect", "断开与服务器的连接。"),
        Map.entry("dismount", "下坐骑。"),
        Map.entry("drop", "丢弃物品。"),
        Map.entry("enchant", "附魔物品。"),
        Map.entry("ender-chest", "打开末影箱。"),
        Map.entry("fake-player", "管理假人。"),
        Map.entry("fov", "更改视野。"),
        Map.entry("friends", "管理好友。"),
        Map.entry("gamemode", "更改游戏模式。"),
        Map.entry("give", "给予物品。"),
        Map.entry("hclip", "水平穿过方块。"),
        Map.entry("input", "模拟按键输入。"),
        Map.entry("inventory", "打开背包。"),
        Map.entry("locate", "查找结构。"),
        Map.entry("macro", "管理宏。"),
        Map.entry("modules", "列出所有模块。"),
        Map.entry("name-history", "查看玩家名称历史。"),
        Map.entry("nbt", "查看手持物品的 NBT。"),
        Map.entry("notebot", "管理音符盒歌曲。"),
        Map.entry("peek", "查看容器内容。"),
        Map.entry("profiles", "管理配置档。"),
        Map.entry("reload", "重新加载配置。"),
        Map.entry("reset", "重置模块设置。"),
        Map.entry("rotation", "控制玩家视角。"),
        Map.entry("save-map", "将地图保存为图片。"),
        Map.entry("say", "在聊天栏发送消息。"),
        Map.entry("server", "显示服务器信息。"),
        Map.entry("settings", "查看和修改模块设置。"),
        Map.entry("spectate", "旁观附近的玩家。"),
        Map.entry("swarm", "向已连接的蜂群工作端发送命令。"),
        Map.entry("toggle", "切换模块状态。"),
        Map.entry("vclip", "垂直穿过方块。"),
        Map.entry("wasp", "设置自动 Wasp 目标。"),
        Map.entry("waypoint", "管理路径点。"),
        Map.entry("farm", "绑定自动农场的农田范围与物流箱子。"),
        Map.entry("yiyiaddon", "管理依依插件更新。"),
        Map.entry("example", "发送一条示例消息。")
    );

    private static final Pattern MODULE_NOT_FOUND = Pattern.compile("Module with name (?:['\"])?(.+?)(?:['\"])? doesn't exist\\.");

    private MeteorCommandTranslations() {}

    public static String translateCommandName(String commandName) {
        if (!YiyiaddonTranslator.enabled() || commandName == null) return commandName;
        return COMMAND_NAMES.getOrDefault(commandName.toLowerCase(Locale.ROOT), commandName);
    }

    public static Set<String> getChineseNames(Command command) {
        Set<String> names = new LinkedHashSet<>();
        addChineseName(names, command.getName());
        for (String alias : command.getAliases()) addChineseName(names, alias);
        return names;
    }

    private static void addChineseName(Set<String> names, String englishName) {
        String chineseName = COMMAND_NAMES.get(englishName.toLowerCase(Locale.ROOT));
        if (chineseName != null) names.add(chineseName);
    }

    public static String reverseTranslate(String chineseName) {
        if (!YiyiaddonTranslator.enabled() || chineseName == null) return chineseName;
        String lower = chineseName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : COMMAND_NAMES.entrySet()) {
            if (entry.getValue().equals(lower)) return entry.getKey();
        }
        return chineseName;
    }

    public static List<String> translateAliases(Command command) {
        if (!YiyiaddonTranslator.enabled() || command == null) return command == null ? List.of() : command.getAliases();
        return command.getAliases().stream()
            .map(MeteorCommandTranslations::translateCommandName)
            .collect(Collectors.toList());
    }

    public static String translateHelpLabel(String label) {
        if (!YiyiaddonTranslator.enabled()) return label;
        return switch (label) {
            case "Help for " -> "命令帮助：";
            case "Description: " -> "说明：";
            case "Aliases: " -> "别名：";
            case "\n Usage:" -> "\n用法：";
            default -> label;
        };
    }

    public static String translateSubcommandName(String name) {
        if (!YiyiaddonTranslator.enabled() || name == null) return name;
        return SUBCOMMAND_NAMES.getOrDefault(name.toLowerCase(Locale.ROOT), name);
    }

    public static String translateChatMessage(String message) {
        if (!YiyiaddonTranslator.enabled() || message == null) return message;
        String translated = YiyiaddonTranslator.translateVisible(message);
        if (!translated.equals(message)) return translated;
        String dynamic = translateDynamicErrors(message);
        return dynamic
            .replace("Press a key to bind the module to.", "请按一个按键绑定该模块。")
            .replace("Recording cancelled", "录制已取消")
            .replace("Bound to %s.", "已绑定到 %s。")
            .replace("Toggled %s on.", "已开启 %s。")
            .replace("Toggled %s off.", "已关闭 %s。")
            .replace("Unknown or incomplete command, see below for error", "未知或不完整的命令，错误详情见下方")
            .replace("Incorrect argument for command", "命令参数错误")
            .replace("at position ", "错误位置 ")
            .replace("<--[HERE]", "<--[此处]")
            .replace("anchor", "锚点")
            .replace("Module not found.", "未找到模块。")
            .replace("Invalid module.", "无效模块。")
            .replace("Player not found.", "未找到玩家。")
            .replace("No permission.", "没有权限。")
            .replace("You are invulnerable.", "你当前处于无敌状态。")
            .replace("No space in hotbar.", "快捷栏没有空位。")
            .replace("You must be in creative mode to use this.", "你必须处于创造模式才能使用此命令。")
            .replace("You need to hold some item to enchant.", "你需要手持物品才能附魔。")
            .replace("Can't drop items while in spectator.", "旁观模式下无法丢弃物品。")
            .replace("Could not find an item with that name!", "找不到该名称的物品！")
            .replace("Sneak to un-spectate.", "按下潜行键退出旁观。")
            .replace("The swarm module must be active to use this command.", "必须启用蜂群模块才能使用此命令。")
            .replace("Set as Baritone goal", "设为 Baritone 目标");
    }

    private static String translateDynamicErrors(String message) {
        Matcher matcher = MODULE_NOT_FOUND.matcher(message);
        if (matcher.find()) {
            return matcher.replaceFirst(Matcher.quoteReplacement("名为 " + matcher.group(1) + " 的模块不存在。"));
        }
        return message;
    }

    public static String translate(String name, String fallback) {
        if (!YiyiaddonTranslator.enabled()) return fallback;
        return DESCRIPTIONS.getOrDefault(name.toLowerCase(Locale.ROOT), fallback);
    }
}
