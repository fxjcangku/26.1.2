# 自动农场 AutoFarmMatrix 全量重做 — 最终交付报告

> 项目：Minecraft 26.1.2 / Fabric / Meteor Client / Java / Mojang 官方映射
> 模块：自动农场（AutoFarmMatrix）
> 状态：重构完成，编译与构建通过，开发客户端启动验证通过

---

## 一、项目硬环境

- Minecraft 26.1.2（Mojang 官方 mappings，非 Yarn）
- Fabric Loader 0.19.3
- Fabric Loom 1.16.3
- Meteor Client 26.1.2-SNAPSHOT
- Java 25（toolchain）
- Baritone（libs/baritone-fabric-26.1.2.jar）
- 所有玩家可见文本统一中文，无 emoji（仅 Unicode 符号 ✓ ✗ ⚠ ▸）

---

## 二、重做目标

彻底废弃旧的「巨型六状态批次 FSM」，重建为 `Observe → Decide → Act → Verify → Replan` 的单任务独占架构，并在其上增量叠加「单颗 / 批量」双收割模式与「单 / 双作物」智能后勤配置。

明确禁止恢复：旧批量队列、蛇形/Z 字巡逻、watchdog、水源系统、harvestOnly、发包即成功、固定 tick 判成功、只搜热键栏、通用箱子替代专用箱、HUD。

---

## 三、最终架构

核心循环：

```
Observe → Decide → Act → Verify → Replan
```

核心不变量：

- `currentTask != null` 时，只允许该任务执行，Scanner 只观察不建新任务、不抢 Baritone、不打断物流。
- 只有 `currentTask == null` 时 Controller 才允许 Decide 创建下一个任务。
- 每个 Task 内部走 `ACT → WAIT_FOR_UPDATE → VERIFY → RESULT`，不拆独立 Verify 状态。
- 安全级事件（模块 OFF / 退出世界 / 切维度 / 死亡 / 客户端异常）可立即中断。

---

## 四、包结构（com.example.addon.autofarm）

```
autofarm/
├── AutoFarmMatrix.java               # Meteor Module、Settings、生命周期、GUI 点位卡片、启动报告
├── controller/
│   ├── FarmController.java           # 任务 + 批量计划生命周期、串行调度、结果播报
│   ├── FarmObserver.java             # 世界/玩家/背包观察
│   ├── FarmDecision.java             # 物流/收割目标/补种决策
│   ├── FarmVerifier.java             # 统一结果验证（读世界 BlockState）
│   └── FarmSelfCheck.java            # 启动前配置检查
├── task/
│   ├── FarmTask.java                 # 任务接口（tick/exclusive/cancel）
│   ├── TaskResult.java               # 结果枚举
│   ├── HarvestTask.java              # 收割
│   ├── PlantTask.java                # 补种
│   ├── CollectTask.java              # 拾取
│   ├── ContainerTask.java            # 容器任务基类
│   ├── UnloadTask.java               # 卸货
│   ├── RestockTask.java              # 补货
│   └── PoisonDumpTask.java           # 毒马铃薯处理
├── model/
│   ├── FarmTarget.java               # 目标（HARVEST/PLANT + CropProfile + BlockPos）
│   ├── FarmSite.java                 # 点位（BlockPos + 维度）
│   ├── FarmState.java                # 状态枚举（含中文名）
│   ├── SiteType.java                 # 6 类站点
│   ├── CropProfile.java              # 作物规则中心
│   └── HarvestMode.java              # 单颗/批量收割模式
├── resource/
│   └── FarmResourceManager.java      # 安全库存、单/双作物卸货/补货决策
├── scan/
│   └── FarmScanner.java              # 分帧范围扫描
├── navigation/
│   └── FarmNav.java                  # Baritone 隔离层
├── container/
│   └── (复用 farm.ContainerBroker)
├── action/
│   └── (复用 farm.FarmPacketOps)
├── render/
│   └── FarmRenderer.java             # 轻量世界渲染（bounds/target/label）
└── command/
    └── NongChangCommand.java         # .farm 指令
```

> 说明：`ContainerBroker` 与 `FarmPacketOps` 因被 mining / autochest / villager / enchant 跨模块复用，按项目规范保留在 `farm` 包，未迁入 `autofarm/container`、`autofarm/action`，否则会破坏其它模块引用。

