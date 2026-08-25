
package com.example.addon.core;

import com.example.addon.translations.YiyiaddonTranslator;
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
// 【用户工作偏好与运行时缺陷排查协议】
// - 触发条件：仅在用户明确给出 Bug、目标项目目录、受影响模块/功能和可复现操作后启动排查；未满足时不得预建监听、调试文档或业务修复。
// - 项目隔离：当前任务只允许读取、修改、构建和收集用户指定项目内的文件；严禁跨项目引用源码、JAR、配置、会话或历史日志作为结论依据。
// - 会话创建：每个独立 Bug 必须在项目根目录创建 `debug-{问题描述}.md`，在 `.dbg/` 创建唯一 `{session-id}.env`；一个 session 只服务一个问题与一轮验证。
// - 调试文档：`debug-{问题描述}.md` 必须包含 Status、Issue、影响范围、复现前提与步骤、可证伪假设、埋点位置、预期事件、证据时间线、根因、最小修复和复测结果；证据缺失时明确标注未确认。
// - 监听配置：`.dbg/{session-id}.env` 固定包含 `DEBUG_SERVER_URL=http://127.0.0.1:7777/event` 与唯一 `DEBUG_SESSION_ID={session-id}`；日志唯一目标为 `.dbg/trae-debug-log-{session-id}.ndjson`。
// - 路径原则：运行时读取调试配置与输出路径必须指向明确的当前项目绝对路径；不得依赖 Minecraft、IDE 或启动器的工作目录相对路径。
// - 埋点原则：只记录与当前假设直接相关的用户操作、配置原始值/生效值、状态转换、关键分支、调用入口、异常和时间戳；不改动无关业务逻辑，不用泛化日志替代关键证据。
// - 收集校验：用户复现前必须启动并验证 `127.0.0.1:7777` 可接收事件；若无 NDJSON，先定位监听服务、配置路径、运行 JAR 与 HTTP 投递链路，禁止直接推断业务根因。
// - 证据驱动：用户说“复现了”后，只读取该 session 最新 NDJSON，按时间线还原操作与状态；旧 session、其他问题、其他项目或旧版本日志均不可作为当前结论。
// - 修复与验证：仅在日志足以证实根因后实施最小业务修复；构建前核实当前项目 Gradle 任务、JDK、版本和真实输出名，复测后以新 session 日志确认结果；用户确认解决后将文档状态标记为 CLOSED.
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
// 【单人世界自动禁用规范】（绕过模块专用）
// - 对于 CATEGORY_TACTICAL 分类下的绕过模块，单人世界无需这些功能
// - onActivate() 中检测单人世界时的标准处理：
//   ```java
//   if (mc.hasSingleplayerServer()) {
//       chatFeedback = false;  // 禁用开关消息
//       toggle();              // 关闭模块
//       chatFeedback = true;   // 恢复开关消息
//       warning("§c单人世界无需XXX");  // 只显示一次警告
//       return;
//   }
//   ```
// - 这样做的好处：
//   · 避免显示两次消息（开关消息 + 警告消息）
//   · 只在单人世界生效，多人服务器正常显示开关消息
//   · 用户体验更简洁清晰
//
// 【启动自检失败处理规范】（自动化模块专用）
// - 对于需要预配置的自动化模块（如自动挖矿、自动农场）
// - onActivate() 中自检失败时的标准处理：
//   ```java
//   String error = selfCheck();
//   if (error != null) {
//       chatFeedback = false;  // 禁用开关消息
//       if (isActive()) toggle();  // 关闭模块
//       chatFeedback = true;   // 恢复开关消息
//       notifyError("启动失败：" + error);  // 只显示错误信息
//       return;
//   }
//   ```
// - 这样做的好处：
//   · 避免显示两次消息（开关消息 + 错误消息）
//   · 用户只看到具体的错误原因，更清晰
//   · 代码逻辑统一，易于维护
//
// 【运行时调试规范】
// - 遇到需要收集运行时证据的 Bug 时，使用上方统一协议，并按以下模板执行：
//
// 1. **创建调试记录文件**
//    位置：项目根目录 `debug-{问题描述}.md`
//    内容至少包含：Status、Issue、症状、可验证假设、复现步骤、监听点、证据、结论、修复和验证结果。
//
// 2. **选择调试方式**
//    - 轻量级：使用游戏内 `info()` 日志和截图，适用于简单、单次复现的问题。
//    - 深度调试：使用 Debug Server 和 NDJSON，适用于复杂问题、需要多次复现或需要记录大量状态的问题。
//
// 3. **深度调试环境配置**
//    在当前项目 `.dbg/{session-id}.env` 中写入：
//    ```
//    DEBUG_SERVER_URL=http://127.0.0.1:7777/event
//    DEBUG_SESSION_ID={session-id}
//    ```
//    日志写入 `.dbg/trae-debug-log-{session-id}.ndjson`。
//
// 4. **添加调试日志**
//    只在当前 Bug 的关键路径记录：方法调用时机、关键变量、条件判断、状态转换、调用入口和必要的调用栈。
//    深度调试通过 HTTP POST 上报事件；事件必须包含 session、假设标识、位置、数据和时间戳。
//
// 5. **构建并复现问题**
//    构建当前项目实际配置的测试版本，启动监听服务并确认端口可接收事件后，再让用户复现。
//
// 6. **分析证据并更新调试文件**
//    根据对应 session 的 NDJSON 按时间线记录关键日志，标记假设为确认或排除；没有证据的结论必须保持未确认。
//
// 7. **修复并验证**
//    仅实施已被日志证明的最小修复；重新构建并使用新的验证 session 复现，确认修复结果后将调试文档标记为 CLOSED。
//
// 8. **清理**
//    调试文档保留用于历史回溯；`.dbg/*.env` 和 `.dbg/*.ndjson` 不提交 Git；不删除与当前或历史问题相关的证据文件。
//
// 调试流程统一遵循上方《用户工作偏好与运行时缺陷排查协议》；本节是具体执行模板。
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
            // 确保使用翻译后的标题（如果翻译已启用，title 字段已经被翻译过了）
            String status = isActive() ? "§a§l已开启" : "§c§l已关闭";
            // 直接发送，不经过 notify()，这样单人世界也能看到
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
     * 格式：§c§l[yiyiaddon]§r§f§l[模块名]§r内容
     */
    public static String formatMessage(String moduleName, String message) {
        String cleanModuleName = stripColorCodes(stripPrefix(moduleName));
        String cleanMessage = stripPrefix(message);
        return "§c§l[yiyiaddon]§r§f§l[" + cleanModuleName + "]§r" + cleanMessage;
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
// ║ 【代码目录结构规范】                                               ║
// ║ 每次新增插件模块后，整理代码目录结构：                             ║
// ║                                                                    ║
// ║ com.example.addon/                                                 ║
// ║ ├─ core/          核心基础类（AddonTemplate、YiyiaddonModule 等）  ║
// ║ ├─ translations/  翻译引擎（*Translations、Translator）            ║
// ║ ├─ utils/         工具类（Watermark、WelcomeService）              ║
// ║ ├─ commands/      指令类（*Command）                              ║
// ║ ├─ modules/       模块类（*Module）                               ║
// ║ ├─ mixin/         Mixin 注入（*Mixin、*Access）                   ║
// ║ ├─ hud/           HUD 组件（*Hud）                                ║
// ║ └─ farm/          农场系统（Scanner、Nav、Renderer 等）            ║
// ║                                                                    ║
// ║ 整理步骤：                                                         ║
// ║ 1. 新建子目录（如有新分类需求）                                   ║
// ║ 2. 使用 git mv 移动文件到对应目录                                 ║
// ║ 3. 更新文件的 package 声明                                        ║
// ║ 4. 批量更新所有文件的 import 语句                                 ║
// ║ 5. 修复 Access 接口等特殊引用                                     ║
// ║ 6. 编译测试：gradlew buildPersonal                                ║
// ║ 7. 提交到 source 分支                                             ║
// ║                                                                    ║
// ║ 反编译防护：                                                       ║
// ║ • 清晰的目录结构不会增加反编译风险                                 ║
// ║ • ProGuard 混淆会重命名所有类名和包名                              ║
// ║ • 结构化组织提升开发效率，发布时仍然完全混淆                       ║
// ║ • 映射文件妥善保存，可以将混淆类名还原                             ║
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
