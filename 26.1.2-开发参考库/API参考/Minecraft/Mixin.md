# Mixin

> 源码依据：Minecraft原始源码 26.1.2 Mojang官方映射（关于 mixin 目录的核实结论见下）

## 一、重要核实结论（Glob 已核实）

- 官方源码 **不存在** `net/minecraft/client/mixin` 目录，也没有任何 `*Mixin.java` 文件。
- 全仓递归搜索 `*mixin*` 目录与 `*Mixin*` 文件名，结果均为空。
- 因此「Minecraft 原生 Mixin 体系」在 Mojang 发布的源码里没有可直接引用的官方 mixin 文件实例。本文以 **Fabric Mixin 库（SpongePowered Mixin）** 为规范视角讲解 addon 常用的注解写法，目标类/方法均为 26.1.2 源码中已核实的真实存在项。

> 上下文：本项目 addon 的 mixin 是针对 Meteor 客户端运行时注入的。Meteor 客户端基于 Fabric（SpongePowered Mixin）加载，addon 的 mixin 目标类是 Minecraft 客户端类（`net.minecraft.client.*` / `net.minecraft.network.*` 等），与本文注解理论一致。

## 二、Fabric Mixin 库（SpongePowered Mixin）基本用法

Mixin 通过在编译期把「注入类」织入「目标类」。核心注解：

| 注解 | 作用 |
| --- | --- |
| `@Mixin(目标类.class)` | 声明注入目标 |
| `@Inject` | 在目标方法头部/返回/调用点插入代码 |
| `@Redirect` | 重定向方法调用/字段访问 |
| `@ModifyConstant` | 修改方法内的字面量常量（如距离、冷却数值） |
| `@ModifyVariable` / `@ModifyArg` | 修改局部变量 / 方法实参 |
| `@Shadow` | 访问目标类私有字段/方法 |
| `@Overwrite` | 完全替换目标方法（不推荐） |
| `@Unique` | 注入类私有的、不会与目标冲突的成员 |

### 1. @Mixin
```java
@Mixin(net.minecraft.client.Minecraft.class)   // 目标：26.1.2 真实类 net.minecraft.client.Minecraft
public abstract class MinecraftMixin {
}
```
- 目标类必须真实存在：`net.minecraft.client.Minecraft`（源码 `net/minecraft/client/Minecraft.java`）存在。

### 2. @Inject（最常用）
```java
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci) {
    // Minecraft#tick() —— 真实方法，源码 net/minecraft/client/Minecraft.java 第 1798 行
}
```
- `at = @At("HEAD")`：方法进入前；`@At("RETURN")`：返回前；`@At("TAIL")`：返回后（可加 `CallbackInfoReturnable<T>`）；`@At(value="INVOKE", target="...")`：某次调用点。
- 回调参数：`CallbackInfo`（void 方法）/ `CallbackInfoReturnable<T>`（有返回值方法）。

### 3. @Redirect（重定向调用）
```java
@Redirect(method = "handleSystemChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;LdisplaySystemMessage(...)"))
private void redirectChat(net.minecraft.client.gui.Gui gui, net.minecraft.network.chat.Component message) {
    // 拦截 ClientPacketListener#handleSystemChat 里的聊天显示调用
    // 注意：target 签名必须逐字匹配 26.1.2 真实方法，否则 @Redirect 织入失败
}
```
- 目标方法示例：`net.minecraft.client.multiplayer.ClientPacketListener#handleSystemChat(ClientboundSystemChatPacket)`（26.1.2 真实存在）。
- `@Redirect` 要求 target 描述符（`L...;`+方法名）与源码完全一致，写错会导致注入崩溃。

### 4. @ModifyConstant（改常量）
```java
@ModifyConstant(method = "someMethod", constant = @Constant(doubleValue = 4.5D))
private double changeReach(double original) {
    return 5.0D; // 修改方法内 4.5 这个 double 常量
}
```
- 常用于修改**原版硬编码的数值**（如攻击距离、速度、冷却），比 @Overwrite 更稳。

## 三、可以用作 mixin 目标的真实类与方法（26.1.2 已核实）

| 目标类（全限定名） | 可用方法（真实签名） | 经典用途 |
| --- | --- | --- |
| `net.minecraft.client.Minecraft` | `public void tick()` | 每帧逻辑钩子 |
| `net.minecraft.client.Minecraft` | `public void setScreen(@Nullable Screen screen)` | 界面切换事件 |
| `net.minecraft.client.multiplayer.ClientPacketListener` | `public void handleSystemChat(ClientboundSystemChatPacket packet)` | 拦截聊天接收 |
| `net.minecraft.client.multiplayer.ClientPacketListener` | `public void handleSetHealth(ClientboundSetHealthPacket packet)` | 血量事件 |
| `net.minecraft.client.multiplayer.ClientPacketListener` | `public void sendCommand(String command)` | 拦截命令发送 |
| `net.minecraft.client.player.LocalPlayer` | `public void tick()` | 本地玩家每帧 |
| `net.minecraft.client.gui.screens.Screen` | `public void tick()` / `protected void init()` | 界面逻辑 |
| `net.minecraft.client.input.KeyboardHandler` | 按键处理相关方法 | 输入增强 |
| `net.minecraft.client.gui.Gui` | 血量/护甲/食物渲染方法 | HUD 注入 |
| `net.minecraft.world.entity.Entity` | `public HitResult pick(double range, float a, boolean withLiquids)` | 射线修改 |

## 四、addon 编写 mixin 的规范建议

1. **目标类名用官方映射**：`net.minecraft.client.Minecraft`、`net.minecraft.client.multiplayer.ClientPacketListener`、`net.minecraft.client.player.LocalPlayer` 等，严禁 Yarn 名。
2. **方法描述符逐字匹配**：`@Inject(method="...", at=...)` 的 `method` 名、参数类型必须与源码一致；重载方法用描述符消歧。
3. **优先 @Inject/@Redirect/@ModifyConstant**：避免 `@Overwrite` 与目标方法耦合过深。
4. **`cancellable = true`**：需要取消原方法时使用（回调变 `CallbackInfoReturnable<T>` 或 `CallbackInfo#cancel()`）。
5. **`@Shadow` 访问私有成员**：目标字段/方法需与真实名一致（如 `Minecraft#player`、`Minecraft#hitResult`）。
6. **Mixin 配置**：Fabric 需要 `*.mixins.json` 清单声明 mixin 类（`mixins` 数组），不在本文源码范围内（属于 addon 工程配置）。

## 五、常见坑

1. **官方源码没有 mixin 文件**：找不到 `net/minecraft/client/mixin` 属正常，不要虚构官方 mixin 文件路径；注入理论统一以 Fabric Mixin 库为准。
2. **目标方法名写错 → 织入期崩溃**：Mixin 在类加载时校验目标，方法名/描述符不匹配直接 throw，而不是静默失效。
3. **不要 mixin 不存在的方法**：例如旧的 `render(...)` 在 26.1.2 Screen 已被 `extractRenderState` 取代，若按旧版写 mixin 必定注入失败。
4. **`@Inject` 到 final 方法需谨慎**：Mixin 默认可注入 final，但某些场景需 `@Mixin` + `require = 1` 确认。
5. **混淆/中间名**：Mojang 官方映射经中间名（intermediary）与运行时对应，addon 不需要关心，但目标字符串务必用官方映射名。
6. **避免注入构造器/静态初始化块**：容易造成循环加载或顺序问题。