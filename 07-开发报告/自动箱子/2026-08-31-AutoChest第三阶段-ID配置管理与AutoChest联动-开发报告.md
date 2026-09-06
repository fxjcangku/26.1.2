# AutoChest 辅助体系 · 第三阶段开发报告（ID配置管理 + AutoChest选择器联动）

> 日期：2026-08-31
> 阶段：ID配置管理 GUI + AutoChest 目标选择器实时联动（已完成并编译通过）
> 依赖：第二阶段报告（ItemIdentity / ItemIdManager / ID文件）

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

- 完成「ID配置管理」完整交互 GUI（识别 / 手动添加 / 删除 / 刷新 / 打开目录 + 实时清单），属于「辅助」分类。
- 识别按钮直接调用 `ItemIdentifier`，**不模拟** `.id 物品` 聊天指令。
- 手动添加经当前 Minecraft Registry 验证，非法 ID 禁止保存。
- AutoChest 目标物品选择器数据源唯一来自 `ItemIdManager`，选择器只持久化「身份键」，运行时 `findByKey` 回查完整 `ItemIdentity`，不复制第二套物品数据。
- 打通「ID识别 → ID管理 → AutoChest」实时联动：`ItemIdManager` 增删/清空/重载后广播，AutoChest 选择器同步移除失效项并提示「ID已失效」，无需重启。
- 补齐基类 `YiyiaddonModule.addUniformButton()`（规范 5.7 要求的方法原本缺失）。

---

## 2. 本阶段新增 / 修改文件

新增（`com.example.addon.itemid`）：
- ItemTargetSetting.java —— AutoChest 目标物品选择器设置（继承 StringListSetting，自定义控件）
- ItemTargetSelectScreen.java —— 目标物品多选屏幕（左可选 / 右已选 + 关键词过滤）
- IdAddScreen.java —— 手动添加物品 ID 屏幕（Registry 验证）

修改（`com.example.addon.itemid`）：
- ItemIdentity.java —— 新增 `identityKey()` + 静态工厂 `fromItemId()`
- ItemIdManager.java —— 新增监听器 `addListener` / `findByKey` / `itemsDirectory`
- EntityIdManager.java —— 新增 `entitiesDirectory`
- IdConfigModule.java —— 重写为完整交互 GUI

修改（`com.example.addon.autochest`）：
- AutoChestSettings.java —— 新增「目标物品」分组 + `targetItems` 选择器
- AutoChestModule.java —— 注册 ItemIdManager 监听器实现选择器联动

修改（`com.example.addon.core`）：
- YiyiaddonModule.java —— 补齐 `addUniformButton()` + `BUTTON_MIN_WIDTH`
- AddonTemplate.java —— IdConfigModule 传入实体管理器 + 注册 ItemTargetSetting

---

## 3. 唯一数据源与选择器模型

数据链（唯一管理层 = `ItemIdManager`）：

```
ID识别 / 手动添加 ──> ItemIdManager（内存 Set + 一物一文件）
                            │  notifyChanged() 广播
                            ▼
                   AutoChest 目标选择器（只存身份键）
                            │  findByKey() 回查
                            ▼
                      完整 ItemIdentity（无副本）
```

关键约束：
- 选择器持久化的是 `identityKey()`（`itemId + 自定义名 + 附魔`，与 `equals` 判据一致），
  不是完整对象序列化，避免「ID识别 / ID管理 / AutoChest 三套数据」。
- 完整 `ItemIdentity` 永远只有 `ItemIdManager` 一份，运行时按键回查。
- `identityKey` 区分「钻石」与「超级钻石」（同 itemId 不同 customName）。

---

## 4. 关键实现点

1. **监听器广播**：`ItemIdManager` 维护 `List<Runnable>`，在 `add/remove/clear/reload` 后
   `notifyChanged()`；`AutoChestModule` 构造时注册监听器，回调里 `pruneInvalid()` 清理
   失效选中项并播报「有 N 个目标 ID 已失效，已从选择器移除」。
2. **选择器实时刷新**：选择屏幕数据源直接 `idManager.all()`，删除后可选列表自动消失、
   重新添加后自动重现，无需重启；`refreshCount()` 同步计数标签。
3. **手动添加验证**：`ItemIdentity.fromItemId()` 走 `Identifier.tryParse` +
   `BuiltInRegistries.ITEM.getValue` + 排除 `Items.AIR`，非法返回 null 禁止落盘。
4. **识别按钮不模拟输入**：`IdConfigModule.identifyItem()` 直接读主手→副手并调
   `ItemIdentifier.identifyItem(held)`，全程不走聊天框。
5. **目录打开**：26.1.2 已移除 `net.minecraft.Util`，改用 `java.awt.Desktop` +
   独立守护线程打开；Windows 路径含空格用 `explorer.exe /select,` 兜底。
6. **内存缓存**：`ItemIdManager.reload()` 只在刷新 / 模块激活时读盘，运行时 `all()`
   返回内存快照，禁止每 tick 读 JSON。

---

## 5. 目录结构（客户端数据目录）

```
<客户端数据目录>/AutoChest/
├── items/       物品身份（本阶段 + 第二阶段实现）
├── entities/    实体身份（本阶段 + 第二阶段实现）
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

## 7. 下一阶段（第四阶段）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `d:\mcaddon\26.1.2\AGENTS.md`
> 2. `d:\mcaddon\26.1.2\src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. **消费层接线**：`ItemIdentityMatcher` / `AutoChestModule` 按选择器勾选结果过滤——
   用 `targetItems.selectedIdentities()` 替代「全部 ID 匹配」，让选择器真正驱动取物。
2. `dataComponents` 反序列化回 `DataComponentPatch`，为改名/附魔物品提供组件级精确匹配。
3. `AutoChestStateMachine.tick()` 完整状态转换 + 按 `ScanMode` 分派。
4. `PathingService.computeStandPosition` 精细站位。
5. 取物完成判定 + 关箱 + 记录 + ESP 变红闭环。

> 结束前自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
