> 源码依据：Meteor原始源码/meteordevelopment/meteorclient/events/packets/PacketEvent.java、utils/player/ChatUtils.java、utils/network/PacketUtils.java，原版包见 Minecraft原始源码/net/minecraft/network/…　Meteor Client 26.1.2-SNAPSHOT

# Packet 数据包

数据包（Packet）是网络通信的最小单元。Meteor 通过 `PacketEvent` 在三处拦截/发送数据包，并在 `PacketEvent.Send` 内提供 `sendSilently` 与 `cancel()` 用于 addon 控制发包。

---

## 1. PacketEvent

### 类名 / 包名 / 源码路径
`PacketEvent`／`meteordevelopment.meteorclient.events.packets`／`meteordevelopment/meteorclient/events/packets/PacketEvent.java`

### 结构（真实内嵌类）
```java
public class PacketEvent {

    public static class Receive extends Cancellable {
        public Packet<?> packet;
        public Connection connection;

        public Receive(Packet<?> packet, Connection connection) { ... }
    }

    public static class Send extends Cancellable {
        public Packet<?> packet;
        public Connection connection;

        public Send(Packet<?> packet, Connection connection) { ... }

        public void sendSilently(Packet<?> packet) {
            connection.send(packet, null, true);
        }
    }

    public static class Sent {
        public Packet<?> packet;
        public Connection connection;

        public Sent(Packet<?> packet, Connection connection) { ... }

        public void sendSilently(Packet<?> packet) {
            connection.send(packet, null, true);
        }
    }
}
```

### 说明
- `Receive`/`Send` extends `meteordevelopment.meteorclient.events.Cancellable`，可用 `.cancel()` 取消（拦截）该包；`Sent`（已发出）不可取消。
- **`PacketEvent.Send.cancel()`**：取消本次发包（供 `PacketCanceller` 这类模块拦包）。
- **`sendSilently(Packet<?> packet)`**：直接 `connection.send(packet, null, true)` 静默发包，**不触发事件**——在事件监听器内发包避免 `StackOverflowError`。addon 里「在 PacketEvent 监听器内再发一个包」的标准做法。

### addon 监听写法
```java
@EventHandler
private void onPacketSend(PacketEvent.Send event) {
    if (event.packet instanceof ServerboundMovePlayerPacket) {
        // event.cancel();   // 拦截
        // event.sendSilently(new ServerboundMovePlayerPacket.Rot(...)); // 静默补发
    }
}
```

---

## 2. 发包通道（以项目真实写法为准）

### 2.1 聊天 / 命令包：ChatUtils.sendPlayerMsg
`meteordevelopment.meteorclient.utils.player.ChatUtils`（源码路径 `utils/player/ChatUtils.java`）
```java
public static void sendPlayerMsg(String message)
public static void sendPlayerMsg(String message, boolean addToHistory)
```
真实实现（关键两行）：
```java
if (message.startsWith("/")) mc.player.connection.sendCommand(message.substring(1));
else mc.player.connection.sendChat(message);
```
> `mc.player.connection` 即 `ClientPacketListener`；`sendCommand(String)` / `sendChat(String)` 是 26.1.2 真实方法（见 Minecraft原始源码 `net/minecraft/client/multiplayer/ClientPacketListener.java`）。

> 任务里提到的「ChatUtils.sendCommand 发包通道」在 26.1.2 的 ChatUtils 中**没有**名为 `sendCommand` 的静态方法——真正的命令发送走 `sendPlayerMsg("/...")` 或直接 `mc.player.connection.sendCommand(...)`。此处以源码为准。

### 2.2 原版原始包：mc.getConnection().send(packet)
```java
mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, onGround, horizontalCollision));
```
> 真实用例见 `utils/player/Rotations.java` 内 `Rotation.sendPacket()`：
> `mc.getConnection().send(new ServerboundMovePlayerPacket.Rot((float) yaw, (float) pitch, mc.player.onGround(), mc.player.horizontalCollision));`
- `mc.getConnection()` 返回 `ClientPacketListener`（真实类：`net.minecraft.client.multiplayer.ClientPacketListener`）。
- `send(Packet<?>)` 定义在 `net.minecraft.network.Connection`（`public void send(Packet<?> packet)`），重载 `send(Packet<?>, ChannelFutureListener)`、`send(Packet<?>, ChannelFutureListener, boolean flush)`。

### 2.3 静默发包
在 `PacketEvent.Send`/`Sent` 监听器内用 `event.sendSilently(packet)`（内部 `connection.send(packet, null, true)`）。

---

## 3. PacketUtils（包类型查询）

### 类名 / 源码路径
`PacketUtils`／`meteordevelopment/meteorclient/utils/network/PacketUtils.java`

