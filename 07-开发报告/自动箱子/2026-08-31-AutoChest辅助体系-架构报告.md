# AutoChest 辅助体系 · 第一阶段架构设计报告

> 日期：2026-08-31
> 阶段：仓库审查 + 辅助模块归类 + 三功能架构 + 数据流 + 类职责 + 集成方案（已完成并编译通过）

---

## 0. 必读（第二步智能体强制要求）

接手继续开发前，**必须完整读完以下两个个人开发习惯文件**，不得跳读：

1. `d:\mcaddon\26.1.2\AGENTS.md`（五条铁律 + 关键速查）
2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`（十章规范唯一正本）

关键铁律提醒（第二步开发前逐条对照）：
- 规范注释只增不删；证据驱动排查（Bug 先埋点取证据）；中文 + 日期归档。
- API 先查后写：Mojang 官方映射，查 `node 03-映射表/工具/查JARAPI.js <类名>`（规范/AGENTS 里写作 `查API.js`，实际磁盘文件名为 `查JARAPI.js`）。
- 分类铁律：新功能独立英文包；中文注释；禁 emoji（用 ✓ ✗ ⚠ ▸）。
- 消息规范：前缀走 `YiyiaddonModule.formatMessage`；面板按钮走 `addUniformButton`；说明面板走 `buildInfoWidget`；强调色走 `highlight*`；状态播报 5.9 带 lastNotifiedState 去重锁。

---

## 1. 项目实际版本

| 项 | 值 |
| --- | --- |
| Minecraft | 26.1.2（内部 1.21.11） |
| 映射 | Mojang 官方映射（非 Yarn） |
| Fabric Loader | 0.19.3 |
| Loom | 1.16.3 |
| Meteor Client | 26.1.2-SNAPSHOT |
| JDK | 25 |
| 构建产物 | `yiyiaddon1.1-beta4-personal.jar` |
| 入口 | `com.example.addon.core.AddonTemplate` |

---

## 2. 现有相关代码（复用点）

| 能力 | 已有实现 | 复用方式 |
| --- | --- | --- |
| 模块基类 | `core/YiyiaddonModule` | AutoChest/ID识别/ID配置管理 三模块都继承它 |
| 容器同步层 | `farm/ContainerBroker` | ChestInteractionService 内部复用（stateId 稳定判定 + openMenu/closeContainer） |
| 发包开箱 | `farm/FarmPacketOps.interactBlock` | ChestInteractionService 复用（带 sequence 预测，窗口失焦也能开箱） |
| 容器槽位操作 | `handleContainerInput(containerId, slot, button, ContainerInput.QUICK_MOVE, player)` | 取物复用（26.1.2 已废弃 clickSlot + SlotActionType） |
| 寻路站位 | `baritone.api.pathing.goals.GoalTwoBlocks` | PathingService 复用（= 容器面前约一格可站立位置） |
| 分帧扫描 | `farm/FarmScanner`（游标 + 预算 + 稳定快照） | ContainerScanner 参照实现 |
| 状态机 | `mining/MinerFSM`（枚举 + stateTick + 播报） | AutoChestStateMachine 参照 |
| 指令持久化 | `commands/WKCommand`（config/yiyiaddon/ 手写 JSON） | AutoChestCommand / 记录管理 / 标点管理 参照 |
| 设置类 | `librarian/AutoLibrarianSettings`（独立 Settings 类） | AutoChestSettings 参照 |
| ESP 渲染 | `villager/render/ContainerESP`（Render3DEvent + renderer.box） | AutoChestRenderer 参照 |
| 分类注册 | `AddonTemplate`（Category + Modules.registerCategory） | 新增「辅助」分类 |

---

## 3. 辅助模块归类（三功能）

新增 Meteor 分类 `CATEGORY_ASSIST = "§c§lyiyiaddon §d§l辅助"`（图标 `Items.CHEST`），三个功能统一归入，同属辅助、功能独立、数据联动：

```
辅助（CATEGORY_ASSIST）
├── ID识别          IdIdentifyModule   → 生成 ItemIdentity，写入 ID 配置
├── ID配置管理       IdConfigModule     → 管理已识别/手动添加的 ID
└── 自动箱子         AutoChestModule    → 消费 ID 配置，扫描容器取物
```

三模块共享**同一个** `ItemIdManager` 实例（在 AddonTemplate 创建后注入三者），保证数据同源。

---

## 4. 三功能数据流

```
ID识别（IdIdentifyModule）
   │  手持物品 → ItemIdentifier.identifyExact → ItemIdentity
   ▼
ID 文件（config/yiyiaddon/ids/{server}.json）  ← 由 ItemIdManager 持久化
   │
ID配置管理（IdConfigModule / ItemIdManager）
   │  增删查、清空、加载
   ▼
