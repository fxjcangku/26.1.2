> 源码依据：Meteor原始源码/meteordevelopment/orbit/… 与 meteordevelopment/meteorclient/events/…　Meteor Client 26.1.2-SNAPSHOT

# Event 事件

Meteor 的事件系统由两部分组成：
1. **orbit**（`meteordevelopment.orbit`）——纯 Java 事件总线库（`IEventBus`/`EventBus`/`EventHandler`/`IListener`/`LambdaListener`/`ConsumerListener`/`ICancellable`）。
2. **Meteor 事件定义**（`meteordevelopment.meteorclient.events`）——按 `game/entity/packets/render/world/meteor` 六个子包划分的具体事件类，以及 `Cancellable` 基类。

--- 先看 orbit 底层 ---

## 1. IEventBus（事件总线接口）

### 源码路径
`meteordevelopment/orbit/IEventBus.java`

### 关键方法（真实签名）
```java
void registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory)
boolean isListening(Class<?> eventClass)
<T> T post(T event)
<T extends ICancellable> T post(T event)
void subscribe(Object object)
void subscribe(Class<?> klass)
void subscribe(IListener listener)
void unsubscribe(Object object)
void unsubscribe(Class<?> klass)
void unsubscribe(IListener listener)
```
> `post(T event)` 有两个重载：普通事件逐个调用所有监听器；`ICancellable` 事件则先 `setCancelled(false)`，逐个调用，**一旦 `isCancelled()` 为真立即停止后续监听器**。

## 2. EventBus（默认实现）

### 源码路径
`meteordevelopment/orbit/EventBus.java`

### 实现要点（真实行为）
- 用 `ConcurrentHashMap<Class<?>, List<IListener>>` 存监听器。
- `subscribe(Object)`：扫描对象（含父类）中所有带 `@EventHandler`、返回 `void`、参数恰好 1 个且非原始类型的方法，通过 `LambdaListener.Factory` 动态生成 lambda 注册。
- **优先级排序**：`insert` 按 `getPriority()` 降序插入（`HIGHEST` 最先执行，数值大的先跑）。
- `unsubscribe(Object)` 反向移除。
- 若未注册对应包的 lambda factory，抛 `NoLambdaFactoryException`。

## 3. EventHandler（注解）与 EventPriority