### 关键方法（真实签名）
```java
public static Set<PacketType<? extends @NotNull Packet<?>>> getPackets()
public static Set<PacketType<? extends @NotNull Packet<?>>> getClientboundPackets()
public static Set<PacketType<? extends @NotNull Packet<?>>> getServerboundPackets()
@Nullable public static PacketType<? extends @NotNull Packet<?>> getClientboundPacket(Identifier id)
@Nullable public static PacketType<? extends @NotNull Packet<?>> getServerboundPacket(Identifier id)
@Nullable public static PacketType<? extends @NotNull Packet<?>> getPacket(Identifier id)
@Nullable public static PacketType<? extends @NotNull Packet<?>> getPacket(String name)
```
> 内部静态块从协议模板（`StatusProtocols`/`LoginProtocols`/`ConfigurationProtocols`/`GameProtocols`/`HandshakeProtocols` 等）枚举出全部 clientbound/serverbound 包类型，并维护一个 `LEGACY_PACKET_MAPPINGS`（旧名 → 新 `PacketType`）映射。addon 若需把「包名字符串」转成真实类型可走 `getPacket(name)`。

---

## 4. 26.1.2 常用包名对照（原版真实名）

以下包/字段名均取自 `PacketUtils.LEGACY_PACKET_MAPPINGS` 与 Minecraft原始源码 `net/minecraft/network/protocol/…`，26.1.2 拍平了旧版 `PacketBuffer` 时代的旧命名，普通包用 WebSocket 风格类名 + `GamePacketTypes` 常量。

### 移动包（最重要）
```java
new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, horizontalCollision)
new ServerboundMovePlayerPacket.PosRot(x, y, z, yRot, xRot, onGround, horizontalCollision)
new ServerboundMovePlayerPacket.Rot(yRot, xRot, onGround, horizontalCollision)
new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision)
```
对应 `GamePacketTypes`：`SERVERBOUND_MOVE_PLAYER_POS` / `SERVERBOUND_MOVE_PLAYER_POS_ROT` / `SERVERBOUND_MOVE_PLAYER_ROT` / `SERVERBOUND_MOVE_PLAYER_STATUS_ONLY`。

### 常用 serverbound（旧名 → 真实类名）
| 旧名 | 真实类名（`net.minecraft.network.protocol.*`） |
|---|---|
| ServerboundAttackPacket | `ServerboundAttackPacket`（game） |
| ServerboundInteractPacket | `ServerboundInteractPacket`（game） |
| ServerboundPlayerActionPacket | `ServerboundPlayerActionPacket`（game） |
| ServerboundPlayerCommandPacket | `ServerboundPlayerCommandPacket`（game） |
| ServerboundUseItemPacket | `ServerboundUseItemPacket`（game） |
| ServerboundUseItemOnPacket | `ServerboundUseItemOnPacket`（game） |
| ServerboundSetCarriedItemPacket | `ServerboundSetCarriedItemPacket`（game） |
| ServerboundContainerClickPacket | `ServerboundContainerClickPacket`（game） |
| ServerboundSwingPacket | `ServerboundSwingPacket`（game） |
| ServerboundChatPacket / ChatCommandPacket | `ServerboundChatPacket` / `ServerboundChatCommandPacket`（game） |
| ServerboundKeepAlivePacket | `ServerboundKeepAlivePacket`（common） |
| ServerboundCustomPayloadPacket | `ServerboundCustomPayloadPacket`（common） |
| ClientIntentionPacket | `ClientIntentionPacket`（handshake，对应 `HandshakePacketTypes.CLIENT_INTENTION`） |

### 常用 clientbound（举例）
`ClientboundPlayerPositionPacket`、`ClientboundSetHealthPacket`、`ClientboundSetEntityDataPacket`、`ClientboundRemoveEntitiesPacket`、`ClientboundExplodePacket`、`ClientboundLevelParticlesPacket`、`ClientboundSoundPacket`、`ClientboundSystemChatPacket`、`ClientboundContainerSetSlotPacket`、`ClientboundContainerSetContentPacket`、`ClientboundSetExperiencePacket` 等（完整列表见 `PacketUtils.LEGACY_PACKET_MAPPINGS`）。

> 若不确定某包在 26.1.2 的确切名，优先看 `Minecraft原始源码/net/minecraft/network/protocol/` 下的 `GamePacketTypes.java` / `CommonPacketTypes.java`，或用 `PacketUtils.getPacket("旧名")` 反查。

---

## 常见坑
1. 拦截收包用 `PacketEvent.Receive.cancel()`，拦截发包用 `PacketEvent.Send.cancel()`；`PacketEvent.Sent` 不可取消。
2. 在 `PacketEvent` 监听器里直接 `mc.getConnection().send(...)` 会无限重入 → 必须用 `event.sendSilently(packet)`。
3. 26.1.2 包命名已拍平：不再有 `ServerboundMovePlayerPacket.PosRot` 以外的 `MovePlayerC2SPacket` 旧名查询入口（旧名靠 `PacketUtils` 映射兼容），**写 addon 用真实类名 import**。
4. `mc.player` 可能为 `null`（未进世界），发包前要判空或用 `Utils.canUpdate()`。
5. `ServerboundMovePlayerPacket.Rot` 等多态子类在 `instanceof` 判断父类 `ServerboundMovePlayerPacket` 时都能命中。