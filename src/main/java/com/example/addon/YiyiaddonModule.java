package com.example.addon;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

// ╔════════════════════════════════════════════════════════════════════╗
// ║                    YiyiaddonModule 基类                            ║
// ║                所有 yiyiaddon 模块的统一基类                        ║
// ╚════════════════════════════════════════════════════════════════════╝
//
// 【核心功能】
// 1. 统一消息格式：[yiyiaddon] [模块名] 内容
// 2. 颜色编码规范：红色前缀、白色模块名、自定义内容颜色
// 3. 说明面板构建：buildInfoWidget() 标准化面板生成
// 4. 高亮工具方法：highlightText/Server/Location/Command
//
// 【消息输出规范】
// - notify()      → 普通消息（白色）
// - notifyError() → 错误消息（橙色加粗）
// - info()        → Meteor 原生 info 拦截并中文化
// - warning()     → 警告消息（黄色加粗）
// - error()       → 错误消息（红色加粗）
//
// 【说明面板规范】
// - 标题：§l模块名 · 使用说明
// - 段落标题色：§e§l▌准备  §a§l▌功能  §b§l▌参数  §d§l▌模式  §c§l▌注意
// - 正文缩进：§f  1. 步骤（有序）  §f  · 条目（无序）  §f    续行
//
// ════════════════════════════════════════════════════════════════════

/**
 * yiyiaddon 模块基类
 * 
 * 统一消息格式、颜色规范、说明面板生成
 * 
 * @author yiyijia
 * @see YiyiaddonWatermark
 */
