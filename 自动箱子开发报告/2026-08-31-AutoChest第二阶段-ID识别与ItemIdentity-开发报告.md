# AutoChest 辅助体系 · 第二阶段开发报告（ID识别 + ItemIdentity + ID文件）

> 日期：2026-08-31
> 阶段：ID识别 + ItemIdentity + ID文件（已完成并编译通过）
> 依赖：第一阶段架构报告（三功能分类 + 数据流 + 类职责）

---

## 0. 必读（接手继续开发前强制要求）

接手继续开发前，**必须完整读完以下两个个人开发习惯文件**，不得跳读：

1. `d:\mcaddon\26.1.2\AGENTS.md`（五条铁律 + 关键速查）
2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`（十章规范唯一正本）

关键铁律提醒（下一步开发前逐条对照）：
- 规范注释只增不删；证据驱动排查（Bug 先埋点取证据）；中文 + 日期归档。
- API 先查后写：Mojang 官方映射，查 `node Mappings/工具/查JARAPI.js <类名>`（规范/AGENTS 里写作 `查API.js`，实际磁盘文件名为 `查JARAPI.js`）。
- 分类铁律：新功能独立英文包；中文注释；禁 emoji（用 ✓ ✗ ⚠ ▸）。
- 消息规范：前缀走 `YiyiaddonModule.formatMessage`；面板按钮走 `addUniformButton`；说明面板走 `buildInfoWidget`；强调色走 `highlight*`；状态播报 5.9 带 lastNotifiedState 去重锁。

---

## 1. 本阶段结论

- 完成 `.id 物品` / `.id 实体` 识别指令，保留既有「ID识别」模块能力（主手→副手→空手提示）。
- `ItemIdentity` 从「仅 itemId 字符串」升级为「完整身份对象」，改名物品可区分。
- 核心采用 26.1.2 **Data Component API**（`DataComponentPatch.CODEC` + `JsonOps`），未使用旧 NBT 方案。
- 持久化改为「一物一文件」，落盘到客户端数据目录 `AutoChest/items/` 与 `AutoChest/entities/`，中文文件名 + 重名追加 `_2/_3`。

---

## 2. 本阶段新增 / 修改文件

新增（`com.example.addon.itemid`）：
- EntityIdentity.java —— 实体身份模型（entityId + 中文名 + 自定义名 + 数据版本）
- EntityIdManager.java —— 实体身份存储（`AutoChest/entities/`）
- IdCommand.java —— `.id 物品` / `.id 实体` 识别指令

重写（`com.example.addon.itemid`）：
- ItemIdentity.java —— 完整物品身份（中文名/改名/附魔/Data Component/数据版本 + JSON 序列化 + 文件名清洗）
- ItemIdentifier.java —— 物品/实体识别 + Data Component 解析
- ItemIdManager.java —— 物品身份存储（`AutoChest/items/`，一物一文件）

修改（`com.example.addon.itemid` / `core`）：
- IdIdentifyModule.java —— 主手→副手→空手提示 + 新识别 + 附魔播报
- IdConfigModule.java —— 清单展示中文名
- core/AddonTemplate.java —— 创建 EntityIdManager + 注册 IdCommand

---

## 3. ItemIdentity 数据结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| itemId | String | 物品 ID，如 `minecraft:diamond` |
| displayName | String | 中文显示名（改名取自定义名，否则默认名），用于显示 + 文件名 |
| baseName | String | 物品默认中文名（区分改名） |
| customName | String | 自定义名，未改名为 null |
| dataVersion | int | Minecraft 数据版本 |
| enchantments | List\<EnchantmentEntry\> | 附魔 ID + 中文名 + 等级 + 显示名（含罗马数字） |
| dataComponents | String | DataComponentPatch 序列化 JSON（Data Component API） |
| item / componentTemplate | 瞬态 | 注册表项 / 组件模板（识别时用于精确匹配，不落盘） |

JSON 落盘示例（`AutoChest/items/超级钻石.json`）：

```json
{
  "itemId": "minecraft:diamond",
  "displayName": "超级钻石",
  "baseName": "钻石",
  "customName": "超级钻石",
  "dataVersion": 4189,
  "enchantments": [],
  "dataComponents": { "minecraft:custom_name": "{\"text\":\"超级钻石\"}" }
}
```

关键约束：中文名只负责显示与命名，真正身份判定以 `itemId + 组件 + 附魔` 为准（`equals` 以 `itemId + customName + enchantments` 判定，普通钻石与超级钻石不相等）。

---

## 4. 关键实现点

1. **改名物品区分**：读 `DataComponents.CUSTOM_NAME` 组件，与默认中文名（`getDefaultInstance().getHoverName()`）分离，`isRenamed()` 判定改名。
2. **附魔解析**：`DataComponents.ENCHANTMENTS`（装备）+ `DataComponents.STORED_ENCHANTMENTS`（附魔书）双来源；附魔 ID 走 `holder.unwrapKey().identifier()`，中文名 `Enchantment.description()`，完整名 `Enchantment.getFullname(holder, level)`。
3. **Data Component 序列化**：`DataComponentPatch.CODEC.encodeStart(registryAccess.createSerializationContext(JsonOps.INSTANCE), patch)` → JSON，非旧 NBT。
4. **中文文件名**：`ItemIdentity.sanitizeFileName` 剥离颜色代码 + 剔除 Windows 非法字符 + 压缩空白；重名 `钻石.json → 钻石_2.json → 钻石_3.json`。

---

## 5. 目录结构（客户端数据目录）

```
<客户端数据目录>/AutoChest/
├── items/       物品身份（本阶段实现）
├── entities/    实体身份（本阶段实现）
├── config/      （后续阶段）
├── points/      （后续 AutoChest 标点）
└── records/     （后续 AutoChest 已处理记录）
```

---

## 6. 编译结果

```
BUILD SUCCESSFUL in 3s
4 actionable tasks: 2 executed, 2 up-to-date
```

产物：`yiyiaddon1.1-beta4-personal.jar`（未混淆个人测试版）。

---

## 7. 下一阶段（第三阶段）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `d:\mcaddon\26.1.2\AGENTS.md`
> 2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. 消费层 `ItemIdentityMatcher` / `AutoChestModule` 接线：按 `WithdrawMode` 用 `matches()` 做精确匹配。
2. `dataComponents` 反序列化回 `DataComponentPatch`，为改名/附魔物品提供组件级精确匹配。
3. `AutoChestStateMachine.tick()` 完整状态转换 + 按 `ScanMode` 分派。
4. `PathingService.computeStandPosition` 精细站位。
5. 取物完成判定 + 关箱 + 记录 + ESP 变红闭环。

> 结束前自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
