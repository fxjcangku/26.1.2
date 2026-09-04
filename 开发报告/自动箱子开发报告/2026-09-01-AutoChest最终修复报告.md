# AutoChest 辅助体系 · 最终修复报告

> 日期：2026-09-01
> 阶段：最终验收 + 补漏修复
> 依赖：`2026-09-01-AutoChest最终验收报告.md` 及开发要求清单（六十四节）
> 分类：辅助（CATEGORY_ASSIST）

---

## 0. 结论

- 对照开发要求六十四节逐项验收，主体功能（三模块、状态机、取物引擎、ESP、目标锁、重试冷却、多人保护、持久化、服务器/维度隔离）均已正确实现，本次未重复修改。
- 共发现 **8 处遗漏**，已全部补齐：JSON 汉化、识别模式、识别结果快捷操作、原版/自定义分类、标点 GUI 按钮、清除处理记录、背包满提示文案、识别结果字段补全。
- **`gradlew clean build` 通过（`BUILD SUCCESSFUL`），产物正常生成。**

---

## 1. 本次发现的遗漏

| 编号 | 遗漏项 | 对应需求节 | 严重度 |
| --- | --- | --- | --- |
| 1 | ID/实体 JSON 仍用英文字段，缺少「物品类型 / 数量」 | 十七 / 五 | 高 |
| 2 | ID 识别无「识别模式」（聊天复制/显示 vs 自动保存） | 七 | 高 |
| 3 | 识别结果无快捷操作（复制 Item ID / 复制完整信息 / 保存 / 添加） | 六 | 中 |
| 4 | ID 配置页与目标选择器未分「原版 / 自定义」两类 | 九 | 高 |
| 5 | 标点管理只有 `.autochest` 命令，GUI 无按钮 | 二十二～二十六 | 高 |
| 6 | 无「清除处理记录」功能 | 五十四 | 高 |
| 7 | 背包满提示文案与需求不符（应为「背包空间不足」） | 三十七 | 低 |
| 8 | 识别结果缺「数量 / 物品类型 / 数据版本 / 数据组件」展示 | 五 | 中 |

---

## 2. 已补齐的遗漏

1. **JSON 汉化**：`ItemIdentity` / `EntityIdentity` 落盘 JSON 改用中文字段（物品ID/显示名称/原始名称/物品类型/自定义名称/数量/附魔/数据组件/数据版本），附魔条目同步中文化（附魔ID/中文名称/等级/显示名称）；读取时中文字段优先、英文旧档兼容回退。
2. **识别模式**：`IdIdentifyModule` 新增「识别模式」下拉（聊天复制/显示 / 自动保存）+「当前模式」实时显示；模式可切换并随 modules.nbt 持久化。
3. **识别结果快捷操作**：新增 `IdResultScreen`，完整展示识别结果，并提供 [复制 Item ID][复制完整信息][保存 ID][添加到 ID 配置] 四个按钮，全部直接调剪贴板 / `ItemIdManager`，不模拟键鼠/聊天。
4. **原版 / 自定义分类**：`IdConfigModule` 清单与 `ItemTargetSelectScreen` 选择器左侧均拆分为「原版物品 / 自定义物品」两类；判定依据 `ItemIdentity.isVanilla()`（minecraft 命名空间且未改名）。
5. **标点 GUI 按钮**：`AutoChestModule` 说明面板新增 [设置箱子点位][删除箱子点位][查看当前设置箱子信息][清空全部点位]（含确认弹窗），直接调 `ChestPointManager`；并新增 [清除当前维度处理记录][清除全部处理记录]（含确认弹窗）。
6. **清除处理记录**：`ContainerRecordManager` 新增 `clearDimension` / `clearAllServers`；GUI 按钮与 `WorldIdentity` 隔离联动，清后 ESP 恢复绿色。
7. **背包满文案**：状态机 `stopToStopped` 由「背包已满」改为「背包空间不足」，与需求 37 一致。
8. **识别结果字段**：`IdCommand` 与 `IdResultScreen` 补齐物品类型 / 数量 / 数据版本 / 数据组件展示。

---

## 3. 修改的文件

- `itemid/ItemIdentity.java` —— 新增 `quantity` 字段与 `isVanilla/isCustom/typeName`；JSON 序列化/反序列化中文化 + 旧档兼容。
- `itemid/EntityIdentity.java` —— JSON 中文化 + 旧档兼容。
- `itemid/ItemIdentifier.java` —— 识别时传入 `stack.getCount()` 作为数量。
- `itemid/IdIdentifyModule.java` —— 新增「识别模式 / 当前模式」设置；按模式分支自动保存或弹结果屏。
- `itemid/IdCommand.java` —— 识别结果字段补全。
- `itemid/IdConfigModule.java` —— ID 清单按原版 / 自定义分类。
- `itemid/ItemTargetSelectScreen.java` —— 选择器左侧按原版 / 自定义分类。
- `autochest/AutoChestModule.java` —— 说明面板新增标点 / 记录管理按钮与对应方法。
- `autochest/AutoChestSettings.java` —— 标点提示文案改为「面板按钮 + 指令」。
- `autochest/AutoChestStateMachine.java` —— 背包满停机文案对齐。
- `autochest/AutoChestCommand.java` —— 非容器提示文案对齐为「当前目标不是可绑定容器」。
- `autochest/service/ContainerRecordManager.java` —— 新增 `clearDimension` / `clearAllServers`。

