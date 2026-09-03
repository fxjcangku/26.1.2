# Event 生命周期

> 事件库是 `meteordevelopment.orbit`（版本 0.2.4，Meteor 依赖，来源于 Modrinth maven），不是 Fabric 的事件 API。

## 概述

Meteor 的事件系统由 orbit 包实现，核心类：`EventBus`（实现 `IEventBus`）、`EventHandler`（注解）、`EventPriority`（优先级常量）、`ICancellable`（可取消接口）、`LambdaListener`（监听器实现）。全局总线单例是 `MeteorClient.EVENT_BUS`（`IEventBus` 类型，实际 `new EventBus()`），模块级也复用同一个总线（模块 toggle 时 subscribe/unsubscribe 自身）。

---

## 1. EventBus 结构（真实字段与签名）

```java
public class EventBus implements IEventBus {
    private final Map<Object, List<IListener>> listenerCache;          // 实例监听缓存（对象身份）
    private final Map<Class<?>, List<IListener>> staticListenerCache;  // 静态监听缓存
    private final Map<Class<?>, List<IListener>> listenerMap;          // 事件类型 -> 监听器(按优先级排序)
    private final List<LambdaFactoryInfo> lambdaFactoryInfos;          // 包前缀 -> lambda 工厂
}
```

关键方法（真实签名，`IEventBus.java`）：

```java
void  registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory);
<T> T post(T event);                       // 非可取消
<T extends ICancellable> T post(T event);  // 可取消，取消即短路
void subscribe(Object object);             // 扫描实例全部 @EventHandler 方法
void subscribe(Class<?> klass);            // 只订阅静态方法
void subscribe(IListener listener);
void unsubscribe(Object object);
void unsubscribe(Class<?> klass);
void unsubscribe(IListener listener);
boolean isListening(Class<?> eventClass);
```

## 2. @EventHandler 注解与监听器扫描

`EventHandler`：

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
    int priority() default EventPriority.MEDIUM;  // 默认 0
}
```

`EventBus.subscribe(Object)` → `getListeners(object.getClass(), object)` 扫描方法：递归遍历本类 + 所有父类（`getSuperclass()` 链）的 `getDeclaredMethods()`，`isValid(method)` 要求：

1. 有 `@EventHandler` 注解；
2. 返回类型 `void`；
3. 参数个数恰好 1；
4. 参数类型不是基本类型（`!isPrimitive()`）。

事件类型由「方法唯一参数类型」决定，`IListener.getTarget()` 即该参数类型。重复 subscribe 同一实例会命中 `listenerCache`（按 `==` 身份比较，不是 `equals`）。

## 3. LambdaListener 缓存（MethodHandle / LambdaMetafactory）

`LambdaListener`（`orbit/listeners/LambdaListener.java`）是监听器实现，构造时**用 `LambdaMetafactory` 在运行时把目标方法直接编译成一个 `Consumer<Object>`**，`call()` 就是 `executor.accept(event)`：

```java
MethodHandle methodHandle = lookup.findVirtual/findStatic(klass, name, methodType);
MethodHandle lambdaFactory = LambdaMetafactory.metafactory(lookup, "accept",
    invokedType, MethodType.methodType(void.class, Object.class), methodHandle, methodType).getTarget();
