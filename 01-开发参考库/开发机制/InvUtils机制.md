# InvUtils 机制

> `InvUtils` 是背包操作的统一入口：找物品用 `find*` + `FindItemResult`，切换物品用 `swap/swapBack`，容器点击用链式 `Action`（`move/click/quickSwap/shiftClick/drop/dropOne`）。它自己不做「槽位索引→容器 id」换算，那部分在 `SlotUtils`。

## 概述

类：`meteordevelopment.meteorclient.utils.player.InvUtils`，含 `private InvUtils(){}`。关键静态字段：

```java
private static final Action ACTION = new Action();  // 链式动作单例，复用
public static int previousSlot = -1;                // swapBack 记录原选中槽
```

槽位分段（由 `SlotUtils` 定义，见 26.1.2 图表）：

```java
HOTBAR_START=0  HOTBAR_END=8   // 热键栏
MAIN_START=9    MAIN_END=35    // 主背包
ARMOR_START=36  ARMOR_END=39   // 四件护甲
OFFHAND=40                      // 副手
```

---

## 1. 查找（find / FindItemResult）

`FindItemResult`（`record FindItemResult(int slot, int count)`，同包）提供判定与归属：

```java
public boolean found()        // slot != -1
public InteractionHand getHand()   // OFF_HAND / MAIN_HAND / null
public boolean isHotbar() / isMain() / isArmor()
```

`find(Predicate<ItemStack>, int start, int end)` 遍历 `mc.player.getInventory().getItem(i)`，找到首个命中槽存 `slot`，并累计目标槽内物品数量 `count`；**返回 `slot=-1` 表示没找到**。注意 `find` 在 `mc.player == null` 时短路返回 `new FindItemResult(0, 0)`（`slot=0`，不是 -1）。

查找重载（真实名）：

```java
public static FindItemResult findEmpty()                       // 找空槽
public static FindItemResult findInHotbar(Item... items)       // 副手→主手→热键栏0-8
public static FindItemResult findInHotbar(Predicate<ItemStack>)
public static FindItemResult find(Item... items)               // 全背包（0..getContainerSize）
public static FindItemResult find(Predicate<ItemStack>)
public static FindItemResult findFastestTool(BlockState state) // 热键栏内挖这方块最快的稿
```

`findInHotbar` 顺序：先测副手（`SlotUtils.OFFHAND`），再测主手选中槽（`getSelectedSlot()`），最后扫 0–8。`findFastestTool` 按 `stack.isCorrectToolForDrops(state)` + `getDestroySpeed(state)` 评分取最大。

## 2. 物品测试（test*）

```java
public static boolean testInMainHand(...) / testInOffHand(...) / testInHands(...) / testInHotbar(...)
```

- 每个都提供 `(Predicate<ItemStack>)` 与 `(Item... items)` 两种重载，`Item...` 版本内部走 `isOneOf(...)`。
- `testInHotbar` 先 `testInHands`（副手+主手）再扫 0–8。
- `testInHands = testInMainHand || testInOffHand`。

## 3. 切换物品（swap / swapBack）

```java
public static boolean swap(int slot, boolean swapBack) {
    if (slot == SlotUtils.OFFHAND) return true;
    if (slot < 0 || slot > 8) return false;
    if (swapBack && previousSlot == -1) previousSlot = mc.player.getInventory().getSelectedSlot();
    else if (!swapBack) previousSlot = -1;

    mc.player.getInventory().setSelectedSlot(slot);
    ((IMultiPlayerGameMode) mc.gameMode).meteor$syncSelected();   // 同步服务端选中槽
    return true;
}

public static boolean swapBack() {
    if (previousSlot == -1) return false;
    boolean r = swap(previousSlot, false);
    previousSlot = -1;
    return r;
}
```

- `swap(slot, true)` 会记住原槽，之后 `swapBack()` 换回；`swap(slot, false)` 不记录（`previousSlot` 置 -1）。
- `setSelectedSlot` 之后必须 `meteor$syncSelected()`（`IMultiPlayerGameMode` mixin 接口）否则服务端不知道你换了物品。
- `BlockUtils.place` 的 `swapBack` 参数就是透传到这里（见「BlockUtils机制」）。

## 4. 容器点击（Action 链式 API）

取单例：

```java
public static Action move()      // PICKUP + two=true    （拿起再放下，用于同屏两槽互换）
public static Action click()     // PICKUP                （单点，可只 from 或 from+to）
public static Action quickSwap() // SWAP                   热键交换（number key）
public static Action shiftClick()// QUICK_MOVE             快速移动（shift）
public static Action drop()      // THROW + data=1         整组丢弃
public static Action dropOne()   // THROW + data=0         丢弃单个
```

`Action` 内部字段：`ContainerInput type`、`boolean two`、`int from`、`int to`、`int data`、`boolean isRecursive`。

`from*` 系列（返回 `Action` 便于链式）与 `to*`/`slot*` 系列（`void`，调用即执行 `run()`）：

```java
public Action fromId(int id) / from(int index) / fromHotbar(i) / fromOffhand() / fromMain(i) / fromArmor(i)
public void toId(int id) / to(int index) / toHotbar(i) / toOffhand() / toMain(i) / toArmor(i)
public void slotId(int id) / slot(int index) / slotHotbar(i) / slotOffhand() / slotMain(i) / slotArmor(i)
```

- `from`/`to` 的 index 版本走 `SlotUtils.indexToId(index)`，id 版本直接用容器 id。quickSwap 注释强调「**from/to 都应传 id 而非 index**」。
- `slot*` 是「同一槽点击」（`from = to = id` 后 `run()`）。
- `fromArmor(i)` / `toArmor(i)` / `slotArmor(i)` 的 `i` 是 `EquipmentSlot` 里的护甲顺序，内部换算 `SlotUtils.ARMOR_START + (3 - i)`。

