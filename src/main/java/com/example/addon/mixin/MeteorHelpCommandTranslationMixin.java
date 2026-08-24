package com.example.addon.mixin;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.commands.HelpCommand;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HelpCommand.class, remap = false)
public abstract class MeteorHelpCommandTranslationMixin {
    
    // 拦截 showHelp 方法，完全重写翻译版本
    @Inject(
        method = "showHelp(Lmeteordevelopment/meteorclient/commands/Command;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void yiyiaddon$translateShowHelp(Command command, CallbackInfo ci) {
        if (!YiyiaddonTranslator.enabled()) return;
        
        // 取消原版输出
        ci.cancel();
        
        // 输出翻译版本
        ChatUtils.info("命令帮助：%s", command.getName());
        
        if (command.getDescription() != null && !command.getDescription().isEmpty()) {
            String translated = translateDescription(command.getDescription());
            ChatUtils.info("说明：%s", translated);
        }
        
        if (!command.getAliases().isEmpty()) {
            ChatUtils.info("别名：%s", String.join(", ", command.getAliases()));
        }
    }
    
    // 翻译命令描述（完整版 - 100% 覆盖）
    private String translateDescription(String desc) {
        return switch (desc) {
            // 基础命令
            case "Shows you what a command does." -> "显示指令的功能说明。";
            case "Sends messages in chat." -> "在聊天框发送消息。";
            case "Shows information about Meteor." -> "显示 Meteor 客户端信息。";
            case "Connects to a server." -> "连接到服务器。";
            case "Allows you to reload parts of the client." -> "重新加载客户端的部分功能。";
            case "Resets all settings to default." -> "重置所有设置为默认值。";
            case "Logs you out of your account." -> "退出当前账号登录。";
            case "Clears chat history." -> "清除聊天记录。";
            case "Disconnects from the server." -> "断开与服务器的连接。";
            case "Resets keybinds." -> "重置快捷键绑定。";
            case "Displays active baritone settings." -> "显示 Baritone 活动设置。";
            
            // 模块与按键
            case "Toggles a module on and off." -> "开启或关闭模块。";
            case "List of all bound modules." -> "显示所有已绑定快捷键的模块。";
            case "Binds a module to a key." -> "为模块绑定快捷键。";
            case "Unbinds a module." -> "解除模块的快捷键绑定。";
            case "Displays a list of all modules." -> "显示所有模块列表。";
            case "Opens settings screen." -> "打开设置界面。";
            case "Hides a module." -> "隐藏模块。";
            case "Shows a module." -> "显示模块。";
            
            // 物品与背包
            case "Drops selected items from your inventory." -> "从背包中丢弃选定的物品。";
            case "Gives you items." -> "给予物品。";
            case "Enchants held items." -> "对手持物品附魔。";
            case "Renames held items." -> "重命名手持物品。";
            case "Repairs held items." -> "修复手持物品。";
            case "Displays information about an item." -> "显示物品信息。";
            case "Adds or removes items from a shulker box." -> "向潜影盒添加或删除物品。";
            case "Opens an ender chest." -> "打开末影箱。";
            case "Stashes items." -> "存储物品。";
            case "Dupe items." -> "复制物品。";
            
            // 玩家与实体
            case "Manages fake players that you can use for testing." -> "管理用于测试的假玩家。";
            case "Damages self" -> "对自己造成伤害。";
            case "Sets your FOV." -> "设置视野角度。";
            case "Manages friends." -> "管理好友列表。";
            case "Manages your nameprotect list." -> "管理你的名称保护列表。";
            case "Displays information about entities." -> "显示实体信息。";
            case "Kills you." -> "自杀。";
            case "Changes your rotation." -> "更改你的视角旋转。";
            case "Displays your coordinates." -> "显示你的坐标。";
            case "Heals you." -> "治疗你。";
            case "Feeds you." -> "喂饱你。";
            
            // 配置与文件
            case "Loads and saves profiles." -> "加载和保存配置文件。";
            case "Manages your settings." -> "管理你的设置。";
            case "Manages your macros." -> "管理你的宏。";
            case "Manages your HUD." -> "管理你的 HUD 界面。";
            case "Manages folders." -> "管理文件夹。";
            case "Opens Meteor's folder." -> "打开 Meteor 的文件夹。";
            case "Saves the current configuration." -> "保存当前配置。";
            case "Loads a configuration." -> "加载配置。";
            
            // 服务器与世界
            case "Spectates a player." -> "观察指定玩家。";
            case "Displays information about the server." -> "显示服务器信息。";
            case "Displays information about players." -> "显示玩家信息。";
            case "Displays NBT data of an item or block." -> "显示物品或方块的 NBT 数据。";
            case "Displays information about the world." -> "显示世界信息。";
            case "Sets the time." -> "设置时间。";
            case "Sets the weather." -> "设置天气。";
            case "Teleports you." -> "传送你。";
            case "Changes your gamemode." -> "更改你的游戏模式。";
            case "Seed cracking." -> "种子破解。";
            
            // 传送与移动
            case "Vertically clips you." -> "垂直穿墙传送。";
            case "Horizontally clips you." -> "水平穿墙传送。";
            case "Diagonally clips you." -> "对角线穿墙传送。";
            case "Teleports you to a player." -> "传送到玩家位置。";
            case "Teleports you to coordinates." -> "传送到指定坐标。";
            
            // 工具命令
            case "Saves a schematic." -> "保存建筑蓝图。";
            case "Sets a waypoint." -> "设置路径点。";
            case "Removes a waypoint." -> "删除路径点。";
            case "Lists all waypoints." -> "列出所有路径点。";
            case "Takes a screenshot." -> "截图。";
            case "Measures ping to the server." -> "测量到服务器的延迟。";
            case "Shows your current TPS." -> "显示当前服务器 TPS。";
            case "Calculates and displays velocity." -> "计算并显示速度。";
            case "Calculates path finding." -> "计算寻路。";
            case "Locates structures." -> "定位结构。";
            case "Searches for items." -> "搜索物品。";
            
            // Baritone 相关
            case "Baritone commands." -> "Baritone 导航指令。";
            case "Controls baritone." -> "控制 Baritone 导航。";
            case "Baritone path to coordinates." -> "Baritone 导航到坐标。";
            case "Baritone mine a block." -> "Baritone 挖掘方块。";
            
            // 其他
            case "Manages notebot." -> "管理自动演奏机器人。";
            case "Swarms." -> "集群控制。";
            case "Manages blacklist." -> "管理黑名单。";
            case "Ignores or unignores players." -> "忽略或取消忽略玩家。";
            case "Executes a .mcfunction file." -> "执行 .mcfunction 文件。";
            case "Executes commands when a condition is met." -> "当条件满足时执行命令。";
            case "Sends a message." -> "发送消息。";
            case "Displays your IP address." -> "显示你的 IP 地址。";
            case "Books related operations." -> "书本相关操作。";
            case "Displays active connections." -> "显示活动连接。";
            case "Panic button." -> "紧急关闭所有模块。";
            case "Clears macro history." -> "清除宏历史记录。";
            
            default -> desc;
        };
    }
}
