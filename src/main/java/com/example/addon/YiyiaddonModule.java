package com.example.addon;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

// ╔════════════════════════════════════════════════════════════════════╗
// ║                    构建与发布防错规则（必读）                      ║
// ╠════════════════════════════════════════════════════════════════════╣
// ║ 开发客户端：gradlew runClient                                     ║
// ║ 个人测试版：gradlew buildPersonal                                 ║
// ║ 输出：build/libs/yiyiaddon1.0-personal.jar（未混淆）               ║
// ║ 官方发布版：gradlew buildOfficial                                 ║
// ║ 输出：build/libs/yiyiaddon1.0.jar（混淆版）                        ║
// ║ gradlew build 只刷新 Personal JAR，不代表 Official 已重新生成      ║
// ║ 每次代码变更后必须重新构建所需类型，禁止复用旧 JAR                 ║
// ║ 构建后核对：类型、文件名、修改时间、大小、SHA-256                  ║
// ║ Personal 与 Official 严禁混用，公开发布只使用 Official             ║
// ║ 构建与部署相互独立；只要求构建时不得修改游戏 mods 目录             ║
// ║ 部署前核对目标实例、实际加载 JAR，并排查重复 JAR                   ║
// ║ 未经明确允许，不得替换、禁用、恢复或重命名实例中的模组             ║
// ║ 发布顺序：私人仓库同步源码和 Personal，再由同一源码构建 Official   ║
// ║ 环境：MC 26.1.2 / Fabric 0.19.3 / Meteor 26.1.2-42 / Java 25       ║
// ║ Baritone 已内嵌，不得额外安装 Baritone JAR                         ║
// ╚════════════════════════════════════════════════════════════════════╝

/**
 * 所有 yiyiaddon 模块的基类。
 * 项目统一提示规范：
 * · [yiyiaddon] 使用红色加粗
 * · [插件名] 使用白色加粗
 * · 普通正文使用白色加粗，成功使用绿色加粗，失败使用红色加粗
 * · 所有模块消息统一由本类输出，不允许出现 [Meteor] 前缀
 * · 模块开关、按键绑定、普通信息、警告和错误均遵循此格式
 */
public abstract class YiyiaddonModule extends Module {

    protected YiyiaddonModule(Category category, String name, String description) {
        super(category, name, description);
    }

    @Override
    public void toggle() {
        super.toggle();
        // 覆盖 GUI 点击路径：Meteor 的 toggle() 不会调用 sendToggledMsg()，只有按键绑定才会
        // 所以在这里统一输出提示，并将 sendToggledMsg() 置为空方法防止按键绑定双重提示
        if (mc.player != null && chatFeedback) {
            String status = isActive() ? "§a§l已启动" : "§c§l已关闭";
            mc.player.sendSystemMessage(Component.literal(formatMessage(title, status)));
        }
    }

    @Override
    public void sendToggledMsg() {
        // 空实现：提示已在 toggle() 中输出，此处留空防止按键绑定路径产生双重提示
    }