### 源码路径
`meteordevelopment/orbit/EventHandler.java`、`meteordevelopment/orbit/EventPriority.java`
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
    int priority() default EventPriority.MEDIUM;
}
```
```java
public class EventPriority {
    public static final int HIGHEST = 200;
    public static final int HIGH = 100;
    public static final int MEDIUM = 0;
    public static final int LOW = -100;
    public static final int LOWEST = -200;
}
```
> 这就是 Meteor 里的 `@EventHandler`（注解定义在 orbit 包，不是 meteorclient 包）。addon 监听写法 `@EventHandler(priority = EventPriority.HIGH)`，数值越大越先执行。

## 4. IListener / LambdaListener / ConsumerListener

### 源码路径
`meteordevelopment/orbit/listeners/IListener.java`、`LambdaListener.java`、`ConsumerListener.java`

### IListener 接口
```java
public interface IListener {
    void call(Object event);
    Class<?> getTarget();
    int getPriority();
    @Deprecated boolean isStatic();
}
```
### LambdaListener
运行时用 `LambdaMetafactory` 为 `@EventHandler` 方法生成 `Consumer<Object>`，`target = method.getParameters()[0].getType()`，`priority = method.getAnnotation(EventHandler.class).priority()`。内部接口 `Factory`：`MethodHandles.Lookup create(Method lookupInMethod, Class<?> klass)`。
### ConsumerListener<T>
```java
public ConsumerListener(Class<T> target, int priority, Consumer<T> executor)
public ConsumerListener(Class<T> target, Consumer<T> executor)  // 默认 EventPriority.MEDIUM
```

## 5. ICancellable（可取消事件接口）

### 源码路径
`meteordevelopment/orbit/ICancellable.java`
```java
public interface ICancellable {
    void setCancelled(boolean cancelled);
    default void cancel() { setCancelled(true); }
    boolean isCancelled();
}
```
> **重要：orbit 里没有 `Event` 基类，也没有 `meteordevelopment.orbit.events` 包。** 所谓「Event 基类」在 Meteor 侧是 `meteordevelopment.meteorclient.events.Cancellable`（下面）。

---

## 6. Cancellable（Meteor 事件基类）

### 源码路径
`meteordevelopment/meteorclient/events/Cancellable.java`
```java
public class Cancellable implements ICancellable {
    private boolean cancelled = false;
    public void setCancelled(boolean cancelled) { ... }
    public boolean isCancelled() { ... }
}
```
> Meteor 所有「可取消事件」都 extends 这个 `Cancellable`，从而获得 `cancel()` / `isCancelled()`。

---

## 7. Meteor events 包分类（真实事件类）

> 约定：Meteor 事件普遍是「单例 + `get(...)` 静态工厂」模式，`get()` 时把本次数据填入静态 `INSTANCE` 后返回，随后 `MeteorClient.EVENT_BUS.post(...)`。

### events/game（游戏流程）
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `meteordevelopment.meteorclient.events.game.GameJoinedEvent` | 进入世界。字段无，`static GameJoinedEvent get()` | 否 |
| `meteordevelopment.meteorclient.events.game.GameLeftEvent` | 离开世界。`static get()` | 否 |
| `...events.game.OpenScreenEvent` | 打开 Screen。字段 `public Screen screen`；`get(Screen)` | 是 |
| `...events.game.ReceiveMessageEvent` | 收到聊天消息。`message`/`indicator`/`id`，`getMessage()/setMessage()/getIndicator()/setIndicator()/isModified()` | 是 |
| `...events.game.SendMessageEvent` | 玩家发出消息。字段 `public String message`；`get(String)` | 是 |
| `...events.game.ChangePerspectiveEvent` | 切换视角。字段 `public CameraType perspective`；`get(CameraType)` | 是 |
| `...events.game.GetFovEvent`（见 render 包外也有）| — | — |
| `...events.game.ItemStackTooltipEvent`、`...events.game.ResolutionChangedEvent`、`...events.game.ResourcePacksReloadedEvent` | 对应场景 | 视源码 |

### events/entity（实体）
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `...events.entity.EntityAddedEvent` | 实体加入世界。字段 `public Entity entity`；`get(Entity)` | 否 |
| `...events.entity.EntityRemovedEvent` | 实体移除。字段 `public Entity entity`；`get(Entity)` | 否 |
| `...events.entity.EntityDestroyEvent`、`EntityMoveEvent`、`BoatMoveEvent`、`DropItemsEvent` | 相应实体行为 | 视源码 |

子包 `events/entity/player/` 常用：
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `...events.entity.player.AttackEntityEvent` | 攻击实体。`public Entity entity`；`get(Entity)` | 是 |
| `...events.entity.player.PlayerMoveEvent` | 玩家移动。`public MoverType type; public Vec3 movement`；`get(MoverType, Vec3)` | 否 |
| `...events.entity.player.SendMovementPacketsEvent`（内部 `Pre`/`Post`）| 发送移动包前后。`Pre.get()`/`Post.get()` | 否 |
| `PlaceBlockEvent`、`BreakBlockEvent`、`StartBreakingBlockEvent`、`InteractBlockEvent`、`InteractEntityEvent`、`InteractItemEvent`、`DoAttackEvent`、`DoItemUseEvent`、`FinishUsingItemEvent` 等 | 相应交互 | 多数可取消（extends Cancellable） |

### events/packets（数据包）
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `...events.packets.PacketEvent`（内部 `Receive`/`Send`/`Sent`）| 收/发包。字段 `public Packet<?> packet; public Connection connection` | `Receive`/`Send` 可取消 |
| `...events.packets.InventoryEvent`、`ContainerSlotUpdateEvent`、`PlaySoundPacketEvent` | 相应包场景 | 视源码 |

### events/render（渲染）
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `...events.render.Render2DEvent` | HUD/2D 渲染。字段 `graphics`/`screenWidth`/`screenHeight`/`frameTime`/`tickDelta`；`get(...)` | 否 |
| `...events.render.Render3DEvent` | 3D 世界渲染。字段 `matrices`/`renderer`/`depthRenderer`/`frameTime`/`tickDelta`/`offsetX/Y/Z`；`get(...)` | 否 |
| `...events.render.GetFovEvent` | FOV 计算。`public float fov`；`get(float)` | 否 |
| `HeldItemRendererEvent`、`ArmRenderEvent`、`ApplyTransformationEvent`、`RenderBlockEntityEvent`、`RenderItemEntityEvent`、`RenderBossBarEvent`、`RenderAfterWorldEvent`、`TooltipDataEvent` | 相应渲染阶段 | 视源码 |

### events/world（世界）
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `...events.world.TickEvent`（内部 `Pre`/`Post`）| 客户端 tick 前后。`Pre.get()`/`Post.get()`，无字段 | 否 |
| `...events.world.BlockUpdateEvent` | 方块更新。`public BlockPos pos; public BlockState oldState, newState`；`get(...)` | 否 |
| `...events.world.ParticleEvent` | 粒子。`public ParticleOptions particle`；`get(...)` | 是 |
| `...events.world.PlaySoundEvent` | 声音。`public SoundInstance sound`；`get(...)` | 是 |
| `ChunkDataEvent`、`ChunkOcclusionEvent`、`CollisionShapeEvent`、`AmbientOcclusionEvent`、`BlockActivateEvent`、`ServerConnectBeginEvent`、`ServerConnectEndEvent` | 相应场景 | 视源码 |

### events/meteor（Meteor 内部）
| 事件类全名 | 触发时机 | 可取消 |
|---|---|---|
| `...events.meteor.KeyInputEvent` | 键盘输入。字段 `public net.minecraft.client.input.KeyEvent input; public KeyAction action`；方法 `int key()`, `int modifiers()` | 是 |
| `...events.meteor.MouseClickEvent` | 鼠标点击。字段 `click`/`input`/`action`；方法 `int button()` | 是 |
| `...events.meteor.MouseScrollEvent` | 滚轮。`public double value` | 是 |
| `...events.meteor.CharTypedEvent` | 字符输入。`public char c` | 是 |
| `...events.meteor.ActiveModulesChangedEvent` | 激活模块集合变化。`static get()` | 否 |
| `...events.meteor.ModuleBindChangedEvent` | 模块按键绑定变化。`public Module module`；`get(Module)` | 否 |
| `...events.meteor.CustomFontChangedEvent` | 自定义字体变化 | 否 |

> 说明：旧版叫 `KeyEvent` 的键盘事件在 26.1.2 中为 `events/meteor/KeyInputEvent`；`TickEvent` 位于 `events/world` 包（不是 game 包）。

---

## 8. post / subscribe / unsubscribe（addon 怎么用）

```java
// 订阅（对象）：扫描本对象所有 @EventHandler 方法
MeteorClient.EVENT_BUS.subscribe(this);
// 订阅（类，仅 static 方法）
MeteorClient.EVENT_BUS.subscribe(MyClass.class);
// 退订
MeteorClient.EVENT_BUS.unsubscribe(this);
// 发事件（普通）
MeteorClient.EVENT_BUS.post(Render3DEvent.get(...));
// 发事件（可取消，取消后会短路后续监听器）
MeteorClient.EVENT_BUS.post(event); // event implements ICancellable
```

`MeteorClient.EVENT_BUS` 定义见 `meteordevelopment/meteorclient/MeteorClient.java`：
```java
public static Minecraft mc;
public static final IEventBus EVENT_BUS = new EventBus();
```

---

## 9. 监听模板（真实代码块）

摘自真实模块 `systems/modules/player/AutoRespawn.java`（`@EventHandler` 写法）：
```java
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

