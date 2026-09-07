# AutoChest 辅助体系 · 第六阶段开发报告（核心容器交互 / 精确取物）

> 日期：2026-08-31
> 阶段：AutoChest 开箱、真实 Slot 识别、完整 ItemIdentity 匹配、按数量差额取物、精确取物、关箱、背包满停止
> 依赖：第五阶段报告（容器扫描 / 点位 / 记录 / ESP）
> 分类：辅助（未移动到其他分类）

---

## 0. 必读（接手继续开发前强制要求）

接手继续开发前，**必须完整读完以下两个个人开发习惯文件**，不得跳读：

1. `<项目根>/AGENTS.md`（五条铁律 + 关键速查）
2. `<项目根>/src\main\java\com\example\addon\convention\YiyiaddonConvention.java`（十章规范唯一正本）

关键铁律提醒（下一步开发前逐条对照）：
- 规范注释只增不删；证据驱动排查（Bug 先埋点取证据）；中文 + 日期归档。
- API 先查后写：Mojang 官方映射，查 `node 03-映射表/工具/查JARAPI.js <类名>`。
- 分类铁律：新功能独立英文包；中文注释；禁 emoji（用 ✓ ✗ ⚠ ▸）。
- 消息规范：前缀走 `YiyiaddonModule.formatMessage`；强调色走 `highlight*`；开关绿 §a / 关红 §c 不颠倒。

---

## 1. 本阶段结论

- 完成 AutoChest「开箱 → 真实 Slot 读取 → 完整 ItemIdentity 匹配 → 按模式取物 → 关箱」全链路，全部中文、禁 emoji。
- **真实 Slot 动态识别**：容器 / 玩家背包 / 快捷栏三分类（`SlotKind`），以 `slot.container != 玩家背包` 判容器侧，绝不写死槽位号；对箱子 / 陷阱箱 / 潜影盒 / 木桶 / 铜箱统一一套核心逻辑。
- **完整 ItemIdentity 组件级匹配**：`ItemIdentity` 反序列化时把落盘的 `dataComponents` 还原成 `DataComponentPatch` 重建组件模板，`matches()` 走 `isSameItemSameComponents` 做 Item ID + 自定义名 + Data Components + 附魔的精确匹配，替代 itemId 粗筛。
- **三种取物模式落地**：按数量差额取（`target - 玩家已有量`）/ 目标物品拿空 / 全部拿空；目标列表来自选中子集，不再用全部 ID 配置粗筛。
- **精确差额取物**：单叠超量时跨 tick 完成「左键拿整叠 → 右键逐格放 N 个 → 左键放回剩余」，只取差额不超取。
- **背包空间确认**：每次取物前 `getSlotWithRemainingSpace` 判断能否容纳，放不下立即停止。
- **完成判定收紧**：只有「取物完成 / 空箱 / 无目标 / 目标完成」才记已处理；容器意外关闭、背包满均不记已处理。
- **背包满停止**：背包满 → 关闭容器 → 播报并关闭自动箱子模块（延迟下一帧防状态机重入）。

---

## 2. 本阶段新增 / 修改文件

修改（`com.example.addon.autochest` 及 `itemid`）：

- `itemid/ItemIdentity.java` —— `fromJsonObject` 末尾用 `dataComponents` 反序列化回 `DataComponentPatch` 重建组件模板（组件级精确匹配恢复）
- `itemid/ItemIdentityMatcher.java` —— 改为工具类，收敛为 `matchTarget(stack, targets)` 完整身份匹配
- `itemid/ItemIdManager.java` —— 删除死代码 `isTarget`（唯一消费方已移除）
- `service/ChestInteractionService.java` —— 重写为完整取物引擎：真实 Slot 三分类 + 三模式取物 + 差额计算 + 精确取物会话 + 背包空间确认 + 三态结果
- `AutoChestStateMachine.java` —— WITHDRAW 改用三态结果；意外关闭不记已处理；背包满停止
- `AutoChestModule.java` —— 适配新构造（交互服务改传设置），新增 `stopAutomation` 停止入口

---

## 3. 关键实现点

1. **真实 Slot 三分类**：`kindOf(slot, inventory)` 返回 `CONTAINER / HOTBAR / INVENTORY`——容器侧以 `slot.container != 玩家背包` 判定，玩家侧按 `Inventory.isHotbarSlot(slot.getContainerSlot())` 细分快捷栏与主背包。取物只遍历 `CONTAINER`，放回只遍历玩家侧，对箱子 / 陷阱箱 / 潜影盒 / 木桶 / 铜箱统一生效，不按容器外观写多套核心逻辑。

