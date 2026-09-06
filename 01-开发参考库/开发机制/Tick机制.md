# Tick 机制

> 源码依据：Minecraft原始源码/net/minecraft/client/Minecraft.java 与 net/minecraft/client/multiplayer/ClientLevel.java 及 Meteor原始源码/meteordevelopment/meteorclient/mixin/MinecraftMixin.java 26.1.2 Mojang官方映射

本文讲「客户端游戏循环（game loop）的执行顺序：帧驱动、tick 计数、主 tick 内部各子系统顺序、收包调度，以及 Meteor addon 的 `TickEvent.Pre/Post` 注入点」。核心结论：**`Minecraft.runTick` 用 `deltaTracker` 把真实时间换算成 0~10 个 game tick，每个 tick 调 `Minecraft.tick()`；`tick()` 内部先 `packetProcessor.processQueuedPackets()` 再跑 GUI/输入/实体/世界；Meteor 在 `Minecraft.tick()` 的 HEAD/TAIL 注入 `TickEvent.Pre/Post`，这是 addon 每 tick 逻辑的唯一正确挂载点**。

---

## 一、帧驱动与 tick 计数（`DeltaTracker`）

```java
// Minecraft.java  源码 292 行
private final DeltaTracker.Timer deltaTracker = new DeltaTracker.Timer(20.0F, 0L, this::getTickTargetMillis);
```

- 目标 **20 TPS**（`20.0F`），`getTickTargetMillis()` 返回每 tick 目标毫秒数（默认 `1000/20 = 50ms`，受「帧率/tick 率」设置影响）。
- `runTick(advanceGameTime)`（源码 1259 行）是每一帧（渲染帧）的入口，`advanceGameTime` 决定本帧是否推进游戏 tick。

```java
// runTick 关键段（源码 1271-1295 行）
int ticksToDo = advanceGameTime ? this.deltaTracker.advanceGameTime(Util.getMillis()) : 0; // 本帧要补几个 tick
if (advanceGameTime) {
    this.packetProcessor.processQueuedPackets();   // 先处理排队的收包（主线程）
    this.runAllTasks();                            // 已调度的任务
    // ...
}
for (int i = 0; i < Math.min(10, ticksToDo); i++) {  // 最多补 10 tick（卡顿追赶）
    this.tick();                                   // 每个 game tick 跑一次
}
if (ticksToDo > 0 && (this.level == null || this.level.tickRateManager().runsNormally())) {
    this.drainedLatestTickGizmos = this.perTickGizmos.drainGizmos();
}
```

要点：
- **帧 ≠ tick**：高帧率时 `advanceGameTime` 可能返回 0（不推进 game tick，只渲染）；低帧率/卡顿会一次补多个 tick（上限 10）。
- `tick()` 可能一帧多次执行（卡顿追赶），addon 的每 tick 逻辑要能接受「一帧内多次触发」。
- 收包处理 `processQueuedPackets()` 在 tick **之前**，保证本 tick 世界逻辑用到的包已落地。

---

## 二、`Minecraft.tick()` 内部顺序（源码 1798 行起）

```java
public void tick() {
    this.clientTickCount++;                                  // tick 计数
    if (this.level != null && !this.pause) {
        this.level.tickRateManager().tick();                 // tick 率管理（冻结/慢速）
    }
    if (this.rightClickDelay > 0) this.rightClickDelay--;
    // gui
    this.textInputManager.tick();
    this.chatListener.tick();
    this.gui.tick(this.pause);
    this.pick(1.0F);                                         // 瞄准/射线
    this.tutorial.onLookAt(this.level, this.hitResult);
    // gameMode
    if (!this.pause && this.level != null) this.gameMode.tick();  // 挖掘进度/使用物品
    // screen / overlay tick（当前界面）
    if (this.screen != null) this.screen.tick();
    if (this.overlay != null) this.overlay.tick();
    // 无界面时处理按键 + 输入
    if (this.overlay == null && this.screen == null) this.handleKeybinds();  // 键位 → 移动/交互
    // 世界
    if (this.level != null) {
        if (!this.pause) {
            this.gameRenderer.tick();
            this.level.tickEntities();        // 实体 tick（含本地玩家，源码 1867 行）
            this.level.tickBlockEntities();   // 方块实体 tick（源码 1869 行）
        }
    }
    this.musicManager.tick();
    this.soundManager.tick(this.pause);
    if (this.level != null && !this.pause) {
        // 世界级 tick：区块/时间/边界/天气
        this.level.tick(() -> true);          // 源码 1892 行
    }
    // ... 截图/统计等收尾
}
```

### 顺序约定（对 addon 很重要）
1. **Pre（tick 开头）**：世界状态是「上一 tick 结算后」的状态。
2. **输入（`handleKeybinds`）**：写在 `LocalPlayer.tick()` 之前的键盘读取，会写入 `input`，随后 `applyInput/aiStep` 消费。
3. **实体 tick（`level.tickEntities`）**：本地玩家移动 + 发包发生在这一阶段（`LocalPlayer.tick` 内）。
4. **世界 tick（`level.tick(() -> true)`）**：区块/时间推进。
5. **Post（tick 结尾）**：世界/玩家均已更新完，最常用于「读更新后的状态、做后续动作」。

