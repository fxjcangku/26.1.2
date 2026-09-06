# AutoChest 辅助体系 · 第四阶段开发报告（AutoChest 配置页面 GUI）

> 日期：2026-08-31
> 阶段：AutoChest 配置页面（模式下拉 + 当前模式实时显示 + 模式专属配置 + 容器选择器 + 检测范围 + 触发距离 + 目标物品选择器 + 三种取物模式 + 每种物品独立数量）
> 依赖：第三阶段报告（ItemTargetSetting / ItemIdManager / AutoChest 联动）
> 分类：辅助（未移动到其他分类）

---

## 0. 必读（接手继续开发前强制要求）

接手继续开发前，**必须完整读完以下两个个人开发习惯文件**，不得跳读：

1. `d:\mcaddon\26.1.2\AGENTS.md`（五条铁律 + 关键速查）
2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`（十章规范唯一正本）

关键铁律提醒（下一步开发前逐条对照）：
- 规范注释只增不删；证据驱动排查（Bug 先埋点取证据）；中文 + 日期归档。
- API 先查后写：Mojang 官方映射，查 `node 03-映射表/工具/查JARAPI.js <类名>`。
- 分类铁律：新功能独立英文包；中文注释；禁 emoji（用 ✓ ✗ ⚠ ▸）。
- 消息规范：前缀走 `YiyiaddonModule.formatMessage`；面板按钮走 `addUniformButton`；强调色走 `highlight*`；开关绿 §a / 关红 §c 不颠倒。

---

## 1. 本阶段结论

- 完成 AutoChest「配置页面」完整 GUI，全部中文、禁 emoji，仅限「辅助」分类。
- 运行模式用**下拉选择**（EnumSetting → WDropdown），三种模式：玩家控制模式 / 寻路模式 / 标点模式；下方**实时显示**「当前模式：X」。
- **只显示当前模式相关配置**：三组模式配置互斥，靠 `.visible()` 按 `scanMode` 动态显隐，切换模式自动重建面板。
- 容器类型选择器**可扩展**：`ContainerTypeRegistry` 内置箱子 / 陷阱箱 / 16色潜影盒 / 木桶 / 铜箱，新增类型只需 `register` 一条，不改扫描核心。
- 检测范围（搜索用）与触发距离（玩家控制模式交互用）**拆成两个独立设置**，不混用。
- 取物模式重定义为三种：按目标数量取 / 目标物品拿空 / 全部拿空；「按目标数量取」为每种目标物品**独立配置数量**。
- 全部拿空模式忽略目标列表（目标物品选择器在该模式下自动隐藏）。

---

## 2. 本阶段新增 / 修改文件

新增（`com.example.addon.autochest` + `autochest.model`）：
- model/ContainerType.java —— 容器类型模型（id + 中文名 + 方块匹配谓词）
- model/ContainerTypeRegistry.java —— 容器类型注册表（内置 5 种 + 可扩展）
- ContainerTypeSetting.java —— 容器类型选择器设置（继承 StringListSetting，自定义控件）
- ContainerTypeSelectScreen.java —— 容器类型多选界面（遍历注册表，点击切换）
- InfoTextSetting.java —— 纯展示信息设置（当前模式 / 标点提示，不参与持久化）
- ItemQuantitySetting.java —— 每种目标物品数量设置（身份键 → 目标数量）
- ItemQuantityScreen.java —— 每种物品数量配置界面（目标项 + 数量输入框）

修改：
- autochest/model/ScanMode.java —— 显示名改为「玩家控制模式 / 寻路模式 / 标点模式」
- autochest/model/WithdrawMode.java —— 重定义为「按目标数量取 / 目标物品拿空 / 全部拿空」
- autochest/AutoChestSettings.java —— 重写为按模式动态呈现的完整配置页
- autochest/AutoChestModule.java —— 使用说明同步新模式名
- itemid/ItemIdentityMatcher.java —— 适配新 WithdrawMode（TARGET_EMPTY / TARGET_COUNT 合并为 isTarget 粗筛）
- itemid/ItemTargetSetting.java —— 增加 IVisible 构造重载（供模式/取物模式显隐）
- core/AddonTemplate.java —— 注册 InfoTextSetting / ContainerTypeSetting / ItemQuantitySetting 三个控件工厂

---

## 3. 配置页结构（设置面板分组）

```
运行模式
  ├─ 运行模式        [下拉：玩家控制模式 / 寻路模式 / 标点模式]
  └─ 当前模式        [实时显示当前模式]
