# Mixin

> 源码依据：Meteor原始源码/meteordevelopment/meteorclient/mixin/... Meteor Client 26.1.2-SNAPSHOT

Meteor 的 mixin 目录 `meteordevelopment/meteorclient/mixin/`（约 200+ 个文件）通过 SpongePowered Mixin + **MixinExtras**（`com.llamalad7.mixinextras`）对原版 Minecraft 注入。本文挑 4 个真实 mixin 讲写法，给出完整类骨架与 mixin json 注册要求，并对照本项目 addon 的 mixin 清单。

---

## mixin 注入方式总览（26.1.2 真实使用的注解）

| 注解 | 用途 | 真实示例 |
| --- | --- | --- |
| `@Mixin` | 声明目标类 | 所有 mixin |
| `@Inject` | 在方法 HEAD/TAIL/RETURN 注入回调 | `CameraMixin` |
| `@Shadow` | 访问目标类私有字段/方法 | `CameraMixin` |
| `@Accessor` | 访问私有字段（接口形式） | `DirectionAccessor` |
| `@ModifyVariable` | 修改局部变量 | `CameraMixin` |
| `@ModifyArgs` | 修改方法调用的参数 | `CameraMixin` |
| `@ModifyReturnValue` | 修改方法返回值 | `CameraMixin` |
| `@ModifyExpressionValue` | 修改方法内表达式返回值 | `AttackRangeMixin` |
| `@Local`（MixinExtras） | 捕获局部变量 | `CameraMixin` |

---

## 真实 Mixin 示例 1：Accessor（接口 + @Accessor）

### 类名
`meteordevelopment.meteorclient.mixin.DirectionAccessor`

### 源码路径
`mixin/DirectionAccessor.java`

```java
package meteordevelopment.meteorclient.mixin;

import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Direction.class)
public interface DirectionAccessor {
    @Accessor("BY_2D_DATA")
    static Direction[] meteor$getHorizontal() {
        throw new AssertionError();
    }
}
```
- 用途：读取 `Direction` 的私有静态字段 `BY_2D_DATA`（水平方向数组）。调用时 `DirectionAccessor.meteor$getHorizontal()`。
- 命名约定：Meteor 的 accessor/injector 方法统一加 `meteor$` 前缀。

---

## 真实 Mixin 示例 2：完整类骨架（@Shadow + @Inject + MixinExtras）

### 类名
`meteordevelopment.meteorclient.mixin.CameraMixin`

### 源码路径
`mixin/CameraMixin.java`

```java
package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.mixininterface.ICamera;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin implements ICamera {
    @Shadow
    private boolean detached;

    @Shadow
    private float yRot;
    @Shadow
    private float xRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void getSubmergedFluidState(CallbackInfoReturnable<FogType> cir) {
        if (Modules.get().get(NoRender.class).noLiquidOverlay()) cir.setReturnValue(FogType.NONE);
    }

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true, name = "cameraDist")
    private float modifyGetMaxZoom(float cameraDist) {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam.isActive()) return 0;
        return cameraDist;
    }

    @ModifyArgs(method = "alignWithEntity",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void onAlignSetPosArgs(Args args, @Local(argsOnly = true, name = "partialTicks") float partialTicks) {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam.isActive()) {
            args.set(0, freecam.getX(partialTicks));
            args.set(1, freecam.getY(partialTicks));
            args.set(2, freecam.getZ(partialTicks));
        }
    }

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float modifyFov(float original) {
        return MeteorClient.EVENT_BUS.post(GetFovEvent.get(original)).fov;
    }

    @Override
    public void meteor$setRot(double yaw, double pitch) {
        setRotation((float) yaw, (float) Mth.clamp(pitch, -90, 90));
    }
}
```
- 注意：Meteor 大量用 `@ModifyReturnValue` 把原版方法返回值「转成事件」post 到 `MeteorClient.EVENT_BUS`，模块再订阅事件。
- 自定义接口方法写在 `mixininterface/` 包（如 `ICamera`），mixin 实现该接口让其它代码可强转调用。

---

## 真实 Mixin 示例 3：@ModifyExpressionValue

### 类名
`meteordevelopment.meteorclient.mixin.AttackRangeMixin`

### 源码路径
`mixin/AttackRangeMixin.java`