---

## 五、作物系统

正式支持 10 种作物，`CropProfile` 是规则中心，每种作物明确：成熟方块、成熟规则、土壤/基底要求、种植物品、收获物品、是否需要补种、特殊收获规则、安全库存关系、毒马铃薯关系。

| 作物 | 类别 | 种植物 | 收获物 | 补种 |
|---|---|---|---|---|
| 小麦 | 双作物 | wheat_seeds | wheat | 是 |
| 胡萝卜 | 单作物 | carrot | carrot | 是 |
| 马铃薯 | 单作物 | potato | potato | 是 |
| 甜菜根 | 双作物 | beetroot_seeds | beetroot | 是 |
| 下界疣 | 单作物 | nether_wart | nether_wart | 是 |
| 竹子 | 柱状 | — | bamboo | 否 |
| 甘蔗 | 柱状 | — | sugar_cane | 否 |
| 仙人掌 | 柱状 | — | cactus | 否 |
| 南瓜 | 果实 | — | pumpkin | 否 |
| 西瓜 | 果实 | — | melon | 否 |

关键判定：

- `isSingleCrop()`：`Kind.CROP` 且 `plantItem == harvestItem`（胡萝卜/马铃薯/下界疣）
- `isDualCrop()`：`Kind.CROP` 且 `plantItem != harvestItem`（小麦/甜菜根）
- 成熟识别基于世界实际 `BlockState`（age 属性 / 果实方块 / 柱状根部），不通过背包种子判断。

南瓜/西瓜特殊规则：

- 只识别 fruit block（pumpkin / melon），stem 不是目标
- 不寻找南瓜/西瓜种子
- 不执行 PlantTask

---

## 六、收割双模式

### 收割模式设置

- `收割模式`（枚举）：`单颗收割`（默认）/ `批量收割`
- `批量收割数量`（1~32，默认 8，`visible` 仅批量模式显示）

### 单颗收割模式

```
Observe → 发现多个成熟作物 → Decision 只选 1 个 FarmTarget
→ HarvestTask → Verify → PlantTask(需补种时) → Verify → CollectTask
→ ResourceCheck → 物流(如需要) → RETURN_FARM → Fresh Observe
```

### 批量收割模式

批量 = 「一次 Decision 锁定多个目标」，**不是并发执行**。

```
Observe → 最多锁定 N 个成熟目标（按距离升序）
→ 第 1 个立即 HarvestTask，其余入 BatchHarvestPlan
→ 每目标 Harvest → Verify → Plant → Verify → Collect 完整闭环
→ 取下一目标（执行前 targetStillValid 校验，无效跳过）
→ 计划耗尽丢弃 → Fresh Observe
```

严格保证：同一时刻 `currentTask` 只有一个，绝不并发、绝不批量发包。

---

## 七、BatchHarvestPlan 生命周期

- 创建 → 串行消耗 → 每个目标执行前校验 → 无效跳过 → 耗尽丢弃
- 以下任一事件立即 `batchPlan = null`：触发物流任务、`NAVIGATION_FAILED`、返回农场到达、玩家死亡、`reset()`（OFF）
- 绝不跨越 Unload / Restock / PoisonDump / 世界切换 / 模块 OFF / 死亡 / 断线

---

## 八、真实验证流程

### Harvest → Verify

1. 检查目标仍存在、仍成熟、距离在交互范围
2. `FarmPacketOps.breakBlock`
3. 等待客户端/服务端世界状态更新（5 tick）
4. `FarmVerifier.harvestSucceeded` 读目标位置 BlockState
5. 确认目标已破坏/变为非成熟，成功才结束任务
6. 失败产生明确 `TaskResult` 并重新规划

### Plant → Verify

1. 找正确 planting item（副手 → 主手 → 快捷栏 → 背包）
2. 临时把材料放到副手，执行使用
3. 等待世界更新
4. `FarmVerifier.plantSucceeded` 验证对应 BlockState
5. 恢复原副手、恢复原选中槽

### Collect

- 不用固定 `collectWait` 判成功
- 检测附近 ItemEntity / 背包数量变化，允许自然拾取
- 不用 Baritone 追远处掉落物，超范围忽略，掉落被他人拾取不判失败

