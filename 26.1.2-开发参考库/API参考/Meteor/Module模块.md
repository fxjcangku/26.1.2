> 源码依据：Meteor原始源码/meteordevelopment/meteorclient/systems/modules/…　Meteor Client 26.1.2-SNAPSHOT

# Module 模块

模块（Module）是 Meteor addon 开发的最高频抽象：一个功能 = 一个 `Module` 子类。Meteor 通过 `Modules` 管理器统一注册、分类、启停、按键绑定、序列化配置。

---

## 1. Module（模块基类）

### 类名
`Module`

### 包名
`meteordevelopment.meteorclient.systems.modules`

### 源码路径
`meteordevelopment/meteorclient/systems/modules/Module.java`

### 继承与接口
```java
public abstract class Module implements ISerializable<Module>, Comparable<Module>
```
- `ISerializable<Module>`：实现 `toTag()` / `fromTag(CompoundTag)` 配置序列化接口（`meteordevelopment.meteorclient.utils.misc.ISerializable`）。
- `Comparable<Module>`：按 `name` 字典序排序。

### 关键字段（真实名 + 类型 + 中文用途）

| 字段 | 类型 | 中文用途 |
|---|---|---|
| `mc` | `protected final Minecraft` | 原版 Minecraft 客户端实例（构造时 `Minecraft.getInstance()`，等价于 `MeteorClient.mc`） |
| `category` | `public final Category` | 所属分类 |
| `name` | `public final String` | 模块唯一名（小写、用 `-` 连接，如 `"auto-respawn"`）。要求**不能含空格**，否则命令行不兼容 |
| `title` | `public final String` | 显示标题，构造时由 `Utils.nameToTitle(name)` 自动生成（如 `"auto-respawn"` → `"Auto Respawn"`） |
| `description` | `public final String` | 模块描述 |
| `aliases` | `public final String[]` | 别名数组（用于搜索/命令） |
| `color` | `public final Color` | 随机生成的模块颜色（`Color.fromHsv(...)`） |
| `addon` | `public final MeteorAddon` | 所属 addon（按类名包前缀匹配，非 addon 则为空 `null`） |
| `settings` | `public final Settings` | 该模块的 Settings 容器（含若干 SettingGroup） |
| `active` | `private boolean` | 是否激活（私有，通过 `isActive()` 读取） |
| `serialize` | `public boolean`（默认 `true`） | 是否需要序列化保存 |
| `runInMainMenu` | `public boolean`（默认 `false`） | 是否在主菜单也运行（默认仅在进入世界后自动订阅事件） |
| `autoSubscribe` | `public boolean`（默认 `true`） | 启停时是否自动 subscribe/unsubscribe 本模块 |
| `keybind` | `public final Keybind`（`Keybind.none()`） | 快捷键绑定 |
| `toggleOnBindRelease` | `public boolean`（默认 `false`） | 松开按键（而非按下）时才切换 |
| `chatFeedback` | `public boolean`（默认 `true`） | 切换时是否在聊天框输出 feedback |
| `favorite` | `public boolean`（默认 `false`） | 是否收藏（GUI 置顶显示用） |

### 构造
```java
public Module(Category category, String name, String description, String... aliases)
public Module(Category category, String name, String desc)
```
> 模块**按 Category + name 构造**。`title`、`color`、`addon` 都是构造时自动推导，你只需提供 `category`、`name`、`description`（可选 `aliases`）。

### 关键方法（真实签名 + 中文用途）

```java
public void onActivate()
```
启用时回调（override 实现你的启用逻辑）。注意：`toggle()` 只有在 `runInMainMenu || Utils.canUpdate()` 为真时才真正调用它。

```java
public void onDeactivate()
```
停用时回调。

```java
public void toggle()
```
切换启用/停用状态的核心方法。流程：首次激活 → `active=true`、加入 `Modules.get().addActive(this)`、`settings.onActivated()`、按需 `EVENT_BUS.subscribe(this)` 并调用 `onActivate()`；再次 → 按需 unsubscribe + `onDeactivate()`，然后 `active=false` 并移出 active 列表。
> addon 开发中：外部代码通常用 `module.toggle()` / `enable()` / `disable()` 控制模块，**不要直接写 `active` 字段**。

