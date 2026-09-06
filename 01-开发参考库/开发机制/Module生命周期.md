# Module 生命周期

> 版本基准：Meteor Client 26.1.2-SNAPSHOT。原文类名/字段/方法名保留英文，勿按旧版 Meteor 记忆（`mc` 单例是 `MeteorClient.mc`）。

## 概述

模块（`Module`）是 Meteor 功能的最小单位：一个功能 = 一个 `Module` 子类。本机源码中 `Module` 的启停、事件订阅、分类、序列化、按键绑定全部由 `Modules` 管理器与 `Module` 自身协作完成。核心事实先钉死：

- 单例入口：`MeteorClient.mc`（`public static Minecraft mc`）。
- 全局事件总线：`MeteorClient.EVENT_BUS`（`IEventBus`，实际是 orbit 的 `EventBus`）。
- 模块容器：`Modules` 是 `System<Modules>` 子类，经 `Systems.get(Modules.class)` 取单例，用 `Modules.get()` 静态包装。

---

## 1. 构造（Category + name + description）

`Module` 构造函数签名：

```java
public Module(Category category, String name, String description, String... aliases)
public Module(Category category, String name, String desc)  // aliases 为空数组
```

构造时发生的事（`Module.java`）：

1. 若 `name` 含空格，`MeteorClient.LOG.warn` 告警「不兼容 Meteor 命令」。
2. 保存 `mc = Minecraft.getInstance()`、`category`、`name`、`description`、`aliases`。
3. `title = Utils.nameToTitle(name)`（`auto-respawn` → `Auto Respawn`）。
4. `color = Color.fromHsv(Utils.random(0.0, 360.0), 0.35, 1)`（随机色）。
5. 遍历 `AddonManager.ADDONS`，若本类 `getClass().getName()` 以某个 addon 的 `getPackage()` 开头，则 `this.addon = addon`，否则 `addon = null`。
6. `settings = new Settings()`、`keybind = Keybind.none()`。

关键默认态：`active=false`（私有）、`serialize=true`、`runInMainMenu=false`、`autoSubscribe=true`、`toggleOnBindRelease=false`、`chatFeedback=true`、`favorite=false`。

## 2. Modules.get() 结构

`Modules` 内部三个真实容器（`Modules.java`）：

```java
private final Map<Class<? extends Module>, Module> moduleInstances; // Class -> 实例
private final Map<Category, List<Module>> groups;                  // Category -> 列表
private final List<Module> active;                                 // 激活列表
```

- `get(Class<T>)` 按类取实例；`get(String)` 按 `name.equalsIgnoreCase` 遍历取实例。
- `getAll()` 返回 `moduleInstances.values()`；`getGroup(Category)` 用 `groups.computeIfAbsent`。
- `activate` 列表由 `addActive`/`removeActive` 维护，加锁 `synchronized (active)`，变更时 post `ActiveModulesChangedEvent`。

## 3. 注册与分类

- 注册发生在 `Modules.init()`（`initCombat/initPlayer/initMovement/initRender/initWorld/initMisc` 一堆 `add(new Xxx())`），在 `MeteorClient.onInitializeClient` 的 `Systems.init()` 里被调用。
- 分类由 `Categories.init()` 先执行：设 `REGISTERING=true`，`Modules.registerCategory(Combat/Player/Movement/Render/World/Misc)`，再让各 `MeteorAddon.onRegisterCategories()` 注册 addon 自己的分类，最后 `REGISTERING=false`。
- `Modules.registerCategory(Category)` 要求 `Categories.REGISTERING` 为真，否则抛 `RuntimeException("...Cannot register category outside of onRegisterCategories callback.")`。

addon 只能在自己的 `onRegisterCategories()` 里 `Modules.registerCategory(...)`，在 `onInitialize()` 里则事后 `Modules.get().add(module)`（分类必须已注册，否则 `Modules.add` 抛「category was not registered」）。

## 4. isActive() / toggle() 真实流转（先插 active 再 onActivate？——源码：是）

`Module.toggle()` 若未激活：

```java
active = true;                       // 1. 先置真
Modules.get().addActive(this);       // 2. 再插入 active 列表（触发 ActiveModulesChangedEvent）
settings.onActivated();              // 3. 触发每个 Setting.onActivated()
if (runInMainMenu || Utils.canUpdate()) {
    if (autoSubscribe) MeteorClient.EVENT_BUS.subscribe(this);  // 4. 订阅事件
    onActivate();                     // 5. 最后回调业务激活
}
```

