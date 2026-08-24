package com.example.addon.translations;

import com.example.addon.mixin.ModuleTranslationAccess;
import com.example.addon.mixin.SettingGroupTranslationAccess;
import com.example.addon.mixin.SettingTranslationAccess;
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

        String normalized = normalizeVisibleText(text);
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
        if (normalized.equals("Please select the account to log into in your browser.")) {
            return "请在浏览器中选择要登录的账户。";
        }
        if (normalized.equals("If the link does not automatically open in a few seconds, copy it into your browser.")) {
            return "如果链接在几秒钟内没有自动打开，请将其复制到浏览器中。";
        }
        if (normalized.equals("Note: on vanilla servers you may give yourself up to 4 blocks of additional reach for specific actions - interacting with block entities (chests, furnaces, etc.) or with vehicles. This does not work on paper servers.")) {
            return "提示：在原版服务器上，部分操作可额外获得最多 4 格触及距离，例如与方块实体（箱子、熔炉等）或载具交互。此功能在 Paper 服务器上无效。";
        }
        if (normalized.equals("Whether to swap on a missed attack. Useful for quickly lunging with spears.")) {
            return "攻击未命中时是否切换。使用长矛快速突进时很有用。";
        }
        if (normalized.equals("Pauses while Crystal Aura is placing.")) {
            return "水晶光环放置水晶时暂停。";
        }
        if (normalized.equals("Whether to apply a bypass for NCP.")) {
            return "是否启用 NCP 绕过。";
        }
        if (normalized.startsWith("serverbound/") || normalized.startsWith("clientbound/")) {
            return translatePacketId(normalized);
        }

        String direct = TRANSLATIONS.get(normalized);
        if (direct != null) return direct;

        String moduleKey = "module." + normalized.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        String moduleTitle = TRANSLATIONS.get(moduleKey);
        if (moduleTitle != null) return moduleTitle;

        return switch (normalized) {
            case "Modules" -> "模块";
            case "Select entities" -> "选择实体";
            case "Select Items", "Select items" -> "选择物品";
            case "Select Blocks", "Select blocks" -> "选择方块";
            case "Select Packets", "Select packets" -> "选择数据包";
            case "Select Color", "Select color" -> "选择颜色";
            case "Select Block" -> "选择方块";
            case "Select item" -> "选择物品";
            case "Select Effects" -> "选择效果";
            case "Select Font" -> "选择字体";
            case "Select Modules" -> "选择模块";
            case "Select Particles" -> "选择粒子";
            case "Select Potion" -> "选择药水";
            case "Select Screen Handlers" -> "选择界面处理器";
            case "Select Sounds" -> "选择声音";
            case "Select Storage Blocks" -> "选择存储方块";
            case "Select Enchantments" -> "选择附魔";
            case "Animals" -> "动物";
            case "Water Animals" -> "水生动物";
            case "Monsters" -> "怪物";
            case "Ambient" -> "环境生物";
            case "Config" -> "配置";
            case "GUI" -> "界面";
            case "HUD" -> "HUD";
            case "Friends" -> "好友";
            case "Macros" -> "宏";
            case "Multiplayer" -> "多人游戏";
            case "Accounts" -> "账户";
            case "Add" -> "添加";
            case "Add Hud element" -> "添加 HUD 元素";
            case "Refreshing" -> "刷新中";
            case "Threads" -> "线程数";
            case "Timeout" -> "超时";
            case "Retries On Timeout" -> "超时重试次数";
            case "Sort By Latency" -> "按延迟排序";
            case "Prune Dead" -> "移除失效代理";
            case "Prune By Latency" -> "按延迟移除";
            case "Prune To Count" -> "移除至指定数量";
            case "Socks5" -> "Socks5";
            case "Address" -> "地址";
            case "Port" -> "端口";
            case "Enabled" -> "启用";
            case "Whether the proxy is enabled." -> "是否启用代理。";
            case "Username" -> "用户名";
            case "Password" -> "密码";
            case "Active Modules" -> "活动模块";
            case "Compass" -> "指南针";
            case "Hole" -> "空洞";
            case "Item" -> "物品";
            case "Keyboard" -> "键盘";
            case "Lag Notifier" -> "延迟通知";
            case "Map" -> "地图";
            case "Module Infos" -> "模块信息";
            case "Player Model" -> "玩家模型";
            case "Player Radar" -> "玩家雷达";
            case "Potion Timers" -> "药水计时器";
            case "Example" -> "示例";
            case "Add Cracked Account" -> "添加离线账户";
            case "Add Microsoft Account" -> "添加微软账户";
            case "Add Session Account" -> "添加会话账户";
            case "Add The Altening Account" -> "添加 The Altening 账户";
            case "Access Token:" -> "访问令牌：";
            case "Active:" -> "激活：";
            case "Authorization Guide" -> "授权指南";
            case "Cancel" -> "取消";
            case "Check proxies" -> "检查代理";
            case "Cleanup" -> "清理";
            case "Click to copy Token" -> "点击复制令牌";
            case "Configure Blocks" -> "配置方块";
            case "Confirm" -> "确认";
            case "Connection dropped" -> "连接已断开";
            case "Copy" -> "复制";
            case "Copy link" -> "复制链接";
            case "Cracked" -> "离线账户";
            case "Create" -> "创建";
            case "Done" -> "完成";
            case "Edit" -> "编辑";
            case "Edit title & author" -> "编辑标题和作者";
            case "From:" -> "来自：";
            case "Import" -> "导入";
            case "Import Proxies" -> "导入代理";
            case "Load" -> "加载";
            case "Load On Join" -> "加入时加载";
            case "Log" -> "日志";
            case "Microsoft" -> "微软";
            case "Modify Amplifiers" -> "修改增幅";
            case "Name:" -> "名称：";
            case "Name" -> "名称";
            case "New" -> "新建";
            case "New Profile" -> "新建配置档";
            case "Notebot Songs" -> "音符盒歌曲";
            case "Proxies Config" -> "代理配置";
            case "Proxies" -> "代理";
            case "Random Song" -> "随机歌曲";
            case "Refresh" -> "刷新";
            case "Save" -> "保存";
            case "Search for the songs..." -> "搜索歌曲……";
            case "Select" -> "选择";
            case "Session" -> "会话";
            case "Settings can be imported using Ctrl+V or the paste button." -> "可使用 Ctrl+V 或粘贴按钮导入设置。";
            case "The settings for this module are now in your clipboard." -> "该模块的设置已复制到剪贴板。";
            case "Title" -> "标题";
            case "Token:" -> "令牌：";
            case "Website" -> "网站";
            case "You can also copy settings using Ctrl+C." -> "也可使用 Ctrl+C 复制设置。";
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
            case "Theme:" -> "主题：";
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
            case "Original" -> "原版";
            case "Protection" -> "保护";
            case "BlastProtection" -> "爆炸保护";
            case "FireProtection" -> "火焰保护";
            case "ProjectileProtection" -> "弹射物保护";
            case "LowestHealth" -> "最低生命值";
            case "LowestDistance" -> "最近距离";
            case "HighestDistance" -> "最远距离";
            case "HighestHealth" -> "最高生命值";
            case "Vanilla" -> "原版";
            case "Burst" -> "爆发";
            case "Glide" -> "滑翔";
            case "Abilities" -> "能力";
            case "Packet" -> "数据包";
            case "40° angle" -> "40° 角度";
            case "Bounce" -> "反弹";
            case "Normal" -> "普通";
            case "Silent" -> "静默";
            case "Sides" -> "侧面";
            case "Start Side Color" -> "起始侧面颜色";
            case "Start Line Color" -> "起始线条颜色";
            case "End Side Color" -> "结束侧面颜色";
            case "End Line Color" -> "结束线条颜色";
            case "Keybind" -> "按键绑定";
            case "Rainbow" -> "彩虹";
            case "Rainbow:" -> "彩虹：";
            case "ExactInstruments" -> "精确乐器";
            case "AnyInstrument" -> "任意乐器";
            case "BlockState" -> "方块状态";
            case "BelowBlock" -> "方块下方";
            case "Harp" -> "竖琴";
            case "Basedrum" -> "底鼓";
            case "Snare" -> "小军鼓";
            case "Hat" -> "镲片";
            case "Bass" -> "贝斯";
            case "Flute" -> "长笛";
            case "Bell" -> "钟琴";
            case "Guitar" -> "吉他";
            case "Chime" -> "风铃";
            case "Xylophone" -> "木琴";
            case "IronXylophone" -> "铁木琴";
            case "CowBell" -> "牛铃";
            case "Didgeridoo" -> "迪吉里杜管";
            case "Bit" -> "比特";
            case "Banjo" -> "班卓琴";
            case "Pling" -> "叮咚";
            case "Open Song GUI" -> "打开歌曲界面";
            case "Align Center" -> "居中对齐";
            case "Module disabled." -> "模块已禁用。";
            case "Resume" -> "恢复";
            case "Stop" -> "停止";
            case "Back" -> "返回";
            case "Start" -> "启动";
            case "Guide" -> "指南";
            case "Messages" -> "消息";
            case "The messages for the macro to send." -> "要发送的宏消息。";
            case "Type" -> "类型";
            case "Optional" -> "可选";
            case "Xcarry" -> "携带容器";
            case "Joins/Leaves" -> "加入/退出";
            case "Player Joins Leaves" -> "玩家加入/退出";
            case "Notification Delay" -> "通知延迟";
            case "Simple Notifications" -> "简洁通知";
            case "Two Bars" -> "双栏";
            case "Clear Rendering Cache" -> "清除渲染缓存";
            case "Flatten" -> "压平";
            case "R:" -> "红：";
            case "G:" -> "绿：";
            case "B:" -> "蓝：";
            case "A:" -> "透明度：";
            case "Swap On Miss" -> "未命中时切换";
            case "Weapon Options" -> "武器选项";
            case "Pause On CA" -> "水晶光环时暂停";
            case "1.12 Placement" -> "1.12 放置方式";
            case "AntiFacePlace" -> "防贴脸放置";
            case "Search Inventory" -> "搜索背包";
            case "Food Priority" -> "食物优先级";
            case "Choose new target" -> "选择新目标";
            case "Ncp Bypass", "NCP Bypass" -> "NCP 绕过";
            case "Sword Slash" -> "剑横扫";
            case "Random Type" -> "随机类型";
            case "Characters" -> "字符数";
            case "ASCII" -> "ASCII";
            case "UTF-8" -> "UTF-8";
            case "MAIN_HAND" -> "主手";
            case "OFF_HAND" -> "副手";
            case "Continuous Breeding" -> "连续繁殖";
            case "Fuel Items Per Refill" -> "每次补充燃料数量";
            case "Auto Close" -> "自动关闭";
            case "Mouse Right" -> "鼠标右键";
            case "Mouse Middle" -> "鼠标中键";
            case "Rendering" -> "渲染";
            case "Frame Input Handling" -> "每帧处理输入";
            case "Repair Threshold" -> "修复阈值";
            case "Mine Threshold" -> "挖掘阈值";
            case "Clear Chunks" -> "清除区块";
            case "Reset Tracers" -> "重置射线";
            case "Use Display Name" -> "使用显示名称";
            case "Fluid Opacity" -> "流体不透明度";
            case "Changes input handling to work every frame instead of every tick. A very minor effect but may make inputs feel smoother, especially in laggy environments. Will flag anticheats that check packet order (Grim)." -> "将输入处理改为每帧执行，而不是每刻执行。影响很小，但可能让输入更加流畅，尤其是在卡顿环境中。会触发检查数据包顺序的反作弊（Grim）。";
            case "Will flag anticheats that check packet order (Grim)." -> "会触发检查数据包顺序的反作弊（Grim）。";
            case "Uses the players server display name instead of their account name." -> "使用玩家的服务器显示名称，而不是账户名称。";
            case "You need to be in a world." -> "你需要进入世界。";
            case "Style" -> "样式";
            case "Appearance" -> "外观";
            case "Actions" -> "操作";
            case "Settings" -> "设置";
            case "Screens" -> "界面";
            case "Targeting" -> "目标";
            case "Sorting" -> "排序";
            case "Safety" -> "安全";
            case "Timing" -> "时序";
            case "Overlay" -> "覆盖层";
            case "Pathing" -> "寻路";
            case "Paving" -> "铺路";
            case "Digging" -> "挖掘";
            case "Death" -> "死亡";
            case "Hand" -> "手部";
            case "Main Hand" -> "主手";
            case "Off Hand" -> "副手";
            case "Body" -> "身体";
            case "Head" -> "头部";
            case "Feet" -> "脚部";
            case "Smart" -> "智能";
            case "Strict" -> "严格";
            case "Simple" -> "简单";
            case "Full" -> "完整";
            case "Platform" -> "平台";
            case "Single" -> "单层";
            case "Top" -> "顶部";
            case "Bottom" -> "底部";
            case "Flat" -> "平面";
            case "Cuboid" -> "长方体";
            case "Sphere-2D" -> "二维球面";
            case "2D" -> "二维";
            case "Down" -> "向下";
            case "Up" -> "向上";
            case "Through Walls" -> "穿墙";
            case "Static" -> "静态";
            case "Stack" -> "堆叠";
            case "Face" -> "面部";
            case "Weapons" -> "武器";
            case "All" -> "全部";
            case "Armor" -> "护甲";
            case "Hands" -> "双手";
            case "Velocity" -> "速度";
            case "Break" -> "破坏";
            case "Smooth" -> "平滑";
            case "Fading" -> "渐隐";
            case "Gradient" -> "渐变";
            case "Disabled" -> "禁用";
            case "Accurate" -> "精确";
            case "Fast" -> "快速";
            case "Client" -> "客户端";
            case "Baby" -> "幼年";
            case "OnHit" -> "命中时";
            case "Ignore" -> "忽略";
            case "EGap" -> "附魔金苹果";
            case "Gap" -> "金苹果";
            case "Crystal" -> "末影水晶";
            case "Totem" -> "不死图腾";
            case "Shield" -> "盾牌";
            case "Potion" -> "药水";
            case "EChest" -> "末影箱";
            case "Obsidian" -> "黑曜石";
            case "Anvil" -> "铁砧";
            case "Held" -> "手持方块";
            case "Sword" -> "剑";
            case "Axe" -> "斧";
            case "UpdatedNCP" -> "更新版 NCP";
            case "OldNCP" -> "旧版 NCP";
            case "Jump" -> "跳跃";
            case "MiniJump" -> "小跳";
            case "Air Place" -> "空中放置";
            case "Place" -> "放置";
            case "Safe" -> "安全";
            case "Unsafe" -> "不安全";
            case "Never" -> "从不";
            case "OnActivate" -> "启用时";
            case "Incomplete" -> "不完整时";
            case "File" -> "文件";
            case "Random" -> "随机";
            case "Ascii" -> "ASCII";
            case "Utf8" -> "UTF-8";
            case "Sequential" -> "顺序";
            case "MineGame" -> "Minecraft 图标";
            case "Snail" -> "蜗牛";
            case "Whitelist", "WhiteList" -> "白名单";
            case "Blacklist", "BlackList" -> "黑名单";
            case "Preview" -> "预览";
            case "Noteblocks" -> "音符盒";
            case "LoadingSong" -> "加载歌曲";
            case "SetUp" -> "设置中";
            case "Tune" -> "调音";
            case "Playing" -> "播放中";
            case "Spawn" -> "生成";
            case "Despawn" -> "消失";
            case "Joins" -> "加入";
            case "Leaves" -> "离开";
            case "Host" -> "主机";
            case "Worker" -> "工作节点";
            case "Flight" -> "飞行";
            case "Sprinting" -> "疾跑时";
            case "Walking" -> "行走时";
            case "Forwards" -> "前进";
            case "Backwards" -> "后退";
            case "Left" -> "左";
            case "Right" -> "右";
            case "TOGGLE" -> "切换";
            case "CHOOSE_NEW_TARGET" -> "选择新目标";
            case "DISCONNECT" -> "断开连接";
            case "Inventory" -> "背包";
            case "Solid" -> "固体";
            case "LowHop" -> "低跳";
            case "AirPlace" -> "空中放置";
            case "BeforeDamage" -> "受到伤害前";
            case "BeforeDeath" -> "死亡前";
            case "Bucket" -> "水桶";
            case "PowderSnow" -> "粉雪";
            case "HayBale" -> "干草捆";
            case "Cobweb" -> "蜘蛛网";
            case "SlimeBlock" -> "粘液块";
            case "Timer" -> "计时器";
            case "Rage" -> "激进";
            case "Sneaking" -> "潜行时";
            case "NotSneaking" -> "非潜行时";
            case "Pitch40" -> "俯角 40°";
            case "WaitForGround" -> "等待落地";
            case "Wait For Ground" -> "等待落地";
            case "Working" -> "工作中";
            case "Unknown" -> "未知";
            case "Unauthorized" -> "未授权";
            case "Smash" -> "猛击";
            case "Regular Mace" -> "普通重锤";
            case "Mace Enchants" -> "重锤附魔";
            case "Sword Enchants" -> "剑类附魔";
            case "Spear Enchants" -> "长矛附魔";
            case "Server" -> "服务端";
            case "Hold" -> "按住";
            case "Press" -> "按下";
            case "Combined" -> "综合";
            case "Hunger" -> "饥饿值";
            case "Saturation" -> "饱和度";
            case "Health" -> "生命值";
            case "Any" -> "任一";
            case "Fortune" -> "时运";
            case "SilkTouch" -> "精准采集";
            case "Diamond" -> "钻石胸甲";
            case "Netherite" -> "下界合金胸甲";
            case "PreferDiamond" -> "优先钻石胸甲";
            case "PreferNetherite" -> "优先下界合金胸甲";
            case "Some" -> "部分";
            case "Pearl" -> "末影珍珠";
            case "XP" -> "经验瓶";
            case "Rocket" -> "烟花火箭";
            case "WindCharge" -> "风弹";
            case "Bow" -> "弓";
            case "Chorus" -> "紫颂果";
            case "AddFriend" -> "添加好友";
            case "Mainhand" -> "主手";
            case "Offhand" -> "副手";
            case "Hit" -> "攻击";
            case "Interact" -> "交互";
            case "Haste" -> "急迫";
            case "Damage" -> "伤害";
            case "Bytes" -> "字节";
            case "Kilobytes" -> "千字节";
            case "Megabytes" -> "兆字节";
            case "Dynamic" -> "动态";
            case "Image" -> "图像";
            case "EntityType" -> "实体类型";
            case "Distance" -> "距离";
            case "Box" -> "方框";
            case "Wireframe" -> "线框";
            case "Shader" -> "着色器";
            case "Glow" -> "发光";
            case "Camera" -> "摄像机";
            case "Gamma" -> "伽马";
            case "Luminance" -> "亮度";
            case "Bedrock" -> "基岩";
            case "Mixed" -> "混合";
            case "Potential" -> "潜在位置";
            case "Percentage" -> "百分比";
            case "Above" -> "上方";
            case "OnTop" -> "顶部";
            case "Everything" -> "全部";
            case "Pillar" -> "柱状";
            case "Lines" -> "线条";
            case "Offscreen" -> "屏幕外";
            case "Walkable" -> "可行走";
            case "PartiallyBlocked" -> "部分阻挡";
            case "FullyBlocked" -> "完全阻挡";
            case "Water" -> "水";
            case "Lava" -> "熔岩";
            case "At" -> "看向";
            case "Away" -> "避开";
            case "Partial" -> "部分";
            case "Shulker" -> "潜影盒";
            case "Replace" -> "替换";
            case "PlaceMissing" -> "放置缺失方块";
            case "Mine" -> "挖掘";
            case "Center" -> "对齐中心";
            case "Forward" -> "前进";
            case "ReLevel" -> "重新调平";
            case "FillLiquids" -> "填充液体";
            case "MineFront" -> "挖掘前方";
            case "MineFloor" -> "挖掘地面";
            case "MineRailings" -> "挖掘护栏";
            case "MineAboveRailings" -> "挖掘护栏上方";
            case "PlaceCornerBlock" -> "放置角落方块";
            case "PlaceRailings" -> "放置护栏";
            case "PlaceFloor" -> "放置地面";
            case "ThrowOutTrash" -> "丢弃垃圾";
            case "Restock" -> "补充物资";
            case "Closest" -> "最近";
            case "Furthest" -> "最远";
            case "TopDown" -> "从上到下";
            case "BottomUp" -> "从下到上";
            case "Cube" -> "立方体";
            case "UniformCube" -> "均匀立方体";
            case "Sphere" -> "球体";
            case "Toast" -> "弹窗";
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
            case "Clear" -> "清除";
            case "Reset to default elements" -> "重置为默认元素";
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
            case "TopLeft" -> "左上角";
            case "BottomLeft" -> "左下角";
            case "BottomRight" -> "右下角";
            case "Hidden" -> "隐藏";
            case "Spoof Saddle*" -> "伪装鞍";
            case "Lets you control rideable entities without a saddle." -> "让你无需鞍即可控制可骑乘实体。";
            case "Lets you control rideable entities without them being saddled. Only works on older server versions." -> "让你无需鞍即可控制可骑乘实体。仅适用于较旧的服务器版本。";
            case "Checks if the entity contains a saddle before mounting." -> "骑乘前检查实体是否装有鞍。";
            case "Lets you attack entities while using an item." -> "允许你在使用物品时攻击实体。";
            case "Lets you use items and attack at the same time." -> "允许你同时使用物品和攻击。";
            case "Lets you jump in the air." -> "允许你在空中跳跃。";
            case "Lets you go into the lava if you have Fire Resistance effect." -> "允许你在拥有防火效果时进入熔岩。";
            case "Lets you go into the lava when you fall over a certain height." -> "允许你从超过指定高度跌落时进入熔岩。";
            case "Lets you go into the lava when your sneak key is held." -> "允许你按住潜行键时进入熔岩。";
            case "Lets you go into the water when you are burning." -> "允许你着火时进入水中。";
            case "Lets you go into the water when you fall over a certain height." -> "允许你从超过指定高度跌落时进入水中。";
            case "Lets you go into the water when your sneak key is held." -> "允许你按住潜行键时进入水中。";
            case "Lets you type infinitely long messages." -> "允许你输入无限长度的消息。";
            case "None" -> "无";
            case "(0 selected)" -> "（未选择）";
            default -> text;
        };
    }

    private static String normalizeVisibleText(String text) {
        return text
            .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static String translateSettingValue(Object value) {
        if (value == null) return "";
        String original = value.toString();
        String translated = translateVisible(original);
        if (!translated.equals(original)) return translated;
        translated = translateUniqueScopedValue(original);
        if (!translated.equals(original)) return translated;
        if (value instanceof Enum<?> enumValue) {
            String name = enumValue.name().replace('_', ' ');
            translated = translateVisible(name);
            if (!translated.equals(name)) return translated;
            StringBuilder formatted = new StringBuilder();
            for (String part : name.toLowerCase().split(" ")) {
                if (!part.isEmpty()) {
                    formatted.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                }
            }
            translated = translateVisible(formatted.toString());
            if (!translated.equals(formatted.toString())) return translated;
        }
        return original;
    }

    private static String translateUniqueScopedValue(String original) {
        String suffix = "." + original;
        String match = null;
        for (Map.Entry<String, String> entry : TRANSLATIONS.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("module.") || key.endsWith(".description") || !key.endsWith(suffix)) continue;
            String translated = entry.getValue();
            if (translated.equals(original)) continue;
            if (match != null && !match.equals(translated)) return original;
            match = translated;
        }
        return match == null ? original : match;
    }

    private static String translatePacketId(String id) {
        String direction = id.startsWith("serverbound/") ? "客户端 → 服务端" : "服务端 → 客户端";
        int separator = id.indexOf(':');
        if (separator < 0 || separator == id.length() - 1) return direction + "（" + id + "）";

        String name = id.substring(separator + 1);
        String translated = switch (name) {
            case "block_entity_tag_query" -> "方块实体标签查询";
            case "resource_pack" -> "资源包";
            case "sign_update" -> "告示牌更新";
            case "move_player_pos_rot" -> "玩家位置与旋转移动";
            case "container_click" -> "容器点击";
            case "interact" -> "交互";
            case "select_trade" -> "选择交易";
            case "accept_teleportation" -> "确认传送";
            case "chat" -> "聊天消息";
            case "spectate_entity" -> "旁观实体";
            case "finish_configuration" -> "完成配置";
            case "set_creative_mode_slot" -> "设置创造模式栏位";
            case "attack" -> "攻击";
            case "chat_session_update" -> "聊天会话更新";
            case "pick_item_from_entity" -> "从实体拾取物品";
            case "configuration_acknowledged" -> "确认配置";
            case "chunk_batch_received" -> "区块批次已接收";
            case "paddle_boat" -> "划船";
            case "move_player_status_only" -> "仅更新玩家状态";
            case "use_item_on" -> "对方块使用物品";
            case "client_information" -> "客户端信息";
            case "container_close" -> "关闭容器";
            default -> formatPacketName(name);
        };
        return direction + " · " + translated + "（" + id + "）";
    }

    private static String formatPacketName(String name) {
        StringBuilder translated = new StringBuilder();
        for (String part : name.split("_")) {
            String value = switch (part) {
                case "block" -> "方块";
                case "entity" -> "实体";
                case "player" -> "玩家";
                case "item" -> "物品";
                case "container" -> "容器";
                case "chunk" -> "区块";
                case "move" -> "移动";
                case "update" -> "更新";
                case "set" -> "设置";
                case "get" -> "获取";
                case "use" -> "使用";
                case "click" -> "点击";
                case "chat" -> "聊天";
                case "command" -> "命令";
                case "sound" -> "声音";
                case "game" -> "游戏";
                case "world" -> "世界";
                case "teleport" -> "传送";
                case "rotation" -> "旋转";
                case "position", "pos" -> "位置";
                case "status" -> "状态";
                case "data" -> "数据";
                default -> part;
            };
            if (!translated.isEmpty()) translated.append(' ');
            translated.append(value);
        }
        return translated.toString();
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
