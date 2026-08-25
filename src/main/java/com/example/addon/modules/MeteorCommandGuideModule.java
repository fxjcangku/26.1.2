package com.example.addon.modules;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

public final class MeteorCommandGuideModule extends YiyiaddonModule {
    public MeteorCommandGuideModule() {
        super(AddonTemplate.CATEGORY, "Meteor指令说明", "打开后查看 Meteor 常用指令、中文别名、参数格式和使用示例。");
        this.toggle();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{ "§lMeteor 指令完全指南（超详细版）" },
            new String[]{
                "§e§l▌ 基础规则",
                "§f  · Meteor 指令使用 §e.§f 前缀，例如 §e.help§f。",
                "§f  · 中文命令别名需要开启“界面汉化”模块。",
                "§f  · 英文命令始终是最稳定的输入方式。",
                "§f  · 输入 §e.commands§f 查看当前已注册的命令。",
                "§f  · 输入 §e.help <命令>§f 查看参数格式和详细帮助。",
                "§f  · 参数中的 §e<内容>§f 表示必填，§e[内容]§f 表示可选。",
                "§f  · 遇到报错时，优先复制英文命令重新检查参数。"
            },
            new String[]{
                "§a§l▌ 命令发现与帮助",
                "§f  §e.commands§f / §e.命令列表§f - 列出当前所有可用命令。",
                "§f  §e.help§f / §e.帮助§f - 查看帮助总览。",
                "§f  §e.help toggle§f - 查看模块开关命令的参数。",
                "§f  §e.help bind§f - 查看模块按键绑定命令的参数。",
                "§f  §e.help settings§f - 查看模块设置命令的参数。",
                "§f  · 如果某个命令不存在，先执行 §e.commands§f 确认当前版本是否注册。"
            },
            new String[]{
                "§b§l▌ 模块控制",
                "§f  §e.toggle <模块>§f - 开启或关闭指定模块。",
                "§f  §e.t <模块>§f - toggle 的简写。",
                "§f  §e.bind <模块> <按键>§f - 直接设置模块按键。",
                "§f  §e.bind <模块>§f - 进入按键录入状态，随后按下目标按键。",
                "§f  §e.binds§f - 查看所有模块当前的按键绑定。",
                "§f  §e.settings <模块>§f - 打开指定模块的设置界面。",
                "§f  §e.modules§f - 查看已加载模块列表。",
                "§f  §e.toggle 空中跳跃§f - 汉化开启后可使用中文模块名。"
            },
            new String[]{
                "§d§l▌ 配置与界面",
                "§f  §e.profiles§f - 查看、加载和管理配置档。",
                "§f  §e.reload§f - 重新加载客户端配置。",
                "§f  §e.reset§f - 重置指定模块或设置。",
                "§f  §e.fov <数值>§f - 修改客户端视野范围。",
                "§f  §e.hud§f - 管理 HUD 项目。",
                "§f  §e.say <消息>§f - 通过聊天栏发送消息。",
                "§f  · 修改配置前建议先保存当前配置档，避免误操作后无法恢复。"
            },
            new String[]{
                "§6§l▌ 玩家与社交",
                "§f  §e.friends§f - 管理好友列表。",
                "§f  §e.friends add <玩家>§f - 添加好友。",
                "§f  §e.friends remove <玩家>§f - 移除好友。",
                "§f  §e.friends list§f - 查看好友列表。",
                "§f  §e.name-history <玩家>§f - 查询玩家曾用名记录。",
                "§f  §e.inventory§f - 查看当前打开的背包信息。",
                "§f  §e.peek§f - 查看手持容器或目标容器内容。",
                "§f  · 玩家名称、服务器权限和命令可用性以当前环境为准。"
            },
            new String[]{
                "§c§l▌ 世界与移动操作",
                "§f  §e.gamemode <模式>§f - 请求切换游戏模式。",
                "§f  §e.locate <结构>§f - 请求查找指定结构。",
                "§f  §e.waypoint§f - 管理路径点。",
                "§f  §e.waypoint list§f - 查看路径点列表。",
                "§f  §e.waypoint add <名称>§f - 添加当前位置路径点。",
                "§f  §e.waypoint remove <名称>§f - 删除指定路径点。",
                "§f  §e.vclip <距离>§f - 执行垂直位移操作。",
                "§f  §e.hclip <距离>§f - 执行水平位移操作。",
                "§f  · 位移、模式和结构查询可能受服务器权限、反作弊和版本限制。"
            },
            new String[]{
                "§5§l▌ 物品与容器",
                "§f  §e.drop§f - 丢弃物品或处理丢弃相关操作。",
                "§f  §e.enchant§f - 为手持物品执行附魔操作。",
                "§f  §e.give <物品> [数量]§f - 请求给予物品。",
                "§f  §e.nbt§f - 查看手持物品的 NBT 数据。",
                "§f  §e.ec§f / §eechest§f - 打开末影箱或查看末影箱内容。",
                "§f  §e.inv§f / §einventory§f - 查看背包相关信息。",
                "§f  · 这些命令通常需要创造模式、管理员权限或服务端允许。"
            },
            new String[]{
                "§9§l▌ 服务器与连接",
                "§f  §e.server§f - 查看当前服务器相关信息。",
                "§f  §edisconnect§f / §edc§f - 断开当前连接。",
                "§f  §edismount§f - 下马或离开当前坐骑。",
                "§f  §espectate <玩家>§f - 请求旁观指定玩家。",
                "§f  §eswarm§f - 管理蜂群连接功能。",
                "§f  · 断开连接不会保存未写入的临时状态，请操作前确认配置已经保存。"
            },
            new String[]{
                "§e§l▌ 典型使用流程",
                "§f  1. 输入 §e.commands§f，确认命令已注册。",
                "§f  2. 输入 §e.help <命令>§f，确认参数顺序。",
                "§f  3. 先用查询类命令测试，再执行修改世界或移动的命令。",
                "§f  4. 模块异常时输入 §e.toggle <模块>§f 关闭后重新检查设置。",
                "§f  5. 中文别名失效时，切换回对应英文命令测试。",
                "§f  6. 不确定命令来源时，不要直接执行带有未知参数的命令。"
            },
            new String[]{
                "§c§l▌ 报错排查",
                "§f  · “命令不完整”：参数数量不足，使用 §e.help <命令>§f 查看格式。",
                "§f  · “参数错误”：检查数字、玩家名、模块名和枚举值。",
                "§f  · “未找到命令”：输入 §e.commands§f 确认命令名称。",
                "§f  · 中文命令无效：确认“界面汉化”模块已经开启。",
                "§f  · 模块名无效：使用 §e.modules§f 查看实际注册名称。",
                "§f  · 服务端拒绝：检查权限、游戏模式和服务器规则。",
                "§f  · 输入 §e.reload§f 前先保存配置，避免覆盖未保存的修改。"
            },
            new String[]{
                "§4§l▌ 安全提醒",
                "§f  · 本说明只介绍 Meteor 常用命令，不保证所有服务器允许使用。",
                "§f  · give、enchant、damage、hclip、vclip 等命令可能触发权限或反作弊检查。",
                "§f  · 不要在不了解参数含义时执行修改世界、移动或连接相关命令。",
                "§f  · 命令列表会随 Meteor 版本、已加载模块和附加组件变化。",
                "§f  · 本模块只显示说明，不会自动执行任何指令。"
            }
        );
    }
}
