# Rotation 机制

> 旋转（Rotation）不是「玩家视角直接改」这么简单：Meteor 用一套「请求队列 + 伪装视角 + 独立发包」的机制，把「我要看向某点」变成实际头部朝向 + `ServerboundMovePlayerPacket.Rot`。

## 概述

核心是 `Rotations`（`meteordevelopment.meteorclient.utils.player.Rotations`），配合 `SendMovementPacketsEvent.Pre/Post`（由 `LocalPlayerMixin.sendPosition` post）实现「每 tick 在发包前临时改玩家 yaw/pitch，发完再还原」。它维护一个按优先级排序的旋转请求队列，并在 sendPosition 生命周期里消费队列。

关键静态字段：

```java
public static float serverYaw; public static float serverPitch;   // 服务器认为的朝向
public static int rotationTimer;
public static boolean rotating = false;
```

---

## 1. 旋转请求如何变成实际朝向（request → 队列）

入口 `Rotations.rotate(...)` 多层重载，最终：

```java
public static void rotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
    Rotation rotation = rotationPool.get();        // 从 Pool<Rotation> 取对象
    rotation.set(yaw, pitch, priority, clientSide, callback);
    int i = 0;
    for (; i < rotations.size(); i++)
        if (priority > rotations.get(i).priority) break;   // 降序插入（高 priority 先）
    rotations.add(i, rotation);
}
```

- 重载精简：`rotate(yaw, pitch, priority, callback)` = clientSide=false；`rotate(yaw, pitch, callback)` = priority 0；`rotate(yaw, pitch)` = priority 0 无 callback。
- `Priority` 越大越先（与 EventPriority 同向，但这里是自定义 int，0 是默认）。

## 2. 发包生命周期（SendMovementPacketsEvent 钩子）

`LocalPlayerMixin.sendPosition` 在两个位置 post（源码证实）：

```java
@Inject(method = "sendPosition", at = @At("HEAD"))  sendPre();   // SendMovementPacketsEvent.Pre
@Inject(method = "sendPosition", at = @At("TAIL"))  sendPost();  // SendMovementPacketsEvent.Post
// 骑乘场景还有 tick 里 ordinal=1 的 before/after 两个对称注入
```

`Rotations` 在 `@PreInit` 里 `EVENT_BUS.subscribe(Rotations.class)`，两个监听：

**Pre**（`onSendMovementPacketsPre`）：若 `mc.getCameraEntity() != mc.player` 直接 return。取队首 `rotations.get(i)`，`setupMovementPacketRotation(rotation)` → `setClientRotation`（`mc.player.setYRot/setXRot` 临时改视角）+ `setCamRotation`（`serverYaw/serverPitch` + `rotationTimer=0`）。若非空，`i++` 推进；否则按 `lastRotation` 保持。

**Post**（`onSendMovementPacketsPost`）：对 `i` 之后的每个剩余旋转走 `setCamRotation → (clientSide? setClientRotation) → rotation.sendPacket() → (clientSide? resetPreRotation)`，最后 `rotations.clear()`。若只有一次请求，则把它记为 `lastRotation` 以便下 tick 保持（`Config.rotationHoldTicks` 决定保持 tick 数）。

`Rotation.sendPacket()`（**真正的旋转发包点**）：

```java
mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
    (float) yaw, (float) pitch, mc.player.onGround(), mc.player.horizontalCollision));
runCallback();
```

`resetPreRotation()` 还原 `preYaw/prePitch`（`setClientRotation` 时保存的原朝向），保证视觉朝向不被真正修改（除非 `clientSide=true`）。

## 3. 要不要 rotation 发送适配？——有，就在 `Rotations` 内发 `ServerboundMovePlayerPacket.Rot`

源码证实：Meteor **没有**独立「rotation 发送适配层」类，旋转发包就在 `Rotations.Rotation.sendPacket()` 里直接 `new ServerboundMovePlayerPacket.Rot(...)` 经 `mc.getConnection().send(...)` 发出。`ServerboundMovePlayerPacketAccessor`（addon）与 `ServerboundMovePlayerPacketMixin`（Meteor）用于外部读写该包字段（如手滑改装 yaw/pitch/onGround）。