AutoChest 物品选择器（ContainerSelector + ItemIdentityMatcher）
   │  消费 ItemIdManager.isTarget / matchesExact
   ▼
真实容器 Slot（ChestInteractionService.chestStacks / withdrawOnce）
   ▼
自动取物（handleContainerInput QUICK_MOVE）
```

**关键约束**：AutoChest 只消费 ID 配置，**绝不建立第二套物品数据库**。

---

## 5. 类职责清单

### 数据/识别/匹配层（`com.example.addon.itemid`）
- `ItemIdentity`：物品身份（itemId + 可选组件模板），可序列化/匹配。
- `ItemIdentifier`：ID识别（从 ItemStack 生成 ItemIdentity）。
- `ItemIdManager`：ID配置管理核心（内存 Set + 持久化 ID 文件，供三者共享）。
- `ItemIdentityMatcher`：按 WithdrawMode 匹配槽位物品。
- `IdIdentifyModule`：ID识别模块（激活识别主手物品，一次性工具）。
- `IdConfigModule`：ID配置管理模块（查看/清空 ID，面板展示清单）。

### 自动箱子层（`com.example.addon.autochest`）
- `AutoChestModule`：模块主类，装配所有服务，tick 驱动。
- `AutoChestSettings`：设置（运行模式/取物模式/扫描/渲染）。
- `AutoChestStateMachine`：状态机（SCAN→SELECT→LOCK→MOVE→OPEN→READ→WITHDRAW→CLOSE→RECORD→NEXT）。
- `AutoChestRenderer`：ESP（未处理绿框、已处理红框）。
- `AutoChestCommand`：标点管理指令（`.autochest add/remove/clear/status`）。
- `model/ScanMode`：玩家控制/寻路/标点 三模式。
- `model/WithdrawMode`：全部取空/仅目标/精确组件匹配。
- `model/ChestTarget`：容器目标快照。
- `model/ContainerRecord`：已处理记录。
- `scan/ContainerScanner`：分帧扫描合法容器。
- `scan/ContainerSelector`：过滤已处理 + 就近选择。
- `service/ChestInteractionService`：开箱/读槽/取物/关箱。
- `service/PathingService`：Baritone 站位寻路。
- `service/ContainerRecordManager`：已处理记录持久化。
- `service/ChestPointManager`：标点持久化。

---

## 6. 集成方案（已完成）

1. `AddonTemplate` 新增 `CATEGORY_ASSIST`，`onRegisterCategories` 注册。
2. `onInitialize` 创建共享 `ItemIdManager`，注册三模块 + `AutoChestCommand`。
3. 数据持久化统一进 `config/yiyiaddon/`：
   - `ids/{server}.json`：ID 配置
   - `autochest/{server}.json`：已处理记录
   - `autochest/points/{server}.json`：标点

---

## 7. 本阶段新增/修改文件

新增（`com.example.addon.itemid`）：
- ItemIdentity.java / ItemIdentifier.java / ItemIdManager.java / ItemIdentityMatcher.java
- IdIdentifyModule.java / IdConfigModule.java

新增（`com.example.addon.autochest` 及子包）：
- AutoChestModule.java / AutoChestSettings.java / AutoChestStateMachine.java / AutoChestRenderer.java / AutoChestCommand.java
- model/ScanMode.java / model/WithdrawMode.java / model/ChestTarget.java / model/ContainerRecord.java
- scan/ContainerScanner.java / scan/ContainerSelector.java
- service/ChestInteractionService.java / service/PathingService.java / service/ContainerRecordManager.java / service/ChestPointManager.java

修改：
- core/AddonTemplate.java（新增分类 + 注册三模块 + 注册指令）

---

## 8. 编译结果

```
BUILD SUCCESSFUL in 3s
4 actionable tasks: 2 executed, 2 up-to-date
```

产物：`yiyiaddon1.1-beta4-personal.jar`（未混淆个人测试版）。

---

## 9. 下一阶段（第二步）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `d:\mcaddon\26.1.2\AGENTS.md`
> 2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. `AutoChestStateMachine.tick()` 实现完整状态转换（含按 ScanMode 分派）。
2. `AutoChestModule.onTick` 按三模式接线（玩家控制/寻路/标点）。
3. `PathingService.computeStandPosition` 精细站位（容器面前约一格可站立坐标）。
4. `ItemIdentity` 组件模板的 NBT 序列化（当前仅 itemId 持久化，精确组件需下一阶段用 ItemStack.save/parse 补全——写前先查 API）。
5. 取物完成判定 + 关箱 + 记录 + ESP 变红的完整闭环。
6. 状态播报按规范 5.9（进入状态 §7正在XXX...，完成 §a✓，带 lastNotifiedState 去重锁）。

> 结束前务必自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