```java
@Mixin(AttackRange.class)
public abstract class AttackRangeMixin {
    @ModifyExpressionValue(method = "isInRange(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/ToDoubleFunction;D)Z",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/component/AttackRange;hitboxMargin:F", opcode = Opcodes.GETFIELD))
    private float modifyHitboxMargin(float original, LivingEntity attacker, ToDoubleFunction<Vec3> distanceFunction, double extraBuffer) {
        float v = (float) Modules.get().get(Hitboxes.class).getEntityValue(attacker);
        return original + v;
    }
}
```
- 要点：`@ModifyExpressionValue` 需要**精确的 method 描述符**（含参数签名），field 目标用 `FIELD` + `opcode = GETFIELD`。

### 真实 Mixin 示例 4：@ModifyExpressionValue(@At INVOKE)

### 类名
`meteordevelopment.meteorclient.mixin.CompassAngleStateMixin`

### 源码路径
`mixin/CompassAngleStateMixin.java`

```java
@Mixin(CompassAngleState.class)
public abstract class CompassAngleStateMixin {
    @ModifyExpressionValue(method = "getWrappedVisualRotationY",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ItemOwner;getVisualRotationYInDegrees()F"))
    private static float callLivingEntityGetYaw(float original) {
        if (Modules.get().isActive(Freecam.class)) return mc.gameRenderer.getMainCamera().yRot();
        return original;
    }
}
```

---

## mixin json 注册要求（client 数组）

Meteor 自身配置：`meteor-client.mixins.json`
```json
{
  "required": true,
  "package": "meteordevelopment.meteorclient.mixin",
  "compatibilityLevel": "JAVA_21",
  "plugin": "meteordevelopment.meteorclient.MixinPlugin",
  "client": [ "AABBMixin", "AbstractBlockStateMixin", "...", "DirectionAccessor" ],
  "injectors": { "defaultRequire": 1 }
}
```

本项目 addon 配置：`src/main/resources/addon-template.mixins.json`
```json
{
  "required": true,
  "package": "com.example.addon.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [ "ExampleMixin", "ModulesScreenTranslationMixin", "...", "MultiPlayerGameModeFastBreakMixin" ],
  "injectors": { "defaultRequire": 1 }
}
```

要求总结：
1. `client` 数组：**列出所有 mixin 类名**（不含包名，包名由 `package` 字段指定）。
2. `package`：mixin 类所在包。
3. `required: true`：全部为必要 mixin。
4. `compatibilityLevel`：Meteor 本体用 `JAVA_21`；本项目 addon 用 `JAVA_25`。
5. `plugin`（可选）：指向 mixin 插件类（Meteor 用 `MixinPlugin.java` 做条件应用）。
6. `injectors.defaultRequire`：默认 1（每个注入点至少匹配一次，否则报错）。

---

## 本项目 addon 完整 mixin 类骨架（ExampleMixin.java）

文件：`src/main/java/com/example/addon/mixin/ExampleMixin.java`

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

本项目 addon 的 mixin 目录：`src/main/java/com/example/addon/mixin/`（含 `ExampleMixin`、`MultiPlayerGameModeFastBreakMixin`、`ClientLevelPredictionAccessor`、`KeyboardInvoker` 等 37 个，已在 `addon-template.mixins.json` 的 `client` 数组登记）。

---

## 相关类
- `meteordevelopment.meteorclient.MixinPlugin`（mixin 条件应用插件）
- `meteordevelopment.meteorclient.mixininterface.ICamera` 等 `mixininterface/` 包（自定义接口，供强转）

---

## 常见坑
1. `@Mixin` 目标方法签名必须**与方法描述符完全匹配**（`Lnet/minecraft/...;` 形式的全限定类名），错一个字符就 `InjectorTargetError`。
2. 新 mixin 类名忘了写进 `client` 数组 → 静默不生效（无报错）。
3. `@Accessor` 目标字段非 `public` 时接口里方法可随便命名，但字段名 `@Accessor("...")` 必须与目标一致。
4. 用 `@Local`/`@ModifyExpressionValue` 需引入 **MixinExtras** 依赖；mixins json 无需额外声明，会自动识别。
5. `@Shadow` 字段必须与目标类字段同名同型，且目标可能被混淆时需注意 `@Shadow` 字段名映射。
6. `MeteorClient` 的 mixin 使用 `meteor$` 前缀命名注入方法，避免与方法目标/其它 mod 冲突。
7. 越界修改原版方法参数（`@ModifyArgs`）可能破坏其它 mod 兼容，尽量只在自有模块逻辑内改。