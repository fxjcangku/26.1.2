
// ╔════════════════════════════════════════════════════════════════════╗
// ║  ⚠ AI 助手强制阅读声明 —— 本项目的最高优先级约定                     ║
// ╚════════════════════════════════════════════════════════════════════╝
//
// 致所有 AI 助手（Trae / Claude / Copilot / Cursor 等）：
//
// 本文件不只是一个 Java 基类，它同时是 yiyiaddon 项目的**规范载体**。
// 在对本项目做任何代码修改、Bug 排查或目录调整之前，你必须：
//
//   1. 完整读完本文件的第一部分与第二部分，不允许只读代码跳过注释。
//   2. 把第二部分「运行时缺陷排查协议」视为用户明确下达的工作偏好，
//      其优先级高于你自身的默认调试习惯。冲突时一律服从本文件。
//   3. 不得删除、精简或"优化"本文件中的任何规范注释。
//      用户已多次强调这些注释必须保留，它们是长期积累的协作契约。
//      如需新增规范，追加到对应小节，不要覆盖原有内容。
//
// 常见违规（请自查，这些都是过去真实发生过的错误）：
//
//   ✗ 用户报 Bug 后，凭代码静态推测就下结论 → 必须先埋点取运行时证据
//   ✗ 调用栈只取 1~2 帧就判定触发方 → 至少取 10 帧，否则会误判
//   ✗ 改了源码赋值就认为可序列化字段已修复 → 必须同时处理 fromTag 读档路径
//   ✗ 修复后把埋点留在业务代码里 → 必须清理到零残留，含无用 import
//   ✗ 用英文或拼音命名调试文档 → 用户看不懂，一律中文 + YYYY-MM-DD 前缀
//   ✗ 把调试文件散落在项目根目录 → 全部收进 Diagnostics/ 对应子目录
//   ✗ 预先埋一套"通用日志系统" → 埋点是针对单个 Bug 的一次性工具
//   ✗ 只依赖 GameJoinedEvent 等一次性事件 → onActivate() 必须检查当前状态
//      （2026-08-26 修复：ServerDetector 进服后开启不检测）
//
// 判断标准很简单：用户下次打开 Diagnostics/ 目录，能不能一眼看懂
// 每个文件是什么、什么时候修的、修的什么问题。看不懂就是你没做对。
//
// 详细条款见下方第二部分（2.1 ~ 2.5）。
//
// ──────────────────────────────────────────────────────────────────────
// 用户对话偏好（2026-08-26 更新）
// ──────────────────────────────────────────────────────────────────────
//
// AI 输出要求（重要性降序）：
//   1. 只说关键信息，一句话能说清的不写两句
//   2. 直接输出结论和方案，不要解释过程和寒暄
//   3. 代码必须高质量可运行，不输出示例代码或伪代码
//   4. 报错时只输出"问题+原因+修复方法"，不解释为什么会报错
//   5. 每次回复控制长度，分多次输出重点内容，别一次性发一大段
//   6. 铁律：不要废话不寒暄只写代码，除非用户主动提问或有重大问题，其他时间一律不能插嘴不能废话
//   7. 输出高级专业的代码，代码质量要达到生产级别
//   8. 禁止在任何文本输出、注释、文档中使用 emoji 表情符号
//
// 代码规范（强制执行）：
//   所有注释必须使用中文，禁止英文注释（用户看不懂）
//
// UI/消息颜色规范（2026-08-27 更新）：
//   - 启动/成功/绑定：绿色 §a§l✓
//   - 关闭/失败/删除：红色 §c§l✗
//   - 坐标格式：XYZ标签 §7灰色 + 数值 §f白色
//   - 维度信息：根据点位类型使用对应颜色
//   - 分隔符：§8▸ 深灰色箭头
//
// 使用说明窗口规范（2026-08-27 新增）：
//   - 所有模块的使用说明统一使用HelpScreen独立窗口显示
//   - 模块描述文字统一改为"...点击按钮查看说明。"
//   - 设置面板顶部必须添加"查看使用说明"按钮，点击打开独立窗口
//   - 按钮颜色：§e黄色（显眼，方便用户点击查看）
//   - 颜色方案：黑客终端风格
//     § §3青色 - 标题和章节标识 [#]
//     § §b亮青色 - 副标题和重点强调
//     § §f白色 - 正文内容
//     § §7灰色 - 补充说明和括号内提示
//     § §8深灰色 - 树形结构线条 ├─ └─ >
//     § §e黄色 - 方式标识、数值
//     § §a绿色 - 流程步骤 [1][2][3]
//     § §6金色 - 参数建议 ▸
//     § §d粉色 - 可选功能
//     § §c红色 - 注意事项 ⚠
//   - 章节结构：使用 [#] 标识，内容使用树形结构 ├─ └─
//   - 指令展示：使用 > 提示符，青色显示指令，灰色显示说明
//   - 禁止使用emoji，使用Unicode符号代替（▸ ⚠ 等）
//
//   技术术语可保留英文（Paper/Spigot/Baritone/Windows/API 等）
//   所有代码、脚本、状态机必须写中文注释（类/方法/关键逻辑）
//   UI 元素（HUD、聊天消息、设置面板）必须使用中文
//   禁止使用 emoji（Minecraft 字体渲染器不支持，会导致后续文字无法显示）
//   写代码或改代码修bug时：代码排版整齐，缩进统一，逻辑块之间保持空行分隔，必须带中文注释
//   ✓ 不凭记忆写 API，有疑问先用 `node Mappings\工具\查JARAPI.js` 查询
//   ✓ 26.1.2 使用官方非混淆命名（Identifier/Minecraft/Component/Level）
//
// 注释原则：
//   - 写"为什么这样做"，不写"做了什么"
//   - 标注风险点、兼容性、性能影响
//   - 状态机/异步/发包必须注释意图
//   - 拒绝废话注释（如"获取XXX""设置XXX"）
//
// 后台 API 配置（2026-08-26 新增）：
//   ✓ 用户统计服务部署在 Cloudflare Workers：https://yiyiaddon-stats.fxjggyx.workers.dev
//   ✓ 注册端点：POST /api/register  参数：uuid, name, version, minecraft_version
//   ✓ 查询端点：GET  /api/stats     返回：total_users, recent_users
//   ✓ 管理端点：GET  /api/users?key=xxx  需要 ADMIN_KEY 鉴权
//   ✓ Workers 源码位于 backend/ 目录（worker.js + wrangler.toml）
//   ✓ D1 数据库表结构：users(uuid, name, version, minecraft_version, first_seen, last_seen)
//   ✓ 客户端实现：YiyiaddonWelcomeService.java 在进入世界时自动上报
//   ✓ 配置常量：AddonTemplate.STATS_API_URL 集中管理后台地址
//
// 启动播报规范（2026-08-26 新增）：
//   ✓ 所有需要配置的模块（自动挖矿/自动农场等）必须在 onActivate() 里播报关键配置
//   ✓ 只报影响本次运行的关键项，不把整个设置面板念一遍，避免聊天栏刷屏
//   ✓ 播报内容包括：目标选择、模式选择、触发阈值、维度检查（若适用）
//   ✓ 用 highlightText/Server/Location/Command 强调关键值，让用户一眼确认配置
//   ✓ 维度不匹配时用 notifyError() 警告用户（如主世界矿跑下界、下界矿跑主世界）
//   ✓ 把播报逻辑抽成 reportStartupInfo() 私有方法，保持 onActivate() 清晰
//   ✓ 参考：AutoMinerModule.reportStartupInfo() 和 AutoFarmMatrix.reportStartupInfo()
//
// 点位绑定覆盖保护规范（2026-08-26 新增）：
//   ✓ 所有点位绑定操作（箱子/坐标/站位）必须加覆盖保护：已有绑定不允许直接覆盖
//   ✓ 用户尝试覆盖已有绑定时，必须拦截并提示删除指令（如 .wk remove 矿物箱）
//   ✓ 防止误操作：手滑点错按钮/输错指令不会把已经跑了一半的点位覆盖掉
//   ✓ 覆盖检测必须在执行绑定操作之前，检测到已有绑定立即返回失败
//   ✓ GUI 按钮调用时返回 false 触发 screen.close()，指令调用时显示错误提示
//   ✓ 适用范围：自动挖矿（矿物箱/食物箱/挂机修复点）、自动农场（起点/终点/卸货箱/补货箱）
//   ✓ 参考实现：WKCommand.bindMineralChest/bindFoodChest/bindAFKPoint 的覆盖检测
//   ✓ 参考实现：NongChangCommand.bind() 方法内的 module.getSite(type) != null 检测


