# AutoChest / ID 辅助体系 · 最终补漏报告

> 日期：2026-09-01
> 阶段：最终功能核对 + 遗漏补全 + Bug 修复
> 结论：`gradlew clean build` 通过，产物 `yiyiaddon1.1-beta4-personal.jar`

---

## 0. 结论

本次为「最终补漏」，未推翻架构、未重写、未删除既有功能。发现 1 个明确 Bug 并修复，补齐 2 处遗漏，其余功能核对确认已实现。

---

## 1. 本次发现的 Bug

**`.id 物品` 没有使用识别模式（IdentifyMode）。**

现象：`IdCommand.identifyItem()` 仍走旧逻辑——识别后直接写入 ID 配置并刷一屏完整识别文本，完全无视「ID识别」模块里的「识别模式」设置。即使把识别模式切到「聊天复制/显示」，执行 `.id 物品` 仍不会弹出结果屏。

---

## 2. 本次发现的遗漏

1. **手动添加物品不支持中文名称搜索**：原 `IdAddScreen` 只接受 `minecraft:diamond` 这类 ID，输入「钻石」「绿宝石」无法识别。
2. **自定义 / 改名物品无法不拿实物手动添加**：原界面只能添加原版物品，无法填写「自定义名称」创建改名物品。

> 说明：标点按钮、记录清理按钮、`ConfirmScreen`、`clearDimension`/`clearAllServers` 已在本次核对时确认存在（说明面板头部按钮方案），未重复实现。

---

## 3. 已修复内容

1. `.id 物品` 改为「统一命令入口」，读取 `IdIdentifyModule.identifyMode` 分流：
   - 自动保存 → 直接写入 + 简短提示「已自动保存」。
   - 聊天复制/显示 → 弹出 `IdResultScreen`（可复制/保存/添加）。
2. 手动添加支持中文名称精确搜索（输入「钻石」→ `minecraft:diamond`），多个命中展示候选列表，不随机选择。
3. 手动添加支持填写「自定义名称」，构造改名 / 自定义物品（customName 非空）。

---

## 4. 修改文件

- `itemid/IdCommand.java` —— `.id 物品` 读 IdentifyMode 分流，删除旧版刷屏输出。
- `itemid/ItemIdentifier.java` —— 新增 `findVanillaByChineseName`（遍历注册表精确匹配）。
- `itemid/ItemIdentity.java` —— 新增 `fromItemAndCustomName`（自定义/改名物品工厂）。
- `itemid/IdAddScreen.java` —— 重写：ID / 中文名双输入 + 自定义名称 + 候选列表。

## 5. 新增文件

无新增文件（仅修改既有文件）。

---

## 6. `.id 物品` 最终调用链

```
.id 物品
  ↓ 读手持（主手 → 副手 → 空提示）
  ↓ ItemIdentifier.identifyItem
  ↓ ItemIdentity
  ↓ 读 IdIdentifyModule.identifyMode（命令自身不维护 mode）
  ├─ 自动保存      → ItemIdManager.add → 简短提示「已自动保存」
  └─ 聊天复制/显示 → IdResultScreen（复制 / 保存 / 添加）
```

## 7. IdentifyMode 最终调用链

```
IdIdentifyModule.identifyMode（EnumSetting，默认「自动保存」）
  ├─ 被 IdIdentifyModule.onActivate 读取（模块按钮识别）
  └─ 被 IdCommand.identifyItem 读取（.id 物品 命令）
     两处共享同一 Setting 实例，实时生效，无需重启
```

---

## 8. 原版 / 自定义添加流程

```
输入物品 ID（minecraft:diamond）
  ↓ ItemIdentity.fromItemId（Registry 验证）
  └─ 原版物品（isVanilla=true）
输入中文名（钻石）
  ↓ 先查已保存 ID → 再查注册表原版名
  └─ 命中 → 原版物品
填写自定义名称（超级钻石）+ 物品 ID（minecraft:diamond）
  ↓ ItemIdentity.fromItemAndCustomName
  └─ 自定义物品（customName=超级钻石，isCustom=true）
```

## 9. 中文名称搜索流程

```
输入中文名
  1. 已保存 ID 配置 displayName 精确匹配（equals）
  2. 注册表原版 hoverName 精确匹配（findVanillaByChineseName）
  多个命中 → 候选列表（点击选择）
  0 命中   → 提示「请先手持识别，或填写真实物品 ID」
（禁止 contains / startsWith 模糊猜测）
```