## 4. 玩家视角实际改动的数据通道

两条路径：

- **视觉朝向**（真正影响玩家相机所见）：由模块/调用方直接 `mc.player.setYRot/setXRot`，或 `Rotations` 在 `clientSide=true` 时 `setClientRotation`（`setYRot`+`setXRot`，不还原）。
- **服务端朝向**（发包）：`serverYaw/serverPitch` + `ServerboundMovePlayerPacket.Rot`，供服务器同步。

`setCamRotation(double yaw, double pitch)` 只改 `serverYaw/serverPitch` 与 `rotationTimer`，不改玩家实体——这就是「静默转向」(silent rotation) 的依据：客户端实体 yaw/pitch 不变，仅发 `Rot` 包告诉服务器朝向。

## 5. 角度计算（getYaw/getPitch）

```java
public static double getYaw(Vec3 pos)   // atan2(dz, dx) - 90, wrapDegrees
public static double getPitch(Vec3 pos) // -atan2(dy, diffXZ), 眼高补偿
public static double getYaw(Entity) / getPitch(Entity, Target) / getYaw(BlockPos) / getPitch(BlockPos)
```

`Target` 枚举（`utils/entity/Target.java`）供 `getPitch(entity, target)` 选择 Head/Body/Feet 瞄准点。

---

## Addon 用法 / 介入点

```java
// 想看向某玩家（仅发包，不改真实视角）：
Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body), () -> {
    // 旋转就位后的回调（通常在 Post 阶段执行）
});
// 想改真实视角（clientSide=true，还原与否由 resetPreRotation 控制）：
Rotations.rotate(yaw, pitch, priority, true, callback);
// 读服务器朝向：
float sy = Rotations.serverYaw, sp = Rotations.serverPitch;
```

- 需要「转过去再操作」的场景（如 `BlockUtils.place`）内部用 `Rotations.rotate(..., priority, () -> { InvUtils.swap(...); interact(...); })` 把回调卡在旋转完成时机。
- `SendMovementPacketsEvent.Pre/Post` 是 addon 可订阅的介入点（模块 `AntiHunger` 等已用）。

## 常见坑

1. **`rotate` 是异步消费**：请求在下一 `sendPosition` 才真正发，不能假设调用后立即生效。
2. **`priority` 竞态**：多个模块同时 rotate，高 priority 的先发；同类模块应统一 priority（0 默认）。
3. **`clientSide=true` 会改真实视角且 Post 才还原**：需要「完全静默」别设 clientSide。
4. **回调时机**：回调在 Post 阶段执行，此时包可能已发；重逻辑别塞回调里。
5. **`mc.getCameraEntity() != mc.player` 时不消费**：Freecam 等改 cameraEntity 的模块会阻断旋转队列。

## 源码依据

- `meteordevelopment/meteorclient/utils/player/Rotations.java` —— rotate 队列、setClientRotation/setCamRotation、onSendMovementPacketsPre/Post、Rotation.sendPacket（发 ServerboundMovePlayerPacket.Rot）、getYaw/getPitch。
- `meteordevelopment/meteorclient/mixin/LocalPlayerMixin.java` —— sendPosition HEAD/TAIL 与 tick 骑乘处的 SendMovementPacketsEvent 注入点。
- `meteordevelopment/meteorclient/events/entity/player/SendMovementPacketsEvent.java` —— Pre/Post 单例事件。
- `meteordevelopment/meteorclient/mixin/ServerboundMovePlayerPacketMixin.java` —— 旋转包字段改写。
- `src/main/java/com/example/addon/mixin/ServerboundMovePlayerPacketAccessor.java`（addon）—— 读旋转包字段的真实用法。
- `meteordevelopment/meteorclient/utils/entity/Target.java` —— Head/Body/Feet 枚举。