public abstract class YiyiaddonModule extends Module {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  构造函数
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    protected YiyiaddonModule(Category category, String name, String description) {
        super(category, name, description);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模块开关覆写 - 统一输出格式
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void toggle() {
        super.toggle();
        // Meteor 的 GUI 点击路径不会调用 sendToggledMsg()
        // 在这里统一输出，sendToggledMsg() 置空防止按键绑定双重提示
        if (mc.player != null && chatFeedback) {
            String status = isActive() ? "§a§l已启动" : "§c§l已关闭";
            mc.player.sendSystemMessage(Component.literal(formatMessage(title, status)));
        }
    }

    @Override
    public void sendToggledMsg() {
        // 空实现：提示已在 toggle() 中输出
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Meteor 原生消息拦截与中文化
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void info(String message, Object... args) {
        // 拦截按键绑定消息并中文化
        if ("Removed bind.".equals(message)) {
            notify("已移除按键绑定");
            return;
        }
        if (message != null && message.startsWith("Bound to")) {
            String expanded = formatArgs(message, args);
            String clean = expanded.replaceAll("\\(highlight\\)|\\(default\\)", "").trim();
            String key = clean.replace("Bound to", "").replace(".", "").trim();
            notify("已绑定按键：" + key);
            return;
        }
        notify(formatArgs(message, args));
    }

    @Override
    public void info(Component message) {
        notify(message.getString());
    }

    @Override
    public void warning(String message, Object... args) {
        notify("§e§l" + formatArgs(message, args));
    }

    @Override
    public void error(String message, Object... args) {
        notify("§c§l" + formatArgs(message, args));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  消息输出方法 - 子类使用
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 普通消息（白色）
     * 格式：§c§l[yiyiaddon] §r§f§l[模块名] §r§f消息
     */
    protected void notify(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§f" + message)));
    }

    /**
     * 错误消息（橙色加粗）
     * 格式：§c§l[yiyiaddon] §r§f§l[模块名] §r§6§l错误消息
     */
    protected void notifyError(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§6§l" + message)));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  高亮工具方法 - 说明面板使用
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 文本高亮（亮绿色粗体） - 用于目标子服等 */
    protected String highlightText(String text) {
        return "§a§l" + text + "§r§f§l";
    }

    /** 服务器高亮（金色粗体） - 用于服务器名 */
    protected String highlightServer(String text) {
        return "§6§l" + text + "§r§f§l";
    }

    /** 位置高亮（紫粉色粗体） - 用于地点坐标 */
    protected String highlightLocation(String text) {
        return "§d§l" + text + "§r§f§l";
    }

    /** 指令高亮（黄色粗体） - 用于指令示例 */
    protected String highlightCommand(String text) {
        return "§e§l" + text + "§r§f§l";
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  说明面板构建 - 子类覆写 getWidget()
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 标准说明面板构建
     * 
     * @param theme Meteor GUI 主题
     * @param sections 章节数组，第一个是标题，后续是段落
     * @return WWidget 面板控件
     * 
     * @example
     * return buildInfoWidget(theme,
     *     new String[]{ "§l自动农场 · 使用说明" },
     *     new String[]{
     *         "§e§l▌ 准备",
     *         "§f  1. 建好农田",
     *         "§f  2. 放置箱子"
     *     },
     *     new String[]{
     *         "§a§l▌ 功能原理",
     *         "§f  · 自动收割补种",
     *         "§f  · 智能物流管理"
     *     }
     * );
     */
    protected WWidget buildInfoWidget(GuiTheme theme, String[]... sections) {
        WTable t = theme.table();
        boolean firstSection = true;
        
        for (String[] section : sections) {
            if (section == null || section.length == 0) continue;
            
            // 段落间空行（第一段标题行前不加）
            if (!firstSection) {
                t.add(theme.label(" ")).expandX();
                t.row();
            }
            
            // 添加段落内容
            for (String line : section) {
                t.add(theme.label(line)).expandX();
                t.row();
            }
            
            firstSection = false;
        }
        
        return t;
    }

    /**
     * 带自定义头部的说明面板构建
     * 
     * @param theme Meteor GUI 主题
     * @param headerWidgets 头部控件构建器（放置按钮等交互控件）
     * @param sections 章节数组
     * @return WWidget 面板控件
     */
    protected WWidget buildInfoWidget(GuiTheme theme, Consumer<WTable> headerWidgets, String[]... sections) {
        WTable t = theme.table();
        
        // 先添加头部控件
        headerWidgets.accept(t);
        t.add(theme.label(" ")).expandX();
        t.row();
        
        // 再添加说明文本
        boolean firstSection = true;
        for (String[] section : sections) {
            if (section == null || section.length == 0) continue;
            
            if (!firstSection) {
                t.add(theme.label(" ")).expandX();
                t.row();
            }
            
            for (String line : section) {
                t.add(theme.label(line)).expandX();
                t.row();
            }
            
            firstSection = false;
        }
        
        return t;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  消息格式化内部方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 格式化模块消息
     * 格式：§c§l[yiyiaddon] §r§f§l[模块名] §r内容
     */
    public static String formatMessage(String moduleName, String message) {
        String cleanModuleName = stripColorCodes(stripPrefix(moduleName));
        String cleanMessage = stripPrefix(message);
        return "§c§l[yiyiaddon] §r§f§l[" + cleanModuleName + "] §r" + cleanMessage;
    }

    /** 去除 [yiyiaddon] 前缀 */
    private static String stripPrefix(String value) {
        return value.replace("[yiyiaddon]", "").trim();
    }

    /** 去除 Minecraft 颜色代码 */
    private static String stripColorCodes(String value) {
        return value.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /** 格式化参数 */
    private static String formatArgs(String message, Object... args) {
        if (args == null || args.length == 0) return message;
        try {
            return String.format(message, args);
        } catch (Exception ignored) {
            return message;
        }
    }
}

// ╔════════════════════════════════════════════════════════════════════╗
// ║                    GitHub 仓库结构说明                             ║
// ╠════════════════════════════════════════════════════════════════════╣
// ║ 仓库地址：https://github.com/fxjcangku/26.1.2                      ║
// ║                                                                    ║
// ║ 分支结构：                                                         ║
// ║ ├─ main (公开)    - 只有 README.md + Release 页面，外界可见       ║
// ║ └─ source (私密)  - 完整源码，不公开                               ║
// ║                                                                    ║
// ║ 重要页面：                                                         ║
// ║ • 发布页面（公开）：https://github.com/fxjcangku/26.1.2/releases   ║
// ║ • 源码分支（私密）：https://github.com/fxjcangku/26.1.2/tree/source ║
// ║                                                                    ║
// ║ 工作流程：                                                         ║
// ║ 1. 本地切换到 source 分支：git checkout source                     ║
// ║ 2. 开发完成后构建混淆版：gradlew buildOfficial                     ║
// ║ 3. 保存映射文件：build/obfuscation-mapping-v{版本}.txt             ║
// ║ 4. 发布到 Release 页面（只上传 jar，不上传源码）                   ║
// ║ 5. 如需更新 README：切换到 main 分支编辑并推送                     ║
// ║ 6. 用户自动收到更新提示（YiyiaddonWelcomeService）                  ║
// ║                                                                    ║
// ║ 分支保护：                                                         ║
// ║ • main 分支：删除了所有源码文件，只保留 README.md + LICENSE        ║
// ║ • source 分支：完整源码 + Personal jar，永远不公开                 ║
// ║ • 本地开发：始终在 source 分支工作                                 ║
// ╠════════════════════════════════════════════════════════════════════╣
// ║                       开发规范快速参考                             ║
// ╠════════════════════════════════════════════════════════════════════╣
// ║ 【构建命令】                                                       ║
// ║ gradlew runClient       - 开发客户端测试                           ║
// ║ gradlew buildPersonal   - 个人测试版（未混淆）                     ║
// ║ gradlew buildOfficial   - 官方发布版（混淆 + 映射文件）            ║
// ║                                                                    ║
// ║ 【模块编写规范】                                                   ║
// ║ • 继承 YiyiaddonModule                                            ║
// ║ • 构造描述以"。详细参考下面使用说明。"结尾                         ║
// ║ • 覆写 getWidget() 返回 buildInfoWidget(theme, sections...)       ║
// ║ • 使用 notify()/notifyError() 输出消息                            ║
// ║ • 使用 highlightText/Server/Location/Command() 高亮文本           ║
// ║                                                                    ║
// ║ 【说明面板颜色规范】                                               ║
// ║ §e§l▌ 准备/使用方法（黄色）                                        ║
// ║ §a§l▌ 功能/原理/流程（绿色）                                       ║
// ║ §b§l▌ 参数建议/说明（青色）                                        ║
// ║ §d§l▌ 模式说明/提示（粉色）                                        ║
// ║ §c§l▌ 注意/警告（红色）                                            ║
// ║                                                                    ║
// ║ 【发布 Release 规范】                                              ║
// ║ • 标签格式：v{版本} 或 v{版本}-beta{n}                             ║
// ║ • 标题：yiyiaddon v{版本}                                          ║
// ║ • 描述：按模板填写（见下方 Release 文案模板）                      ║
// ║ • 附件：yiyiaddon{版本}.jar（混淆版，必传）                        ║
// ║ • 附件：yiyiaddon{版本}-personal.jar（可选，内部测试用）           ║
// ║                                                                    ║
// ║ 【映射文件管理】                                                   ║
// ║ • 位置：build/obfuscation-mapping-v{版本}.txt                      ║
// ║ • 作用：崩溃日志复原（混淆类名 → 原始类名）                        ║
// ║ • 保存：复制到 Documents/26.1.2-mappings/ 按版本存档               ║
// ║ • 不要提交到 Git（.gitignore 已排除）                             ║
// ╠════════════════════════════════════════════════════════════════════╣
// ║                    Release 发布文案模板                            ║
// ╠════════════════════════════════════════════════════════════════════╣
// ║ ## yiyiaddon v{版本}                                               ║
// ║                                                                    ║
// ║ > 🧪 Beta 测试版，欢迎反馈 bug  ← beta 版加此行，正式版删掉        ║
// ║ > Minecraft 26.1.2 | Meteor Client 26.1.2-SNAPSHOT                 ║
// ║                                                                    ║
// ║ ---                                                                ║
// ║                                                                    ║
// ║ ### 📦 安装说明                                                     ║
// ║                                                                    ║
// ║ 1. 下载 `yiyiaddon{版本}.jar` 放入 `.minecraft/mods/` 文件夹      ║
// ║ 2. 启动游戏，按 Right Shift 打开 Meteor 菜单                       ║
// ║ 3. 在 `yiyiaddon 工具` 分类中找到所有模块                          ║
// ║ 4. Baritone 已内置，无需额外安装                                   ║
// ║                                                                    ║
// ║ ---                                                                ║
// ║                                                                    ║
// ║ ### 🔥 新增/更新内容                                               ║
// ║                                                                    ║
// ║ **新增模块：{模块名}**                                             ║
// ║ {一句话功能描述}                                                   ║
// ║                                                                    ║
// ║ **核心功能**                                                       ║
// ║ - 🔄 **特性一**：说明                                              ║
// ║ - ⚡ **特性二**：说明                                              ║
// ║ - 🔧 **特性三**：说明                                              ║
// ║                                                                    ║
// ║ **指令**（如有）                                                   ║
// ║ ```                                                                ║
// ║ .{指令名} {子命令}   说明                                          ║
// ║ ```                                                                ║
// ║                                                                    ║
// ║ ---                                                                ║
// ║                                                                    ║
// ║ ### 🔧 修复与优化                                                   ║
// ║                                                                    ║
// ║ - 修复内容一                                                       ║
// ║ - 修复内容二                                                       ║
// ║                                                                    ║
// ║ ---                                                                ║
// ║                                                                    ║
// ║ ## 环境要求                                                        ║
// ║                                                                    ║
// ║ | 依赖 | 版本 |                                                    ║
// ║ |------|------|                                                   ║
// ║ | Minecraft | 26.1.2 |                                             ║
// ║ | Fabric Loader | 0.19.3 |                                         ║
// ║ | Meteor Client | 26.1.2-SNAPSHOT |                                ║
// ║ | Java | 25 |                                                      ║
// ║                                                                    ║
// ║ 💬 Discord：https://discord.gg/vwrRCtET                            ║
// ║ 🔗 GitHub：https://github.com/fxjcangku/26.1.2                     ║
// ║                                                                    ║
// ║ emoji 选色参考：                                                   ║
// ║ 🌾🌱🟫🚿 → 农业/浇水类                                              ║
// ║ ⚔️💎🔴💥 → 战斗/PVP 类                                              ║
// ║ 🎒📦🗃️ → 背包/物品类                                                ║
// ║ 🔍📌🗺️ → 辅助/嗅探/导航类                                          ║
// ║ 🔧⚙️ → 底层修复/工具类                                             ║
// ╚════════════════════════════════════════════════════════════════════╝