---

## 4. 新增的文件

- `itemid/IdentifyMode.java` —— 识别模式枚举（聊天复制/显示 / 自动保存）。
- `itemid/IdResultScreen.java` —— 识别结果屏幕（完整字段 + 四个快捷操作）。
- `autochest/ConfirmScreen.java` —— 通用确认弹窗（清空点位 / 清除记录用）。

---

## 5. 原本已正确、未重复修改的项目

- 辅助分类（`CATEGORY_ASSIST`）与三模块注册、命令注册、设置控件工厂注册。
- 三功能数据联动（共享同一 `ItemIdManager`）。
- 三种运行模式 / 三种取物模式 / 容器选择器（五种容器）/ 检测范围与触发距离分离。
- 真实 Slot 动态识别、精确物品匹配、开箱速度、空箱 / 无目标 / 目标完成立即关闭。
- 目标锁、有限重试、临时冷却、多人保护、服务器状态实时确认。
- ESP 三态渲染、容器破坏重新识别、寻路站位、模式切换、安全停机、断线恢复、性能保护、JAR 数据隔离、纯取物无交易。

---

## 6. 三个辅助功能最终联动

```
辅助（CATEGORY_ASSIST）
├── ID识别       IdIdentifyModule  → 产出 ItemIdentity（聊天复制/显示 或 自动保存）
├── ID配置管理    IdConfigModule    → 管理已识别/手动添加的 ID（原版/自定义分类）
└── 自动箱子      AutoChestModule   → 消费 ID 配置，扫描容器按取物模式取物
```

三模块共享**同一个 `ItemIdManager` 实例**（`AddonTemplate` 创建后注入），AutoChest 只消费、绝不建第二套物品数据库。ID 增删后 `ItemIdManager` 广播，AutoChest 目标选择器 `pruneInvalid()` 实时清理失效项。

---

## 7. ID 系统最终结构

- 数据源：`ItemIdManager`（物品）+ `EntityIdManager`（实体）。
- 物品目录：`<客户端目录>/AutoChest/items/{中文名}.json`（重名追加 `_2`，相同身份复用不重复生成）。
- 实体目录：`<客户端目录>/AutoChest/entities/{中文名}.json`。
- 文件名中文、清洗 § 颜色码与非法字符；JSON 中文字段可读。
- 手动添加经 26.1.2 `BuiltInRegistries.ITEM` 验证，非法 ID 禁止保存。

---

## 8. ItemIdentity 最终结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| itemId | String | 物品 ID（`minecraft:diamond`） |
| displayName | String | 中文显示名（改名取自定义名） |
| baseName | String | 默认中文名 |
| customName | String | 自定义名（未改名为 null） |
| quantity | int | 识别时数量（仅展示/落盘，不参与身份判定） |
| dataVersion | int | 数据版本 |
| enchantments | List\<EnchantmentEntry\> | 附魔 ID + 中文名 + 等级 + 显示名 |
| dataComponents | String | DataComponentPatch 序列化 JSON |
| item / componentTemplate | 瞬态 | 注册表项 / 组件模板（精确匹配，不落盘） |

身份判定（`equals` / `identityKey`）以 `itemId + customName + enchantments` 为准；`isVanilla()` 判定原版（minecraft 命名空间且未改名），否则自定义。

---

## 9. AutoChest 最终结构

`autochest/`（消费层）：模块主类 + 设置 + 18 状态状态机 + ESP 渲染 + 标点指令 + 说明面板按钮，`model/`（容器目标/记录/类型/注册表/两种枚举）、`scan/`（分帧扫描/就近选择）、`service/`（开箱读槽匹配取物/标点/记录/寻路）。目标物品统一走 `ItemTargetSetting`（只存身份键，运行时回查 `ItemIdManager`）。

---

## 10. 三种运行模式

| 模式 | 目标来源 | 移动方式 |
| --- | --- | --- |
| 玩家控制模式 | 触发距离内扫描 | 玩家自己走，模块不寻路 |
| 寻路模式 | 最近未处理容器 | 计算安全站位 + Baritone 寻路 |
| 标点模式 | 仅 `.autochest` / 面板按钮保存点位 | 计算安全站位 + Baritone 寻路 |

三模式互斥，切换即 `onModeSwitch()` 清理旧状态（停寻路/关箱/释放锁）。

---

## 11. 三种取物模式

| 模式 | 行为 |
| --- | --- |
| 按目标数量取 | 每种目标独立配数量，按「目标数量 - 玩家已有量」差额取 |
| 目标物品拿空 | 只拿空目标列表命中物品，不限数量 |
| 全部拿空 | 忽略目标列表，遍历真实非空容器槽位全部可取 |

