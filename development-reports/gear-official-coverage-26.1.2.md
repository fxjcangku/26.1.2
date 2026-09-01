# 原版装备极品附魔 · 官方装备全集覆盖检查报告（26.1.2）

日期：2026-09-01
报告文件：gear-official-coverage-26.1.2.md

---

## 1. Minecraft 版本
26.1.2（Mojang mappings）

## 2. 官方数据来源
- `ToolMaterial.java`（工具/剑/矛材质 + enchantability）
- `ArmorMaterials.java`（护甲材质 + enchantability）
- `Items.java`（Item Registry 实际注册）
- `tags/item/enchantable/*`（附魔台适用性）
- `data/minecraft/enchantment/*.json`（附魔 primary/supported/exclusive_set）

## 3. Registry 扫描方式
直接读取反编译源码 `.vftmp/src/net/minecraft/world/item/Items.java`，
grep `registerItem("(wooden|stone|copper|iron|golden|diamond|netherite)_(pickaxe|axe|shovel|hoe|sword|spear)"`、
`registerItem("(leather|chainmail|copper|iron|golden|diamond|netherite|turtle)_(helmet|chestplate|leggings|boots)"` 及
`bow/crossbow/trident/mace` 注册行，得到真实 Item ID 全集。未凭记忆或旧版本 Wiki。

## 4. 官方装备总数
**75 件**（属于「工具 / 武器 / 护甲」业务范围的玩家可穿戴/使用装备）

## 5. 当前项目装备总数（补全后）
**75 件**（`src/main/resources/enchantment/vanilla/items/gears.json`）

## 6. 缺失装备（OFFICIAL - CURRENT）
补全前仅 11 件（钻石工具×4、钻石剑、弓、弩、钻石护甲×4），缺失 **64 件**，已全部补全。

## 7. 新增装备（64 件）
- 工具：木/石/铜/铁/金/下界合金 各 镐/斧/锹/锄 = 24 件
- 剑：木/石/铜/铁/金/下界合金 = 6 件
- 矛（26.1.2 新武器）：木/石/铜/铁/金/钻石/下界合金 = 7 件
- 三叉戟、重锤（mace）= 2 件
- 护甲：皮革/锁链/铜/铁/金/下界合金 各 头盔/胸甲/护腿/靴子 = 24 件 + 海龟壳 = 25 件

## 8. 非法装备（CURRENT - OFFICIAL）
**0 件**（原 11 件全部为官方真实 Item）

## 9. 虚构装备
**0 件**（未生成钻石弓/铜弓/钻石弩等任何不存在的 Item）

## 10. 工具完整列表（28 件）
| 类型 | 材质 |
|------|------|
| 镐 pickaxe | wooden/stone/copper/iron/golden/diamond/netherite |
| 斧 axe | wooden/stone/copper/iron/golden/diamond/netherite |
| 锹 shovel | wooden/stone/copper/iron/golden/diamond/netherite |
| 锄 hoe | wooden/stone/copper/iron/golden/diamond/netherite |

## 11. 工具种类
镐、斧、锹、锄（4 种）

## 12. 工具材质/品质（enchantability）
木 15 / 石 5 / 铜 13 / 铁 14 / 金 22 / 钻石 10 / 下界合金 15
> 铜工具为 26.1.2 真实 Item（`ToolMaterial.COPPER` 已存在），已纳入。

## 13. 护甲完整列表（29 件）
| 材质 | 部位 |
|------|------|
| 皮革 leather | 头盔/胸甲/护腿/靴子 |
| 铜 copper | 头盔/胸甲/护腿/靴子 |
| 锁链 chainmail | 头盔/胸甲/护腿/靴子 |
| 铁 iron | 头盔/胸甲/护腿/靴子 |
| 金 golden | 头盔/胸甲/护腿/靴子 |
| 钻石 diamond | 头盔/胸甲/护腿/靴子 |
| 下界合金 netherite | 头盔/胸甲/护腿/靴子 |
| 海龟壳 turtle_helmet | 头盔（特殊） |

## 14. 护甲部位
头盔、胸甲、护腿、靴子（4 部位）

## 15. 护甲材质/品质（enchantability）
皮革 15 / 铜 8 / 锁链 12 / 铁 9 / 金 25 / 钻石 10 / 海龟壳 9 / 下界合金 15

## 16. 武器完整列表（18 件）
- 剑 sword：wooden/stone/copper/iron/golden/diamond/netherite（7）
- 矛 spear：wooden/stone/copper/iron/golden/diamond/netherite（7）
- 弓 bow、弩 crossbow、三叉戟 trident、重锤 mace（4）

## 17. 其他纳入的装备
无（业务范围严格限定为工具/武器/护甲）

