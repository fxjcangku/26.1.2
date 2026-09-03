# Mixin 示例

## 概述

26.1.2 addon 通过 Mixin 注入 Minecraft / Meteor 类。本项目 mixin 统一放在 `src/main/java/com/example/addon/mixin/`，并**必须在 `src/main/resources/addon-template.mixins.json` 的 `client` 数组声明**，否则 Mixin 不生效（这也是项目「漏注入检查」铁律）。

Mixin 常见三类用途：

| 用途 | 注解 | 举例 |
| --- | --- | --- |
| 注入代码 | `@Inject` + `@At` | 在方法 HEAD/TAIL/RETURN 插桩 |
| 访问私有字段 | `@Accessor`（+`@Mutable` 写 final） | 改写包的 `y`/`onGround` |
| 调用包私有方法 | `@Invoker` | 取 `getBlockStatePredictionHandler` |

关键 import 全部来自 `org.spongepowered.asm.mixin.*`，Fabric 的 `@Mixin` 就是它。回调类型 `CallbackInfo`（void 方法）、`CallbackInfoReturnable<T>`（有返回值方法）。

---

## 示例 1：注入构造器 TAIL（最简 Mixin）

出处：`src/main/java/com/example/addon/mixin/ExampleMixin.java`（全文）

```java
package com.example.addon.mixin;

import com.example.addon.core.AddonTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ExampleMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onGameLoaded(GameConfig gameConfig, CallbackInfo ci) {
        AddonTemplate.LOG.info("Hello from ExampleMixin!");
    }
}
```

要点：
- `@Mixin(目标类.class)` 声明注入目标；类必须 `abstract`（Accessor/Invoker 是 `interface`）。
- 构造函数目标写法是 `method = "<init>"`；`@At("TAIL")` 在构造末尾注入。
- 注入方法签名：参数**照抄目标方法参数** + 末尾追加 `CallbackInfo ci`（void）/ `CallbackInfoReturnable<...>`。

---

## 示例 2：@Accessor 暴露并改写 final 字段（飞行绕过）

出处：`src/main/java/com/example/addon/mixin/ServerboundMovePlayerPacketAccessor.java`（全文）

```java
package com.example.addon.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {

    /** 改写移动包的 Y 坐标 */
    @Mutable
    @Accessor("y")
    void yiyiaddon$setY(double y);

    /** 改写移动包的落地标志 */
    @Mutable
    @Accessor("onGround")
    void yiyiaddon$setOnGround(boolean onGround);
}
```

要点：
- Accessor mixin 用 `interface` 而非 class。
- 字段名是**官方映射** `y`、`onGround`（`ServerboundMovePlayerPacket` 的 protected final 字段）。
- 目标字段是 `final` 时必须加 `@Mutable` 才能写；方法是 setter（`void xxx$setY(double)`）。
- 使用：`((ServerboundMovePlayerPacketAccessor) packet).yiyiaddon$setY(...)`。命名规范 `xxx$setXxx`。

---

## 示例 3：@Invoker 调用包私有方法（取预测处理器）

出处：`src/main/java/com/example/addon/mixin/ClientLevelPredictionAccessor.java`（第 19-28 行）

```java
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface ClientLevelPredictionAccessor {

    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler yiyiaddon$getPredictionHandler();
}
```

要点：
- `@Invoker("方法名")` 暴露目标类的包私有方法，返回值与参数与原方法一一对应。
- 使用：`((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler()`（见「Packet 示例」FarmPacketOps）。

---

## 示例 4：@Inject + @Unique 字段 + 可取消（秒破方块）

出处：`src/main/java/com/example/addon/mixin/MultiPlayerGameModeFastBreakMixin.java`（第 34-84 行，节选）

```java
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeFastBreakMixin {

    /** 上次秒破的时间戳（毫秒），用于节流；跨世界切换也不受影响 */
    @Unique
    private long yiyiaddon$lastBreakTime = 0L;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void yiyiaddon$instantBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // ... 判断模块开启、方块可破坏、节流 ...
        if (now - yiyiaddon$lastBreakTime < module.getBreakInterval() * 50L) {
            cir.setReturnValue(false);
            return;
        }
        // ... 发包秒破 ...
        cir.setReturnValue(true);
    }
}
```

要点：
- mixin 自身要用的字段加 `@Unique`（避免与目标类字段冲突），命名 `xxx$字段`。
- `cancellable = true` + `CallbackInfoReturnable<Boolean>` 配合 `cir.setReturnValue(...)` 提前截断原方法并返回值。
- 注入方法参数照抄目标方法 `startDestroyBlock(BlockPos, Direction)`，末尾追加 `CallbackInfoReturnable<Boolean> cir`。

---

## 模式要点

1. **必须进 mixins.json**：每个 `@Mixin` 类都要写在 `addon-template.mixins.json → client` 数组（见「注册示例」），否则静默失效。
2. **Accessor/Invoker 用 `interface`，@Inject 用 `abstract class`**。
3. **方法目标名是官方映射**：`startDestroyBlock`、`getBlockStatePredictionHandler`、字段 `y`/`onGround`/`positionReminder`，不是 Yarn 名。
4. **注入方法签名 = 目标方法参数 + CallbackInfo/CallbackInfoReturnable**，参数类型必须完全一致，否则注入点找不到。
5. **写 final 字段加 `@Mutable`**；mixin 自用字段加 `@Unique`；跨 mixin 共享接口用 `mixininterface`（Meteor 自己的 `IMultiPlayerGameMode` 等）。

## 常见坑

- **只写类、没加 mixins.json**：Mixin 类存在但不在 `client` 数组，运行时完全不加载，无任何报错提示——这是最隐蔽的坑。
- **目标方法/字段是 Yarn 名**：26.1.2 用官方映射，`getBlockStatePredictionHandler` 而非 Yarn `getBlockStatePredictionHandler`（同名但注意是**包私有**才需要 @Invoker）、`startDestroyBlock` 而非 `attackBlock`。
- **`@Inject` 到 `abstract` class 却写了 `implements`**：Accessor/Invoker 是 `interface`，@Inject 是 `abstract class`，混用会编译失败。
- **回调类型选错**：void 方法用 `CallbackInfo`，有返回值方法用 `CallbackInfoReturnable<T>`；不写 `cancellable=true` 时 `cir.setReturnValue` 无效。
- **`@Unique` 字段忘加命名前缀**：不加前缀可能与目标类/父类字段撞名导致歧义注入错误。

## 26.1.2 注意

- **`@Accessor` 写 final 必须 `@Mutable`**：26.1.2 的 `ServerboundMovePlayerPacket.y` / `onGround` 是 `final`，只加 `@Accessor` 会报「final field cannot be modified」。
- **`ClientLevel` 的预测处理器方法是包私有**：无法在 `farm` 包直接调，所以项目做了 `ClientLevelPredictionAccessor`（`@Invoker("getBlockStatePredictionHandler")`）。
- **`LocalPlayer.positionReminder` 字段**：26.1.2 客户端移动包重发的节流字段，`LocalPlayerAccessor` 暴露它供飞行绕过调节重发频率。
- **`Packet.codec(...)` 与 StreamCodec**：协议包用 `StreamCodec` 而非旧 `FriendlyByteBuf` 编码器；mixin 目标若是包内字段，注意 26.1.2 包内很多字段从 public 收紧为 protected/final，需要 Accessor。
- **mixin 配置 `compatibilityLevel: JAVA_25`**：本项目用 Java 25（`addon-template.mixins.json` 第 4 行），环境 jdk 必须 ≥25 才能跑。