### 容器 Verify

- `ContainerBroker.isBoundContainer(BlockPos)` 比对箱子侧 slot 的 `container` 引用与目标 `BlockEntity` 身份
- 区分：打开成功 / 同步成功 / Deposit 成功 / Withdraw 成功 / 无目标物 / 箱满 / 箱空 / 容器异常 / 开错容器

---

## 九、单/双作物智能后勤配置

GUI 通过 `CropProfile.isSingleCrop()/isDualCrop()` 智能识别，用 `visible()` 动态显示 4 个加减框设置：

| 启用类型 | 设置 | 默认 | 范围 | 语义 |
|---|---|---|---|---|
| 单作物 | 单作物保留数量 | 3组 | 1~10 | 保留多少组补种，超出才卸货 |
| 单作物 | 单作物卸货数量 | 20组 | 1~36 | 产物(即种子)满多少组触发卸货 |
| 双作物 | 双作物种子补货 | 3组 | 1~10 | 种子保留多少组，低于补货 |
| 双作物 | 双作物农作物卸货 | 20组 | 1~36 | 农作物产物满多少组触发卸货 |

后端：

- `FarmResourceManager.safetyStock(crop)` 按单/双作物取对应保留组数
- `FarmDecision` 卸货触发：单作物达单作物卸货数量 或 双作物达双作物农作物卸货 或 背包快满

---

## 十、单/双/三作物专用箱体系（6 点位）

`SiteType` 6 类：

| 点位 | 中文显示 |
|---|---|
| START | 农场点位 1 |
| END | 农场点位 2 |
| SINGLE_STORAGE | 单作物箱 |
| DUAL_STORAGE | 双作物箱 |
| TRIPLE_STORAGE | 三作物箱 |
| POISON_STORAGE | 毒马铃薯箱 |

统一来源 `SiteType.cropStorageFor(启用数量)`：

- 0 作物 → SelfCheck 失败
- 1 作物 → SINGLE_STORAGE
- 2 作物 → DUAL_STORAGE
- 3 作物 → TRIPLE_STORAGE

毒马铃薯始终走 POISON_STORAGE，禁止进入任意普通作物箱。

专用箱同时承担 Deposit + Withdraw。对应箱子按 `CropProfile` 与 `FarmResourceManager` 判断哪些资源保留、哪些卸货。

---

## 十一、启动自检（FarmSelfCheck）

开启模块时读取：启用作物、作物数量、农场点位、当前维度、农场范围、对应专用箱、毒马铃薯箱、Baritone 状态。

- 0 种：失败
- 1 种：要求单作物箱 + 毒马铃薯箱 + 农场点位1/2
- 2 种：要求双作物箱 + 毒马铃薯箱 + 农场点位1/2
- 3 种：要求三作物箱 + 毒马铃薯箱 + 农场点位1/2
- 缺失项一次性列出

自检失败不启动 Controller / Baritone / 扫描 / 箱子操作。

> 自检（配置能否启动）与运行时资源检查（FarmResourceManager）严格分离：「玩家身上没有种子」不算启动失败，属于运行时补货。

---

## 十二、状态机与任务结果

`FarmState`：OBSERVE / HARVEST / PLANT / COLLECT / RESOURCE_CHECK / UNLOAD / RESTOCK / POISON_DUMP / RETURN_FARM

`TaskResult`：IN_PROGRESS / SUCCESS / TARGET_INVALID / HARVEST_FAILED / PLANT_FAILED / RESOURCE_INSUFFICIENT / CONTAINER_MISSING / CONTAINER_FULL / CONTAINER_EMPTY / CONTAINER_SYNC_FAILED / CONTAINER_OPEN_FAILED / NAVIGATION_FAILED / POISON_CONTAINER_FULL / DIMENSION_MISMATCH / FARM_AREA_INVALID / CANCELLED

---

## 十三、状态播报体系（参考自动村民交易）

