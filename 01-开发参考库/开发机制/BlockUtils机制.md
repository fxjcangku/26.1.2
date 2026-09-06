# BlockUtils 机制

> `BlockUtils` 是「放方块 + 挖方块」的统一入口，addon 的自动化建房/挖矿/穿墙都靠它。它内部组合 `Rotations`（转视角）、`InvUtils`（切物品）、`mc.gameMode.useItemOn/startDestroyBlock`（真实交互），并用一对 `TickEvent` 监听维护「正在挖掘」的状态机。

## 概述

类：`meteordevelopment.meteorclient.utils.world.BlockUtils`，`private BlockUtils(){}`。关键静态字段：

```java
public static boolean breaking;             // 是否处于「持续挖」状态（对外可读）
private static boolean breakingThisTick;    // 本 tick 是否发起过挖掘
```

`@PreInit` 自订阅事件：

```java
@PreInit
public static void init() {
    MeteorClient.EVENT_BUS.subscribe(BlockUtils.class);
}
```

---

## 1. 放置（place / interact）

多层重载最终汇聚到：

```java
public static boolean place(BlockPos blockPos, InteractionHand hand, int slot,
    boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack)
```

内部流程（源码证实）：

1. `slot < 0 || slot > 8` 直接 false（必须热键栏）。
2. 从 `hand` 对应槽取 `ItemStack`，若是 `BlockItem` 取其 `block`，否则默认 `Blocks.OBSIDIAN`。
3. `canPlaceBlock(blockPos, checkEntities, toPlace)` 不满足即退出。
4. `hitPos = Vec3.atCenterOf(blockPos)`；`getPlaceSide(blockPos)` 找摆放侧面，无侧面则 `Direction.UP` + `neighbour = blockPos`（直接点目标方块自身），有侧面则偏移 0.5 网格作命中点。
5. 构造 `BlockHitResult(hitPos, side.getOpposite(), neighbour, false)`。
6. `rotate=true` 时 `Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotationPriority, () -> { InvUtils.swap(slot, swapBack); interact(bhr, hand, swingHand); if (swapBack) InvUtils.swapBack(); })`；`rotate=false` 直接同步顺序执行。

`interact(BlockHitResult, InteractionHand, boolean swing)` 是真正发包点：

```java
boolean wasSneaking = mc.player.isShiftKeyDown();
mc.player.setShiftKeyDown(false);                  // 临时取消潜行（防止 shift 右击）
InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, blockHitResult);
if (result.consumesAction()) {
    if (swing) mc.player.swing(hand);
    else mc.getConnection().send(new ServerboundSwingPacket(hand));
}
mc.player.setShiftKeyDown(wasSneaking);
```

辅助判定（真实名）：

- `canPlaceBlock(BlockPos, boolean checkEntities, Block block)`：`Level.isInSpawnableBounds` + `getBlockState(blockPos).canBeReplaced()` + （可选）`mc.level.isUnobstructed(block.defaultBlockState(), blockPos, CollisionContext.empty())`。
- `canPlace(BlockPos, boolean checkEntities)` / `canPlace(BlockPos)`：用 `Blocks.OBSIDIAN` 兜底。
- `getPlaceSide(BlockPos)`：遍历 6 方向，跳过空气/可点击方块/流体邻居，按「视线方向与该轴方向点积」取最高相关度侧面。
- `getClosestPlaceSide(BlockPos)` / `getClosestPlaceSide(BlockPos, Vec3 pos)`：同上但按邻居距离最近。

## 2. 挖掘状态机（breaking / onTickPre / onTickPost）

```java
@EventHandler(priority = EventPriority.HIGHEST + 100)
private static void onTickPre(TickEvent.Pre event) { breakingThisTick = false; }

@EventHandler(priority = EventPriority.LOWEST - 100)
private static void onTickPost(TickEvent.Post event) {
    if (!breakingThisTick && breaking) {
        breaking = false;
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }
}
```

