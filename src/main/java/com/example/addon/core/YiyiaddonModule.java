
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
//
// 判断标准很简单：用户下次打开 Diagnostics/ 目录，能不能一眼看懂
// 每个文件是什么、什么时候修的、修的什么问题。看不懂就是你没做对。
//
// 详细条款见下方第二部分（2.1 ~ 2.5）。

package com.example.addon.core;

import com.example.addon.translations.YiyiaddonTranslator;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
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
//     · `Diagnostics/工具/`             监听服务脚本（`调试监听服务.js`），可提交，跨会话复用
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
//    只读该会话的 NDJSON，按时间线记录关键日志，标记假设为确认或排除；没有证据的结论必须保持未确认。
//    调用栈证据优先：能指出「谁触发了这个行为」的帧，比一堆状态快照更有价值。
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
//   Meteor 设置项
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
