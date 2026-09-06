# Packet 示例

## 概述

26.1.2 里发包（C2S）与收包（S2C）只有一个统一入口分支：

| 方向 | 入口 | 说明 |
| --- | --- | --- |
| 发包 | `mc.player.connection.send(Packet)` / `mc.getConnection().send(Packet)` | 直接构造协议包发往服务端 |
| 聊天/指令 | `mc.player.connection.sendChat(msg)` / `sendCommand(cmd)` | 走聊天协议，等价玩家敲聊天框 |
| 收包监听 | `PacketEvent.Receive` + `event.cancel()` / `event.setCancelled(true)` | 拦包、改包 |
| 发包监听 | `PacketEvent.Send`（`meteordevelopment.orbit.EventHandler`） | 拦包、替换、重发 |

协议包类都在 `net.minecraft.network.protocol.game.*`（游戏内）、`net.minecraft.network.protocol.common.*`（登录/通用）下，命名是**官方映射** `Serverbound*`（客户端→服务端）/ `Clientbound*`（服务端→客户端），**不是 Yarn 的 `*C2SPacket`/`*S2CPacket`**。

一个关键 26.1.2 机制是**方块交互包的 `sequence` 序号**：`ServerboundPlayerActionPacket`、`ServerboundUseItemOnPacket` 等服务端校验 sequence，必须通过 `BlockStatePredictionHandler` 取号 + 登记本地预测状态，否则方块会「服务端回滚、客户端闪烁复原」。

---

## 示例 1：完整发包封装（破坏 / 使用 / 开箱，带 sequence 取号）

出处：`src/main/java/com/example/addon/farm/FarmPacketOps.java`（第 51-146 行）

```java
package com.example.addon.farm;

import com.example.addon.mixin.ClientLevelPredictionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FarmPacketOps {

    private FarmPacketOps() {
    }

    /** 取出预测处理器。原版方法是包私有，通过 accessor mixin 暴露。 */
    private static BlockStatePredictionHandler predictionHandler(ClientLevel level) {
        return ((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler();
    }

    /** 发送破坏方块包（瞬间破坏路径：START + STOP 同 tick 连发）。 */
    public static boolean breakBlock(BlockPos pos, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        BlockState original = level.getBlockState(pos);
        if (original.isAir()) return false;

        BlockStatePredictionHandler handler = predictionHandler(level);

        // 开启预测窗口：登记服务端已知状态，取号，发包
        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(pos, original, player);
            int sequence = predicting.currentSequence();

            player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, sequence));

            // 本地立刻置空气，保证同 tick 内的后续逻辑（如播种）不会读到旧状态
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        }

        // STOP 包不占用新的 sequence，服务端仅用于确认动作结束
        player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face));

        return true;
    }

    /** 发送对方块使用物品包（播种），副手补种传 OFF_HAND。 */
    public static boolean useOnBlock(InteractionHand hand, BlockPos soilPos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        Vec3 hitVec = new Vec3(soilPos.getX() + 0.5, soilPos.getY() + 1.0, soilPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, soilPos, false);

        BlockPos plantPos = soilPos.above();
        BlockState original = level.getBlockState(plantPos);

        BlockStatePredictionHandler handler = predictionHandler(level);

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(plantPos, original, player);
            int sequence = predicting.currentSequence();

            player.connection.send(new ServerboundUseItemOnPacket(hand, hitResult, sequence));
        }

        player.swing(hand);
        return true;
    }
}
```

要点：
- `predictionHandler(level)` 通过 `ClientLevelPredictionAccessor` mixin 暴露原版**包私有**方法 `getBlockStatePredictionHandler()`（见「Mixin 示例」）。
- sequence 取号流程固定：`startPredicting()` 开窗口 → `retainKnownServerState()` 登记原状态 → `currentSequence()` 取号 → 发包 → try-with-resources 自动 `close()` 关口。
- `STOP_DESTROY_BLOCK` 与 `ABORT_DESTROY_BLOCK` **不带 sequence**；只有 START 带。

---

## 示例 2：发包拦截 + 假包替换（Brand 伪造 / 限速 / 聊天排队）

出处：`src/main/java/com/example/addon/tactical/AntiKickBypass.java`（第 401-486 行）

```java
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@EventHandler(priority = -100)
private void onPacketSend(PacketEvent.Send event) {
    if (!isActive()) return;
    Packet<?> packet = event.packet;

    // ① 伪装客户端：Brand 包替换成 vanilla（已伪装的重发包要放行，否则无限递归）
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

    // ② 聊天排队：拦截后进队列，tick 里按间隔重发
    if (enableChatQueue.get() && packet instanceof ServerboundChatPacket chatPacket) {
        event.setCancelled(true);
        chatQueue.offer(chatPacket.message());
        return;
    }

    // ③ 限制发包：超过每秒阈值直接丢包
    if (applyThrottle(event, packet)) return;
}
```

要点：
- `@EventHandler(priority = -100)` 让监听器比别的 handler 先执行（数值越小越靠前）。
- `event.setCancelled(true)` 拦截原包；要「替换」则拦掉旧包后 `event.connection.send(new ...)` 发新包。
- 替换后必须放行已成品，否则「拦截→重发→再拦截」会死循环。
- 收包端也用同一套：`PacketEvent.Receive` + `event.packet instanceof ClientboundPlayerPositionPacket` 判包。

---

## 示例 3：收包监听（拉回自动确认）

出处：`src/main/java/com/example/addon/tactical/AntiKickBypass.java`（第 492-499 行、第 619-636 行）

