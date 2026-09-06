# AutoChest 辅助体系 · 第七阶段开发报告（寻路与保护机制）

> 日期：2026-08-31
> 阶段：AutoChest 寻路（真实安全站位）、后台挂机、多人保护、目标锁、有限重试、临时冷却
> 依赖：第六阶段报告（核心容器交互 / 精确取物）
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

- 完成 AutoChest「寻路 / 后台挂机 / 多人保护 / 目标锁 / 有限重试 / 临时冷却」全链路，全部中文、禁 emoji。
- **真实安全站位寻路**：`PathingService.computeStandPosition` 按四个水平方向找「脚下完整碰撞方块 + 脚部/头部可通行」的可站立格，`GoalBlock` 精确寻路到站位；找不到才退化 `GoalTwoBlocks`。绝对禁止把容器中心坐标当最终站位。
- **目标锁落地**：状态机持单一 `lockedTarget`，`未处理 → 处理中 → 已处理` 三态；扫描运行期间「处理中」绝不重复入队，锁定后不因新扫描结果频繁更换目标。
- **多人保护**：`hasNearbyPlayer` 遍历 `mc.level.players()`（跳过自身与旁观者），其他玩家进入检测距离即视为「正在用箱」——不抢、不强制操作，临时跳过进入冷却，绝不标记已处理。
- **有限重试 + 临时冷却**：开箱/寻路/容器意外关闭共用 `retryCount` 计数，达到 `最大重试次数` 即 `skipWithCooldown`（记录冷却截止毫秒，释放锁定，不记已处理），杜绝无限卡在同一箱子。
- **后台运行 / 玩家操作保护**：全程走客户端内部 API（`FarmPacketOps` 发包 + Baritone 内部寻路），不模拟鼠标键盘、不抢窗口焦点、不强制前台；玩家手动关模块 → `onDeactivate` 立即停寻路、关容器、重置状态机。
- **防刷操作**：交互服务延续「发包 → 等 stateId 稳定 → 读结果」节奏（`actionCooldown` / `isSynced`），寻路用 `isPathing` + 超时限流重发，不无确认连续发包。

---

## 2. 本阶段新增 / 修改文件

修改（`com.example.addon.autochest`）：

- `AutoChestSettings.java` —— 新增「保护」分组：多人保护开关、玩家检测距离、最大重试次数、临时冷却时长
- `service/PathingService.java` —— 实现真实安全站位计算（`computeStandPosition` + `isStandable`），寻路改 `GoalBlock(站位)`，`stop()` 清目标使 `isPathing` 立即回落，删除死方法 `isActive`
- `AutoChestStateMachine.java` —— 目标锁 + 多人保护 + 有限重试 + 临时冷却 + 到达确认 + 冷却过滤选目标
- `AutoChestModule.java` —— 使用说明新增「保护机制」小节

---

## 3. 关键实现点

1. **真实安全站位（禁止容器中心为最终站位）**：`computeStandPosition` 依次尝试容器同层 / 下一层 / 上一层的四个水平相邻格，`isStandable` 判「脚下为完整碰撞方块、脚部与头部均非完整碰撞方块」，命中即返回；寻路 `GoalBlock(stand)`，无安全站位才 `GoalTwoBlocks(chestPos)` 兜底（其语义保证不站在容器方块内）。

2. **寻路生命周期**：`LOCK` 立即 `pathTo` 发起首次寻路（失败 → `skipWithCooldown("寻路不可用")` 不卡死）；`MOVE` 用 `isPathing()` 判定是否在前进——到达邻接即 `stop()` 转 `OPEN`，未在寻路则限流重发，超过 `MOVE_TIMEOUT_TICKS` 记一次失败走有限重试。

3. **目标锁**：`tick()` 仅在 `lockedTarget == null && state != NEXT` 时选新目标；锁定后 `validLocked()` 每 tick 校验「维度未切换 / 容器仍存在 / 未记录已处理 / 无他人占用」，失效才释放锁。`retryCount` 在锁定新目标时清零。

4. **多人保护**：`hasNearbyPlayer(pos)` 以 `玩家检测距离`（默认 3 格）为半径判他人是否在容器附近，旁观者跳过；命中时在 `validLocked` 与 `filterAvailable` 双处拦截——选目标时直接过滤、处理中命中则 `skipWithCooldown` 释放锁，均不写「已处理」。

5. **有限重试**：`reachedMaxRetries(reason)` 统一累加 `retryCount`，未达上限播「§e⚠ 重试 x/y」并让调用方重置该环节状态重试；达到上限转 `skipWithCooldown`（跳过 + 冷却 + 释放锁）。开箱超时、寻路超时、取物中容器意外关闭三处接入。

6. **临时冷却**：`cooldowns` 表以 `维度:x,y,z` 为键存冷却截止毫秒，`skipWithCooldown` 写入，`filterAvailable` 过滤冷却中容器，冷却结束自动重新检测；`reset()` 清空冷却表。

7. **到达确认**：`MOVE` 邻接判定（切比雪夫 ≤ 1）满足即停止移动；`OPEN` 内部再以 `OPEN_REACH` 距离 + `isOpen()` 二次确认「位置 / 距离 / 可交互」，满足才进入读写。

8. **后台挂机 / 玩家操作保护**：交互走 `FarmPacketOps.interactBlock` 发包、移动走 Baritone 内部 API，无 `Robot`/键鼠模拟/`setScreen` 抢焦点；`onDeactivate` 停寻路、关容器、重置状态机，玩家主动关闭立即停止任务。

9. **冷却表生命周期**：`skipWithCooldown` 写入、`isCoolingDown` 惰性判定，`reset()` 清空；模块关闭/切服即释放，避免内存无限增长。

---

## 4. 编译结果

```
BUILD SUCCESSFUL in 1s
4 actionable tasks: 2 executed, 2 up-to-date
```

产物：`yiyiaddon1.1-beta4-personal.jar`（未混淆个人测试版，版本号未变更）。

---

## 5. 下一阶段（第八阶段）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `d:\mcaddon\26.1.2\AGENTS.md`
> 2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. **真机验证**：三模式寻路站位准确性、多人保护触发距离、有限重试/冷却节奏、后台挂机稳定性，按第六章协议埋点取证据修正。
2. **多人并发抢箱细化**：精确取物会话中途其他玩家操作容器导致 stateId 剧烈变化的放弃判定与冷却联动。
3. **站位可达性校验**：`computeStandPosition` 与容器真实朝向/可达面的对齐（潜影盒 / 木桶朝向差异），必要时按朝向选站位。

> 结束前自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
