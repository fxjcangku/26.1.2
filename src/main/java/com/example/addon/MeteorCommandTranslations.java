package com.example.addon;

import java.util.Map;

public final class MeteorCommandTranslations {
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
        Map.entry("enderchest", "打开末影箱。"),
        Map.entry("fakeplayer", "管理假人。"),
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
        Map.entry("namehistory", "查看玩家名称历史。"),
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
        Map.entry("waypoint", "管理路径点。")
    );

    private MeteorCommandTranslations() {}

    public static String translate(String name, String fallback) {
        if (!YiyiaddonTranslator.enabled()) return fallback;
        return DESCRIPTIONS.getOrDefault(name, fallback);
    }
}