若已激活（关闭，源码对称）：

```java
if (runInMainMenu || Utils.canUpdate()) {
    if (autoSubscribe) MeteorClient.EVENT_BUS.unsubscribe(this);
    onDeactivate();
}
active = false;
Modules.get().removeActive(this);
```

**结论（源码证实）**：激活路径是「先 `active=true` + `addActive` 插入 activeModules，再调 `onActivate`」。因此 `onActivate()` 里 `isActive()` 已经返回 `true`，且此时模块已在 active 列表。addon 项目 `YiyiaddonModule.reportSelfCheck()` 的注释正是据此（「Module.toggle() 先 addActive 再调 onActivate，在 onActivate 里直接 toggle() 会状态机重入」）。

`enable()` / `disable()` 只是 `if (!isActive()) toggle()` / `if (isActive()) toggle()`。

### 4.1 onActivate / onDeactivate 的完整调用点清单

| 调用点 | 位置 | 时机 |
|---|---|---|
| `Module.toggle()` | `Module.java` | 手动启停（按键/GUI/命令） |
| `Modules.onGameJoined(GameJoinedEvent)` | `Modules.java` | 进服时，对「已 active 且非 runInMainMenu」的模块补 subscribe + `onActivate()` |
| `Modules.onGameLeft(GameLeftEvent)` | `Modules.java` | 离服时，对称 unsubscribe + `onDeactivate()` |

注意：`runInMainMenu=false`（默认）的模块在「主菜单/非世界阶段」被 toggle 时不会 subscribe 也不调 `onActivate`，只置 active；真正 `onActivate` 延迟到 `GameJoinedEvent`。这正是「主菜单开启、进服才真正生效」的机制本源。

## 5. fromTag / toTag 存档链（Settings 序列化）

- `Module.toTag()` 写 `name`、`keybind`、`toggleOnKeyRelease`、`chatFeedback`、`favorite`、`settings`（`settings.toTag()`）、`active`。
- `Module.fromTag()` 反向读，最后 `if (active != isActive()) toggle()` —— 即存档里 active 与内存不一致时自动 toggle。
- `Modules.toTag()` 收集所有 `module.toTag()`（`serialize=false` 则返回 null 被跳过）入 `ListTag("modules")`；`fromTag` 先 `disableAll()` 再逐条 `get(name).fromTag()`。
- `System<Modules>` 基类负责落盘：`System(String name)` 生成 `<MeteorClient.FOLDER>/<name>.nbt`，模块系统名是 `"modules"`，即 `meteor-client/modules.nbt`。序列化走 NBT，`save` 用临时文件+原子移动，`load` 损坏时备份为 `.backup.nbt`。`Systems.onGameLeft` 触发 `save()`，JVM shutdown hook 也 save。

## 6. 模块绑定按键触发路径

链路：

1. 底层 `KeyboardHandlerMixin`/`MouseHandlerMixin` 捕获输入 → post `KeyInputEvent` / `MouseClickEvent`（`Cancellable`）。
2. `Modules.onKey(KeyInputEvent)`（`@EventHandler(priority=HIGH)`）与 `onMouseClick` 都调 `onAction(isKey, value, modifiers, isPress)`。
3. `onAction`：`if (mc.screen != null || Input.isKeyPressed(GLFW_KEY_F3)) return;` 然后遍历所有模块，`module.keybind.matches(isKey, value, modifiers)` 且 `(isPress || (module.toggleOnBindRelease && module.isActive()))` 时 `module.toggle()` + `module.sendToggledMsg()`。
4. 绑定录制：`setModuleToBind` 置 `moduleToBind`，`awaitKeyRelease()` 防回车键立即绑定；`onKeyBinding`（`HIGHEST`）在 Release 时 `keybind.set(...)` 并 post `ModuleBindChangedEvent.get(moduleToBind)`。
5. 松键关闭：`Modules.onOpenScreen`（`HIGHEST+1`）在开屏时把「toggleOnBindRelease 且激活」的模块关掉。