## 10. 自定义物品手动添加流程

```
填写物品 ID（真实存在）+ 自定义名称
  ↓ ItemIdentity.fromItemAndCustomName
  ↓ customName=自定义名，baseName=原版中文名，displayName=自定义名
  ↓ ItemIdManager.add → 落盘 JSON（物品类型=自定义物品）
```

---

## 11. ItemIdentity 最终结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| itemId | String | 物品 ID |
| displayName | String | 中文显示名 |
| baseName | String | 默认中文名 |
| customName | String | 自定义名（改名为非 null） |
| dataVersion | int | 数据版本 |
| enchantments | List\<EnchantmentEntry\> | 附魔（ID/中文名/等级/显示名） |
| dataComponents | String | DataComponentPatch JSON |
| quantity | int | 识别时数量（仅展示/落盘） |
| item / componentTemplate | 瞬态 | 注册表项 / 组件模板 |

判定：`isVanilla`=minecraft 命名空间且未改名；`isCustom`=改名或非 minecraft。身份相等以 `itemId + customName + enchantments` 为准。

## 12. Data Components 处理方式

- 序列化：`DataComponentPatch.CODEC.encodeStart(...)` → JSON。
- 反序列化：`DataComponentPatch.CODEC.parse(...)` → `applyComponents` 重建组件模板。
- 26.1.2 Data Component API，未使用旧 NBT 方案。

## 13. JSON 最终结构

中文字段（Java 内部保持英文），示例：

```json
{
  "物品ID": "minecraft:diamond",
  "显示名称": "超级钻石",
  "原始名称": "钻石",
  "物品类型": "自定义物品",
  "自定义名称": "超级钻石",
  "数量": 1,
  "数据版本": 4790,
  "附魔": [],
  "数据组件": {}
}
```

## 14. AutoChest 与 ID 系统联动方式

```
ID识别 → ItemIdentity → ID文件 → ItemIdManager → ID配置管理
  → AutoChest物品选择器（ItemTargetSetting，只存身份键）
  → ItemIdentityMatcher.matchTarget → 真实Slot → 自动取物
```
AutoChest 只消费 ID 数据，单一数据源，无第二套数据库。

---

## 15 ~ 27（已实现项核对）

| 项 | 状态 |
| --- | --- |
| 三种运行模式（玩家控制/寻路/标点） | 已实现 |
| 三种取物模式（按数量/拿空/全部拿空） | 已实现 |
| 容器选择器（箱/陷阱箱/16色潜影盒/木桶/铜箱，可扩展） | 已实现 |
| 点位系统（设置/删除/查看/清空 + 确认弹窗） | 已实现（说明面板按钮） |
| ESP 三状态（绿/黄/红） | 已实现 |
| 目标锁 | 已实现 |
| 有限重试 | 已实现 |
| 冷却 | 已实现 |
| 多人保护 | 已实现 |
| 背包满停机 | 已实现 |
| 后台运行（不抢鼠标/焦点） | 已实现 |
| 持久化（ID/配置/点位/记录，不写 JAR） | 已实现 |
| 26.1.2 API 适配 | 已实现（Mojang 官方映射 + Data Component API） |

## 28. Gradle 最终结果

```
BUILD SUCCESSFUL in 5s
5 actionable tasks: 5 executed
```
产物：`build/libs/yiyiaddon1.1-beta4-personal.jar`

---

## 29. 尚未实现的内容

1. **手动输入 Data Components 完整 JSON**：当前自定义物品仅支持填写「自定义名称」，暂不支持手动粘贴完整 Data Components / 附魔 JSON 创建身份（需手持识别才带组件）。这是文档十三节的「可选增强」，非核心链路。
2. **真机测试**：第 30 节全部 53 项需进游戏验证，代码层面已实现但未跑真机。

## 30. 必须真实服务器测试的内容

重点：`.id 物品` 两模式切换、中文名搜索（钻石/绿宝石/金锭）、自定义物品添加、改名/附魔物品匹配、三模式寻路、三人保护、背包满、箱子破坏重放、死亡/退服/换维度、后台失焦。完整 53 项见需求文档第五十九节。

---

## 最终确认

系统保持：辅助 ├── 自动箱子 ├── ID识别 └── ID配置管理；三者同属辅助、联动、单一 ID 数据源。AutoChest 不含任何出售/换绿宝石/自动出售等经济逻辑。`.id 物品 / .id 实体` 命令保留，`.id` 不汉化。