```java
public void enable()
public void disable()
```
分别等价于「如果未激活就 toggle」/「如果激活就 toggle」。安全的幂等接口。

```java
public void sendToggledMsg()
```
输出「Toggled X on/off」聊天反馈（受 `Config.chatFeedback` 与 `chatFeedback` 双重控制）。

```java
public void info(Component message)
public void info(String message, Object... args)
public void warning(String message, Object... args)
public void error(String message, Object... args)
```
向聊天栏输出带模块标题前缀的信息/警告/错误（前缀自动使用本模块 `title`）。addon 开发里这是最常用的「模块内报错/提示」通道。

```java
public boolean isActive()
```
返回是否激活。**任何模块逻辑判断都应在入口处检查 `isActive()`**。

```java
public String getInfoString()
```
返回在 GUI 模块列表右侧显示的一行状态信息（如 Speed 显示 `XX blocks/second`），默认 `null`。

```java
public WWidget getWidget(GuiTheme theme)
```
自定义 GUI 顶层控件，默认 `null`。

```java
public CompoundTag toTag()
public Module fromTag(CompoundTag tag)
```
配置序列化/反序列化（保存 name/keybind/设置/active 等）。`serialize == false` 时 `toTag()` 返回 `null`。

```java
public boolean equals(Object o)
public int hashCode()
public int compareTo(@NotNull Module o)
```
按 `name` 判等/哈希/排序。

### 相关类
`Modules`、`Category`、`Categories`、`Settings`、`SettingGroup`、`MeteorAddon`。

### 26.1.2 注意事项
- 26.1.2 中**没有** `ModuleList` / `ModuleGroup` 这两个类。模块按分类分组存储于 `Modules` 内部的 `Map<Category, List<Module>> groups`，通过 `getGroup(Category)` 访问。
- 模块名含空格会在日志告警 `"Module '{}' contains invalid characters..."`，请用 `-` 连接。

---

## 2. Modules（模块管理器）

### 类名
`Modules`

### 包名 / 源码路径
`meteordevelopment.meteorclient.systems.modules`／`meteordevelopment/meteorclient/systems/modules/Modules.java`

### 继承
```java
public class Modules extends System<Modules>
```

### 关键方法（真实签名 + 中文用途）

```java
public static Modules get()
```
获取单例（`Systems.get(Modules.class)`）。addon 里访问模块管理器的入口。

```java
public <T extends Module> T get(Class<T> klass)
```
按类精确获取模块实例（返回 `@Nullable`）。**这是 addon 最常用接口**：`Modules.get().get(KillAura.class)`。

```java
public <T extends Module> Optional<T> getOptional(Class<T> klass)
```
同上的 Optional 包装。

```java
public Module get(String name)
```
按名字（忽略大小写）获取模块，不存在返回 `null`。

```java
public boolean isActive(Class<? extends Module> klass)
```
判断某模块是否激活（内部安全判空）。

```java
public List<Module> getGroup(Category category)
```
按分类取该分类下所有模块。

```java
public Collection<Module> getAll()
```
所有模块实例集合。

```java
public int getCount()
```
模块总数。

```java
public List<Module> getActive()
```
当前激活的模块列表。

```java
public List<Tuple<Module, String>> searchTitles(String text)
public Set<Module> searchSettingTitles(String text)
```
标题 / 设置项标题模糊搜索（Levenshtein）。

```java
public static void registerCategory(Category category)
```
注册自定义分类。**只能在回调节点 `MeteorAddon.onRegisterCategories()` 中调用**（`Categories.REGISTERING` 为 true），否则抛 `RuntimeException`。

```java
public static Iterable<Category> loopCategories()
```
遍历所有已注册分类。

```java
public void add(Module module)
```
注册一个模块实例（校验分类已注册、按 name 去重、建立 groups 映射、注册颜色设置）。addon 在 `onInitialize()` 里用 `Modules.get().add(new MyModule())` 注册模块。