`Keybind` 结构：`isKey/value/modifiers`，`matches()` 先判 `isSet()` 与 `isKey` 一致，无修饰键则比对 `value`，有则比对 `value+modifiers`。`ModuleBindChangedEvent` / `ActiveModulesChangedEvent` 都是单例复用（`get()` 返回 `INSTANCE`）。

## 7. info/warning/error 消息通道

`Module` 三个方法都先 `ChatUtils.forceNextPrefixClass(getClass())` 再走 `ChatUtils`：

- `info(Component)` → `ChatUtils.sendMsg(title, message)`（带 `[标题]` 前缀）。
- `info(String, Object...)` → `ChatUtils.infoPrefix(title, ...)`；`warning` → `warningPrefix`；`error` → `errorPrefix`。
- 前缀配色：info 前缀紫色 + 正文灰；warning 前缀紫 + 正文黄；error 前缀紫 + 正文红。
- `sendToggledMsg()` 受 `Config.get().chatFeedback.get() && chatFeedback` 控制。

---

## Addon 用法 / 介入点

```java
public class MyModule extends Module {
    public MyModule() { super(AddonTemplate.CATEGORY, "my-module", "描述"); }
    @Override public void onActivate()  { }   // 此时 isActive()==true
    @Override public void onDeactivate(){ }
    // 可选：getWidget(GuiTheme) 自定义设置面板、getInfoString() 返回 HUD 信息串
}
// AddonTemplate.onRegisterCategories(): Modules.registerCategory(CATEGORY);
// AddonTemplate.onInitialize():     Modules.get().add(new MyModule());
```

- 用 `Modules.get().get(MyModule.class)` / `Modules.get().isActive(MyModule.class)` 判断状态。
- 需要「主菜单也运行」的模块设 `runInMainMenu = true`；想手动控制订阅设 `autoSubscribe = false`。
- `onActivate` 里禁止直接 `toggle()`（状态机重入），改用 `mc.execute(() -> toggle())` 延后一帧（见 `YiyiaddonModule.reportSelfCheck` 实际写法）。

## 常见坑

1. **name 含空格**：只在日志告警不报错，但命令/别名搜索失效。
2. **`onInitialize` 里 add 却没注册分类**：`Modules.add` 抛「category was not registered」。
3. **在 `onActivate` 里 toggle**：toggle 已先置 active，重入会走关路径，导致「开一下就关」。
4. **`runInMainMenu=false` 时在主菜单 toggle**：不会 `onActivate`，只有进服才回调。
5. **重复名 add**：`Modules.add` 会按 name 移除旧实例并 `unregisterColorSettings`，可覆盖但不能同 name 并存（见 Module注册机制.md）。

## 源码依据

- `meteordevelopment/meteorclient/systems/modules/Module.java` —— 构造、toggle、onActivate/onDeactivate、fromTag/toTag、info/warning/error、isActive、sendToggledMsg。
- `meteordevelopment/meteorclient/systems/modules/Modules.java` —— moduleInstances/groups/active 容器、add/removeActive、add()、onKey/onAction 按键链、onGameJoined/onGameLeft、registerCategory、disableAll、toTag/fromTag。
- `meteordevelopment/meteorclient/systems/modules/Categories.java` —— REGISTERING、init、六个默认分类。
- `meteordevelopment/meteorclient/systems/modules/Category.java` —— name/icon/nameHash、equals/hashCode。
- `meteordevelopment/meteorclient/systems/System.java` —— 系统落盘/读档、临时文件+原子移动、损坏备份。
- `meteordevelopment/meteorclient/systems/Systems.java` —— add/init/get、onGameLeft save、shutdown 前 save。
- `meteordevelopment/meteorclient/MeteorClient.java` —— `mc`、`EVENT_BUS`、onInitializeClient 初始化顺序、shutdown hook。
- `meteordevelopment/meteorclient/utils/misc/Keybind.java` —— isKey/value/modifiers、matches、canBindTo。
- `meteordevelopment/meteorclient/events/meteor/ModuleBindChangedEvent.java`、`ActiveModulesChangedEvent.java` —— 事件单例。
- `meteordevelopment/meteorclient/mixin/KeyboardHandlerMixin.java`、`MouseHandlerMixin.java` —— KeyInputEvent/MouseClickEvent 来源。
- `com/example/addon/core/YiyiaddonModule.java`（addon 真实用法）—— reportSelfCheck 对「先 addActive 再 onActivate」的印证。