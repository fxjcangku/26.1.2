# AutoChest 辅助体系 · 最终验收报告

> 日期：2026-09-01
> 阶段：三功能整合 + 完整状态机落地 + 最终验收
> 依赖：第一～第七阶段全部报告
> 分类：辅助（CATEGORY_ASSIST）

---

## 0. 结论

- AutoChest 三功能（ID识别 / ID配置管理 / 自动箱子）已完成整合，全部中文、禁 emoji、统一「辅助」分类。
- 状态机升级为 **18 个清晰状态**的完整状态机，废除旧版 9 状态与零散布尔量驱动，单一 `State` 枚举推进全流程。
- 取物三模式（按目标数量取 / 目标物品拿空 / 全部拿空）全部接入核心取物引擎，共享同一套「真实 Slot + 组件级匹配 + 差额计算」逻辑。
- **`gradlew clean build` 通过，产物 `yiyiaddon1.1-beta4-personal.jar` 生成成功。**
- 全部满足「Minecraft 26.1.2 / Fabric / Meteor Client」约束，辅助三功能职责独立、数据联动。

---

## 1. 最终文件结构

```
com.example.addon.autochest/           自动箱子（消费层）
├── AutoChestModule.java                模块主类（装配服务 + tick 驱动 + 广播 + 停机入口）
├── AutoChestSettings.java              配置（运行/保护/容器/目标/取物/渲染）
├── AutoChestStateMachine.java          完整状态机（18 状态）★ 本阶段重写
├── AutoChestRenderer.java              ESP 三态渲染（绿/黄/红）
├── AutoChestCommand.java               标点管理指令（.autochest add/remove/clear/status）
├── WorldIdentity.java                  服务器/维度/数据版本身份工具
├── ContainerTypeSetting.java           容器类型选择器设置
├── ContainerTypeSelectScreen.java      容器类型选择界面
├── InfoTextSetting.java                纯展示信息设置（当前模式）
├── ItemQuantitySetting.java            每种目标物品数量设置
├── ItemQuantityScreen.java             每种物品数量配置界面
├── model/
│   ├── ChestTarget.java                容器目标快照
│   ├── ContainerRecord.java            已处理记录
│   ├── ContainerType.java              容器类型模型
│   ├── ContainerTypeRegistry.java      容器类型注册表（可扩展）
│   ├── ScanMode.java                   三种运行模式
│   └── WithdrawMode.java               三种取物模式
├── scan/
│   ├── ContainerScanner.java           分帧扫描（周期 + 预算 + 快照）
│   └── ContainerSelector.java          就近选择（过滤已处理）
└── service/
    ├── ChestInteractionService.java    开箱/读槽/匹配/取物/关箱 ★ 本阶段增强
    ├── ChestPointManager.java          标点持久化
    ├── ContainerRecordManager.java     已处理记录持久化
    └── PathingService.java             安全站位寻路

com.example.addon.itemid/               ID 识别 + 配置管理（数据源）
├── IdIdentifyModule.java               ID识别模块（第一环）
├── IdConfigModule.java                 ID配置管理模块（第二环）
├── IdCommand.java                      .id 物品 / .id 实体 识别指令
├── ItemIdentifier.java                 物品/实体识别核心
├── ItemIdentity.java                   物品身份（可序列化/匹配）
├── ItemIdManager.java                  ID 配置管理核心（唯一数据源）
├── ItemIdentityMatcher.java            身份匹配工具
├── ItemTargetSetting.java              AutoChest 目标物品选择器
├── ItemTargetSelectScreen.java         目标物品多选界面
├── IdAddScreen.java                    手动添加物品 ID 界面
├── EntityIdentity.java                 实体身份模型
└── EntityIdManager.java                实体身份存储
```

---

## 2. 新增文件

第一阶段～第七阶段新增（`com.example.addon.autochest` 及 `itemid`）：

- 全部 `autochest/`（含 model/ scan/ service/）与 `itemid/` 下 30 个类均为本辅助体系新增。
- 本最终阶段**无新增文件**，仅对既有文件做整合与状态机升级。

---

## 3. 修改文件

本最终阶段修改：

- `autochest/AutoChestStateMachine.java` —— 9 状态 → 18 状态完整状态机；新增致命条件（死亡/换维度）检测、模式切换清理、`STOPPED` 安全停机态、`FAILED` 重试态、`MATCHING` 只读匹配态；删除死方法 `reachedMaxRetries`。
- `autochest/service/ChestInteractionService.java` —— 新增 `MatchResult` 枚举与只读 `matchOnce(mode)`；抽取 `findTarget(...)` 统一「匹配 + 差额 + 空间确认」判据，供匹配阶段与取物阶段复用，消除逻辑漂移。