public class AutoRespawn extends Module {
    @EventHandler(priority = EventPriority.HIGH)
    private void onOpenScreenEvent(OpenScreenEvent event) {
        if (!(event.screen instanceof DeathScreen)) return;
        Modules.get().get(WaypointsModule.class).addDeath(mc.player.position());
        mc.player.respawn();
        event.cancel();
    }
}
```
要点：
- 监听方法**必须**：`@EventHandler` 注解、返回 `void`、参数恰好 1 个事件对象（非原始类型）、通常 `private`。
- 可取消事件直接 `event.cancel()`；`EventPriority` 数值越大越先执行。
- 模块启用时 `Module.autoSubscribe=true` 会自动 `subscribe(this)`，无需手动订阅。

---

## 常见坑
1. orbit **没有** `Event` 基类和 `orbit.events` 包；别在 addon 里 import 不存在的 `meteordevelopment.orbit.events.*`。
2. `post(ICancellable)` 会先 `setCancelled(false)` 再分发，取消后剩余监听器不再执行——顺序敏感逻辑要用 `EventPriority`。
3. 监听方法签名不符（返回非 void / 参数多于 1 个 / 参数是原始类型）会被 `EventBus.isValid` 静默过滤，**不会报错但也不会生效**。
4. 事件多为单例复用，`post` 之后勿长时间持有事件的字段引用。
5. 26.1.2 键盘事件类名是 `KeyInputEvent`（事件包 `events/meteor`），`TickEvent` 在 `events/world`。按类全名 import，不要凭旧版包路径写。
6. `@EventHandler` 的 import 是 `meteordevelopment.orbit.EventHandler`（不是 meteorclient）。