- **启动报告**：合并多行消息块，正文 `标签 §8▸ 值` 对齐，只带一次模块前缀
- **进度播报**：物流状态进入时 `§7正在卸货...`（`lastNotifiedState` 去重锁；收割/补种/拾取高频不播防刷屏）
- **结果播报**：`FarmController.setLogger` 回调，物流完成 `§a✓ 卸货完成` / 失败 `§c✗ 卸货失败`
- **批量进度**：`§b批量收割 §8▸ 剩余 N 个目标`（`lastBatchProgress` 去重锁）
- **批量完成**：`§a✓ 本轮批量收割完成`

---

## 十四、文件清单

### 新增（29 个，位于 com.example.addon.autofarm）

AutoFarmMatrix、model/{HarvestMode,CropProfile,FarmState,FarmTarget,FarmSite,SiteType}、controller/{FarmController,FarmDecision,FarmObserver,FarmVerifier,FarmSelfCheck,BatchHarvestPlan}、task/{FarmTask,TaskResult,HarvestTask,PlantTask,CollectTask,ContainerTask,UnloadTask,RestockTask,PoisonDumpTask}、resource/FarmResourceManager、scan/FarmScanner、navigation/FarmNav、render/FarmRenderer、command/NongChangCommand

### 修改（3 个）

- farm/FarmPacketOps.java — 删 getFortuneLevel 死代码，保留跨模块发包方法
- farm/ContainerBroker.java — 删 chestSlotCount 死代码，新增 isBoundContainer
- core/AddonTemplate.java — import 指向新包

### 删除（9 个旧架构）

modules/AutoFarmMatrix、commands/NongChangCommand、farm/{FarmState,FarmSite,SiteType,CropProfile,FarmScanner,FarmNav,FarmRenderer}

---

## 十五、旧代码删除清单

六状态 FSM（STANDBY / NUKE_FARMING / COLLECTING / JUDGMENT / UNLOADING / RESTOCKING）、patrolRoute、patrolIndex、蛇形/Z 字巡逻、watchdog、watchdogStrikes、MAX_WATCHDOG_STRIKES、固定 collectWait 判成功、发包即成功、只搜热键栏、水源扫描/覆盖/渲染、HUD、海带、甜浆果、南瓜/西瓜 stem 目标、远距离追掉落物。

死代码：NongChangCommand.handleDefault、FarmPacketOps.getFortuneLevel、FarmRenderer.countMoistenedFarmland、FarmRenderer.waterCapacity、ContainerBroker.chestSlotCount、CropProfile.all、无用 import、emoji 文案。

---

## 十六、编译与验证结果

- `.\gradlew.bat compileJava --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**
- `.\gradlew.bat buildPersonal --console=plain` → **BUILD SUCCESSFUL**
- `GetDiagnostics` → 无诊断信息
- `runClient` 开发客户端启动 → Mixin 注入成功 / addon 初始化成功 / Meteor 加载成功 / Baritone 可用 / 无崩溃

---

## 十七、旧逻辑残留检查

全项目搜索以下关键词均 `No matches found`：

```
workHarvest / workPlant / processedHarvest / processedPlant / harvestBuffer / plantBuffer
harvestQueue / plantQueue / completedSweeps / scanRound / currentRound / roundFinished
batchFinished / harvestOnly / restockCooldownTicks / collectWait / serpentine
patrolRoute / patrolIndex / watchdog / Watchdog / watchdogStrikes / MAX_WATCHDOG_STRIKES
renderWaterRange / waterRangeColor / waterRangeShapeMode / waterMaxSources
countMoistenedFarmland / waterCapacity / getFortuneLevel / handleDefault
unloadThreshold / seedSafetyStock / depositableStacks() / safetyStockGroups
```

新功能关键词全部就位：HarvestMode / SINGLE / BATCH / batchPlan / BatchHarvestPlan / batchCount / isSingleCrop / isDualCrop。

---

## 十八、下一步计划

1. 实机验证：绑定点位、单/双/三作物切换、批量进度播报、容器身份校验。
2. 重点回归：`isBoundContainer` 在静默容器模式下的可靠性；Baritone 不可用时 NAVIGATION_FAILED 清计划不卡死；三作物混合卸货触发。
3. 可选增强（需确认）：逐颗收割结果播报（评估批量刷屏）；帮助文档补充单/双作物卸货说明。
4. 发布前复核：`buildOfficial` 混淆时 `HarvestMode` 枚举 `name()` 持久化不受影响。