    @Override
    public void info(String message, Object... args) {
        if ("Removed bind.".equals(message)) {
            notify("已移除按键绑定");
            return;
        }
        // "Bound to (highlight)%s(default)." 按键绑定成功消息
        if (message != null && message.startsWith("Bound to")) {
            // 先展开 %s 等格式参数，再提取按键名
            String expanded = formatArgs(message, args);
            String clean = expanded.replaceAll("\\(highlight\\)|\\(default\\)", "").trim();
            // "Bound to Q." → "已绑定按键：Q"
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

    /**
     * 向玩家发送统一格式的公屏通知。
     * 格式：§c§l[yiyiaddon] §r§f§l[插件名] §r§f§l消息
     *
     * @param message 消息内容（不含前缀）
     */
    protected void notify(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§f" + message)));
    }

    /**
     * 向玩家发送橙色加粗错误通知，与红色 [yiyiaddon] 前缀形成视觉区分。
     * 格式：§c§l[yiyiaddon] §r§f§l[插件名] §r§6§l错误消息
     *
     * @param message 错误内容（不含前缀）
     */
    protected void notifyError(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§6§l" + message)));
    }

    protected String highlightText(String text) {
        return "§a§l" + text + "§r§f§l";
    }

    protected String highlightServer(String text) {
        return "§6§l" + text + "§r§f§l";
    }

    protected String highlightLocation(String text) {
        return "§d§l" + text + "§r§f§l";
    }

    protected String highlightCommand(String text) {
        return "§e§l" + text + "§r§f§l";
    }

    public static String formatMessage(String moduleName, String message) {
        // 去掉模块名中的颜色代码，保证 [ 和 ] 与模块名同色（§f§l 白色粗体）
        String cleanModuleName = stripColorCodes(stripPrefix(moduleName));
        String cleanMessage = stripPrefix(message);
        return "§c§l[yiyiaddon] §r§f§l[" + cleanModuleName + "] §r" + cleanMessage;
    }

    private static String stripPrefix(String value) {
        // 去除可能重复出现的 [yiyiaddon] 前缀；其余模块名括号由调用方保证不重复传入
        return value
            .replace("[yiyiaddon]", "")
            .trim();
    }

    /** 去除 Minecraft §x 格式化代码 */
    private static String stripColorCodes(String value) {
        return value.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private static String formatArgs(String message, Object... args) {
        if (args == null || args.length == 0) return message;
        try {
            return String.format(message, args);
        } catch (Exception ignored) {
            return message;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ▌ 个人习惯规范（所有模块必须遵守）
    // ══════════════════════════════════════════════════════════════════
    //
    //  【0】运行时调试工作流（TRAE-debugger）
    //  ─────────────────────────────────────────────────────────────────
    //  遇到无法靠静态分析定位的 bug，走以下流程：
    //
    //  Step 1 描述症状给 AI
    //    · 实际现象 vs 期望现象
    //    · 复现步骤（越精确越好）
    //    · 已知线索（截图/日志/调用栈）
    //
    //  Step 2 AI 启动本地调试服务器
    //    python3 <TRAE-debugger>/tools/debug-server/python/debug-server.py
    //        --session <sessionId> --outdir .dbg --clean --idle 1200
    //    · sessionId 格式：小写英文 + 连字符，2~4 词（如 multiplayer-translation-init）
    //    · 服务器自动写出 .dbg/<sessionId>.env（含端口和 sessionId）
    //
    //  Step 3 AI 在相关模块插入埋点（Java 一行式，HTTP POST 到服务器）
    //    埋点格式（写在关键位置，用 // #region / // #endregion 包裹）：
    //
    //    // #region debug-point A:check-state
    //    new Thread(() -> { try { var c = new java.net.URL("http://127.0.0.1:7777/event").openConnection(); ((java.net.HttpURLConnection)c).setRequestMethod("POST"); ((java.net.HttpURLConnection)c).setDoOutput(true); ((java.net.HttpURLConnection)c).setRequestProperty("Content-Type","application/json"); String body="{\"sessionId\":\"<sid>\",\"runId\":\"pre-fix\",\"hypothesisId\":\"A\",\"location\":\"ClassName:line\",\"msg\":\"[DEBUG] <描述>\",\"ts\":"+System.currentTimeMillis()+"}"; ((java.net.HttpURLConnection)c).getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); ((java.net.HttpURLConnection)c).getResponseCode(); } catch(Exception ignored){} }).start();
    //    // #endregion
    //
    //    · hypothesisId 对应假设编号 A/B/C/D/E
    //    · runId：埋点阶段用 "pre-fix"，修复后验证用 "post-fix"
    //    · 每次只改埋点代码，不动业务逻辑
    //
    //  Step 4 构建 jar，进游戏复现 bug
    //    gradlew buildPersonal  → 把 jar 放入 .minecraft/mods/
    //
    //  Step 5 AI 读日志分析
    //    · 日志文件：.dbg/trae-debug-log-<sessionId>.ndjson
    //    · AI 通过服务器 GET /logs 或直接读文件，逐条比对假设
    //
    //  Step 6 AI 给出最小范围修复
    //    · 有证据才动业务代码，不猜测修复
    //
    //  Step 7 重新构建，进游戏验证（post-fix 日志对比）
    //
    //  Step 8 确认修复后 AI 清理埋点 + 删除 .dbg/ 调试文件
    //
    //  调试文件命名约定：
    //    debug-<sessionId>.md              ← 调试任务文档（症状/假设/证据/状态）
    //    .dbg/<sessionId>.env              ← 服务器地址和 sessionId
    //    .dbg/trae-debug-log-<sessionId>.ndjson  ← 运行时埋点日志
    //
    // ──────────────────────────────────────────────────────────────────
    //
    //  【1】构造函数描述格式
    //  ─────────────────────────────────────────────────────────────────
    //  super(AddonTemplate.XXX, "模块名",
    //      "[一句话功能摘要，逗号分隔各能力]。详细参考下面使用说明。");
    //
    //  · 末尾必须以"。详细参考下面使用说明。"结尾（固定话术，不得更改）
    //  · 摘要尽量控制在 25 汉字以内，用顿号或逗号并列
    //  · 不写"支持 XXX"或"提供 XXX"等多余前缀，直接写功能
    //
    //  示例：
    //    "自动催熟农作物，补骨粉，ESP高亮，视角静默同步。详细参考下面使用说明。"
    //    "创造模式飞行手感，隐藏飞行能力，定时假落地包绕过飞行检测。详细参考下面使用说明。"
    //
    // ──────────────────────────────────────────────────────────────────
    //
    //  【2】getWidget 说明面板结构
    //  ─────────────────────────────────────────────────────────────────
    //  · 必须调用 buildInfoWidget(theme, sections...)，不允许手写 WTable
    //  · 第一个 String[] 是大标题，固定格式："§l模块名 · 使用说明"
    //  · 后续每个 String[] 是一个色块段落，第一行是段落标题，后续是正文
    //
    //  段落标题颜色约定（固定色，不得随意更改）：
    //    §e§l▌  准备 / 使用方法（黄色）
    //    §a§l▌  功能 / 原理 / 流程（绿色）
    //    §b§l▌  参数建议 / 说明（青色）
    //    §d§l▌  模式说明 / 提示（粉色）
    //    §c§l▌  注意 / 警告（红色）
    //
    //  正文行缩进约定：
    //    有序步骤  → "§f  1. 内容"（两空格 + 数字点）
    //    无序条目  → "§f  · 内容"（两空格 + 中间点）
    //    续行缩进  → "§f    内容"（四空格，与上一行对齐）
    //
    //  目标子服 → highlightText("目标子服")（亮绿色粗体）
    //  登录服/服务器名 → highlightServer("服务器")（金色粗体）
    //  主城/地点 → highlightLocation("地点")（紫粉色粗体）
    //  指令 → highlightCommand("/指令")（黄色粗体）
    //
    //  【3】仅服务器可用的模块（绕过类、防踢类）
    //  ─────────────────────────────────────────────────────────────────
    //  在 onActivate() 最开头加单人世界守卫：
    //
    //    if (mc.isInSingleplayer()) {
    //        notify("§c本模块仅在服务器中生效，单人世界已自动关闭。");
    //        toggle();
    //        return;
    //    }
    //
    // ──────────────────────────────────────────────────────────────────
    //
    //  【4】反编译声明 / 发布 / 上传仓库约定
    //  ─────────────────────────────────────────────────────────────────
    //  反编译声明：本项目源码仅供学习、调试与个人维护使用，公开发布版本请使用混淆构建。
    //  个人版保留完整符号，便于排查问题；公开版执行混淆与裁剪，以降低反编译可读性。
    //
    //  私人仓库  https://github.com/fxjcangku/26.1.2
    //  · 构建任务：gradlew buildPersonal  →  输出未混淆 Personal JAR
    //  · 上传内容：源码 + Personal JAR（保留完整符号，方便自查调试）
    //  · 用途：自用存档、断点还原、跨设备同步
    //
    //  公开仓库  https://github.com/fxjcangku/26.1.2
    //  · 构建任务：gradlew buildOfficial  →  输出混淆 Official JAR
    //  · 上传内容：仅 Official JAR（已加密混淆，不含源码，对外发布）
    //  · Release 标签命名：v<版本>-beta<n>  例：v1.3-beta3
    //  · 每次公开发版前必须先完成私人仓库提交，Official 构建基于同一源码快照
    //
    // ──────────────────────────────────────────────────────────────────
    //
    //  【5】Release 发布文案模板（参考历史版本风格，带 emoji）
    //  ─────────────────────────────────────────────────────────────────
    //
    //  ## yiyiaddon v<版本>
    //
    //  > 🧪 Beta 测试版，欢迎反馈 bug            ← beta 版加此行，正式版删掉
    //  > Minecraft 26.1.2 | Meteor Client 26.1.2-SNAPSHOT
    //
    //  ---
    //
    //  ### 📦 安装说明
    //
    //  1. 下载 `yiyiaddon1.0.jar` 放入 `.minecraft/mods/` 文件夹
    //  2. 启动游戏，按 Right Shift 打开 Meteor 菜单，找到 yiyiaddon 分类即可使用
    //  3. Baritone 已内置在插件中，使用自动导路功能无需额外安装
    //  4. 个人测试版文件名为 `yiyiaddon1.0-personal.jar`
    //
    //  ---
    //
    //  ### 🔥 新增：<模块名> — <一句话简介>
    //
    //  <两行功能摘要>
    //
    //  **核心功能**
    //
    //  - 🔄 **特性一**：说明
    //  - ⚡ **特性二**：说明
    //  - 🔧 **特性三**：说明
    //
    //  **指令**（如有）
    //
    //  ```
    //  .<指令名> <子命令>   说明
    //  ```
    //
    //  ---
    //
    //  ### 🔧 底层修复（如有）
    //
    //  - 修复内容一
    //  - 修复内容二
    //
    //  ---
    //
    //  ## 环境
    //
    //  | 依赖 | 版本 |
    //  |------|------|
    //  | Minecraft | 26.1.2 |
    //  | Fabric Loader | 0.19.3 |
    //  | Meteor Client | 26.1.2-SNAPSHOT |
    //  | Java | 25 |
    //
    //  💬 Discord：https://discord.gg/vwrRCtET
    //  🔗 GitHub：https://github.com/fxjcangku/26.1.2
    //
    //  ─────────────────────────────────────────────────────────────────
    //  注：emoji 选色约定
    //    🌾/🌱/🟫/🚿  → 农业/浇水类
    //    ⚔️/💎/🔴/💥  → 战斗/PVP 类
    //    🎒/📦/🗃️      → 背包/物品类
    //    🔍/📌/🗺️      → 辅助/嗅探/导航类
    //    🔧/⚙️          → 底层修复/工具类
    //
    //  附件上传顺序（用户看到的顺序）：
    //    1. yiyiaddon1.0.jar         ← 官方混淆版，必传
    //    2. yiyiaddon1.0-personal.jar ← 个人测试版，按需上传
    //
    // ══════════════════════════════════════════════════════════════════
    //  buildInfoWidget 示例（新模块直接复制并修改内容）
    //  ─────────────────────────────────────────────────────────────────
    //  @Override
    //  public WWidget getWidget(GuiTheme theme) {
    //      return buildInfoWidget(theme,
    //          new String[]{ "§l模块名 · 使用说明" },
    //          new String[]{
    //              "§e§l▌ 准备",
    //              "§f  1. 第一步",
    //              "§f  2. 第二步"
    //          },
    //          new String[]{
    //              "§a§l▌ 功能原理",
    //              "§f  · 特性一",
    //              "§f  · 特性二"
    //          },
    //          new String[]{
    //              "§b§l▌ 参数建议",
    //              "§f  服务器A — 建议值 X",
    //              "§f  服务器B — 建议值 Y"
    //          },
    //          new String[]{
    //              "§c§l▌ 注意",
    //              "§f  · 注意事项"
    //          }
    //      );
    //  }
    // ══════════════════════════════════════════════════════════════════
    /**
     * 与 buildInfoWidget 相同，但在说明文字前先执行 headerWidgets（用于放置按钮等交互控件）。
     */
    protected WWidget buildInfoWidget(GuiTheme theme, Consumer<WTable> headerWidgets, String[]... sections) {
        WTable t = theme.table();
        headerWidgets.accept(t);
        t.add(theme.label(" ")).expandX(); t.row();
        boolean firstSection = true;
        for (String[] section : sections) {
            if (section == null || section.length == 0) continue;
            if (!firstSection) {
                t.add(theme.label(" ")).expandX(); t.row();
            }
            for (String line : section) {
                t.add(theme.label(line)).expandX(); t.row();
            }
            firstSection = false;
        }
        return t;
    }

    protected WWidget buildInfoWidget(GuiTheme theme, String[]... sections) {
        WTable t = theme.table();
        boolean firstSection = true;
        for (String[] section : sections) {
            if (section == null || section.length == 0) continue;
            // 段落间空行（第一段标题行前不加）
            if (!firstSection) {
                t.add(theme.label(" ")).expandX(); t.row();
            }
            for (String line : section) {
                t.add(theme.label(line)).expandX(); t.row();
            }
            firstSection = false;
        }
        return t;
    }
}