package com.example.addon.core;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

// ╔════════════════════════════════════════════════════════════════════╗
// ║                    YiyiaddonModule 基类                            ║
// ║                所有 yiyiaddon 模块的统一基类                        ║
// ╚════════════════════════════════════════════════════════════════════╝
//
// ┌─ 本文件导航 ───────────────────────────────────────────────────────┐
// │ 文件顶部  AI 助手强制阅读声明（在 package 之前，务必先看）          │
// │ 第一部分  模块开发规范                                             │
// │   1.1 核心功能        1.2 消息输出        1.3 说明面板             │
// │   1.4 单人世界自动禁用 1.5 启动自检失败                            │
// │ 第二部分  运行时缺陷排查协议（用户工作偏好，务必遵守）             │
// │   2.1 启动与边界      2.2 命名与目录      2.3 埋点与证据           │
// │   2.4 修复与结案      2.5 八步执行流程                             │
// │ 第三部分  代码实现（构造、读档修正、消息、面板、格式化）           │
// │ 文件末尾  仓库结构说明与开发规范快速参考                           │
// └────────────────────────────────────────────────────────────────────┘
//
// ════════════════════════════════════════════════════════════════════
//  第一部分 · 模块开发规范
// ════════════════════════════════════════════════════════════════════
//
// 【1.1 核心功能】
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
// 【1.3 说明面板规范】
// - 标题：§l模块名 · 使用说明
// - 段落标题色：§e§l▌准备  §a§l▌功能  §b§l▌参数  §d§l▌模式  §c§l▌注意
// - 正文缩进：§f  1. 步骤（有序）  §f  · 条目（无序）  §f    续行
//
// 【1.4 单人世界自动禁用规范】（绕过模块专用）
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
// 【1.5 启动自检失败处理规范】（自动化模块专用）
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
// ════════════════════════════════════════════════════════════════════
//  第二部分 · 运行时缺陷排查协议（用户工作偏好，务必遵守）
// ════════════════════════════════════════════════════════════════════
//
// 【2.1 启动与边界】
// - 触发条件：仅在用户明确给出 Bug、目标项目目录、受影响模块/功能和可复现操作后启动排查；
//   未满足时不得预建监听、调试文档或业务修复。
// - 项目隔离：只允许读取、修改、构建和收集用户指定项目内的文件；
//   严禁跨项目引用源码、JAR、配置、会话或历史日志作为结论依据。
// - 路径原则：运行时读取调试配置与输出路径必须指向当前项目的明确绝对路径；
//   不得依赖 Minecraft、IDE 或启动器的工作目录相对路径。
//
// 【2.2 命名与目录】
// - 命名规范：会话名、文档名、日志名、目录名一律使用中文，用户必须能直接看懂；
//   禁止英文缩写或拼音会话名。
// - 日期前缀：所有会话文档与 `.env` 必须带 `YYYY-MM-DD-` 前缀
//   （如 `2026-08-25-服务器检测-开启后自动关闭.md`），
//   使 `会话记录/已修复/` 天然按时间排序，直接充当修复与开发记录台账。
// - 目录规范：所有诊断产物统一收进 `Diagnostics/`，四个子目录职责固定，不得散落到项目根目录。
//     · `Diagnostics/工具/`             通用脚本（`调试监听服务.js`、`日志分析器.js`），可提交，跨会话复用
//     · `Diagnostics/会话记录/进行中/`  正在排查的会话文档 `{日期}-{中文会话名}.md`，可提交
//     · `Diagnostics/会话记录/已修复/`  已结案的会话文档，即历史修复台账，可提交
//     · `Diagnostics/运行日志/`         `.env` 与 `.ndjson` 运行时证据，`.gitignore` 排除，不提交
// - 会话创建：每个独立 Bug 建 `Diagnostics/会话记录/进行中/{日期}-{中文会话名}.md` 与
//   `Diagnostics/运行日志/{日期}-{中文会话名}.env`；一个会话只服务一个问题与一轮验证。
// - 监听配置：`.env` 固定包含 `DEBUG_SERVER_URL=http://127.0.0.1:7777/event`
//   与唯一 `DEBUG_SESSION_ID={日期}-{中文会话名}`；
//   日志唯一目标为 `Diagnostics/运行日志/调试日志-{会话名}.ndjson`。
//
// 【2.3 埋点与证据】
// - 按需埋点：不预埋通用日志系统。用户报出 Bug 后，针对该问题现场编写专用埋点
//   （选点、字段、调用栈深度都由当前假设推导）；埋点是一次性排查工具，不是长期基础设施。
// - 埋点原则：只记录与当前假设直接相关的用户操作、配置原始值/生效值、状态转换、
//   关键分支、调用入口、异常和时间戳；不改动无关业务逻辑，不用泛化日志替代关键证据。
// - 调用栈深度：定位「谁触发了这个行为」时必须取足够帧数（建议 10 帧），
//   只截到自身或 Meteor 第一层会导致误判（参见 2026-08-24 会话的失误记录）。
// - 收集校验：用户复现前必须启动并验证 `127.0.0.1:7777` 可接收事件；
//   若无 NDJSON，先定位监听服务、配置路径、运行 JAR 与 HTTP 投递链路，禁止直接推断业务根因。
// - 证据驱动：用户说「复现了」后，只读取该会话最新 NDJSON，按时间线还原操作与状态；
//   旧会话、其他问题、其他项目或旧版本日志均不可作为当前结论。
// - 调试文档：必须包含状态、问题、影响范围、复现前提与步骤、可证伪假设、埋点位置、
//   证据时间线、根因、最小修复和复测结果；证据缺失时明确标注未确认。
//
// 【2.4 修复与结案】
// - 修复与验证：仅在日志足以证实根因后实施最小业务修复；
//   构建前核实当前项目 Gradle 任务、JDK、版本和真实输出名。
// - 修复层级：修复落在根因所在层。基类问题不要只补子类；
//   涉及可序列化字段时，必须同时处理内存与存档（`fromTag`）两条路径。
// - 修复后清理：根因确认并修复后，必须删除本次会话在业务源码里加的全部埋点、辅助方法
//   和随之无用的 import，源码回到零调试残留状态；
//   只有会话文档与 `Diagnostics/工具/` 的通用脚本保留。
// - 结案归档：状态改为已修复 → 文档移入 `会话记录/已修复/` → 摘除源码埋点 → 重新构建确认无残留。
// - 历史复核：结案前回看同一模块的历史会话；若旧文档结论被新证据推翻，
//   要在新文档里写明它错在哪，并把旧文档标注为「结论被推翻」。
//
// 【2.5 八步执行流程】
//
// 0. **目录布局**（固定，勿变）
//    Diagnostics/
//    ├─ 工具/               调试监听服务.js —— 监听服务，跨会话复用，提交
//    ├─ 会话记录/
//    │  ├─ 进行中/          {日期}-{中文会话名}.md —— 正在排查，提交
//    │  └─ 已修复/          {日期}-{中文会话名}.md —— 已结案，即修复台账，提交
//    └─ 运行日志/           {日期}-{会话名}.env + 调试日志-{会话名}.ndjson —— 证据，不提交
//
// 1. **创建调试记录文件**
//    位置：`Diagnostics/会话记录/进行中/{日期}-{中文会话名}.md`
//    内容至少包含：状态、问题、症状、可证伪假设、复现步骤、埋点位置、证据时间线、根因、修复和复测结果。
//
// 2. **选择调试方式**
//    - 轻量级：使用游戏内 `info()` 日志和截图，适用于简单、单次复现的问题。
//    - 深度调试：使用监听服务和 NDJSON，适用于复杂问题、需要多次复现或需要记录大量状态的问题。
//
// 3. **深度调试环境配置**
//    在 `Diagnostics/运行日志/{日期}-{中文会话名}.env` 中写入：
//    ```
//    DEBUG_SERVER_URL=http://127.0.0.1:7777/event
//    DEBUG_SESSION_ID={日期}-{中文会话名}
//    ```
//    启动 `node Diagnostics/工具/调试监听服务.js`，日志自动写入 `Diagnostics/运行日志/调试日志-{会话名}.ndjson`。
//    服务附带 `GET /健康` 探活接口、控制台重复事件折叠与 Ctrl+C 统计摘要。
//
// 4. **按需编写针对性埋点**
//    只在当前 Bug 的关键路径记录：方法调用时机、关键变量、条件判断、状态转换、调用入口和必要的调用栈。
//    埋点必须用 `// #region debug-point {会话名}` 与 `// #endregion` 包裹，便于结案时精确摘除。
//    深度调试通过 HTTP POST 上报事件；事件必须包含会话名、假设标识、位置、数据和时间戳。
//    取证轮 runId 用 `probe-N`，修复复测轮用 `verify-N`，便于区分同一会话的前后两批证据。
//
// 5. **构建并复现问题**
//    构建当前项目实际配置的测试版本，启动监听服务并确认端口可接收事件后，再让用户复现。
//
// 6. **分析证据并更新调试文件**
//    先跑 `node Diagnostics/工具/日志分析器.js {会话名}`，不要手翻 NDJSON——
//    几分钟埋点就能产出上千行，肉眼看不出规律，靠印象下结论正是历史误判的来源。
//    分析器给出四类结论，各自对应不同判断：
//      · 时间线      间隔 ≥500ms 标「明显停顿」，指向卡住或等待超时的位置
//      · 调用栈聚合  同一埋点按直接调用方分组计数，是认定触发方的唯一可靠依据
//      · 状态迁移    检测 `A ⇄ B` 往复，状态机卡死的典型特征
//      · 节律分析    平均间隔 ≤60ms 说明是 tick 级刷屏，该收窄埋点条件重新取证
//    只读该会话的 NDJSON，按时间线记录关键日志，标记假设为确认或排除；没有证据的结论必须保持未确认。
//    调用栈证据优先：能指出「谁触发了这个行为」的帧，比一堆状态快照更有价值。
//    分析器常用参数：`--栈` 只看调用栈聚合，`--时间线 50` 调整条数，`--列表` 查看全部日志。
//
// 7. **修复并验证**
//    仅实施已被日志证明的最小修复；修复应落在根因所在层（基类问题不要只补子类）。
//    重新构建，埋点 runId 切到 `verify-N`，让用户复现验收。
//
// 8. **结案归档**
//    状态改为已修复 → 文档移入 `Diagnostics/会话记录/已修复/` → 摘除源码埋点与无用 import → 重新构建确认无残留。
//    `Diagnostics/运行日志/` 由 .gitignore 排除；历史会话文档不删除，按日期沉淀为修复与开发记录。
//    结案前必须回看同一模块的历史会话：若旧文档结论被新证据推翻，要在新文档里写明它错在哪。
//
// 调试流程统一遵循上方《用户工作偏好与运行时缺陷排查协议》；本节是具体执行模板。
//
// ════════════════════════════════════════════════════════════════════
//  第三部分 · 26.1.2 API 规范与开发模板（写代码前必读）
// ════════════════════════════════════════════════════════════════════
//
// 【3.1 版本事实】
//
//   对外版本号   26.1.2      新的 CalVer 规则：年.批次.修订
//   内部版本号   1.21.11     Loom 缓存、映射文件都用这个号
//   映射类型     Mojang 官方映射（不是 Yarn！）
//   是否混淆     否。26.1 起官方发布不混淆版本，官方名直接编译进 JAR
//   Fabric Loader 0.19.3     Meteor 26.1.2-SNAPSHOT     JDK 25
//
//   注意：`build.gradle.kts` 里没有 `mappings(loom.officialMojangMappings())`
//   是正确的，不是漏写。26.1 不混淆，不需要重映射步骤。不要"好心"补上这行。
//
// 【3.2 绝对禁止：用 Yarn 名或旧官方名】
//
//   26.1.2（1.21.11）Mojang 做了大规模改名，Yarn 映射同期退役。
//   网上大量教程和 AI 记忆里的类名在本版本已不存在，照抄直接编译失败。
//
//   以下写法一律编译不过（已用官方映射文件逐个核实）：
//
//     ✗ ResourceLocation          ✓ net.minecraft.resources.Identifier
//     ✗ MinecraftClient           ✓ net.minecraft.client.Minecraft
//     ✗ ClientPlayerEntity        ✓ net.minecraft.client.player.LocalPlayer
//     ✗ ClientWorld               ✓ net.minecraft.client.multiplayer.ClientLevel
//     ✗ PlayerEntity              ✓ net.minecraft.world.entity.player.Player
//     ✗ Text / MutableText        ✓ Component / MutableComponent
//     ✗ NbtCompound / NbtList     ✓ CompoundTag / ListTag
//     ✗ World / ServerWorld       ✓ Level / ServerLevel
//     ✗ Formatting                ✓ net.minecraft.ChatFormatting
//     ✗ Box                       ✓ net.minecraft.world.phys.AABB
//     ✗ Vec3d                     ✓ net.minecraft.world.phys.Vec3
//     ✗ Hand                      ✓ net.minecraft.world.InteractionHand
//     ✗ ActionResult              ✓ net.minecraft.world.InteractionResult
//     ✗ DrawContext               ✓ net.minecraft.client.gui.GuiGraphics
//     ✗ PlayerInventory           ✓ net.minecraft.world.entity.player.Inventory
//     ✗ ScreenHandler             ✓ net.minecraft.world.inventory.AbstractContainerMenu
//     ✗ HungerManager             ✓ net.minecraft.world.food.FoodData
//     ✗ RegistryKey               ✓ net.minecraft.resources.ResourceKey
//     ✗ DynamicRegistryManager    ✓ net.minecraft.core.RegistryAccess
//     ✗ StatusEffects             ✓ net.minecraft.world.effect.MobEffects
//     ✗ GameMode                  ✓ net.minecraft.world.level.GameType
//
//   方法层面同样有改动：
//
//     ✗ ResourceKey#location()             ✓ ResourceKey#identifier()
//     ✗ Level#random（字段）               ✓ Level#getRandom()（方法）
//     ✗ new ResourceLocation(ns, path)     ✓ Identifier.fromNamespaceAndPath(ns, path)
//
// 【3.3 不确定就查，禁止凭记忆猜】
//
//   项目内已建好映射速查体系（`Mappings/`，事实来源是官方映射原文件）：
//
//     node Mappings/工具/查API.js Identifier           查类的完整路径与全部方法
//     node Mappings/工具/查API.js LocalPlayer sendSys  在指定类里搜方法
//     node Mappings/工具/查API.js --找 sendCommand     不确定在哪个类时全局搜
//
//     Mappings/易错对照表-26.1.2.txt   39 个高频 API 新旧对照
//     Mappings/简名对照-26.1.2.txt     简名 → 完整包路径，写 import 时查
//     Mappings/分类速查/               按功能域分 15 类，带中文用途注释
//     Mappings/说明.md                 完整用法与版本事实
//
//   规则：写任何不在本项目现有代码里出现过的 net.minecraft API 之前，
//   先用 `查API.js` 确认它在 26.1.2 真实存在。查不到就是不存在，不要硬写。
//   clone 之后原文件需重建：powershell Mappings/工具/下载官方映射.ps1
//
//   映射只能回答「这个类/方法存在吗、签名是什么」。若还需要知道
//   「实际项目怎么组织这段代码」（Mixin 注入点、包拦截、容器交互等），
//   查 `Reference/`：里面放已完成 26.1.2 迁移的第三方项目源码，
//   当前是 JsMacros Reloaded v2.0.3。用法与索引见 Reference/README.md。
//
//   ⚠ Reference/ 的边界：只读写法与思路，禁止复制代码进本项目，
//   也禁止把第三方 JAR 嵌套打包进产物。原因有三：
//     · 许可 —— MPL-2.0 是弱传染性，复制文件会带上保留声明、标明修改、提供源码的义务
//     · 体积 —— 单个 JsMacros 就 29 MB，嵌套后产物膨胀几十倍，每次同步仓库都要推这一坨
//     · 冲突 —— 用户 mods 里若已装独立版，mod id 重复会让 Fabric 直接启动失败
//   需要联动第三方 mod 时，用 fabric.mod.json 的 `recommends` 声明软依赖 + 反射调用，
//   装了就启用增强功能，没装也不崩。不要用 `depends` 把它变成硬性前置。
//
// 【3.4 本项目已验证可用的常用 API】
//
//   这些都是当前源码里实际在跑的写法，可直接照抄：
//
//   客户端与玩家（基类已提供 protected 的 mc 字段，直接用）
//     mc.player                          LocalPlayer，用前必须判空
//     mc.level                           ClientLevel
//     mc.getConnection()                 ClientPacketListener
//     mc.gameMode                        MultiPlayerGameMode
//     mc.hasSingleplayerServer()         是否单人世界
//
//   聊天与消息
//     mc.player.sendSystemMessage(Component.literal("文本"))
//     mc.getConnection().sendCommand("指令不带斜杠")
//
//   玩家状态
//     mc.player.getMainHandItem()        ItemStack
//     mc.player.getOffhandItem()         ItemStack
//     mc.player.getInventory().getItem(i)
//     mc.player.getFoodData()            FoodData
//     mc.player.blockPosition()          BlockPos
//     mc.player.getYRot() / getXRot()    朝向；setYRot / setXRot 设置
//     mc.player.isDeadOrDying()
//
//   方块与世界
//     mc.level.getBlockState(pos)        BlockState
//     mc.level.getBlockEntity(pos)       BlockEntity
//     BuiltInRegistries.BLOCK.getKey(block).toString()   取方块 ID
//
//   注册表与标识
//     Identifier.tryParse("minecraft:stone")
//     Identifier.fromNamespaceAndPath("minecraft", "stone")
//     ResourceKey.create(Registries.DIMENSION, identifier)
//
//   Meteor 事件（用 meteordevelopment.orbit.EventHandler）
//     @EventHandler private void onTick(TickEvent.Pre event)
//     @EventHandler private void onGameJoined(GameJoinedEvent event)
//     @EventHandler private void onPacketReceive(PacketEvent.Receive event)
//     @EventHandler(priority = -100) 可控制优先级
//     事件回调第一行统一写 `if (!isActive()) return;`
//
//   【3.4 Meteor 设置项与配置分组规范】
//
//   Meteor 设置项（常用类型）：
//     private final SettingGroup sgX = settings.createGroup("组名");
//     sgX.add(new BoolSetting.Builder().name("名").description("说明")
//         .defaultValue(true).build());
//     可用类型：BoolSetting / IntSetting / DoubleSetting / StringSetting /
//               EnumSetting / BlockListSetting / KeybindSetting
//     条件显示用 .visible(() -> 条件)，变更回调用 .onChanged(v -> ...)
//
// 【3.5 新模块标准模板】
//
//   package com.example.addon.modules;
//
//   import com.example.addon.core.YiyiaddonModule;
//   import meteordevelopment.meteorclient.events.world.TickEvent;
//   import meteordevelopment.meteorclient.gui.GuiTheme;
//   import meteordevelopment.meteorclient.gui.widgets.WWidget;
//   import meteordevelopment.meteorclient.settings.*;
//   import meteordevelopment.orbit.EventHandler;
//
//   import static com.example.addon.core.AddonTemplate.CATEGORY;
//
//   public class 示例Module extends YiyiaddonModule {
//
//       private final SettingGroup sgGeneral = settings.getDefaultGroup();
//
//       private final Setting<Boolean> 开关 = sgGeneral.add(new BoolSetting.Builder()
//           .name("开关名")
//           .description("这个开关做什么。")
//           .defaultValue(true)
//           .build()
//       );
//
//       public 示例Module() {
//           // 描述必须以「。详细参考下面使用说明。」结尾
//           super(CATEGORY, "模块中文名", "一句话功能说明。详细参考下面使用说明。");
//       }
//
//       @Override
//       public void onActivate() {
//           // 初始化状态；需要世界的操作先判空
//       }
//
//       @Override
//       public void onDeactivate() {
//           // 清理状态，恢复被改动的玩家数据
//       }
//
//       @EventHandler
//       private void onTick(TickEvent.Pre event) {
//           if (!isActive() || mc.player == null || mc.level == null) return;
//           // 主逻辑
//       }
//
//       @Override
//       public WWidget getWidget(GuiTheme theme) {
//           return buildInfoWidget(theme,
//               new String[]{ "§l模块名 · 使用说明" },
//               new String[]{
//                   "§e§l▌ 准备",
//                   "§f  1. 第一步",
//                   "§f  2. 第二步"
//               },
//               new String[]{
//                   "§a§l▌ 功能原理",
//                   "§f  · 做什么",
//                   "§f  · 怎么做"
//               }
//           );
//       }
//   }
//
//   新模块写完后必须在 AddonTemplate.onInitialize() 里注册：
//     Modules.get().add(new 示例Module());
//
// 【3.6 覆写基类方法的注意事项】
//
//   基类已覆写 toggle() / sendToggledMsg() / info() / warning() / error() / fromTag()，
//   子类如需再覆写，必须调 super，否则会破坏统一消息格式或配置修正逻辑。
//
//   子类输出消息统一用 notify() / notifyError()，不要直接调 mc.player.sendSystemMessage，
//   否则丢掉 [yiyiaddon][模块名] 前缀。
//
//   涉及可序列化字段（会写进 modules.nbt 的），改内存值的同时必须处理 fromTag，
//   否则旧存档会把值读回来——这是 2026-08-25 那次 Bug 的根因。
//
// 【3.7 说明面板与消息前缀统一规范】（所有模块必须一致）
//
//   用户要求所有模块的配置页面与聊天输出长得一模一样。以下四条是硬性约定，
//   新增模块或改动旧模块时逐条对照，不允许「这个模块特殊」。
//
//   ── 一、聊天前缀 ──────────────────────────────────────────
//
//   唯一合法格式（由 formatMessage 统一产出，方括号之间没有空格）：
//     §c§l[yiyiaddon]§r§f§l[模块名]§r内容
//
//   模块内部：一律用 notify() / notifyError()。
//   模块外部（指令、Service、工具类）：调 YiyiaddonModule.formatMessage("显示名", 消息)。
//
//   ✗ 禁止硬编码 "§c§l[yiyiaddon] §f..."（注意那个空格），也禁止自己拼前缀。
//     曾经 YiyiaddonWelcomeService 就是这么写的，导致欢迎语比模块消息多一个空格，
//     两种前缀在聊天栏里并排出现时一眼就能看出不齐。
//
//   ── 二、说明面板结构 ──────────────────────────────────────
//
//   必须覆写 getWidget()，返回 buildInfoWidget(...)，不要自己拼 WTable。
//   段落顺序固定：标题 → 使用方法 → 功能说明 → 当前状态 → 注意事项。
//
//     标题：  "§l模块名 · 使用说明"          （中间是「空格·空格」）
//     小节：  "§{色}§l▌ 小节名"              （▌ 后面一个空格）
//     条目：  "§f  1. 步骤"                  （有序，缩进两空格）
//             "§f  · 要点"                   （无序，缩进两空格）
//     子条目："§f     · 补充说明"            （挂在有序条目下，缩进五空格）
//             "§f    · 补充说明"             （挂在无序条目下，缩进四空格）
//
//   子条目只用于「某个步骤内部还要拆细」的场合，最多一层，不要再往下嵌。
//
//   小节配色固定，不要自创。常规五色（绝大多数模块只用这五个就够）：
//     §e§l▌ 准备 / 使用方法            黄
//     §a§l▌ 功能 / 原理 / 流程         绿
//     §b§l▌ 参数建议 / 当前状态        青
//     §d§l▌ 模式说明 / 提示            粉
//     §c§l▌ 注意 / 警告                红
//
//   小节超过五个时（指令说明类模块动辄十几节），按顺序续用以下扩展色，
//   不要跳着挑，也不要用五色之外的其它颜色码：
//     §6§l▌ 指令系统 / 分类小节        橙
//     §5§l▌ 次级分类                   紫
//     §9§l▌ 高级功能 / 连接相关        蓝
//     §4§l▌ 安全提醒（比 §c 更重）      深红
//
//   ── 三、面板按钮 ──────────────────────────────────────────
//
//   一律用 addUniformButton(theme, table, "文字", 回调)，多行按钮之间用 table.row() 分行。
//
//   ✗ 禁止 table.add(theme.button(...)).expandX()。
//     WTable 的列宽取该列内容最大值，expandX() 只让第一列吃掉剩余空间，
//     结果第一列被拉得很长、后面几列按文字宽度收缩，三列布局明显参差
//     （PinkThemeModule 的粉色主题面板原来就是这个毛病）。
//     addUniformButton 用 minWidth(BUTTON_MIN_WIDTH) 统一列宽
//     + expandWidgetX() 让按钮填满单元格，才能真正等宽。
//
//   ── 四、启动自检的缺项播报 ────────────────────────────────
//
//   需要前置配置才能跑的模块（绑坐标、填指令、勾作物那类），selfCheck() 必须
//   返回 List<String> 收集全部缺项，然后交给 reportSelfCheck(missing) 播报。
//
//   ✗ 禁止「遇到第一个缺项就 return 一条错误」。
//     那样用户得反复开关模块，配一项试一次，挤牙膏式试错。
//     正确的做法是一次列全，用户配好一项下次启动就少一条，剩几项一目了然。
//
//   ✗ 禁止在 onActivate() 里直接调 toggle() 自我关闭。
//     Module.toggle() 的字节码顺序是「先 addActive 再调 onActivate」，
//     在 onActivate 内部再走一遍完整 toggle() 会造成状态机重入。
//     reportSelfCheck 已用 mc.execute 把关闭推迟到下一帧，直接用它就行。
//
//   ── 五、关闭容器界面的守卫 ────────────────────────────────
//
//   凡是要调 player.closeContainer() 的地方，必须先确认当前 Screen 真的是容器界面：
//     if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
//
//   player.closeContainer() 会无条件关掉当前打开的任意 Screen。若在 onDeactivate()
//   里无守卫地调用，用户从 Meteor GUI 点击开关模块时，Meteor 面板会被一起关掉
//   （AutoFarmMatrix 的 onDeactivate 原来就是这个毛病，表现为「点一下面板就没了」）。
//
//   ✗ 判断 mc.player.containerMenu != null 没用——玩家自身背包菜单始终非 null，
//     这个条件永远为真，等于没加守卫。必须判断 Screen 类型。
//
//   ── 六、自检 ──────────────────────────────────────────────
//
//   改完任意模块，先跑这几条确认没有漏网的写法：
//     Grep "\[yiyiaddon\] "        → 应只在基类注释里出现，不应出现在字符串里
//     Grep "expandX\(\)"           → 只允许 buildInfoWidget 内部的 label 使用
//     Grep "onActivate" 内是否有 toggle() → onActivate 里一律不许自我 toggle
//       （事件回调里的 if (isActive()) toggle() 是合法的，比如离开世界、
//        看门狗连续超时停机，那些不在 toggle() 调用栈内，不会重入）
//     Grep "closeContainer\(\)"     → 每处调用点都要有 Screen 类型守卫
//
//   ── 七、混淆与发布规范 ────────────────────────────────────
//
//   本项目使用 ProGuard 7.8.1 进行代码混淆保护，混淆配置在 build.gradle.kts 中。
//   混淆相关的所有文件统一放在 Obfuscation/ 目录，结构如下：
//
//     Obfuscation/
//     ├─ 字典/混淆字典.txt                 209 条易混字符（l/I/O/0/1 全排列）
//     ├─ 工具/还原崩溃日志.js               崩溃日志反混淆工具
//     └─ 映射存档/
//         ├─ 混淆映射-v{版本号}.txt         当前版本的完整映射
//         └─ 混淆映射-v{版本号}-备份-{时间戳}.txt  自动备份
//
//   【版本与映射的一一对应关系】
//
//   每个 jar 版本对应唯一的映射文件，版本号不同混淆结果就不同：
//     yiyiaddon1.1-beta1.jar → 混淆映射-v1.1-beta1.txt
//     yiyiaddon1.1-beta2.jar → 混淆映射-v1.1-beta2.txt
//     yiyiaddon1.2.jar       → 混淆映射-v1.2.txt
//
//   原因：ProGuard 每次构建会从字典中随机抽取名字，且类的处理顺序不保证一致，
//   所以同一个类这次叫 IlI 下次可能叫 ll。版本号不同，映射必不相同。
//
//   ✗ 禁止混用映射文件——1.1-beta1 的崩溃日志用 1.1-beta2 的映射还原会对不上。
//
//   【映射文件必须入库】
//
//   映射存档/ 目录必须提交到 Git，原因：
//     1. build/ 目录在 .gitignore 内，且 gradlew clean 会整个删掉
//     2. 映射文件一旦丢失，该版本的崩溃日志就永远无法还原成真实类名
//     3. 映射是长期维护的历史存档，每个版本都要保留
//
//   构建系统已配置自动备份：每次 gradlew buildOfficial 前会备份上一版映射
//   （文件名带时间戳），新映射直接写入 映射存档/混淆映射-v{版本号}.txt。
//
//   【还原崩溃日志的三种用法】
//
//   工具路径：Obfuscation/工具/还原崩溃日志.js
//
//   1. 还原完整崩溃日志：
//      node 还原崩溃日志.js <日志路径> <版本号>
//      示例：node 还原崩溃日志.js latest.log 1.1-beta1
//
//   2. 查询单个混淆名：
//      node 还原崩溃日志.js --查 <混淆名>
//      示例：node 还原崩溃日志.js --查 IlI
//      输出：com.example.addon.modules.AutoFarmMatrix
//
//   3. 列出所有存档：
//      node 还原崩溃日志.js --列表
//      按版本号排序显示所有可用的映射文件
//
//   【混淆强度说明】
//
//   当前配置已达到免费 ProGuard 的极限：
//     · 类名：ll / lI / lO / Il / II / IO / IlI / llIl 等 209 种易混字符组合
//     · 方法重载混淆：同一类内多个 l() 方法不同签名
//     · 包结构打平：所有类移至根包
//     · 删除调试信息：源文件名统一，无行号表
//     · 删除类型信息：泛型、内部类、嵌套结构信息全部移除
//     · 优化：5 轮优化，但排除破坏 Mixin 的三类（内联/类合并/字段传播）
//
//   反编译后代码可读性极低，IDE 跳转和搜索功能基本失效。
//
//   【发布流程检查清单】
//
//   每次发布新版本前必须确认：
//     1. gradle/libs.versions.toml 中 mod-version 已更新
//     2. jar 文件名自动跟随版本号（由 build.gradle.kts 中 archiveFileName 控制）
//     3. 映射文件已自动生成到 Obfuscation/映射存档/ 并包含正确版本号
//     4. 映射文件已提交到 Git（git status 检查是否有未跟踪的映射文件）
//     5. Release 页面已上传对应版本的 jar 和 SHA256 校验值
//     6. Release notes 中已说明该版本的混淆配置（如果有变化）
//
//   ✗ 禁止手动修改 jar 文件名——必须通过 libs.versions.toml 改版本号，
//     否则 jar 名和映射文件名会对不上，还原时找不到对应映射。
//
//   ── 八、运行时埋点与诊断规范（智能体必须照做）──────────────────
//
//   【目的】
//   静态代码无法确定的开关异常、事件未触发、容器同步、网络包、状态机卡死等问题，必须按：
//   假设 → 埋点 → 复现 → 日志判定 → 最小修复 → 复测 执行。未拿到证据，禁止猜测式改业务逻辑。
//
//   【唯一诊断归档：全部中文】
//   所有测试 Bug 文件只允许放入 d:\\mcaddon\\26.1.2\\Diagnostics\\：
//     ├─ 会话记录\\进行中\\  YYYY-MM-DD-问题名称.md
//     ├─ 会话记录\\已修复\\  YYYY-MM-DD-问题名称.md
//     ├─ 运行日志\\          YYYY-MM-DD-问题名称.ndjson
//     ├─ 游戏操作录像\\      YYYY-MM-DD-问题名称.env、启动日志监听.ps1
//     └─ 工具\\              调试监听服务.js、日志分析器.js
//   会话目录、文件展示名称、问题名称必须中文；.env、.ndjson、会话记录都是证据，必须提交 Git。
//   sessionId 仅为 HTTP 协议字段，可使用英文短横线；不得用 debug-xxx.md 或英文会话 ID 替代中文归档。
//
//   【会话记录模板】
//   新问题先在 Diagnostics\\会话记录\\进行中\\ 新建中文 Markdown，按固定章节填写：
//   问题描述、影响范围、复现前提、复现步骤、3~5 个可证伪假设、埋点位置、证据时间线、
//   根因、最小修复方案、复测结果。确认修复后移动到 会话记录\\已修复\\，不能删除证据。
//
//   【埋点设计】
//   1. 每个假设分配稳定编号 A、B、C……；埋点区域必须标记：
//      // #region debug-point A:中文位置名称
//      // #endregion
//   2. 只记录能证伪假设的状态：方法入口、分支条件、前后状态、资源数量、界面/容器同步号、
//      网络包类型、调用栈或最终结果；禁止每 Tick 无条件刷日志。
//   3. 状态机必须记录“旧状态 → 新状态”、触发原因、阶段、关键资源与当前界面；容器流程必须记录
//      打开请求、处理器类型、同步号、槽位/物品快照、扫描结果和实际点击结果。
//   4. 所有模块内部 toggle() 前必须先记录具体停机原因；onDeactivate() 必须记录最终停机原因、
//      当前状态、阶段、界面、关键资源、模式和寻路/任务状态。没有内部停机记录即视为外部关闭证据。
//
//   【可直接抄用的 Java 实现】
//   需要导入：java.net.HttpURLConnection、java.net.URL、java.nio.charset.StandardCharsets、
//   java.util.concurrent.atomic.AtomicLong。
//
//   // #region debug-point A:字段与统一上报
//   private static final String 调试地址 = "http://127.0.0.1:7777/event";
//   private static final String 调试会话 = "模块名称-问题简称"; // 协议标识
//   private static final String 调试展示名称 = "模块名称-中文问题名称"; // 决定中文日志文件名
//   private final AtomicLong 调试序号 = new AtomicLong();
//   private String 停机原因 = "外部关闭或原因未知";
//
//   private void debugEvent(String 假设编号, String 消息, String 数据) {
//       long 序号 = 调试序号.incrementAndGet();
//       long 时刻 = System.currentTimeMillis();
//       String json = "{\"sessionId\":\"" + 转义(调试会话) + "\",\"displayName\":\""
//           + 转义(调试展示名称) + "\",\"runId\":\"本次复现\",\"hypothesisId\":\""
//           + 转义(假设编号) + "\",\"location\":\""
//           + 转义(getClass().getSimpleName()) + "\",\"ts\":" + 时刻
//           + ",\"data\":{\"sequence\":" + 序号 + ",\"detail\":\"" + 转义(数据)
//           + "\"},\"msg\":\"" + 转义(消息) + "\"}";
//       new Thread(() -> {
//           for (int 尝试 = 1; 尝试 <= 3; 尝试++) {
//               HttpURLConnection 连接 = null;
//               try {
//                   连接 = (HttpURLConnection) new URL(调试地址).openConnection();
//                   连接.setRequestMethod("POST");
//                   连接.setConnectTimeout(250);
//                   连接.setReadTimeout(250);
//                   连接.setDoOutput(true);
//                   连接.setRequestProperty("Content-Type", "application/json; charset=utf-8");
//                   try (java.io.OutputStream 输出 = 连接.getOutputStream()) {
//                       输出.write(json.getBytes(StandardCharsets.UTF_8));
//                   }
//                   int 状态码 = 连接.getResponseCode();
//                   if (状态码 >= 200 && 状态码 < 300) return; // 监听器已确认写盘
//               } catch (Exception ignored) {
//                   // 监听器尚未就绪时有限重试；不得把错误抛回游戏线程。
//               } finally {
//                   if (连接 != null) 连接.disconnect();
//               }
//               try {
//                   Thread.sleep(100L * 尝试);
//               } catch (InterruptedException ignored) {
//                   Thread.currentThread().interrupt();
//                   return;
//               }
//           }
//       }, "诊断上报-" + 序号).start();
//   }
//
//   private static String 转义(String 文本) {
//       return 文本 == null ? "" : 文本.replace("\\", "\\\\").replace("\"", "\\\"")
//           .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
//   }
//   // #endregion
//
//   【可直接抄用的调用点】
//   // #region debug-point B:状态切换
//   private void 切换状态(State 新状态, String 原因) {
//       State 旧状态 = state;
//       state = 新状态;
//       debugEvent("B", "状态切换", "旧状态=" + 旧状态 + "，新状态=" + 新状态
//           + "，原因=" + 原因 + "，经验=" + mc.player.experienceLevel + "，界面=" + 当前界面名());
//   }
//   // #endregion
//
//   // #region debug-point C:内部停机
//   private void 请求停机(String 原因) {
//       停机原因 = 原因;
//       debugEvent("C", "请求停机", "原因=" + 原因 + "，状态=" + state + "，界面=" + 当前界面名());
//   }
//
//   @Override
//   public void onDeactivate() {
//       debugEvent("C", "模块停用", "停机原因=" + 停机原因 + "，状态=" + state
//           + "，界面=" + 当前界面名());
//       // 原有清理逻辑
//   }
//
//   // 任何内部关闭都必须这样写：
//   请求停机("附魔台不存在");
//   toggle();
//   // #endregion
//
//   // #region debug-point D:容器同步
//   debugEvent("D", "请求打开容器", "目标=" + 目标坐标 + "，状态=" + state);
//   // 收到容器界面后：
//   debugEvent("D", "容器已打开", "处理器=" + mc.player.currentScreenHandler.getClass().getSimpleName()
//       + "，同步号=" + mc.player.currentScreenHandler.syncId + "，槽位数="
//       + mc.player.currentScreenHandler.slots.size());
//   // 点击前后均记录：槽位号、物品、数量、点击方式、点击结果。
//   // #endregion
//
//   上报数据必须只包含诊断所需字段；不要记录账号、令牌、服务器密码或完整聊天内容。
//
//   【Java 上报约束】
//   1. 每个模块只保留一份上述 debugEvent()，调用处只传“假设编号、中文消息、中文数据”。
//   2. 地址仅允许 127.0.0.1:7777/event；连接/读取超时保持短暂；异常必须吞掉，绝不影响游戏线程。
//   3. 每条事件带单调递增 sequence。异步发送使 NDJSON 文件顺序不可靠，分析时按 ts + sequence
//      联合排序，不能只按文件行号判断因果。
//   4. 同一问题只使用一个 sessionId、一套上报方法和一个中文运行日志文件，禁止把关键事件拆到
//      多份日志或多个会话。
//
//   【监听与分析】
//   1. 监听脚本固定使用 Diagnostics\\工具\\调试监听服务.js，监听 127.0.0.1:7777，日志写入
//      Diagnostics\\运行日志\\；只接受本机回环地址，不开放局域网端口。
//   2. 通过 http://127.0.0.1:7777/健康 确认监听存活；复现前先确认新日志文件已创建且事件数递增。
//   3. 使用 Diagnostics\\工具\\日志分析器.js 按时间线、调用栈、状态迁移、节律分析 NDJSON；
//      大日志禁止肉眼全量翻读，优先按假设编号、停机原因、状态和 sequence 筛选。
//
//   【万能案例：任何“功能没反应、自动关闭、界面异常、状态卡死”都按这一套】
//   下面用历史日志“附魔循环反复执行但结果异常”演示完整闭环。案例中的模块名、状态名、资源名
//   可以替换成当前 Bug 的实际名称；方法不依赖附魔、箱子或具体 Minecraft 版本。
//
//   第一步：只描述现象，不先写结论
//   例：用户看到模块没有按预期完成任务，怀疑模块停了、事件没触发、状态机卡住或资源判断错误。
//
//   第二步：列出可证伪假设，每个假设必须有“如果为真/如果为假”的证据
//   A：模块根本没有收到启动或事件回调；若为真，看不到入口事件，若为假，入口事件存在。
//   B：模块内部条件主动关闭；若为真，先出现“请求停机”，再出现“模块停用”。
//   C：状态机进入了错误分支或没有推进；若为真，状态切换停止，或同一状态和条件重复出现。
//   D：容器、网络包或界面同步失败；若为真，有打开请求但没有打开确认，或 syncId/槽位/物品不一致。
//   E：外部按键、框架、断线或世界切换关闭；若为真，没有任何内部“请求停机”，但出现“模块停用”。
//
//   第三步：只在能区分假设的位置埋点
//   // #region debug-point A:入口与自检
//   @Override
//   public void onActivate() {
//       debugEvent("A", "监听自检", "模块=" + getClass().getSimpleName() + "，状态=" + isActive());
//       debugEvent("A", "功能入口", "阶段=" + 当前阶段 + "，界面=" + 当前界面名());
//       // 原有启动逻辑
//   }
//   // #endregion
//
//   // #region debug-point B:每个内部关闭点
//   private void 请求停机(String 原因) {
//       停机原因 = 原因;
//       debugEvent("B", "请求停机", "原因=" + 原因 + "，状态=" + state
//           + "，阶段=" + 当前阶段 + "，界面=" + 当前界面名());
//       toggle();
//   }
//   // #endregion
//
//   // #region debug-point C:状态机决策
//   private void 记录决策(String 决策, String 条件) {
//       debugEvent("C", "状态机决策", "状态=" + state + "，决策=" + 决策
//           + "，条件=" + 条件 + "，阶段=" + 当前阶段);
//   }
//
//   private void 切换状态(State 新状态, String 原因) {
//       State 旧状态 = state;
//       state = 新状态;
//       debugEvent("C", "状态切换", "旧状态=" + 旧状态 + "，新状态=" + 新状态 + "，原因=" + 原因);
//   }
//   // #endregion
//
//   // #region debug-point D:容器或网络边界
//   private void 记录容器(String 事件, String 数据) {
//       debugEvent("D", 事件, "处理器=" + 当前处理器名() + "，同步号=" + 当前同步号()
//           + "，槽位数=" + 当前槽位数() + "，" + 数据);
//   }
//   // 请求前记录目标；回调中记录实际处理器、syncId、槽位、物品、数量；点击前后记录结果。
//   // 网络包同样记录“发送/收到、包类型、关键字段、结果”，禁止只记录“处理了包”。
//   // #endregion
//
//   // #region debug-point E:最终快照
//   @Override
//   public void onDeactivate() {
//       debugEvent("E", "模块停用", "停机原因=" + 停机原因 + "，状态=" + state
//           + "，阶段=" + 当前阶段 + "，界面=" + 当前界面名() + "，资源=" + 关键资源快照());
//       // 原有清理逻辑
//   }
//   // #endregion
//
//   第四步：启动监听器并验证“确实能收日志”，自检没落盘不能开始复现
//   1. 启动 Diagnostics\\工具\\调试监听服务.js。
//   2. 访问 http://127.0.0.1:7777/健康，必须返回 200。
//   3. 开启模块，第一条必须是“监听自检”。
//   4. 检查 Diagnostics\\运行日志\\ 中对应中文文件已出现该事件，且 sequence 从 1 递增。
//   5. 自检失败时只修监听链路，不得把业务 Bug 和日志丢失混在一起判断。
//
//   第五步：按 ts + sequence 阅读 NDJSON，不按文件行号猜因果
//   真实日志可抽象成：
//   {"hypothesisId":"C","data":{"sequence":101,"detail":"状态=等待处理，决策=继续"},"ts":1000}
//   {"hypothesisId":"D","data":{"sequence":102,"detail":"请求打开容器"},"ts":1010}
//   {"hypothesisId":"D","data":{"sequence":103,"detail":"容器已打开，syncId=4，槽位数=46"},"ts":1050}
//   {"hypothesisId":"B","data":{"sequence":104,"detail":"请求停机，原因=目标方块不存在"},"ts":1100}
//   {"hypothesisId":"E","data":{"sequence":105,"detail":"模块停用，停机原因=目标方块不存在"},"ts":1110}
//   若 ts 相同，使用 data.sequence；异步 HTTP 可能改变文件落盘顺序，不能用行号代替事件顺序。
//
//   第六步：用“存在”和“不存在”同时判定假设
//   1. 有“监听自检”但没有“功能入口”：优先查启动后的业务入口或事件注册。
//   2. 有“功能入口”但没有 D 的“容器已打开”：容器/网络/回调链路未完成，不要先改状态机。
//   3. 有 B 的“请求停机”且紧接 E：根因就是该停机点记录的条件，做最小修复。
//   4. 只有 E、没有 B：不能说业务内部主动关闭；继续查按键、框架、断线、世界切换和外部 toggle。
//   5. C 的同一状态和同一条件无限重复：这是状态机推进条件或完成标志错误，不要靠延时掩盖。
//   6. D 的 syncId、槽位数或物品数量变化异常：先修同步时机/目标容器校验，再判断点击逻辑。
//
//   第七步：从证据写出根因和最小修复，不扩大修改范围
//   例：日志连续显示“remaining=0 → 砂轮处理完成 → IDLE决策”，没有停机事件，说明模块没有关闭，
//   而是完成标志没有改变或下一轮条件仍允许进入处理分支。最小修复只能改完成标志/分支条件，
//   不能顺手重写寻路、容器或整个状态机。修复后必须使用相同步骤再次产生日志。
//
//   第八步：复测必须证明“原 Bug 消失且正常路径仍在”
//   修复前后至少对比：监听自检、入口、关键状态迁移、关键资源、最终结果、停机原因；
//   通过条件是：自检落盘、关键事件只发生一次或符合预期、没有异常重复循环、最终结果正确。
//   只有复测通过，才把中文会话记录从 会话记录\\进行中\\ 移到 会话记录\\已修复\\；证据不删除。
//
//   【案例提炼】
//   不管 Bug 是自动关闭、事件不触发、GUI 秒关、包处理无效、配置不生效还是状态卡死，
//   都先用 A 确认入口、用 B 确认内部副作用、用 C 确认状态机、用 D 确认外部边界、用 E 确认最终结果；
//   通过一条完整证据链排除假设，再改唯一被证实的根因。这就是可复制的通用调试方法。
//
//   【修复与清理】
//   1. 仅修复日志已确认的根因，保持最小改动；修复后使用同一复现步骤产出新的运行日志并对比。
//   2. 用户确认前，不得删除或覆盖埋点、监听服务、.env、.ndjson、会话记录；确认后才可移除临时
//      埋点并将会话归档为“已修复”。
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
        toggleOnBindRelease = false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  配置反序列化 - 清理历史残留
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Meteor 的 fromTag 会把 modules.nbt 里的 toggleOnKeyRelease 读回内存。
     * 早期版本误设过 true，该值会导致 Modules.onOpenScreen 在关闭 GUI 时
     * 把模块一并关掉（表现为「刚开就自动关」）。
     * yiyiaddon 所有模块都不需要这个行为，读档后一律压回 false。
     */
    @Override
    public Module fromTag(CompoundTag tag) {
        Module result = super.fromTag(tag);
        toggleOnBindRelease = false;
        return result;
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
     * 格式：§c§l[yiyiaddon]§r§f§l[模块名]§r§f消息
     */
    protected void notify(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§f" + message)));
    }

    /**
     * 错误消息（橙色加粗）
     * 格式：§c§l[yiyiaddon]§r§f§l[模块名]§r§6§l错误消息
     */
    protected void notifyError(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§6§l" + message)));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  启动自检 - 需要前置配置的模块使用
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 报告自检缺项并关闭模块
     *
     * 一次列出所有缺失项，用户配好一项下次启动就少一条，不用挤牙膏式反复试。
     * 只报第一个错会让用户来回开关模块，配一项试一次，体验很差。
     *
     * 关闭走 mc.execute 延后到下一帧：Module.toggle() 是先 addActive 再调
     * onActivate，在 onActivate 里直接 toggle() 会造成状态机重入。
     *
     * @param missing 缺项清单，为空表示自检通过
     * @return true 表示自检通过可以继续启动，false 表示已中止
     */
    protected boolean reportSelfCheck(java.util.List<String> missing) {
        if (missing.isEmpty()) return true;

        chatFeedback = false;
        mc.execute(() -> {
            if (isActive()) toggle();
            chatFeedback = true;
        });

        notifyError("还差 " + missing.size() + " 项没配好，配完再开：");
        for (int i = 0; i < missing.size(); i++) {
            notify("§6  " + (i + 1) + ". §f" + missing.get(i));
        }
        return false;
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

    /** 说明面板按钮的统一最小宽度，保证同一面板内所有按钮等宽 */
    protected static final double BUTTON_MIN_WIDTH = 90;

    /**
     * 添加等宽按钮到说明面板
     *
     * WTable 的列宽取该列内容的最大值，若用 expandX() 只有第一列会吃掉剩余空间，
     * 导致同一行的按钮宽度不一致（第一列很长、后面按文字长度收缩）。
     * 这里改用 minWidth 统一列宽 + expandWidgetX 让按钮填满单元格，
     * 三列布局才会真正等宽。
     *
     * @param theme  Meteor GUI 主题
     * @param table  面板表格
     * @param title  按钮文字
     * @param action 点击回调
     */
    protected void addUniformButton(GuiTheme theme, WTable table, String title, Runnable action) {
        WButton button = theme.button(title);
        button.action = action;
        table.add(button).minWidth(BUTTON_MIN_WIDTH).expandWidgetX();
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

    /**
     * 格式化坐标显示（个人习惯）
     * 格式：§7X§f38 §7Y§f-60 §7Z§f59
     * XYZ标签灰色，坐标数值白色
     */
    public static String formatCoords(int x, int y, int z) {
        return "§7X§f" + x + " §7Y§f" + y + " §7Z§f" + z;
    }

    /**
     * 格式化坐标与维度显示（个人习惯）
     * 格式：§7X§f38 §7Y§f-60 §7Z§f59 §8▸ §f主世界
     * 使用深灰色箭头分隔坐标和维度，末尾自动重置颜色代码防止污染后续文本
     */
    public static String formatCoordsWithDimension(int x, int y, int z, String dimension) {
        return formatCoords(x, y, z) + " §8▸ §f" + dimension + "§r";
    }

    /**
     * 格式化坐标与维度显示（带自定义维度颜色）
     * 格式：§7X§f38 §7Y§f-60 §7Z§f59 §8▸ §6主世界
     * 维度颜色可自定义，末尾自动重置颜色代码防止污染后续文本
     */
    public static String formatCoordsWithDimension(int x, int y, int z, String dimension, String dimColor) {
        return formatCoords(x, y, z) + " §8▸ " + dimColor + dimension + "§r";
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
// ║ • 源码分支（私密）：https://github.com/fxjcangku/26.1.2-source    ║
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
// ║ ⚠️ **更新内容书写规范**（重要！客户端会提取前3条作为更新提示）      ║
// ║ • 必须用无序列表（- 或 * 开头），不要用编号列表（1. 2. 3.）       ║
// ║ • 不要用引用块（> 开头）写核心更新内容                             ║
// ║ • 标题（### ## #）和分隔线（---）会被跳过                          ║
// ║ • 客户端只显示前3条无序列表项，把最重要的更新写在前面              ║
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