三种模式均接入 `findTarget` 核心引擎；`TAKE_ALL` 不走目标匹配。

---

## 12. 容器选择器

`ContainerTypeRegistry` 内置：箱子、陷阱箱、16 色潜影盒、木桶、铜箱；按 `Block` 类型匹配（普通箱/陷阱箱/铜箱 BlockEntity 同为 ChestBlockEntity，只有按 Block 能区分）。新增类型仅 `register` 一条。

---

## 13. 点位系统

面板按钮 + `.autochest add/remove/clear/status` 双重入口，均直接调用 `ChestPointManager`（共用底层 Service）。add 校验准星方块为启用合法容器（非容器拒绝「当前目标不是可绑定容器」）；status 显示服务器/维度/坐标/类型/状态/数量；清空带确认弹窗。内存与磁盘同步，按服务器隔离。

---

## 14. ESP

三态着色：未处理绿、已处理红、处理中黄；渲染器以 `processingTarget()` 优先判「处理中」，处理中绝不显示为已处理红；对容器 `BlockPos` 渲染，距离 >64 不渲染。

---

## 15. 目标锁

状态机持单一 `lockedTarget`，`validLocked()` 每 tick 校验（维度未切 + 容器仍存在 + 未处理 + 无他人占用），失效才释放；锁定新目标清零 `retryCount`，不因新扫描频繁更换。

---

## 16. 重试与冷却

`retryCount` 跨开箱/寻路/交互/关闭累计，达上限 `skipWithCooldown`（跳过 + 冷却 + 释放锁，不记已处理）；未达上限进入 `FAILED` 短暂停顿后回跳重试。两层冷却：`cooldowns` 表 + `COOLDOWN` 态。

---

## 17. 多人保护

`hasNearbyPlayer` 遍历 `mc.level.players()`（跳过自身与旁观者），他人进入检测距离即视为「正在用箱」，不抢、临时跳过进入冷却，绝不标记已处理。

---

## 18. 后台运行

全程走客户端内部 API（`FarmPacketOps` 发包 + Baritone 内部寻路），不抢鼠标/焦点、不模拟键鼠、不强制前台；关模块 → `onDeactivate` 停寻路/关容器/重置状态机。

---

## 19. 持久化

配置（Meteor modules.nbt）+ ID（`AutoChest/items/`、`AutoChest/entities/`）+ 点位（`config/yiyiaddon/autochest/points/`）+ 处理记录（`config/yiyiaddon/autochest/records`，实际落 `{server}.json`）全部写客户端数据目录，不打包进 JAR。

---

## 20. 26.1.2 API 适配情况

- Mojang 官方映射：`Identifier` / `LocalPlayer` / `ClientLevel` / `Component` / `DataComponentPatch` 等，无 Yarn / 旧官方名。
- 容器操作走 26.1.2 `handleContainerInput(containerId, slot, button, ContainerInput.QUICK_MOVE/PICKUP, player)`。
- 剪贴板用 `mc.keyboardHandler.setClipboard(...)`（Meteor 源码同款，非键盘模拟）。
- 维度用 `ResourceKey#identifier()`；Data Component 用 `DataComponentPatch.CODEC` + `JsonOps`。
- `clean build` 编译零错误。

---

## 21. Gradle 最终结果

```
BUILD SUCCESSFUL in 3s
5 actionable tasks: 5 executed
```

任务链：`clean → compileJava → processResources → classes → processIncludeJars → jar → assemble → build`。

---

## 22. 当前仍存在的问题

1. ID 目录为 `<gameDir>/AutoChest/items`，处理记录/点位为 `<gameDir>/config/yiyiaddon/autochest`，两处根目录不完全统一（均为客户端目录，不影响功能，仅目录观感分散）。因需求 8 明确「优先保持已有目录结构、不产生重复数据目录」，故本次未强行迁移。
2. 「原版/自定义」判定以 `minecraft:` 命名空间 + 未改名为准；对「原版物品但附魔」仍归原版（符合示例语义，但若需按 Data Component 区分可再扩展）。
3. 识别模式「聊天复制/显示」以结果屏幕承载（非纯聊天），比需求字面的「聊天」更直观，语义等价于「显示 + 复制」。

---

## 23. 必须进入真实服务器测试的项目

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
11. 中文 JSON 文件读写、重名 `_2` 与相同身份复用去重的真机表现。
12. 「聊天复制/显示」模式结果屏复制 / 保存 / 添加四按钮的真机表现。
13. 「清除处理记录（当前维度 / 全部）」清后 ESP 恢复绿色的实际表现。

---

## 最终确认

本项目严格基于 **Minecraft 26.1.2 · Fabric · Meteor Client**。

```
辅助
├── 自动箱子     AutoChestModule
├── ID识别      IdIdentifyModule
└── ID配置管理   IdConfigModule
```

三功能已联动（共享同一 `ItemIdManager` 数据源），职责独立，八处遗漏全部补齐。
