# AutoChest 辅助体系 · 第五阶段开发报告（容器扫描 / 点位 / 记录 / ESP）

> 日期：2026-08-31
> 阶段：AutoChest 容器扫描、点位、ContainerRecord、ESP、维度/服务器隔离、容器生命周期
> 依赖：第四阶段报告（配置页面 GUI）
> 分类：辅助（未移动到其他分类）

---

## 0. 必读（接手继续开发前强制要求）

接手继续开发前，**必须完整读完以下两个个人开发习惯文件**，不得跳读：

1. `d:\mcaddon\26.1.2\AGENTS.md`（五条铁律 + 关键速查）
2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`（十章规范唯一正本）

关键铁律提醒（下一步开发前逐条对照）：
- 规范注释只增不删；证据驱动排查（Bug 先埋点取证据）；中文 + 日期归档。
- API 先查后写：Mojang 官方映射，查 `node Mappings/工具/查JARAPI.js <类名>`。
- 分类铁律：新功能独立英文包；中文注释；禁 emoji（用 ✓ ✗ ⚠ ▸）。
- 消息规范：前缀走 `YiyiaddonModule.formatMessage`；强调色走 `highlight*`；开关绿 §a / 关红 §c 不颠倒。

---

## 1. 本阶段结论

- 完成 AutoChest「容器扫描 + 点位 + 已处理记录 + ESP + 维度/服务器隔离 + 容器生命周期」全链路，全部中文、禁 emoji。
- **扫描三层缓存**：扫描周期（`scanInterval`）控节奏 + 分帧预算（每 tick 512 格）控开销 + 稳定快照供状态机读取；**绝不整世界每 tick 全扫**。
- **容器合法判定**改为按 `ContainerTypeRegistry` 启用的 `ContainerType` 做 **Block 类型匹配**，不再用粗糙的 `BlockEntity instanceof Container`（排除漏斗、发射器等）。
- **三种模式分派落地**（状态机 `AutoChestStateMachine.tick()`）：玩家控制（不寻路、触发距离内处理）、寻路（就近锁定 + Baritone 寻路）、标点（只读点位）。
- **目标锁**：寻路/标点锁定后不因新扫描结果反复切换；容器被破坏即失效释放。
- **ContainerRecord 完整字段**：服务器/世界 + 维度 + X/Y/Z + 容器类型 + 状态 + 处理时间 + 数据版本；服务器 + 维度 + 坐标三者共同决定身份。
- **ESP 三态**：未处理绿色 / 处理中黄色 / 已处理红色；处理中绝不算已处理红。
- **容器生命周期**：破坏 → 旧记录失效；重新放置（同类或异类）→ 重新识别 → ESP 恢复绿色。

---

## 2. 本阶段新增 / 修改文件

新增：
- `WorldIdentity.java` —— 服务器/维度/数据版本身份工具（统一 server/dimension/dataVersion 与中文维度名）

修改（`com.example.addon.autochest` 及子包）：
- `model/ContainerRecord.java` —— 重写为完整字段（server + dimension + pos + containerType + status + processedAt + dataVersion），新增 `Status` 枚举与类型比对
- `model/ChestTarget.java` —— 增加 `server` / `containerType` 字段（扫描结果与点位共用）
- `model/ContainerTypeRegistry.java` —— 新增 `match(Block)` / `match(Block, List<ContainerType>)` 按启用类型取首个命中
- `service/ContainerRecordManager.java` —— 类型感知的 `isProcessed`、`invalidate`、`find`，持久化含 type/ver
- `service/ChestPointManager.java` —— 点位增删查清，保存 server + containerType，内存与磁盘同步
- `service/ChestInteractionService.java` —— 新增 `hasWithdrawable(mode)`；开箱距离判定放宽为可达距离（4.5 格）
- `scan/ContainerScanner.java` —— 容器类型过滤 + 扫描周期（requestScan 空闲机制）+ 本轮完成信号
- `scan/ContainerSelector.java` —— `isProcessed` 类型感知
- `AutoChestStateMachine.java` —— 完整状态转换 + 按 `ScanMode` 分派 + 目标锁 + 结果播报
- `AutoChestRenderer.java` —— ESP 三态着色（绿/黄/红）
- `AutoChestSettings.java` —— 新增「处理中颜色」设置
- `AutoChestModule.java` —— 扫描周期驱动 + 容器破坏对账 + 处理中目标/播报入口
- `AutoChestCommand.java` —— 设置点位校验合法容器 + 查看信息（服务器/维度/坐标/类型/状态）

---

## 3. 关键实现点

1. **扫描三层缓存**：`ContainerScanner` 由模块按 `scanInterval` 调 `requestScan` 启动一轮，`tick` 每 tick 只扫 `BUDGET_PER_TICK=512` 格（x→z→y 推进），整轮完成后替换 `snapshot` 并进入空闲；状态机/渲染器始终读到稳定快照。
2. **容器合法判定（Block 类型匹配）**：`ContainerTypeRegistry.match(block, enabledTypes)` 按启用类型取首个命中；普通箱子/陷阱箱/铜箱虽同为 `ChestBlockEntity`，但按 `Block` 可区分，选择器语义正确。
3. **三种模式分派**：玩家控制只从触发距离内选目标、不寻路（玩家自己走）；寻路选最近未处理并 `GoalTwoBlocks` 寻路；标点只读 `ChestPointManager` 当前维度点位。目标一旦锁定，`validLocked()` 只校验「维度未切 + 容器仍存在 + 未处理」，不因新扫描切换。
4. **服务器/维度隔离**：记录文件按服务器（`{server}.json`），单条记录内 `server + dimension + pos` 共同判等；维度用 `minecraft:overworld` 稳定标识（`ResourceKey#identifier()`）而非 `ResourceKey.toString()` 包装格式。
5. **类型感知的已处理判定**：`isProcessed(pos, dim, type, expireMs)` 在坐标命中后还会比对容器类型——类型不一致视为「换容器」，旧记录失效并返回未处理。
6. **容器生命周期（破坏→重放）**：模块维护 `lastSeenContainers`，每轮扫描完成时对「仍在本轮范围内但已消失」的位置调 `recordManager.invalidate`，使「破坏 → 重放同类容器」被重新识别为未处理（ESP 恢复绿色）。
7. **ESP 三态**：渲染器按 `processingTarget()`（当前锁定目标）优先判「处理中」用黄框，再按记录判绿/红；处理中绝不算已处理红。
8. **已处理语义闭环**：状态机 OPEN → READ → WITHDRAW → CLOSE → RECORD，`hasWithdrawable` 返回 false（空箱/无目标/背包满）也走正常关闭后记录；只有「开箱成功 + 读取稳定 + 取物完成/确认无需取物 + 正常关闭」才 markProcessed。
9. **点位四操作**：`add`（准星识别合法容器，非容器提示「目标方块不是有效容器」并拒绝保存）/ `remove`（内存+磁盘同步）/ `clear` / `status`（显示服务器、维度、坐标、容器类型、处理状态）。

---

## 4. 编译结果

```
BUILD SUCCESSFUL in 1s
4 actionable tasks: 2 executed, 2 up-to-date
```

产物：`yiyiaddon1.1-beta4-personal.jar`（未混淆个人测试版，版本号未变更）。

---

## 5. 下一阶段（第六阶段）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `d:\mcaddon\26.1.2\AGENTS.md`
> 2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. **取物差额计算（TARGET_COUNT）**：按 `itemQuantities.quantityOf(key)` + 玩家已有量算差额取物，替代当前「粗筛全取目标物品」的过渡实现；`TARGET_EMPTY` 只拿空目标列表物品。
2. `dataComponents` 反序列化回 `DataComponentPatch`，为改名/附魔物品提供组件级精确匹配（当前 `ItemIdentity` 反序列化后组件模板为 null，仅 itemId 粗筛）。
3. 背包满 / 取物中断的重试与「放弃判定」细化（当前背包满直接关箱记已处理）。
4. 真机验证三模式 + ESP 三态 + 容器破坏重放闭环，按第六章协议埋点取证据修正。

> 结束前自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