---

## 4. 三个辅助功能关系

```
辅助（CATEGORY_ASSIST）
├── ID识别          IdIdentifyModule   → 产出 ItemIdentity，写入 ID 配置
├── ID配置管理       IdConfigModule     → 管理已识别/手动添加的 ID
└── 自动箱子         AutoChestModule    → 消费 ID 配置，扫描容器取物
```

三模块共享**同一个 `ItemIdManager` 实例**（`AddonTemplate` 创建后注入），职责独立、数据同源。AutoChest 只消费 ID 配置，绝不建立第二套物品数据库。

---

## 5. ID 数据流

```
ID识别（IdIdentifyModule）
   │  手持物品 → ItemIdentifier.identifyItem → ItemIdentity
   ▼
ID 文件（config/yiyiaddon/autochest/items/{中文名}.json） ← ItemIdManager 持久化
   │
ID配置管理（IdConfigModule / ItemIdManager）
   │  增删查、清空、加载
   ▼
AutoChest 物品选择器（ItemTargetSetting → selectedIdentities）
   │  只存身份键，运行时 findByKey 回查完整 ItemIdentity
   ▼
ItemIdentityMatcher.matchTarget(stack, targets)
   │  ItemIdentity.matches() 组件级精确匹配
   ▼
真实容器 Slot（ChestInteractionService.findTarget）
   ▼
自动取物（handleContainerInput QUICK_MOVE / PICKUP）
```

---

## 6. ItemIdentity 结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| itemId | String | 物品 ID（`minecraft:diamond`） |
| displayName | String | 中文显示名（改名取自定义名） |
| baseName | String | 默认中文名（区分改名） |
| customName | String | 自定义名（未改名为 null） |
| dataVersion | int | Minecraft 数据版本 |
| enchantments | List\<EnchantmentEntry\> | 附魔 ID + 中文名 + 等级 + 显示名 |
| dataComponents | String | DataComponentPatch 序列化 JSON |
| item / componentTemplate | 瞬态 | 注册表项 / 组件模板（精确匹配用，不落盘） |

身份判定（`equals` / `identityKey`）以 `itemId + customName + enchantments` 为准，普通钻石与超级钻石不相等。

---

## 7. Data Component 实现

- 识别：`DataComponentPatch.CODEC.encodeStart(registryAccess.createSerializationContext(JsonOps.INSTANCE), patch)` → JSON。
- 反序列化：`DataComponentPatch.CODEC.parse(...)` 还原补丁 → `applyComponents` 重建组件模板，恢复 `isSameItemSameComponents` 精确匹配；注册表未就绪优雅退化为 itemId 粗筛。
- 全程 26.1.2 Data Component API，未使用旧 NBT（CompoundTag/ListTag）方案。

---

## 8. 改名物品处理

- 读 `DataComponents.CUSTOM_NAME`，与默认名分离，`isRenamed()` 判定改名。
- 匹配不比较中文显示名，走 `ItemStack.isSameItemSameComponents(componentTemplate, stack)`（有模板时）或 `stack.is(targetItem)`（无模板兜底）。
- 附魔双来源：`DataComponents.ENCHANTMENTS`（装备）+ `DataComponents.STORED_ENCHANTMENTS`（附魔书）。

---

## 9. ID 实时同步

`ItemIdManager` 维护监听器列表，`add/remove/clear/reload` 后 `notifyChanged()` 广播；`AutoChestModule` 构造时注册监听器，回调 `pruneInvalid()` 清理失效选中项并提示「ID 已失效」。识别新物品后点击「识别物品」即可生成，AutoChest 立即可用「+ 添加物品」，无需重启。

---

## 10. AutoChest GUI

- 运行模式：`EnumSetting<ScanMode>` 下拉（玩家控制模式 / 寻路模式 / 标点模式）。
- 下方 `InfoTextSetting` 实时显示「当前模式：X」。
- 模式专属配置互斥显隐（触发距离 / 到达判定距离 / 标点提示）。
- 容器类型选择器 / 目标物品选择器 / 每种物品数量均为独立设置控件。
- 全部中文、禁 emoji；GUI 操作直接调用 Service，不模拟聊天框输入。

---

## 11. 三种运行模式