- 每 tick 先于所有模块重置 `breakingThisTick`；tick 末若**本 tick 没有重新发起挖掘**但 `breaking` 仍为 true，说明挖矿被中断（目标被破坏/更换），主动 `stopDestroyBlock()` 停止原版持续挖掘并复位。
- 优先级用 `HIGHEST + 100`（最早）与 `LOWEST - 100`（最晚），夹住中间所有模块的挖掘调用。

`breakBlock`（注释要求**只在 `TickEvent.Pre` 里调用**）：

```java
public static boolean breakBlock(BlockPos blockPos, boolean swing) {
    if (!canBreak(blockPos, mc.level.getBlockState(blockPos))) return false;
    BlockPos pos = blockPos instanceof BlockPos.MutableBlockPos ? new BlockPos(blockPos) : blockPos; // 防外部字段被改

    InstantRebreak ir = Modules.get().get(InstantRebreak.class);
    if (ir != null && ir.isActive() && ir.blockPos.equals(pos) && ir.shouldMine()) { ir.sendPacket(); return true; }

    if (mc.gameMode.isDestroying()) mc.gameMode.continueDestroyBlock(pos, getDirection(blockPos));
    else mc.gameMode.startDestroyBlock(pos, getDirection(blockPos));

    if (swing) mc.player.swing(InteractionHand.MAIN_HAND);
    else mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

    breaking = true;
    breakingThisTick = true;
    return true;
}
```

`canBreak(BlockPos, BlockState)`：非创造且 `state.getDestroySpeed(mc.level, blockPos) < 0`（基岩/不可破坏）返回 false；`state.getShape(...) != Shapes.empty()` 才可破。

挖掘速度判定（真实名）：

- `canInstaBreak(BlockPos, float breakSpeed)`：创造模式或 `calcBlockBreakingDelta2(...) >= 1`。
- `canInstaBreak(BlockPos)`：用 `mc.player.getDestroySpeed(state)` 兜底。
- `calcBlockBreakingDelta2(BlockPos, float breakSpeed)`：`f == -1` 返回 0，否则 `breakSpeed / f / (hasCorrectToolForDrops ? 30 : 100)`。
- `getBreakDelta(int slot, BlockState state)`：按指定槽的 `getDestroySpeed(slot, state)` 计算破坏进度 delta。
- 私有 `getDestroySpeed(int slot, BlockState)`：复刻 `Player#getDestroySpeed`（`@see` 注释），含效率附魔、急迫/挖掘疲劳、水下、空中减速修正。

## 3. 其他工具

- `isClickable(Block block)`：`instanceof` 判断工作台/铁砧/织布机/制图台/砂轮/切石机/按钮/压力板/门/床/栅栏门等可交互方块（`getPlaceSide` 跳过这类邻居的依据）。
- `getDirection(BlockPos)`：按玩家眼位与方碰撞箱关系选 UP/DOWN/水平方向（`startDestroyBlock` 参数用）。
- `isValidMobSpawn(BlockPos, BlockState, int spawnLightLimit)`：返回 `MobSpawn.Never/Potential/Always`，考虑雪层、下方 `isValidSpawnBlock`、方块光/天空光阈值。
- `isValidSpawnBlock(BlockState)`：基岩/屏障/`TransparentBlock`/脚手架返回 false，灵魂沙/泥土 true，`SlabBlock` 上半砖、`StairBlock` 上半台阶 true，否则 `isSolidRender()`。
- `isExposed(BlockPos)`：6 方向任一邻居非 `isSolidRender()` 即视为暴露。
- `mutateAround(MutableBlockPos mutable, BlockPos origin, int x, int y, int z)`：就地改写 `MutableBlockPos` 偏移后返回。
- 枚举 `MobSpawn { Never, Potential, Always }`。

## 运行链路

