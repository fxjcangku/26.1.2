package com.example.addon.translations;

import meteordevelopment.meteorclient.commands.Command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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

    /** 动态拼接消息的正则模板（HighwayBuilder、Notebot、ServerSpoof 等） */
    private static final List<Template> MESSAGE_TEMPLATES = List.of(
        template("Unable to perform restock for '(.+)'\\.", "无法为 '$1' 执行补货。"),
        template("Starting new restock task for (.+)", "开始为 $1 执行新的补货任务。"),
        template("Found less than the minimum amount of pickaxes required: (.+)", "镐的数量低于最低要求：$1。"),
        template("Loading song '(.+)' timed out\\.", "加载歌曲 '$1' 超时。"),
        template("Song '(.+)' has been loaded to the memory! Took (.+)ms", "歌曲 '$1' 已加载到内存！耗时 $2ms"),
        template("Loading song '(.+)' was cancelled\\.", "加载歌曲 '$1' 已取消。"),
        template("An error occurred while loading song '(.+)'\\. See the logs for more details", "加载歌曲 '$1' 时出错，详情见日志。"),
        template("Invalid resource pack URL: (.+)", "无效的资源包 URL：$1")
    );

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
            .replace("Set as Baritone goal", "设为 Baritone 目标")
            .replace("Added (highlight)%s (default)to friends.", "已将 (highlight)%s (default)添加为好友。")
            .replace("Already friends with that player.", "该玩家已是你的好友。")
            .replace("Not friends with that player.", "该玩家不是你的好友。")
            .replace("Removed (highlight)%s (default)from friends.", "已将 (highlight)%s (default)移出好友列表。")
            .replace("Failed to remove that friend.", "移除好友失败。")
            .replace("--- Friends ((highlight)%s(default)) ---", "--- 好友 ((highlight)%s(default)) ---")
            .replace("Couldn't find a Fake Player with that name.", "找不到该名称的假人。")
            .replace("Removed Fake Player %s.", "已移除假人 %s。")
            .replace("--- Fake Players ((highlight)%s(default)) ---", "--- 假人 ((highlight)%s(default)) ---")
            .replace("--- Bound Modules ((highlight)%d(default)) ---", "--- 已绑定模块 ((highlight)%d(default)) ---")
            .replace("--- Commands ((highlight)%d(default)) ---", "--- 命令 ((highlight)%d(default)) ---")
            .replace("--- Modules ((highlight)%d(default)) ---", "--- 模块 ((highlight)%d(default)) ---")
            .replace("No active keypress handlers.", "没有正在运行的按键处理器。")
            .replace("Cleared all keypress handlers.", "已清除所有按键处理器。")
            .replace("Active keypress handlers: ", "正在运行的按键处理器：")
            .replace("(highlight)%d(default) - (highlight)%s %d(default) ticks left out of (highlight)%d(default).", "(highlight)%d(default) - (highlight)%s 剩余 %d tick，共 (highlight)%d(default) 个。")
            .replace("Index out of range.", "索引超出范围。")
            .replace("Removed keypress handler.", "已移除按键处理器。")
            .replace("You need to hold a (highlight)buried treasure map(default)!", "你需要手持 (highlight)藏宝图(default)！")
            .replace("Couldn't locate the map icons!", "无法定位地图图标！")
            .replace("Couldn't locate the buried treasure!", "无法定位埋藏的宝藏！")
            .replace("You need to hold a (highlight)woodland explorer map(default)!", "你需要手持 (highlight)林地探险家地图(default)！")
            .replace("Couldn't locate the mansion!", "无法定位林地府邸！")
            .replace("Couldn't locate the monument!", "无法定位海底神殿！")
            .replace("No monument found. Try using an (highlight)ocean explorer map(default) for more success.", "未找到海底神殿。可尝试使用 (highlight)海洋探险家地图(default) 提高成功率。")
            .replace("Locating this structure without an (highlight)ocean explorer map(default) requires Baritone.", "不使用 (highlight)海洋探险家地图(default) 定位此结构需要 Baritone。")
            .replace("Please throw the first Eye of Ender", "请扔出第一颗末影之眼")
            .replace("No stronghold found nearby. You can use (highlight)Ender Eyes(default) for more success.", "附近未找到要塞。使用 (highlight)末影之眼(default) 可提高成功率。")
            .replace("No Eyes of Ender found in hotbar.", "快捷栏中没有末影之眼。")
            .replace("You need to be in the nether to locate a nether fortress.", "你需要身处下界才能定位下界要塞。")
            .replace("Locating this structure requires Baritone.", "定位此结构需要 Baritone。")
            .replace("No nether fortress found.", "未找到下界要塞。")
            .replace("You need to be in the end to locate an end city.", "你需要身处末地才能定位末地城。")
            .replace("No end city found.", "未找到末地城。")
            .replace("You need to hold a (highlight)lodestone(default) compass!", "你需要手持 (highlight)磁石(default)指南针！")
            .replace("Couldn't get the components data. Are you holding a (highlight)lodestone(default) compass?", "无法获取组件数据。你手持的是 (highlight)磁石(default)指南针吗？")
            .replace("Couldn't get the lodestone's target!", "无法获取磁石的目标位置！")
            .replace("Locate canceled", "定位已取消")
            .replace("Only %d block(s) found. This search might be a false positive.", "仅找到 %d 个方块，本次搜索可能是误判。")
            .replace("%s Eye of Ender's trajectory saved.", "第 %s 颗末影之眼的轨迹已保存。")
            .replace("Please throw the second Eye Of Ender from a different location.", "请从另一个位置扔出第二颗末影之眼。")
            .replace("Missing position data", "缺少位置数据")
            .replace("Unable to calculate intersection.", "无法计算交汇点。")
            .replace("Recording started", "开始录制")
            .replace("Song saved.", "歌曲已保存。")
            .replace("Couldn't create the file.", "无法创建文件。")
            .replace("Error while bruteforcing a note level! Sound: ", "暴力尝试音符等级时出错！声音：")
            .replace("Can't find the instrument from sound! Sound: ", "无法从声音识别乐器！声音：")
            .replace("There was an error fetching that users name history.", "获取该玩家曾用名时出错。")
            .replace("The waypoint (highlight)'%s'(default) has been deleted.", "路径点 (highlight)'%s'(default) 已删除。")
            .replace("Created waypoint with name: (highlight)%s(default)", "已创建路径点：(highlight)%s(default)")
            .replace("Set mainhand stack count to %s.", "已将主手物品数量设置为 %s。")
            .replace("Creative mode only.", "仅限创造模式。")
            .replace("You must hold an item in your main hand.", "你需要主手持有物品。")
            .replace("Reloading systems, this may take a while.", "正在重载系统，可能需要一些时间。")
            .replace("No macros are currently scheduled.", "当前没有排期的宏。")
            .replace("Cleared all scheduled macros.", "已清除所有排期宏。")
            .replace("This macro is not currently scheduled.", "该宏当前未排期。")
            .replace("Cleared scheduled macro.", "已清除排期宏。")
            .replace("Loaded profile (highlight)%s(default).", "已加载配置档 (highlight)%s(default)。")
            .replace("Saved profile (highlight)%s(default).", "已保存配置档 (highlight)%s(default)。")
            .replace("Deleted profile (highlight)%s(default).", "已删除配置档 (highlight)%s(default)。")
            .replace("Reset all settings.", "已重置所有设置。")
            .replace("Reset all module settings", "已重置所有模块设置")
            .replace("Reset all GUI settings.", "已重置所有界面设置。")
            .replace("Reset bind.", "已重置按键绑定。")
            .replace("Reset all binds.", "已重置所有按键绑定。")
            .replace("Reset all elements.", "已重置所有 HUD 元素。")
            .replace("Current TPS: %s%.2f(default).", "当前 TPS：%s%.2f(default)。")
            .replace("Singleplayer", "单人游戏")
            .replace("Version: %s", "版本：%s")
            .replace("Couldn't obtain any server information.", "无法获取任何服务器信息。")
            .replace("Port: %d", "端口：%d")
            .replace("Type: %s", "类型：%s")
            .replace("Motd: %s", "描述：%s")
            .replace("unknown", "未知")
            .replace("Protocol version: %d", "协议版本：%d")
            .replace("Difficulty: %s (Local: %.2f)", "难度：%s（本地：%.2f）")
            .replace("Day: %d", "天数：%d")
            .replace("Permission level: %s", "权限等级：%s")
            .replace("Plugins (%d): %s ", "插件（%d）：%s ")
            .replace("No plugins found.", "未找到插件。")
            .replace("An error occurred while trying to find plugins.", "查找插件时出错。")
            .replace("Error writing map texture", "写入地图纹理时出错")
            .replace("Setting (highlight)%s(default) is (highlight)%s(default).", "设置 (highlight)%s(default) 的当前值为 (highlight)%s(default)。")
            .replace("Setting (highlight)%s(default) changed to (highlight)%s(default).", "设置 (highlight)%s(default) 已更改为 (highlight)%s(default)。")
            .replace("Are you sure you want to connect to '%s:%s'?", "确定要连接到 '%s:%s' 吗？")
            .replace("No pending swarm connections.", "没有待确认的蜂群连接。")
            .replace("Connected to (highlight)%s.", "已连接到 (highlight)%s。")
            .replace("Connected to (highlight)%s", "已连接到 (highlight)%s")
            .replace("Error connecting to swarm host.", "连接蜂群主机时出错。")
            .replace("--- Swarm Connections (highlight)(%s/%s)(default) ---", "--- 蜂群连接 (highlight)(%s/%s)(default) ---")
            .replace("(highlight)Worker %s(default): %s.", "(highlight)工作端 %s(default)：%s。")
            .replace("No active connections", "没有活动连接")
            .replace("The follow host command must be used by the host.", "follow 命令必须由蜂群主机执行。")
            .replace("Visible: §aTrue", "可见：是")
            .replace("Visible: §cFalse", "可见：否")
            // 蜂群（Swarm）
            .replace("Server not found at %s on port %s.", "在 %s 端口 %s 上未找到服务器。")
            .replace("Connected to Swarm host on at %s on port %s.", "已连接到 %s 端口 %s 的蜂群主机。")
            .replace("Received command: (highlight)%s", "收到命令：(highlight)%s")
            .replace("Error fetching command.", "获取命令时出错。")
            .replace("Error in connection to host.", "与主机连接出错。")
            .replace("Disconnected from host.", "已与主机断开连接。")
            .replace("Couldn't start a server on port %s.", "无法在端口 %s 启动服务器。")
            .replace("Listening for incoming connections on port %s.", "正在端口 %s 监听传入连接。")
            .replace("Error making a connection to worker.", "连接工作端时出错。")
            .replace("Server closed on port %s.", "端口 %s 的服务器已关闭。")
            .replace("New worker connected on %s.", "新工作端已连接：%s。")
            .replace("Encountered error when sending command.", "发送命令时遇到错误。")
            .replace("Error creating a connection with %s on port %s.", "与 %s 端口 %s 建立连接时出错。")
            .replace("Worker disconnected on ip: %s.", "工作端已断开，IP：%s。")
            // 提醒（Notifier）
            .replace("(highlight)%s(default) has entered your visual range!", "(highlight)%s(default) 进入了你的可视范围！")
            .replace("(highlight)%s(default) has left your visual range!", "(highlight)%s(default) 离开了你的可视范围！")
            .replace("(highlight)%s (default)popped (highlight)%d (default)%s.", "(highlight)%s (default)已触发 (highlight)%d (default)次不死图腾。")
            .replace("(highlight)%s (default)died after popping (highlight)%d (default)%s.", "(highlight)%s (default)在触发 (highlight)%d (default)次不死图腾后死亡。")
            // 高速建造（HighwayBuilder）
            .replace("No empty slots.", "没有空槽位。")
            .replace("Cannot find pickaxe without silk touch to mine ender chests.", "找不到没有精准采集的镐来挖掘末影箱。")
            .replace("No empty slots for restocking items.", "没有空槽位用于补充物品。")
            .replace("Invalid restocking action.", "无效的补货操作。")
            .replace("Invalid block at container restocking position?", "容器补货位置的方块无效？")
            .replace("No bow found to destroy crystal traps with. Toggling the setting off.", "未找到用于摧毁水晶陷阱的弓，正在关闭该设置。")
            .replace("Detected potential hangup on a crystal. Adding it to ignore list and continuing forward.", "检测到水晶可能导致卡顿，已将其加入忽略列表并继续前进。")
            .replace("No empty space in hotbar.", "快捷栏没有空位。")
            .replace("Out of blocks to place.", "没有可放置的方块了。")
            // 音符盒（Notebot）
            .replace("Malformed line %d", "第 %d 行格式错误")
            .replace("Invalid character at line %d", "第 %d 行存在无效字符")
            .replace("Note at tick %d out of range.", "第 %d tick 的音符超出范围。")
            // 其他
            .replace("Caught exception: %s", "捕获到异常：%s")
            .replace("Could not copy to clipboard: Out of memory.", "无法复制到剪贴板：内存不足。")
            // 路径点（WaypointCommand）
            .replace("No created waypoints.", "尚未创建任何路径点。")
            .replace("Name: (highlight)'%s'(default), Dimension: (highlight)%s(default), Pos: (highlight)%s(default)", "名称：(highlight)'%s'(default)，维度：(highlight)%s(default)，位置：(highlight)%s(default)")
            .replace("Name: ", "名称：")
            .replace("Actual Dimension: ", "实际维度：")
            .replace("Position: ", "位置：")
            // 音符盒（Notebot）
            .replace("File not found", "找不到文件")
            .replace("File is in wrong format. Decoder not found.", "文件格式错误，未找到解码器。")
            .replace("Loading song \"%s\".", "正在加载歌曲 \"%s\"。")
            .replace("Delaying check for noteblocks", "延迟检查音符盒")
            .replace("Loading done.", "加载完成。")
            // 自动熔炉（AutoSmelter）
            .replace("You do not have any items in your inventory that can be smelted. Disabling.", "背包中没有可烧炼的物品，正在关闭。")
            .replace("You do not have any fuel in your inventory. Disabling.", "背包中没有燃料，正在关闭。")
            .replace("Your inventory is full. Disabling.", "背包已满，正在关闭。")
            // 自动命名牌（AutoNametag）
            .replace("No Nametag in Hotbar", "快捷栏中没有命名牌")
            // 数据包记录（PacketLogger）
            .replace("Failed to initialize packet logging: %s", "初始化数据包记录失败：%s")
            .replace("Failed to write to packet log file: %s. File logging disabled.", "写入数据包日志文件失败：%s，已禁用文件记录。")
            // 钻地（Burrow）
            .replace("Already burrowed, disabling.", "已处于钻地状态，正在关闭。")
            .replace("Not in a hole, disabling.", "不在坑中，正在关闭。")
            .replace("Not enough headroom to burrow, disabling.", "上方空间不足，无法钻地，正在关闭。")
            .replace("No burrow block found, disabling.", "未找到钻地方块，正在关闭。")
            .replace("Waiting for manual jump.", "等待手动跳跃。")
            // 防挂机（AntiAFK）
            .replace("Message list is empty, disabling messages...", "消息列表为空，正在禁用消息……")
            // Component 级输出（经 sendMsg(Component) 路径）
            .replace("Found stash at ", "发现储藏点于 ")
            .replace("It looks like there are coordinates in your message! ", "你的消息中似乎包含坐标！")
            .replace(" joined.", " 加入了游戏。")
            .replace(" left.", " 离开了游戏。");
    }

    /**
     * 翻译 Component 级聊天消息（Meteor 通过 sendMsg(Component) 输出的消息）。
     *
     * 逐叶子节点翻译文本，保留每个节点的样式与 hover/click 交互事件，
     * 避免整体替换导致按钮、寻路点击等事件丢失。
     */
    public static Component translateComponent(Component component) {
        if (!YiyiaddonTranslator.enabled() || component == null) return component;
        MutableComponent translated = Component.empty();
        component.visit((style, text) -> {
            translated.append(Component.literal(translateChatMessage(text)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return translated;
    }

    private static String translateDynamicErrors(String message) {
        Matcher matcher = MODULE_NOT_FOUND.matcher(message);
        if (matcher.find()) {
            return matcher.replaceFirst(Matcher.quoteReplacement("名为 " + matcher.group(1) + " 的模块不存在。"));
        }
        for (Template template : MESSAGE_TEMPLATES) {
            Matcher templateMatcher = template.pattern().matcher(message);
            if (templateMatcher.find()) {
                return templateMatcher.replaceAll(template.replacement());
            }
        }
        return message;
    }

    private static Template template(String regex, String replacement) {
        return new Template(Pattern.compile(regex), replacement);
    }

    private record Template(Pattern pattern, String replacement) {}

    public static String translate(String name, String fallback) {
        if (!YiyiaddonTranslator.enabled()) return fallback;
        return DESCRIPTIONS.getOrDefault(name.toLowerCase(Locale.ROOT), fallback);
    }
}