| 模式 | 目标来源 | 移动方式 |
| --- | --- | --- |
| 玩家控制模式 | 触发距离内扫描 | 玩家自己走，模块不寻路 |
| 寻路模式 | 最近未处理容器 | 计算安全站位 + Baritone 寻路 |
| 标点模式 | 仅 `.autochest` 保存点位 | 计算安全站位 + Baritone 寻路 |

三种模式互斥，模式切换时 `onModeSwitch()` 立即清理旧模式状态（停寻路/关箱/释放锁），绝不同时执行。

---

## 12. 容器选择器

`ContainerTypeRegistry` 内置：箱子、陷阱箱、16色潜影盒、木桶、铜箱。按 `Block` 类型匹配（普通箱/陷阱箱/铜箱同为 ChestBlockEntity，只有按 Block 能区分）。新增类型仅 `register` 一条，扫描核心与选择器无需改动。

---

## 13. 检测范围

`scanRadius`（默认 16，可调 4~64）负责发现容器，配合「扫描周期 + 分帧预算（512 格/tick）+ 稳定快照」三层缓存，**绝不每 tick 全世界扫描**。

---

## 14. 触发距离

`triggerDistance`（默认 4，可调 1~16）独立设置，仅玩家控制模式用于判定「进入触发距离即自动开箱」，与检测范围语义分离。

---

## 15. 三种取物模式

| 模式 | 行为 |
| --- | --- |
| 按目标数量取 | 每种目标物品独立配数量，按 `目标数量 - 玩家已有量` 差额取 |
| 目标物品拿空 | 只拿空目标列表命中的物品 |
| 全部拿空 | 忽略目标列表，取走容器内所有能合法放入背包的物品 |

三种模式均已接入 `findTarget` 核心取物引擎；`TAKE_ALL` 不走目标匹配，遍历所有真实非空容器槽位。

---

## 16. 真实 Slot 处理

`kindOf(slot, inventory)` 三分类：容器侧以 `slot.container != 玩家背包` 判定，玩家侧按 `Inventory.isHotbarSlot` 细分快捷栏/主背包。取物只遍历 `CONTAINER`，对箱子/陷阱箱/潜影盒/木桶/铜箱统一生效，不写死槽位号。

---

## 17. 开箱 / 取物 / 关箱流程

```
OPENING      发包开箱（FarmPacketOps.interactBlock，可达距离校验）
   ↓ isOpen
READING_SLOTS 等 stateId 稳定（ContainerBroker.isReady）
   ↓ isSynced
MATCHING      matchOnce 只读匹配 → TAKABLE / DONE / INVENTORY_FULL
   ↓
TAKING        withdrawOnce（shift 整叠 / 精确差额跨 tick）
   ↓ FINISHED
VERIFYING → CLOSING → COMPLETED（markProcessed）
```

---

## 18. 点位系统

`.autochest add / remove / clear / status`：add 校验准星方块为启用合法容器（非容器拒绝），保存服务器/维度/坐标/类型；status 显示服务器、维度、XYZ、容器类型、处理状态。内存与磁盘同步。

---

## 19. ESP

三态着色：未处理绿、已处理红、处理中黄。渲染器优先以 `processingTarget()` 判「处理中」，处理中绝不显示为已处理红。

---

## 20. 已处理记录

`ContainerRecord` 完整字段：服务器 + 维度 + X/Y/Z + 容器类型 + 状态 + 处理时间 + 数据版本。只有「开箱成功 + 读取稳定 + 取物完成/确认无需取物 + 正常关闭」才 `markProcessed`；容器意外关闭、背包满均不记已处理。

---

## 21. 服务器 / 维度隔离

记录文件按服务器（`{server}.json`），单条记录内以 `维度 + 坐标 + 容器类型` 判等；服务器 + 维度 + 坐标三者共同决定身份，不同服务器/维度即使 XYZ 相同也不共用记录。

---

## 22. 目标锁

状态机持单一 `lockedTarget`，锁定后 `validLocked()` 每 tick 校验（维度未切 + 容器仍存在 + 未处理 + 无他人占用），失效才释放；`retryCount` 锁定新目标时清零，不因新扫描结果频繁更换。

---

## 23. 多人保护

`hasNearbyPlayer` 遍历 `mc.level.players()`（跳过自身与旁观者），其他玩家进入检测距离即视为「正在用箱」——不抢、不强制操作，临时跳过进入冷却，绝不标记已处理。

---

## 24. 有限重试

`retryCount` 跨开箱/寻路/交互/关闭累计，达到 `最大重试次数` 即 `skipWithCooldown`（跳过 + 冷却 + 释放锁，不记已处理）；未达上限进入 `FAILED` 态短暂停顿后回跳对应环节重试，杜绝无限卡箱。