1.（放方块）模块拿到目标 `BlockPos` + `FindItemResult` → `BlockUtils.place(...)` → `Rotations.rotate` 转过去 → 回调里 `InvUtils.swap` 切物品 → `mc.gameMode.useItemOn` 发 `UseItemOn` 交互 → `swapBack`。
2.（挖方块）模块在 `TickEvent.Pre` 调 `BlockUtils.breakBlock` → `startDestroyBlock/continueDestroyBlock` → 原版每 tick 持续挖掘 → `BlockUtils.onTickPost` 检测中断则 `stopDestroyBlock`。
3. `canPlaceBlock/canBreak/canInstaBreak` 作为「能不能做」前置校验，`getPlaceSide`/`getDirection` 提供命中面/朝向。

## Addon 用法 / 介入点

```java
// 放黑曜石（自动转头 + 切物品 + 放完切回）
FindItemResult obs = InvUtils.findInHotbar(Items.OBSIDIAN);
if (obs.found() && BlockUtils.canPlace(targetPos, true)) {
    BlockUtils.place(targetPos, obs, 100, true);   // (pos, findItem, priority, checkEntities)
}

// 挖方块（只在 Pre 阶段）
@EventHandler
private void onTick(TickEvent.Pre event) {
    if (BlockUtils.canBreak(pos)) BlockUtils.breakBlock(pos, true);
}

// 是否一击即破（SpeedMine/PacketMine 判定）
if (BlockUtils.canInstaBreak(pos)) { ... }
```

## 常见坑

1. **`breakBlock` 必须在 `TickEvent.Pre` 里调**（源码注释明确），否则 `breakingThisTick` 状态机时序错乱，`stopDestroyBlock` 会误触发。
2. **`place` 的 `slot` 必须是 0–8 热键栏索引**（`slot < 0 || slot > 8` 直接 false），主背包物品要先移到热键栏或用 `fromMain` 整理。
3. **含实体的格子 `checkEntities=true` 会拒绝放置**：`mc.level.isUnobstructed` 用 `CollisionContext.empty()`，实体碰撞即失败；需忽略用 `checkEntities=false`。
4. **`interact` 把潜行临时取消又恢复**：潜行交互类（如箱子/按钮在 shift 右击逻辑）自行处理，不要依赖 `BlockUtils` 保留潜行状态。
5. **`place` 的 `swapBack` 与 `InvUtils.previousSlot` 联动**：传 `true` 时记住原槽、放完切回；传 `false` 不记（见「InvUtils机制」坑 4）。
6. **`canBreak` 对基岩**：非创造 `getDestroySpeed < 0` 返回 false，防死循环挖不可破坏方块。
7. **`getPlaceSide` 可能返回 null**：被完全包围时 `side==null` 走 UP 兜底并 `neighbour=blockPos`，命中点用方块中心——此时实际能否放置由 `canPlaceBlock` 前置把关。

## 源码依据

- `meteordevelopment/meteorclient/utils/world/BlockUtils.java` —— place/interact/break/状态机/placeSide/canBreak/canInstaBreak/getDestroySpeed/isClickable/isValidMobSpawn/isExposed。
- `meteordevelopment/meteorclient/utils/player/Rotations.java` —— `rotate`/`getYaw`/`getPitch`（place 依赖）。
- `meteordevelopment/meteorclient/utils/player/InvUtils.java` —— `swap`/`swapBack`（place 依赖）。
- `meteordevelopment/meteorclient/utils/player/FindItemResult.java` —— `slot/isOffhand/isHotbar`。
- `meteordevelopment/meteorclient/utils/player/SlotUtils.java` —— `OFFHAND` 常量。
- `meteordevelopment/meteorclient/systems/modules/player/InstantRebreak.java` —— `breakBlock` 的秒破分支依赖。
- `meteordevelopment/meteorclient/events/world/TickEvent.java` —— `TickEvent.Pre/Post`（状态机注入）。
- `meteordevelopment/meteorclient/utils/PreInit.java` —— `@PreInit` 自订阅。