玩家控制模式（仅玩家控制模式显示）
  └─ 触发距离
寻路模式（仅寻路模式显示）
  └─ 到达判定距离
标点模式（仅标点模式显示）
  └─ 标点管理        [提示 .autochest add / remove]
容器
  ├─ 容器类型        [选择器：箱子/陷阱箱/16色潜影盒/木桶/铜箱]
  ├─ 检测范围
  ├─ 扫描周期
  └─ 已处理记录过期
目标物品（全部拿空模式下隐藏）
  └─ 目标物品        [选择器，数据源唯一来自 ItemIdManager]
取物
  ├─ 取物模式        [下拉：按目标数量取 / 目标物品拿空 / 全部拿空]
  ├─ 每种物品数量    [按目标数量取专属，逐项配数量]
  └─ 动作延迟
渲染
  ├─ ESP高亮
  ├─ 未处理颜色
  └─ 已处理颜色
```

---

## 4. 关键实现点

1. **模式下拉 + 当前模式实时显示**：`scanMode` 用 `EnumSetting<ScanMode>`（Meteor 自动渲染下拉）；下方
   `InfoTextSetting` 提供 `Supplier<String>`，配合 `.visible()` 联动触发 `Settings.tick()` 重建面板，
   无需 onRender 轮询即可刷新「当前模式」文字。
2. **模式专属配置互斥显隐**：触发距离（PLAYER_CONTROL）、到达判定距离（PATHING）、标点提示（MARKER）
   分别 `.visible(() -> scanMode.get() == ...)`；切换模式时相关项可见性翻转，自动重建，三组不堆叠。
3. **检测范围 / 触发距离分离**：`scanRadius`（检测范围，负责发现容器）与 `triggerDistance`（触发距离，
   玩家控制模式交互）是两个独立 IntSetting，语义不混用。
4. **容器选择器可扩展**：`ContainerTypeRegistry` 用 `Predicate<Block>` 匹配方块类型（普通箱子需排除
   `TrappedChestBlock` / `CopperChestBlock`，二者 BlockEntity 同为 ChestBlockEntity 只能按 Block 区分）；
   铜箱已核实 26.1.2 存在 `CopperChestBlock`（`WeatheringCopperChestBlock` 继承它，各氧化阶段一并覆盖）。
   新增容器类型仅 `register` 一条，选择器与扫描核心无需改动。
5. **目标物品选择器**：复用第三阶段 `ItemTargetSetting`（只存身份键，运行时回查 `ItemIdManager`），
   显示中文名；新增 IVisible 重载以支持「全部拿空时隐藏」。
6. **三种取物模式**：`WithdrawMode` 重定义为 TARGET_COUNT / TARGET_EMPTY / TAKE_ALL；
   `ItemIdentityMatcher` 对 TARGET_EMPTY 与 TARGET_COUNT 统一走 `isTarget` 粗筛（差额计算留待消费层）。
7. **每种物品独立数量**：`ItemQuantitySetting` 存「身份键 → 目标数量」映射，`ItemQuantityScreen` 列出
   目标选择器选中项逐项配数量；`quantityOf` 未配置默认 64；数量语义为「玩家最终想持有的总数」，差额
   计算交由状态机（下一阶段消费层）。

---

## 5. 编译结果

```
BUILD SUCCESSFUL in 2s
4 actionable tasks: 2 executed, 2 up-to-date
```

产物：`yiyiaddon1.1-beta4-personal.jar`（未混淆个人测试版，版本号未变更）。

---

## 6. 下一阶段（第五阶段）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `d:\mcaddon\26.1.2\AGENTS.md`
> 2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. **消费层接线**：状态机按 `scanMode` 分派（玩家控制 / 寻路 / 标点），用
   `containerTypes.enabledTypes()` 过滤扫描结果，用 `scanRadius` / `triggerDistance` / `arriveDistance`
   驱动扫描与交互。
2. **取物差额计算**：`TARGET_COUNT` 模式下按 `itemQuantities.quantityOf(key)` + 玩家已有量算差额取物；
   `TARGET_EMPTY` 只拿空目标列表物品；`TAKE_ALL` 忽略目标列表取走容器所有合法物品。
3. `dataComponents` 反序列化回 `DataComponentPatch`，为改名/附魔物品提供组件级精确匹配。
4. `AutoChestStateMachine.tick()` 完整状态转换 + 按 `ScanMode` 分派。
5. 取物完成判定 + 关箱 + 记录 + ESP 变红闭环。

> 结束前自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
