# Packet-Event 生命周期

> 抓包 / 改包 / 拦截包是 addon 绕过与修 bug 的核心手段。Meteor 通过 mixin 到原版 `Connection` 的收发入口，把「每一进出数据包」转换成一个 `PacketEvent`，在 `MeteorClient.EVENT_BUS` 上 post；可取消（`Cancellable`）的直接被 mixin 丢弃，不发到服务端/不交给原版处理。

## 概述

事件类：`meteordevelopment.meteorclient.events.packets.PacketEvent`，含三个静态内部类：

| 内部类 | 继承 | 字段 | 语义 |
|---|---|---|---|
| `PacketEvent.Receive` | `Cancellable` | `packet`、`connection` | 客户端收到包，取消即丢弃不处理 |
| `PacketEvent.Send` | `Cancellable` | `packet`、`connection` | 客户端准备发包，取消即不发送 |
| `PacketEvent.Sent` | （无） | `packet`、`connection` | 包已发出（不可取消，仅观察/静默重发） |

`Cancellable`（`meteordevelopment.meteorclient.events.Cancellable`）实现 orbit 的 `ICancellable`，只有 `cancelled` 布尔 + `setCancelled/isCancelled`。

`Receive`/`Send` 构造里都先 `setCancelled(false)`。`Send`/`Sent` 额外提供 `sendSilently(Packet<?>)`（见下文）。

此外还有包级专属事件（非通用 `PacketEvent`），由 `ClientPacketListenerMixin` 在特定 `handleXxx` 里 post，例如 `PlaySoundPacketEvent`、`InventoryEvent`、`ContainerSlotUpdateEvent`、`ChunkDataEvent`、`PickItemsEvent`、`EntityDestroyEvent` 等。

---

## 1. Receive 注入点（channelRead0）

`ConnectionMixin`（`meteordevelopment.meteorclient.mixin.ConnectionMixin`）：

```java
@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V",
        shift = At.Shift.BEFORE), cancellable = true)
private void onHandlePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
    if (packet instanceof ClientboundBundlePacket bundle) {
        for (Iterator<Packet<? super ClientGamePacketListener>> it = bundle.subPackets().iterator(); it.hasNext(); ) {
            if (MeteorClient.EVENT_BUS.post(new PacketEvent.Receive(it.next(), (Connection) (Object) this)).isCancelled())
                it.remove();                       // 取消 → 从 bundle 里剔除该子包
        }
    } else if (MeteorClient.EVENT_BUS.post(new PacketEvent.Receive(packet, (Connection) (Object) this)).isCancelled())
        ci.cancel();                                // 取消 → 不让原版处理该包
}
```

- 注入点是 `Connection.channelRead0` 在调用 `genericsFtw`（原版把包分发给 listener 的泛型桥）**之前**，即「读完还没分发给 handler」。
- `ClientboundBundlePacket`（1.21 的 bundle 包）被特殊处理：**逐条 post 子包**，被取消的子包从 `bundle.subPackets()` 移除，bundle 本身不取消。

## 2. Send / Sent 注入点（send 方法）

同一 `ConnectionMixin`：

```java
@Inject(at = @At("HEAD"), method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", cancellable = true)
private void onSendPacketHead(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
    if (MeteorClient.EVENT_BUS.post(new PacketEvent.Send(packet, (Connection) (Object) this)).isCancelled()) {
        ci.cancel();                              // 取消 → 包不发出
    }
}

@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
private void onSendPacketTail(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
    MeteorClient.EVENT_BUS.post(new PacketEvent.Sent(packet, (Connection) (Object) this));
}
```

- `Send` 在 `send(...)` 方法 **HEAD**，取消即拦截发送；`Sent` 在 **TAIL**，包已写入，只读 / 补发用。
- 收发都用的同一个 `Connection` 对象，`connection` 字段随时可调用 `connection.send(...)`。

## 3. sendSilently —— 事件内静默发包防递归

`PacketEvent.Send` 与 `PacketEvent.Sent` 都提供：

```java
public void sendSilently(Packet<?> packet) {
    connection.send(packet, null, true);   // 走三参重载直接写入（Meteor 的 @Inject 只在二参 send 上）
}
```

用途：在 `Send`/`Sent` 监听器里再发一个包时，若不静默会再次触发 `PacketEvent.Send` → 无限递归 `StackOverflowError`。`sendSilently` 直接调三参 `Connection.send(Packet, ChannelFutureListener, boolean)` 重载，绕开 mixin 在二参 `send(...)` 上的注入点，从而不再触发事件。

## 4. 包级专属事件（ClientPacketListenerMixin）

`ClientPacketListenerMixin` 在对应 `handleXxx` 方法里 post 高阶事件（真实示例）：

