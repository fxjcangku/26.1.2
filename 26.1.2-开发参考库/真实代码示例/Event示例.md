# Event 示例

## 概述

26.1.2 的事件系统是 `meteordevelopment.orbit`（独立库，非旧版 `meteordevelopment.orbit` 的 API 不变）。事件监听用 `@EventHandler` 注解打在**任意 private 方法**上，订阅由 `MeteorClient.EVENT_BUS` 负责。

- 注解来自 `meteordevelopment.orbit.EventHandler`（不是 `meteordevelopment.meteorclient.events` 里的同名类）。
- 优先级用 `@EventHandler(priority = EventPriority.XXX)`，`EventPriority` 来自 `meteordevelopment.orbit`。
- 有 `pre`/`post` 结构的事件类（如 `TickEvent.Pre` / `TickEvent.Post`）区分执行阶段。
- 可取消的事件调用 `event.cancel()` 或 `event.setCancelled(true)`（不同事件内部实现不同，见下）。

模块一旦开启（`autoSubscribe=true`）事件自动订阅；类编译时事件由 `EVENT_BUS.subscribe(this)` 反射扫描 `@EventHandler` 方法。

---

## 示例 1：orbit 完整监听写法（含 priority + post，Meteor 本体）

出处：`Meteor原始源码/meteordevelopment/meteorclient/systems/modules/player/AutoRespawn.java`（第 22-29 行）与 `Meteor原始源码/meteordevelopment/meteorclient/MeteorClient.java`（第 152-157 行）

```java
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

// 带优先级的可取消事件
@EventHandler(priority = EventPriority.HIGH)
private void onOpenScreenEvent(OpenScreenEvent event) {
    if (!(event.screen instanceof DeathScreen)) return;
    Modules.get().get(WaypointsModule.class).addDeath(mc.player.position());
    mc.player.respawn();
    event.cancel();
}

// Tick 的 Post 阶段（MeteorClient 自身订阅）
@EventHandler
private void onTick(TickEvent.Post event) {
    if (mc.screen == null && mc.getOverlay() == null && KeyBinds.OPEN_COMMANDS.consumeClick()) {
        mc.setScreen(new ChatScreen(Config.get().prefix.get(), true));
    }
}
```

要点：
- `@EventHandler(priority = EventPriority.HIGH)`：优先级枚举有 `LOWEST / LOW / MEDIUM / HIGH / HIGHEST`。
- `TickEvent.Pre` 在客户端 tick 前、`TickEvent.Post` 在 tick 后。
- `OpenScreenEvent` 可 cancel（取消开屏）。

---

## 示例 2：本项目三个真实监听（TickEvent.Pre / GameJoinedEvent / PacketEvent.Send）

出处均为本项目的真实模块，逐条标注。

### 2.1 TickEvent.Pre（AutoBoneMeal）

出处：`src/main/java/com/example/addon/modules/AutoBoneMeal.java`（第 388-396 行）

```java
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

@EventHandler
private void onTick(TickEvent.Pre event) {
    if (mc.player == null || mc.level == null || mc.gameMode == null
            || mc.getConnection() == null) return;

    // 服务器卡顿 / 拉回冷却时暂停催熟，避免顶风作案被踢
    if (respectLag.get() && (TacticalFSM.isServerLagging() || TacticalFSM.isRubberBandCooldown())) {
        return;
    }
    // ...
}
```

### 2.2 GameJoinedEvent（ServerDetector 与 AutoLoginModule）

出处：`src/main/java/com/example/addon/tactical/ServerDetector.java`（第 250-273 行）与 `src/main/java/com/example/addon/autologin/AutoLoginModule.java`（第 448-450 行）

```java
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;

// ServerDetector：进服后延迟侦测（指令树/插件频道要等服务端下发完）
@EventHandler
private void onGameJoined(GameJoinedEvent event) {
    if (!isActive()) return;

    detectionDone = false;
    seenChannels.clear();

    long delayMs = detectDelay.get() * 1000L;
    Thread waiter = new Thread(() -> {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        mc.execute(this::performDetection); // 回主线程
    }, "yiyiaddon-ServerDetector");
    waiter.setDaemon(true);
    waiter.start();
}

// AutoLoginModule：状态机入口
@EventHandler
private void onGameJoined(GameJoinedEvent event) {
    if (!isActive()) return;
    if (closeForUnsupportedEnvironment()) return;
    // ... 驱动登录/注册状态机 ...
}
```

要点：
- `GameJoinedEvent` 在玩家加入世界（多人或单人）后触发，是「进服自动开跑」的标准入口。
- 事件回调里如果要做耗时操作，**不能阻塞事件线程**，应开线程最后 `mc.execute(...)` 回主线程。