```java
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@EventHandler
private void onReceivePacket(PacketEvent.Receive event) {
    if (!isActive()) return;

    if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
        handleRubberBand(packet);
    }
}

private void handleRubberBand(ClientboundPlayerPositionPacket packet) {
    // 先回确认包，再静默回位置包，最后进冷却
    mc.player.connection.send(new ServerboundAcceptTeleportationPacket(packet.id()));
    TacticalFSM.setRubberBandCooldown(true);

    for (int i = 0; i < 3; i++) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
            packet.change().position().x,
            packet.change().position().y,
            packet.change().position().z,
            mc.player.getYRot(),
            mc.player.getXRot(),
            true, false
        ));
    }
}
```

要点：
- 服务端主动下发位置时（拉回），要响应 `ServerboundAcceptTeleportationPacket(packet.id())` 确认，否则服务端判定客户端「无视同步」。
- `ServerboundMovePlayerPacket` 有内部子类 `Pos` / `Rot` / `PosRot` / `LookAndOnGround`（见「26.1.2 注意」），按需选型。

---

## 示例 4：聊天消息与指令发送

出处：`Meteor原始源码/meteordevelopment/meteorclient/utils/player/ChatUtils.java`（第 81-93 行）

```java
/**
 * Sends the message as if the user typed it into chat.
 */
public static void sendPlayerMsg(String message, boolean addToHistory) {
    if (addToHistory) mc.gui.getChat().addRecentChat(message);

    if (message.startsWith("/")) mc.player.connection.sendCommand(message.substring(1));
    else mc.player.connection.sendChat(message);
}
```

要点：
- `ChatUtils` **没有 `sendCommand` 方法**；发指令就是给 `sendPlayerMsg` 传以 `/` 开头的字符串，内部走 `mc.player.connection.sendCommand(...)`。
- 直接发原生指令用 `mc.player.connection.sendCommand("指令")`（不带 `/`）；发聊天用 `mc.player.connection.sendChat("文本")`。

---

## 模式要点

1. **发包统一走 `mc.getConnection().send(packet)` 或 `mc.player.connection.send(packet)`**。`mc.getConnection()` 等价 `mc.player.connection`（都是 `ClientPacketListener`），但 `player` 为空时前者更安全。
2. **包类型判断用 `instanceof` + 类型模式匹配**（Java 21+ 语法 `packet instanceof XxxPacket x`），26.1.2 用官方映射名 `Serverbound*`/`Clientbound*`。
3. **方块交互包必须带 sequence**（`ServerboundPlayerActionPacket` START、`ServerboundUseItemOnPacket`、`ServerboundUseItemPacket`）；sequence 唯一来源是 `BlockStatePredictionHandler`，且**每个包独立取号，不能复用**。
4. **监听器里发新包要用 `event.connection.send(...)` 或 `event.sendSilently(packet)`**（后者不触发事件，防递归），不要再用 `mc.getConnection().send()` 否则可能重新进监听器造成 StackOverflow。
5. **取消与恢复**：`event.cancel()`（旧风格）与 `event.setCancelled(true)`（Cancellable 通用）等价；`event.packet = newXxx` 直接改包体也能实现改写。

## 常见坑

- **忘记取号 / 复用一个 sequence**：会导致服务端回滚方块，表现为「方块挖掉又复原、方块闪烁」。每个交互包必须独立 `currentSequence()`。
- **在 `PacketEvent.Send` 监听器里再 `mc.getConnection().send()` 发同类型包**：立刻二次进入监听器，无限递归 StackOverflow。正确是 `event.sendSilently(...)` 或用「拦截旧包 + connection.send 新包」。
- **取消整包替穿戴按键**：`ServerboundPlayerInputPacket` 携带全部按键，`event.setCancelled(true)` 等于当帧前后左右跳跃全丢、移动瞬停。应该重写包体、只摘掉矛盾标记（`AntiKickBypass` 假潜行/假疾跑就是这样做的）。
- **发指令用 `ChatUtils.sendPlayerMsg("/xxx")` 时错误加了历史**：无历史版本是 `sendPlayerMsg(msg, false)`。
- **把 Yarn 包名当标签**：`C2SPacket`、`S2CPacket` 是 Yarn 名，26.1.2 官方映射是 `Serverbound*Packet`/`Clientbound*Packet`，`instanceof` 判型必须用后者，否则匹配不上。

## 26.1.2 注意

- **`ServerboundMovePlayerPacket` 有内部类**：`Pos`、`Rot`、`PosRot`、`LookAndOnGround`。构造 `PosRot` 是 `new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, onGround, horizontalCollision)`——最后一个参数是**水平碰撞**标志，不是 Yarn 旧版的简单 5 参数。
- **`ServerboundUseItemPacket` 构造函数是 4 参数**：`(InteractionHand hand, int sequence, float yRot, float xRot)`（`ServerboundUseItemPacket.java` 第 18 行），大招需要的 sequence 在这里也要取。
- **`ServerboundPlayerActionPacket` 的 Action 枚举**是包内枚举：`START_DESTROY_BLOCK` / `STOP_DESTROY_BLOCK` / `ABORT_DESTROY_BLOCK` / `DROP_ITEM` 等，取法 `ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK`。
- **`BrandPayload` / `ServerboundCustomPayloadPacket` 在 `net.minecraft.network.protocol.common` 包**，`CustomPacketPayload` 判断命名空间用 `payload.type().id().getNamespace()`（拦截 Fabric/meteor mod 频道时用）。
- **`ClientboundPlayerPositionPacket`** 的同步数据在 `packet.change()`（`Relative`），里面 `position()` / `yaw()` / `pitch()`，不是旧版字段直接挂在包上。