### Action.run() 执行逻辑

```java
private void run() {
    boolean hadEmptyCursor = mc.player.containerMenu.getCarried().isEmpty();

    if (type == ContainerInput.SWAP) { data = from; from = to; }   // quickSwap 交换 from/to

    if (type != null && from != -1 && to != -1) {
        click(from);
        if (two) click(to);        // move 是两段：拿起 from 后放到 to
    }
    // ... 重置所有字段 ...

    // 递归补偿：若原本鼠标空、执行了 PICKUP+two 且结束后鼠标非空，自动把多拿的放回 from
    if (!isRecursive && hadEmptyCursor && preType == ContainerInput.PICKUP && preTwo
        && (preFrom != -1 && preTo != -1) && !mc.player.containerMenu.getCarried().isEmpty()) {
        isRecursive = true;
        InvUtils.click().slotId(preFrom);
        isRecursive = false;
    }
}

private void click(int id) {
    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, id, data, type, mc.player);
}
```

`click(id)` 走原版 `MultiPlayerGameMode.handleContainerInput(containerId, slotId, data, ContainerInput, player)`。

`dropHand()` 独立：清空鼠标悬停物品 `mc.gameMode.handleContainerInput(containerId, SLOT_CLICKED_OUTSIDE, 0, PICKUP, player)`。

## 5. SlotUtils —— 索引→容器 id 换算

`SlotUtils.indexToId(int slotIndex)` 根据当前打开的 `mc.player.containerMenu` 类型（`switch (handler)`）把背包索引映射成该屏幕的槽位 id（`InventoryMenu`/`CreativeModeInventoryScreen.ItemPickerMenu`/`ChestMenu`/`CraftingMenu`/`FurnaceMenu` 等 /`EnchantmentMenu`/`AnvilMenu`/`GrindstoneMenu`/`MerchantMenu`/`HorseInventoryMenu` 等 20+ 种），未知返回 -1。id 才是「与服务器通信」用的槽位号。

## 运行链路

1. 模块调 `InvUtils.find(Items.DIAMOND_PICKAXE)` → 得到 `FindItemResult`（含 slot/count）。
2. 若 `res.isHotbar()` 且非副手 → `InvUtils.swap(res.slot(), true)` 切过去（同步服务端）→ 做操作 → `InvUtils.swapBack()`。
3. 容器内整理 → `InvUtils.move().fromId(a).toId(b)` 或 `InvUtils.shiftClick().slot(0)`，`Action.run()` 内部 `handleContainerInput` 发包。
4. `InventorySorter`（`utils/player/InventorySorter.java`）用 `InvUtils.move().fromId(...).toId(...)` 做自动整理（真实用法）。

## Addon 用法 / 介入点

```java
FindItemResult r = InvUtils.find(Items.OBSIDIAN, Items.ENDER_CHEST);   // 多物品任一
if (r.found() && r.isHotbar()) {
    InvUtils.swap(r.slot(), true);   // 记住原槽
    BlockUtils.place(pos, r, 100);   // 配合放置
    InvUtils.swapBack();
}

// 快速移动一堆原木到箱子（当前容器打开时）
InvUtils.shiftClick().slotHotbar(0);
// 丢弃一组石子
InvUtils.drop().slotMain(3);
```

## 常见坑

1. **`find` 空连锁陷阱**：`mc.player == null` 时返回 `slot=0`（不是 -1），判断「是否找到」务必用 `found()`（`slot != -1`）而非 `slot == 0`。
2. **`findFastestTool` 只扫热键栏 0–8**：主背包里的工具不会进结果。
3. **`swap` 必须 `syncSelected`**：直接 `setSelectedSlot` 不改服务端，要用 `InvUtils.swap` 而非裸 `getInventory().setSelectedSlot`。
4. **`previousSlot` 是全局单例状态**：`swap(slot, true)` + 未 `swapBack()` 就第二次 `swap(..., true)` 会因 `previousSlot != -1` 拒绝记录新原槽（`previousSlot` 仍是旧的）。要成对调用，或多层交换用参数 `swapBack=false`。
5. **id 与 index 别混用**：`from`/`to` 参数用的是**容器 id**（`SlotUtils.indexToId` 或直接 Slot 的 id）；热键/主背包是 id==index 的场景才等价，打开工作台/铁砧等屏幕时完全不同。
6. **`ACTION` 是共享单例**：`Action` 链式调用不是线程安全、非重入；别在链式中间重入调用另一个 `InvUtils.xxx()` 动作。
7. **`move()` 是两段点击（拿起+放下）**：`fromId(...).toId(...)` 里 `two=true` 会连点两次，若两个槽物品数量不等会触发 `run()` 末尾的「多拿补偿递归」，注意该行为。

## 源码依据

- `meteordevelopment/meteorclient/utils/player/InvUtils.java` —— find/test/swap/Action 全部方法、`run()` 递归补偿、`previousSlot`。
- `meteordevelopment/meteorclient/utils/player/FindItemResult.java` —— `found/getHand/isHotbar/isMain/isArmor`。
- `meteordevelopment/meteorclient/utils/player/SlotUtils.java` —— 槽位常量、`indexToId` 全容器类型映射。
- `meteordevelopment/meteorclient/utils/player/InventorySorter.java` —— `InvUtils.move()` 链式真实用法。
- `meteordevelopment/meteorclient/mixininterface/IMultiPlayerGameMode.java` —— `meteor$syncSelected`。
- `meteordevelopment/meteorclient/mixininterface/ISlot.java` —— 槽位 index 读取（InventorySorter 依赖）。