---

## 25. 冷却

两层：`cooldowns` 表（键 = 维度+坐标，值 = 冷却截止毫秒）过滤冷却中容器，冷却结束自动重新检测；`COOLDOWN` 态做完成后/跳过后的短暂停顿再选下一个。`reset()` 清空冷却表。

---

## 26. 寻路

`PathingService.computeStandPosition` 按四水平方向找「脚下完整碰撞方块 + 脚/头可通行」的可站立格，`GoalBlock` 精确寻路到站位；找不到才退化 `GoalTwoBlocks`。绝对禁止把容器中心坐标当最终站位。

---

## 27. 后台挂机

全程走客户端内部 API（`FarmPacketOps` 发包 + Baritone 内部寻路），不抢鼠标、不抢窗口焦点、不模拟键鼠、不强制前台；玩家手动关模块 → `onDeactivate` 立即停寻路、关容器、重置状态机。

---

## 28. 背包满停机

取物前 `getSlotWithRemainingSpace` 确认空间，放不下返回 `INVENTORY_FULL` → 关闭容器 → `stopToStopped("背包已满")` 播报并关闭模块，**不得继续寻找下一个箱子**。

---

## 29. 异常停机

死亡 / 退服 / 换维度 / 背包满 / 网络异常 / 模块关闭均进入安全状态：`stopToStopped` 停寻路 → 停扫描 → 停交互 → 关容器 → 释放目标锁 → 进入 `STOPPED`。退服由 `mc.level == null` 入口拦截不发包；换维度由维度标识变化捕获并重置重载。

---

## 30. 持久化

配置（Meteor modules.nbt）+ ID（`AutoChest/items/`）+ 点位（`autochest/points/`）+ 处理记录（`autochest/records`）全部保存到用户自己的客户端数据目录，不写入 JAR。

---

## 31. JAR 数据隔离

数据目录统一在 `config/yiyiaddon/autochest/` 与 `<gameDirectory>/AutoChest/`，开发者个人数据不打包进 JAR；`build.gradle.kts` 仅打包 baritone 嵌套依赖与 LICENSE。

---

## 32. Minecraft 26.1.2 API 适配情况

- Mojang 官方映射：`Identifier` / `LocalPlayer` / `ClientLevel` / `Component` / `DataComponentPatch` 等，无 Yarn 名、无旧官方名。
- 容器操作走 26.1.2 `handleContainerInput(containerId, slot, button, ContainerInput.QUICK_MOVE/PICKUP, player)`，未使用已废弃的 `clickSlot` + `SlotActionType`。
- 维度用 `ResourceKey#identifier()` 稳定标识；Data Component 用 `DataComponentPatch.CODEC` + `JsonOps`。
- `clean build` 编译零错误。

---

## 33. Gradle 最终结果

```
BUILD SUCCESSFUL in 3s
5 actionable tasks: 5 executed
```

任务链：`clean → compileJava → processResources → classes → processIncludeJars → jar → assemble → build`。
产物：`build/libs/yiyiaddon1.1-beta4-personal.jar`。

---

## 34. 还需要真人服务器测试的项目

1. 三模式寻路站位准确性（玩家控制 / 寻路 / 标点）。
2. 多人保护触发距离与「他人正在用箱不抢」实际表现。
3. 有限重试 / 临时冷却节奏是否卡手。
4. 后台挂机长时间稳定性（失焦、多窗口）。
5. 精确差额取物在单叠超量跨 tick 会话中的正确性。
6. 多人并发抢箱时容器 stateId 剧烈变化的放弃判定。
7. 潜影盒 / 木桶朝向对 `computeStandPosition` 可达面的影响。
8. 换维度 / 退服 / 死亡即时停机的实际触发。
9. 改名 / 附魔物品组件级匹配的真机命中率。
10. 记录过期、容器破坏重放后 ESP 变绿的实际表现。

---

## 最终确认

本项目严格基于 **Minecraft 26.1.2 · Fabric · Meteor Client**。

```
辅助
├── 自动箱子     AutoChestModule
├── ID识别      IdIdentifyModule
└── ID配置管理   IdConfigModule
```

三个功能已联动（共享同一 `ItemIdManager` 数据源），职责保持独立。

> 结束前自检通过：死代码（删除 `reachedMaxRetries`）、@Mixin 声明（本体系无新增 Mixin）、模块/指令注册（`AddonTemplate` 已注册三模块 + AutoChestCommand + IdCommand）、文件夹分类（autochest / itemid 独立包）、无用 import（编译通过无告警残留）。