### 2.3 PacketEvent.Send（AntiKickBypass）

出处：`src/main/java/com/example/addon/tactical/AntiKickBypass.java`（第 401-486 行，摘关键片段）

```java
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

@EventHandler(priority = -100)
private void onPacketSend(PacketEvent.Send event) {
    if (!isActive()) return;
    Packet<?> packet = event.packet;

    // ① 伪装客户端：Brand 包替换成 vanilla
    if (fakeBrand.get() && packet instanceof ServerboundCustomPayloadPacket customPayload) {
        CustomPacketPayload payload = customPayload.payload();
        if (payload instanceof BrandPayload brandPayload) {
            if (!"vanilla".equals(brandPayload.brand())) {
                event.setCancelled(true);
                event.connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
            }
            return;
        }
    }

    // 聊天排队：取消原包，改由 Tick 按间隔重发
    if (enableChatQueue.get() && packet instanceof ServerboundChatPacket chatPacket) {
        event.setCancelled(true);
        chatQueue.offer(chatPacket.message());
        return;
    }
}
```

要点：
- 发包含两个可取消态的方法：`event.setCancelled(true)`（本项目统一用这个）与 `event.cancel()`（Meteor 部分片段用，`PacketEvent` 内部 `cancel()` 即 `setCancelled(true)`）。收包同理。
- `PacketEvent.Send` 还能**替换包体**：直接给 `event.packet` 赋新对象（`AntiKickBypass` 第 463 行 `event.packet = new ServerboundPlayerInputPacket(...)`）。
- 优先级可以给负数（`-100`），表示在 Meteor 其他监听**之前**执行——本项目用它在原生处理前抢先拦截。

---

## 模式要点

1. **事件类路径**（26.1.2）：
   - `meteordevelopment.meteorclient.events.world.TickEvent`（`.Pre` / `.Post`）
   - `meteordevelopment.meteorclient.events.game.{GameJoinedEvent, GameLeftEvent, OpenScreenEvent, ReceiveMessageEvent}`
   - `meteordevelopment.meteorclient.events.packets.PacketEvent`（`.Receive` / `.Send`）
   - `meteordevelopment.meteorclient.events.render.{Render3DEvent, Render2DEvent}`
   - `meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent` 等
2. **方法签名**：`private void onXxx(XxxEvent event)`，方法名无要求，参数必须恰一个事件对象。
3. **模块内自动订阅**，主类/GUI 等要手动 `MeteorClient.EVENT_BUS.subscribe(this)`。
4. **取消事件**优先级要够高（或够低），否则被别的监听器先处理。本项目大量用 `@EventHandler(priority = -100)`（发包含）/ `EventPriority.HIGH`（收包前抢先）。

## 常见坑

- **导错 `EventHandler`**：必须是 `meteordevelopment.orbit.EventHandler`。IDE 自动补全会误导成别的同名类。
- **`TickEvent.Pre/Post` 混用**：写 `onTick(TickEvent.Pre event)` 与 `onTick(TickEvent.Post event)` 是两个不同方法，别把逻辑堆在 Post 里又以为在 Pre。
- **事件里做耗时/网络操作**：卡主线程会掉帧，长任务开线程 + `mc.execute` 回主线程（ServerDetector 的规范写法）。
- **拿 `event.packet` 直接比对 null**：`PacketEvent.packet` 理论上不为 null，但对 `isActive()` 判空要放在最前。
- **忘了判 `isActive()`**：事件方法在模块关闭后不会触发（已 unsubscribe），但模块内部有多个事件时，防御性 `if (!isActive()) return;` 是项目惯例（尤其 `PacketEvent`）。

## 26.1.2 注意

- 事件库是 `meteordevelopment.orbit`（Meteor 自己的 fork，`MeteorClient.EVENT_BUS = new EventBus()`），不是 Fabric API 的 event。事件类本身就是简单 Java 类，`@EventHandler` 方法靠反射订阅。
- `PacketEvent` 的取消/替换语义：`setCancelled(true)` 后原包不发出；`event.packet = 新包` 可当场替换发送内容（本项目 AntiKickBypass 用此替换 `ServerboundPlayerInputPacket` 而避免整包取消导致移动瞬停）。
- `GameJoinedEvent` 是「进服」而非「进世界开始渲染」——`mc.level` 可能已就绪但指令树/插件频道尚未下发，因此 ServerDetector 故意延迟数秒再探测。
- `Render3DEvent` 携带 `renderer` 字段（`Render3DEvent.renderer`），配合 `MeshBuilder`/`MeteorRenderPipelines` 渲染，本项目 `AutoBoneMeal` 用 `event.renderer.box(...)` 画框。