## 18. 被排除的官方物品
| 物品 | 排除原因 |
|------|----------|
| 鹦鹉螺护甲（iron/golden/diamond/netherite/copper_nautilus_armor） | 鹦鹉螺坐骑装备，非玩家护甲 |
| 狼铠（armadillo_scute） | 狼的护甲，非玩家 |
| 马铠（horse_armor） | 坐骑装备，非玩家 |
| 鞘翅 elytra | 飞行装备，非护甲，且无「极品附魔」业务价值 |
| 盾牌 shield | 副手装备，非工具/武器/护甲 |
| 钓鱼竿 fishing_rod | 有海之眷顾/饵钓，但非「装备极品」业务范围 |

## 19. 每个排除项的原因
见上表「排除原因」列。核心判定：是否属于「玩家可穿戴/使用的工具/武器/护甲」业务范围。

## 20. Candidate 覆盖情况
**75/75**。每件装备生成独立 `candidates/level30/*-level30.json`，按 `isPrimaryItem` + 附魔成本算法计算可达等级。已验证：
- 铜镐/铜斧等 TOOL 类只有 efficiency/fortune/silk_touch/unbreaking
- 矛（SPEAR）有 lunge 无 sweeping_edge
- 重锤（MACE）有 breach/density/unbreaking（wind_burst 为宝藏，不进候选）
- 斧不因 supported_items 含 sharp_weapon 而获得锋利（严格 isPrimaryItem）

## 21. Target Profile 覆盖情况
**11/75**（钻石工具×4、钻石剑、弓、弩、钻石护甲×4）。
> 下界合金全套、三叉戟、重锤、矛等「核心极品装备」的 Profile **尚未补全**，属下一步工作。
> 其余低端材质（木/石/铜/铁/金/皮革/锁链）按指令「不凑数」原则暂不生成 Profile，
> GUI 中显示「暂无极品方案」。

## 22. GUI 覆盖情况
**75/75**。装备选择器重构为三级分类（大类→类型→材质）动态生成，数据源为官方全集，
仅显示真实 Item，显示中文名 + Item ID，无硬编码按钮，无虚构组合。

## 23. 工具箱取货覆盖情况
按真实 Item ID 精确匹配（`minecraft:diamond_pickaxe` 只取钻石镐），复用现有点位系统，未新建。

## 24. 异常装备箱删除情况
已彻底删除：
- PointType.ERROR_STORAGE 枚举删除
- `.fumo set/remove 异常装备箱` 指令节点删除
- `posError` 字段 / `GEAR_WALK_ERROR` / `GEAR_STORE_ERROR` 状态删除
- `tickGearWalkError` / `tickGearStoreError` 方法删除
- 启动自检不再要求异常箱（requiredPoints 移除 ERROR_STORAGE）
- 失败装备改为「跳过该件继续下一件」（gearFail 内部 gearNext + GEAR_IDLE）
- 旧配置 posError 字段加载时自动忽略（readPos 不再读 posError，无 NPE）

## 25. PointType 删除情况
ERROR_STORAGE 已删除，switch/case/switch 表达式同步清理，无死代码。

## 26. Java 指令删除情况
`.fumo` 指令中「异常装备箱」set/remove 节点已删除，Javadoc 同步更新。

## 27. 三模式回归情况
- 原版附魔书模式：未改动（未触碰 Book 流程、空白书/青金石/砂轮逻辑）
- 自定义附魔模式：未改动
- 本次改动仅作用于 GEAR（原版装备极品附魔）模式

## 28. Validator 结果
`VanillaEnchantRuleValidator` 数据一致性校验通过（互斥双向、候选等级 ≤ maxLevel、装备/附魔存在）。
> 静态审查另发现并修复 `conflicts.json` riptide 互斥组语义错误（详见上一阶段报告）。

## 29. 编译结果
`gradlew clean build` → **BUILD SUCCESSFUL**

## 30. 尚未进行的真机测试
全部真机验证（附魔台实际结果、CandidateMatcher/TargetMatcher/PlanningEngine 判断、铁砧、成品箱）均**未执行**。
> 另修复了 3 个与本任务无关但阻塞编译的预先存在语法错误（AutoBoneMeal / AutoVillagerTradeModule / FlightBypass 中 `SettingUiHelper.currentValueLine` 缺 `InfoTextSetting` 字段声明前缀），已最小修复（加字段声明 + import）。

---

## 附加：修复的问题汇总
1. 【本任务】删除异常装备箱（PointType/FumoCommand/AutoEnchantBook 全链路）
2. 【本任务】装备全集从 11 件补全到 75 件（含铜/锁链/皮革/矛/重锤）
3. 【本任务】GUI 三级分类动态生成重构
4. 【静态审查】conflicts.json riptide 互斥组语义错误（上一阶段）
5. 【阻塞修复】3 个文件 `SettingUiHelper.currentValueLine` 缺字段声明前缀的预先存在语法错误