- `handleSoundEvent` HEAD → `PlaySoundPacketEvent.get(packet)`（复用单例对象，`INSTANCE.packet = packet`）。
- `handleContainerSetSlot` TAIL → `ContainerSlotUpdateEvent.get(packet)`。
- `handleContainerContent` TAIL → `InventoryEvent.get(packet)`。
- `handleLevelChunkWithLight` TAIL → `new ChunkDataEvent(chunk)`。
- `handleTakeItemEntity` → `PickItemsEvent.get(item, amount)`。
- `handleRemoveEntities` → 逐个 `EntityDestroyEvent.get(entity)`。
- 聊天：`sendChat` HEAD → 若不以 `Config.get().prefix.get()` 开头且非 Baritone 前缀，post `SendMessageEvent`（可取消）；以前缀开头则走 `Commands.dispatch(...)` 并 `ci.cancel()`（见「Command注册机制」）。

这些是包内容已部分解析后的「业务事件」，比裸 `PacketEvent.Receive` 更好用。

## 运行链路

1. 服务端发来字节 → Netty → `Connection.channelRead0` → mixin 先 `post(PacketEvent.Receive)`。
2. 若被取消（`setCancelled(true)`），`ci.cancel()`/从 bundle 移除 → 包不进原版 `genericsFtw`。
3. 未被取消 → 原版分发到 `ClientPacketListener.handleXxx` → `ClientPacketListenerMixin` 在特定 handler 里再 post 包级专属事件。
4. 客户端主动 `connection.send(pkt)` → mixin HEAD `post(PacketEvent.Send)` → 取消则不发 → 未取消写入 → TAIL `post(PacketEvent.Sent)`。

## Addon 用法 / 介入点

```java
// 拦截出站移动包（典型绕过视角检测）
@EventHandler
private void onPacketSend(PacketEvent.Send event) {
    if (event.packet instanceof ServerboundMovePlayerPacket.Rot rot) {
        // 改写 yaw/pitch 需要 Accessor，或直接取消再 sendSilently 新包
        event.cancel();
        event.sendSilently(new ServerboundMovePlayerPacket.Rot(...));
    }
}

// 观察入站包（不可取消，只读）
@EventHandler
private void onPacketSent(PacketEvent.Sent event) {
    if (event.packet instanceof ServerboundInteractPacket) { /* ... */ }
}
```

- 监听需在模块 `onActivate` 里 `MeteorClient.EVENT_BUS.subscribe(this)`（或模块 `autoSubscribe=true` 由 `toggle()` 自动订阅，见「Module生命周期」）。
- 改写包字段一般用 Accessor 接口（如 `ServerboundMovePlayerPacketAccessor`，addon 已实现）而不是直接取消重建。

## 常见坑

1. **`Sent` 不可取消**：`Sent` 不继承 `Cancellable`，想「撤回已发包」不存在，只能在 `Send` 阶段拦截。
2. **递归发包**：在 `Send`/`Sent` 里再 `connection.send(...)` 会再触发 `Send`；必须用 `sendSilently`。
3. **`ClientboundBundlePacket` 特殊**：bundle 内的子包是逐个 post 的，取消子包是「从 bundle 移除」而非取消整个 `channelRead0`；不要对 bundle 整体 try-cast `PacketEvent.Receive.packet` 当普通包。
4. **包类型判断用 `instanceof` 匹配新协议类**：26.1.2 的移动包是 `ServerboundMovePlayerPacket.Pos/Rot/PosRot/StatusOnly` 静态子类，不再是旧版扁平类；改写字段走 Accessor 而非直接访问。
5. **事件在 `Connection` 层无 `Connection` 时也可能触发**：判空 `mc.getConnection()` 前先检查 `event.connection` 与 `mc.player` 非 null。
6. **高频包性能**：每次 `Send`/`Sent` 都会实例化事件对象，PvP/移动高频场景避免在监听里做重计算（配合模块 `isActive()` 早退）。

## 源码依据

- `meteordevelopment/meteorclient/events/packets/PacketEvent.java` —— `Receive`/`Send`/`Sent` 三内部类、`sendSilently`、字段定义。
- `meteordevelopment/meteorclient/events/Cancellable.java` —— `cancelled` 布尔、`setCancelled/isCancelled`。
- `meteordevelopment/meteorclient/mixin/ConnectionMixin.java` —— `channelRead0`（Receive）、`send` HEAD/TAIL（Send/Sent）、`exceptionCaught`、`ServerConnectEndEvent`。
- `meteordevelopment/meteorclient/mixin/ClientPacketListenerMixin.java` —— 包级专属事件 post 点、`sendChat` 前缀解析/命令分发。
- `meteordevelopment/meteorclient/events/packets/PlaySoundPacketEvent.java` —— 单例复用式包级事件示例。
- `src/main/java/com/example/addon/mixin/ServerboundMovePlayerPacketAccessor.java`（addon）—— 读写移动包字段的真实用法。