```java
public void disableAll()
```
停用全部模块。

其余（绑定相关）：
```java
public void setModuleToBind(Module moduleToBind)
public void awaitKeyRelease()
public boolean isBinding()
public void sortModules()
```

### 26.1.2 注意事项
- `add(Module)` 内部要求 `module.category` 已被注册，否则抛异常——请务必先在 `onRegisterCategories()` 里 `Modules.registerCategory(...)`。
- `Modules.get().getActive()` 返回的是内部 `active` 列表引用。

---

## 3. Category（模块分类）

### 类名 / 包名 / 源码路径
`Category`／`meteordevelopment.meteorclient.systems.modules`／`meteordevelopment/meteorclient/systems/modules/Category.java`

### 关键字段
| 字段 | 类型 | 中文用途 |
|---|---|---|
| `name` | `public final String` | 分类名 |
| `icon` | `public final Supplier<ItemStack>` | 分类图标（GUI 显示），空则 `ItemStack.EMPTY` |
| `nameHash` | `private final int` | name 的哈希（用于 equals/hashCode） |

### 构造 / 方法
```java
public Category(String name, Supplier<ItemStack> icon)
public Category(String name)   // icon 为 null → ItemStack.EMPTY
public String toString()        // 返回 name
```

---

## 4. Categories（预定义分类 + 注册入口）

### 类名 / 源码路径
`Categories`／`meteordevelopment/meteorclient/systems/modules/Categories.java`

### 关键字段（真实预定义分类）
```java
public static final Category Combat;
public static final Category Player;
public static final Category Movement;
public static final Category Render;
public static final Category World;
public static final Category Misc;
```
`public static boolean REGISTERING;` 用于标记「分类注册窗口」。

```java
public static void init()
```
内部 `REGISTERING = true` → `Modules.registerCategory(...)` 注册 6 个内置分类 → 遍历 `AddonManager.ADDONS` 调用每个 addon 的 `onRegisterCategories()` → `REGISTERING = false`。

---

## 5. 真实 Module 子类骨架

摘自真实模块 `meteordevelopment/meteorclient/systems/modules/render/Fullbright.java`（已按源码简化，展示 Settings 用法）：

```java
package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Fullbright extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The mode to use for Fullbright.")
        .defaultValue(Mode.Gamma)
        .onChanged(mode -> { if (isActive()) { /* ... */ } })
        .build()
    );

    public Fullbright() {
        super(Categories.Render, "fullbright", "Lights up your world!");
    }

    @Override
    public void onActivate() { /* ... */ }

    @Override
    public void onDeactivate() { /* ... */ }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mode.get().equals(Mode.Potion)) return;
        // ...
    }

    public enum Mode { Gamma, Potion, Luminance }
}
```

最小监听式模块骨架（摘自 `systems/modules/player/AutoRespawn.java`）：
```java
public class AutoRespawn extends Module {
    public AutoRespawn() {
        super(Categories.Player, "auto-respawn", "Automatically respawns after death.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onOpenScreenEvent(OpenScreenEvent event) {
        if (!(event.screen instanceof DeathScreen)) return;
        Modules.get().get(WaypointsModule.class).addDeath(mc.player.position());
        mc.player.respawn();
        event.cancel();
    }
}
```

---

## 常见坑
1. **模块名含空格**导致命令不兼容，务必用 `-` 连接。
2. 直接改 `active` 私有字段无效——用 `toggle()`/`enable()`/`disable()`。
3. addon 新增分类必须在 `onRegisterCategories()` 里 `Modules.registerCategory(...)`，否则 `add(Module)` 抛异常。
4. `onActivate()`/`onDeactivate()` 默认只在进入世界后才真正执行（受 `runInMainMenu` 影响）；若逻辑必须主菜单也生效需 `runInMainMenu = true`。
5. 模块头里 `info/warning/error` 会自动加本模块前缀；`getInfoString()` 用于 GUI 状态行，注意返回 `null` 表示不显示。
6. `autoSubscribe` 默认 `true`，模块启用时自动 `subscribe(this)`；若用外部方式订阅要避免重复。