### `ClientLevel.tick(BooleanSupplier)`（源码 283 行）
```java
public void tick(final BooleanSupplier haveTime) {
    this.updateSkyBrightness();
    if (this.tickRateManager().runsNormally()) {
        this.getWorldBorder().tick();
        this.tickTime();                     // gameTime++
    }
    // ... 爆炸/区块 tick：this.chunkSource.tick(haveTime, true)
}
```
注意 `tickEntities()`（源码 383 行）不在这里，是 `Minecraft.tick()` 单独调用的（见上）。

---

## 三、客户端 20-tick 节拍与「世界时间」

- 游戏时间 `gameTime` 由 `ClientLevel.tickTime()` 每 tick +1（`setTimeFromServer` 会以服务端 `ClientboundSetTimePacket` 权威值覆盖，源码 `handleSetTime` 1137 行）。
- `clientTickCount` 是客户端本地累计 tick（`Minecraft.tick()` 首个自增），与 `gameTime` 不同源。

---

## 四、Meteor addon 的注入点（`TickEvent.Pre/Post`）

源码 `meteordevelopment/meteorclient/mixin/MinecraftMixin.java`：

```java
@Inject(at = @At("HEAD"), method = "tick")
private void onPreTick(CallbackInfo ci) {
    // MeteorClient.EVENT_BUS.post(TickEvent.Pre.get());
}

@Inject(at = @At("TAIL"), method = "tick")
private void onTick(CallbackInfo ci) {
    // MeteorClient.EVENT_BUS.post(TickEvent.Post.get());
}

@Inject(method = "runTick", at = @At("HEAD"))
private void onRunTick(CallbackInfo ci) {
    // Utils.frameTime = 本帧耗时（秒）
}
```

- `TickEvent.Pre`（`tick()` HEAD）：在「输入还没写、世界还没动」时触发。
- `TickEvent.Post`（`tick()` TAIL）：在「world tick 完成」后触发。
- 还有 `runTick` HEAD 计算 `frameTime`（帧耗时），供 addon 做帧相关平滑/节流。
- addon 订阅：`EventHandler<TickEvent.Post>` 处理「每 tick 读状态/驱动动作」（Meteor Module 的 `onTick()` 走这里）；`onPreTick` 细分可用 `TickEvent.Pre`。
- 注意 `tick()` 可能一帧多次（卡顿追赶 10 上限），`TickEvent` 也会同帧多次 post；高频逻辑要节流（`stateTick%N` 或去重锁）。

---

## addon 介入点

1. **每 tick 逻辑**：订阅 `TickEvent.Post`（或 Module `onTick`），做周期检测/自动动作/状态机推进。
2. **每 tick 前置**：订阅 `TickEvent.Pre`（输入前的状态快照、按键覆盖）。
3. **帧耗时**：读 `Utils.frameTime`（Meteor）或自算，做运动平滑。
4. **直接拦 tick**：Mixin `Minecraft.tick()` HEAD/TAIL（自定义，注意别与 Meteor 重复注入冲突）。
5. **tick 率感知**：`level.tickRateManager().runsNormally()` / `isEntityFrozen(entity)` 判断世界是否冻结/慢速，决定要不要暂停自动化。

---

## 常见坑

1. **`tick()` 会一帧多次**：卡顿时 `ticksToDo` 一次补多个 tick，`TickEvent` 同帧多次；别用「每帧一次」假设，高频状态用去重锁。
2. **Pre 与 Post 的世界状态不同**：Pre 是旧状态、Post 是新状态；读「更新后」结果放 Post，改「输入」放 Pre 或 `handleKeybinds` 前后。
3. **暂停时不推进**：`this.pause` 为真时 `level.tickEntities/level.tick` 全跳过，addon 在暂停屏继续推进有状态机会「世界没动但状态机在跑」，需判 `mc.isPaused()`。
4. **世界冻结（tick 率）**：`tickRateManager` 可冻结实体/世界（`isEntityFrozen`），此时实体 `isRemoved` 不变但 `tickCount` 不增，别用 `tickCount` 做唯一节拍。
5. **收包在 tick 前处理**：`processQueuedPackets` 先于本 tick，所以 Post 里读到的容器/位置已是「本轮最新包」；想在收包前拦东西要 Mixin 到 `channelRead0`/`handleXxx`。
6. **`gameTime` 与 `clientTickCount` 不是一回事**：服务端权威用 `ClientboundSetTimePacket` 校准 `gameTime`，节律统计别混用两个计数。

## 源码依据

- `net/minecraft/client/Minecraft.java` — `deltaTracker`(292)、`runTick`(1259)、`ticksToDo`(1271)、`processQueuedPackets`(1276)、`tick`(1798)、`level.tickEntities`(1867)、`level.tickBlockEntities`(1869)、`level.tick`(1892)、`getDeltaTracker`(2698)
- `net/minecraft/client/multiplayer/ClientLevel.java` — `tick`(283)、`tickTime`、`setTimeFromServer`、`tickEntities`(383)、`tickNonPassenger`(352)
- `net/minecraft/client/multiplayer/ClientPacketListener.java` — `handleSetTime`(1137)（世界时间校准）
- `net/minecraft/util/DeltaTracker.java`（关联） — `Timer`、`advanceGameTime`、`getGameTimeDeltaPartialTick`
- `Meteor原始源码/meteordevelopment/meteorclient/mixin/MinecraftMixin.java` — `onPreTick`(133)、`onTick`(147)、`onRunTick`(267)、`TickEvent`
- `Meteor原始源码/meteordevelopment/meteorclient/events/world/TickEvent.java`（关联） — `Pre/Post` 单例事件