this.executor = (Consumer<Object>) lambdaFactory.invoke(object);  // 或 invoke() 静态
```

私有方法的访问靠 `MethodHandles.privateLookupIn`（Java 9+，`static` 块里反射拿到 `privateLookupInMethod`）：

```java
lookup = factory.create(privateLookupInMethod, klass);
```

而 `Factory` 接口 `create(Method lookupInMethod, Class<?> klass)` 由 Meteor 在每个 addon 包上注册：

```java
EVENT_BUS.registerLambdaFactory(addon.getPackage(),
    (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
```

含义：**每个 addon 通过 `getPackage()` 包名前缀绑定一个 lambda 工厂**。Meteor 主 addon 前缀是 `"meteordevelopment.meteorclient"`；addon 若未注册（如包名 `com.example.addon` 的类但代码里没走到注册），`getLambdaFactory` 会抛 `NoLambdaFactoryException`。addon 过旧（缺少 `registerLambdaFactory` 方法）会抛 `AbstractMethodError` 被 catch 并转成「addon 太旧」运行时异常。

## 4. 优先级处理（源码正序，不是「<200 前置」）

`EventPriority` 常量：

```java
HIGHEST = 200; HIGH = 100; MEDIUM = 0; LOW = -100; LOWEST = -200;
```

`EventBus.insert()` 按**优先级降序**插入：

```java
for (; i < listeners.size(); i++) {
    if (listener.getPriority() > listeners.get(i).getPriority()) break;
}
listeners.add(i, listener);
```

**结论：数值越大越先执行**，顺序为 HIGHEST(200) → HIGH(100) → MEDIUM(0) → LOW(-100) → LOWEST(-200)。旧版记忆里的「priority<200 前置」与源码相反，以源码为准：`@EventHandler(priority=EventPriority.HIGH)` 会比默认 `MEDIUM` 的监听器先执行。同优先级按订阅先后（后插入的靠后）。

## 5. 事件投递与 cancellable 短路

`post(T event)`（普通事件）：取 `listenerMap.get(event.getClass())` 的列表，直接 `for (IListener l : listeners) l.call(event)`，无取消逻辑，返回原事件。

`post(T extends ICancellable)`（可取消事件）：

```java
event.setCancelled(false);              // 先重置
for (IListener listener : listeners) {
    listener.call(event);
    if (event.isCancelled()) break;     // 取消即短路
}
```

所以可取消事件的契约是：**先投递给优先级最高的监听器，一旦某个监听器 `event.cancel()`，后续监听器不再执行**。事件类型匹配用精确的 `event.getClass()`（`listenerMap` 的 key 是事件类；父类事件不匹配子类）。

- `ICancellable`（orbit）：`setCancelled(boolean)`、`cancel()` 默认调 `setCancelled(true)`、`isCancelled()`。
- `Cancellable`（meteor，`events/Cancellable.java`）：Meteor 侧可取消事件的基类，持有 `private boolean cancelled`，实现 `ICancellable`。

## 6. MeteorClient.EVENT_BUS 与模块级总线

- `MeteorClient.EVENT_BUS` 是**全局唯一**总线。
- 主程序自身：`MeteorClient.onInitializeClient` 里 `EVENT_BUS.subscribe(this)`；`Modules`、`Systems`、`Commands`、`Rotations`、`BlockUtils` 等静态工具也用 `EVENT_BUS.subscribe(Xxx.class)` 订阅静态方法。
- 模块级：`Module.toggle()` 里对模块实例 `EVENT_BUS.subscribe(this)` / `unsubscribe(this)`（`autoSubscribe=true` 时），所以模块事件处理器是「激活才订阅、关掉即退订」。
- 发送方由 mixin 触发，例如 `KeyboardHandlerMixin` post `KeyInputEvent`、`ConnectionMixin` post `PacketEvent`、`LocalPlayerMixin` post `SendMovementPacketsEvent`。

---

## Addon 用法 / 介入点

```java
// 实例订阅
MeteorClient.EVENT_BUS.subscribe(this);
// 静态订阅
MeteorClient.EVENT_BUS.subscribe(MyStaticListeners.class);

@EventHandler
private void onTick(TickEvent.Post event) { }

@EventHandler(priority = EventPriority.HIGHEST)
private void onPacket(PacketEvent.Receive event) { event.cancel(); }  // 取消可取消事件
```

- 订阅对 addon 生效的前提：在 `onInitialize` 之前已由 Meteor 执行 `registerLambdaFactory(getPackage(), ...)`（这是自动流程，`MeteorClient.onInitializeClient` 里遍历 `AddonManager.ADDONS` 完成）。跨 jar 只要包前缀一致即可。
- 自定义可取消事件：继承 `meteordevelopment.meteorclient.events.Cancellable`（或直接实现 `ICancellable`）。
- 防递归/防 stack overflow：在处理事件内再次触发同类事件要谨慎（参考 `PacketEvent.Send.sendSilently` 绕过事件的思路）。

## 常见坑

1. **方法签名不规范**（非 void / 参数非 1 个 / 参数是基本类型）：`isValid` 直接过滤，静默不订阅。
2. **优先级方向记反**：`HIGHEST=200` 先执行；负值 LOW 后执行。别写「数字小先跑」。
3. **可取消事件未重置**：`Cancellable` 事件单例复用（`get()` 返回 `INSTANCE`），post 时 `setCancelled(false)` 兜底；自己手工 new 事件则无此问题。
4. **addon 包前缀未注册 lambda 工厂**：会抛 `NoLambdaFactoryException`；旧 addon 会抛「too old and cannot be ran」。
5. **父类私有监听**：父类上的 `@EventHandler` 方法也会被扫描到（递归 superclass），这是特性不是 bug。
6. **监听器缓存按身份**：`getListeners` 用 `==` 匹配实例，重写 `equals` 不影响订阅匹配。

## 源码依据

- `meteordevelopment/orbit/EventBus.java` —— listenerMap/listenerCache/lambdaFactoryInfos、post 两种重载、insert 降序、subscribe/unsubscribe、getListeners/isValid、getLambdaFactory。
- `meteordevelopment/orbit/IEventBus.java` —— 接口真实签名与文档注释。
- `meteordevelopment/orbit/EventHandler.java` —— 注解、默认 MEDIUM。
- `meteordevelopment/orbit/EventPriority.java` —— HIGHEST/HIGH/MEDIUM/LOW/LOWEST 数值。
- `meteordevelopment/orbit/ICancellable.java` —— setCancelled/cancel/isCancelled。
- `meteordevelopment/orbit/listeners/LambdaListener.java` —— LambdaMetafactory/MethodHandle/privateLookupIn、Factory 接口。
- `meteordevelopment/orbit/listeners/IListener.java` —— call/getTarget/getPriority/isStatic。
- `meteordevelopment/orbit/NoLambdaFactoryException.java` —— 未注册 lambda 工厂异常。
- `meteordevelopment/meteorclient/events/Cancellable.java` —— Meteor 可取消事件基类。
- `meteordevelopment/meteorclient/MeteorClient.java` —— `EVENT_BUS` 定义、`registerLambdaFactory` 调用、`subscribe(this)`。
- `meteordevelopment/meteorclient/systems/modules/Module.java` —— 模块 toggle 时 subscribe/unsubscribe。
- `meteordevelopment/meteorclient/mixin/KeyboardHandlerMixin.java` / `MouseHandlerMixin.java` / `ConnectionMixin.java` / `LocalPlayerMixin.java` —— 常见事件 post 点。