package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

public final class BaritoneCommandGuideModule extends YiyiaddonModule {
    public BaritoneCommandGuideModule() {
        super(AddonTemplate.CATEGORY, "Baritone指令说明", "打开后查看 Baritone 常用指令、参数格式和使用示例。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§lBaritone指令说明 · 使用说明" },
            new String[]{
                "§e§l▌ 基础规则",
                "§f  · Baritone 指令默认使用前缀 §e#§f，例如 §e#goto 100 64 200§f。",
                "§f  · 如果客户端修改了 Baritone 前缀，请以当前设置为准。",
                "§f  · 坐标支持相对坐标 §e~§f，例如 §e#goto ~10 ~ ~-5§f。",
                "§f  · 输入 §e#help§f 查看命令列表，输入 §e#help <命令>§f 查看详细帮助。",
                "§f  · 输入 §e#cancel§f 可以停止当前寻路、挖矿、建造或跟随任务。"
            },
            new String[]{
                "§a§l▌ 移动与目标",
                "§f  · §e#goto <方块>§f：前往附近或缓存中指定类型的方块。",
                "§f  · §e#goto <x> <y> <z>§f：前往指定三维坐标。",
                "§f  · §e#goto <x> <z>§f：前往指定 X、Z 坐标，Y 高度自动处理。",
                "§f  · §e#goal <x> <y> <z>§f：设置目标但不立即开始寻路。",
                "§f  · §e#goal clear§f：清除当前目标。§e#path§f：开始前往当前目标。",
                "§f  · §e#thisway <距离>§f：在视线方向指定距离处设置目标。",
                "§f  · §e#come§f：前往摄像机所在位置；§e#axis§f：前往最近的 X=0 或 Z=0 轴。",
                "§f  · §e#surface§f / §e#top§f：从洞穴、矿井等地下区域前往地表。"
            },
            new String[]{
                "§b§l▌ 资源采集",
                "§f  · §e#mine <方块>§f：搜索并挖掘指定方块，例如 §e#mine diamond_ore§f。",
                "§f  · §e#find <方块>§f：仅搜索 Baritone 已缓存区域中的方块位置。",
                "§f  · §e#explore§f：从当前位置随机探索未缓存区域。",
                "§f  · §e#explore <x> <z>§f：从指定坐标附近开始探索。",
                "§f  · §e#farm§f：收获并重新种植附近成熟作物。",
                "§f  · §e#pickup§f：拾取附近掉落物；也可指定物品名称。",
                "§f  · §e#tunnel <高> <宽> <深>§f：沿当前朝向挖掘隧道。",
                "§f  · §e#blacklist§f：将最近的目标方块加入黑名单，避免再次接近。"
            },
            new String[]{
                "§d§l▌ 建造与原理图",
                "§f  · §e#build <文件名>§f：加载并建造 .schematic 原理图。",
                "§f  · §e#build <文件名> <x> <y> <z>§f：在指定位置开始建造。",
                "§f  · §e#litematica§f：建造当前打开的 Litematica 原理图。",
                "§f  · §e#sel pos1§f / §e#sel pos2§f：设置选区两个角点。",
                "§f  · §e#sel set <方块>§f：用方块填充选区；§e#sel replace <旧方块> <新方块>§f：替换方块。",
                "§f  · §e#sel walls§f、§e#sel shell§f、§e#sel sphere§f：创建墙体、外壳或球体。",
                "§f  · §e#sel clear§f：清除选区；§e#sel undo§f：撤销上一次选区操作。",
                "§f  · 建造前确认背包材料充足，并准备好安全区域。"
            },
            new String[]{
                "§6§l▌ 路径点与家",
                "§f  · §e#wp save <标签> <名称>§f：保存当前位置为路径点。",
                "§f  · §e#wp list§f：列出全部路径点；§e#wp info <标签或名称>§f：查看详情。",
                "§f  · §e#wp goal <标签或名称>§f：将路径点设置为目标。",
                "§f  · §e#wp goto <标签或名称>§f：设置目标并立即开始寻路。",
                "§f  · §e#wp delete <标签或名称>§f：删除指定路径点。",
                "§f  · §e#sethome§f：在当前位置保存名为 home 的路径点。",
                "§f  · §e#home§f：前往 home 路径点。"
            },
            new String[]{
                "§c§l▌ 跟随、暂停与飞行",
                "§f  · §e#follow players§f：跟随所有玩家；§e#follow player <玩家名>§f：跟随指定玩家。",
                "§f  · §e#follow entities§f：跟随实体；也可以指定 skeleton、horse 等实体类型。",
                "§f  · §e#pause§f：暂停当前任务；§e#resume§f：继续任务；§e#paused§f：查看暂停状态。",
                "§f  · §e#elytra§f：使用鞘翅飞向当前目标（需要合适的装备和环境）。",
                "§f  · §e#elytra reset§f：重置鞘翅进程；§e#elytra supported§f：检查原生库支持情况。",
                "§f  · §e#invert§f：反转目标方向，让 Baritone 远离当前目标。"
            },
            new String[]{
                "§5§l▌ 设置与维护",
                "§f  · §e#set§f：列出设置；§e#set <设置名>§f：查看当前值。",
                "§f  · §e#set <设置名> <值>§f：修改设置；§e#set toggle <设置名>§f：切换布尔设置。",
                "§f  · §e#set modified§f：查看已修改设置；§e#set reset <设置名>§f：恢复单项默认值。",
                "§f  · §e#set reset all§f：恢复全部默认设置；执行前请确认确实需要重置。",
                "§f  · §e#eta§f：查看预计到达时间；§e#proc§f：查看当前进程信息。",
                "§f  · §e#waypoints save/load§f：保存或加载路径点数据。",
                "§f  · §e#repack§f：重新整理附近区块缓存；§e#reloadall§f：重新加载世界缓存。",
                "§f  · §e#saveall§f：保存缓存；§e#version§f：查看 Baritone 版本。"
            },
            new String[]{
                "§e§l▌ 常见问题",
                "§f  · 不执行命令：确认使用了 Baritone 前缀，并检查命令拼写和参数。",
                "§f  · 找不到方块：先在目标区域走动以建立缓存，或确认方块 ID 正确。",
                "§f  · 卡住或路线不合适：使用 §e#cancel§f 停止，再调整目标或设置。",
                "§f  · 建造缺材料：补充背包材料后使用 §e#resume§f，必要时重新执行建造。",
                "§f  · 本模块只提供说明，不会自动执行任何 Baritone 指令。"
            }
        );
    }
}