2. **组件级精确匹配恢复**：识别时组件模板来自原始 `ItemStack`，可 `isSameItemSameComponents` 精确匹配；文件反序列化后组件模板原本为 null 退化为 itemId 粗筛。本阶段在 `fromJsonObject` 里 `DataComponentPatch.CODEC.parse` 还原补丁 → `applyComponents` 重建模板，改名 / 附魔物品恢复组件级精确匹配；注册表未就绪返回 null 优雅退化。

3. **匹配层收敛**：链路 `ItemStack → ItemIdentifier → ItemIdentity → ItemIdentityMatcher` 收口为工具类 `ItemIdentityMatcher.matchTarget(stack, targets)`（内部走 `identity.matches`），删除 `shouldWithdraw` / `matchesExact` / `ItemIdManager.isTarget` 死代码。

4. **三种取物模式**：`TAKE_ALL` 不匹配取所有非空；`TARGET_EMPTY` 只取目标列表命中；`TARGET_COUNT` 按差额取。目标列表来自 `targetItems.selectedIdentities()`（用户选中子集），替代原「全部 ID 配置」粗筛。

5. **差额计算**：`need = itemQuantities.quantityOf(identityKey) - countPlayerHas(inventory, identity)`；`countPlayerHas` 遍历玩家背包 36 格（快捷栏 + 主背包）统计匹配该身份的物品总数。目标 64、玩家已有 40、箱内 64 → 只取 24。

6. **精确差额取物**：整叠不超量（`takeCount >= stack.getCount()`）走一次 shift；单叠超量走跨 tick 会话 `PreciseWithdraw`——左键拿整叠 → 右键逐格放 1 个 × N → 左键放回剩余，光标快照用于找同种未满堆叠，避免超取。

7. **背包空间确认**：每次取物前 `getSlotWithRemainingSpace(stack) == -1` 视为放不下，标记 `foundButFull` 继续找下一个能放下的目标；全部放不下返回 `INVENTORY_FULL`。

8. **三态结果驱动状态机**：`withdrawOnce` 返回 `WITHDREW / FINISHED / INVENTORY_FULL`——`WITHDREW` 继续、`FINISHED` 关箱记已处理、`INVENTORY_FULL` 关箱 + 停止模块。

9. **完成判定收紧**：只有 `FINISHED`（取物完成 / 空箱 / 无目标 / 目标全部达标）→ `CLOSE → RECORD` 记已处理；WITHDRAW 中容器意外关闭改走 `reset + NEXT`（不记）；背包满走 `stopAutomation`（不记）。

10. **背包满停止**：`INVENTORY_FULL` → `interactionService.close()` + `lockedTarget = null` + `module.stopAutomation("背包已满")`；`stopAutomation` 播报后经 `mc.execute` 延后下一帧 `toggle()` 关模块，避免在状态机 `tick()` 内 toggle 造成重入。

11. **多人同步**：`withdrawOnce` 每次重新读 `menu.slots` 真实状态，不缓存旧数据；其他玩家拿走则本槽自动跳过，不按旧数据重复操作。

---

## 4. 编译结果

```
BUILD SUCCESSFUL in 1s
4 actionable tasks: 2 executed, 2 up-to-date
```

产物：`yiyiaddon1.1-beta4-personal.jar`（未混淆个人测试版，版本号未变更）。

---

## 5. 下一阶段（第七阶段）待办

> 下一阶段开工前，**必须先完整读完以下两个个人开发习惯文件**（与第 0 节相同，不得跳读）：
> 1. `<项目根>/AGENTS.md`
> 2. `<项目根>/src\main\java\com\example\addon\convention\YiyiaddonConvention.java`

1. **真机验证**：三模式 + 精确差额取物 + 背包满停止 + 容器破坏重放闭环，按第六章协议埋点取证据修正。
2. **跨数据版本兼容**：`dataComponents` 反序列化在 registry 变更时组件模板可能失效，需处理版本升级后的降级路径。
3. **多人并发抢箱**：精确取物会话中途其他玩家操作容器导致 stateId 剧烈变化的放弃判定细化。

> 结束前自检：死代码、@Mixin 声明、模块/指令注册、文件夹分类、无用 import。
