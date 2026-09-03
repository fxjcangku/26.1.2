# Module 注册机制

> 一个 `Module` 子类不会自动出现——必须经 `Modules.add(...)` 注册，且其 `Category` 必须先经 `Categories.init()` 注册（否则 `add` 直接抛异常）。这套「分类先注册、模块后 add、addon 之后统一排序」的链条，被 `MeteorClient.onInitializeClient` 的固定启动顺序串起来。

## 概述

- `Category`：模块分类（构造 `new Category(String name, Supplier<ItemStack> icon)`，图标用 `DisplayItemUtils.toStack(Items.XXX)`）。
- `Categories`：内置 6 类（Combat/Player/Movement/Render/World/Misc）+ `REGISTERING` 锁。
- `Modules`：模块注册表（`moduleInstances` / `groups` / `active`）。
- `ReflectInit`：`@PreInit/@PostInit` 反射调度器。

---

## 1. Category 与 Categories

```java
public class Categories {
    public static final Category Combat = new Category("Combat", () -> DisplayItemUtils.toStack(Items.GOLDEN_SWORD));
    public static final Category Player  = new Category("Player", () -> DisplayItemUtils.toStack(Items.ARMOR_STAND));
    public static final Category Movement = new Category("Movement", ...);
    public static final Category Render = new Category("Render", ...);
    public static final Category World = new Category("World", ...);
    public static final Category Misc = new Category("Misc", ...);

    public static boolean REGISTERING;

    public static void init() {
        REGISTERING = true;
        Modules.registerCategory(Combat);   // ... 6 个内置分类
        AddonManager.ADDONS.forEach(MeteorAddon::onRegisterCategories);   // ★ addon 补充分类
        REGISTERING = false;
    }
}
```

`Categories.init()` 整个流程被 `REGISTERING` 布尔包裹：**只有在这期间 `Modules.registerCategory` 才合法**（addon 的 `onRegisterCategories` 就是被这里的 `forEach` 回调调用）。

## 2. Modules.registerCategory / Modules.add

```java
public static void registerCategory(Category category) {
    if (!Categories.REGISTERING)
        throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");
    CATEGORIES.add(category);
}

public void add(Module module) {
    // 1. 分类未注册直接抛异常
    if (!CATEGORIES.contains(module.category))
        throw new RuntimeException("Modules.addModule - Module's category was not registered.");

    // 2. 移除同名旧模块（并注销其颜色设置）
    moduleInstances.values().removeIf(...);   // 同名则 removedModule 记录、settings.unregisterColorSettings()
    getGroup(removedModule.get().category).remove(removedModule.get());

    // 3. 注册进注册表
    moduleInstances.put(module.getClass(), module);   // key 是 Class
    getGroup(module.category).add(module);

    // 4. 注册颜色设置（ColorSetting 反序列化用）
    module.settings.registerColorSettings(module);
}
```

- `moduleInstances` 是 `Map<Class<? extends Module>, Module>`（`Reference2ReferenceOpenHashMap`），也支持 `get(String name)` 按名字查（忽略大小写）、`get(Class)` 按类查。
- `add` 是幂等的「同名替换」：同 name 旧模块会被移除再插入。

## 3. 初始化顺序（MeteorClient.onInitializeClient 里与注册相关的部分）

```
Categories.init()            // 注册 Meteor 6 类 + addon 分类（onRegisterCategories）
↓
Systems.init()               // 内部 new Modules() → Modules.init() → initCombat/initPlayer/... → 每个 add(new Xxx())
↓
AddonManager.ADDONS.forEach(MeteorAddon::onInitialize)   // ★ addon 在这里 Modules.get().add(...)
↓
Modules.get().sortModules()  // 每条分类下按 title 排序
```

要点：

1. `Systems.init()` 里 `Modules.init()` 先注册 Meteor 全部内置模块（六类分批 `add`）。
2. addon 的 `onInitialize` 在 `Systems.init` **之后**执行，用 `Modules.get().add(...)` 追加自己的模块。
3. 全部注册完才 `sortModules()`（`Comparator.comparing(o -> o.title)`），所以 addon 加模块的先后不影响 GUI 排序。

## 4. Module 构造与分类归属

`Module(Category category, String name, String description, String... aliases)`——模块构造时就把 `category` 定死，`title` 由 `Utils.nameToTitle(name)` 推导，`addon` 字段按类名包前缀匹配所属 addon（Meteor 源码里 `Module` 构造阶段推导，非 addon 模块为 null）。同一模块注册到哪个分类由构造传入的 `Category` 实例决定。

`Systems`（`meteordevelopment.meteorclient.systems.Systems`）是「系统」总注册表：`Modules`/`Config`/`Hud` 等都是 `System`。`ReflectInit.init(PostInit.class)` 阶段如 `Commands.init`（`@PostInit(dependencies = PathManagers.class)`）会依赖序执行（见「Command注册机制」）。

## 运行链路

Fabric 启动 → `MeteorClient.onInitializeClient` → `Categories.init`（开锁→注册分类→回调 addon `onRegisterCategories`→上锁）→ `Systems.init`（`Modules.init` 批量 `add` 内置模块）→ `addon.onInitialize`（`Modules.get().add` 自己的模块）→ `sortModules` → `Systems.load`（反序列化绑定/启停）→ 玩家进世界后 `Modules.onGameJoined` 对 `runInMainMenu=false` 的活跃模块 `subscribe + onActivate`。

## Addon 用法 / 介入点

```java
// 1. 定义分类（静态常量 + 图标）
public static final Category CATEGORY = new Category("工具", () -> DisplayItemUtils.toStack(Items.WRITABLE_BOOK));

// 2. 在 onRegisterCategories 里注册（必须）
@Override public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }

// 3. 在 onInitialize 里 add 模块
Modules.get().add(new MyModule());       // MyModule extends Module，构造传 CATEGORY

// 4. （可选）立即启用
Modules.get().add(new MyModule());
Modules.get().get(MyModule.class).enable();
```

真实用法见 `src/main/java/com/example/addon/core/AddonTemplate.java`（4 个 Category + 批量 `Modules.get().add(...)` + `enable()`）。

## 常见坑

1. **分类没注册就 add**：`Modules.addModule - Module's category was not registered.`——`Module` 构造传的 `Category` 必须先在 `onRegisterCategories` 里 `registerCategory`，且该回调必须在 `Categories.init` 的 `REGISTERING=true` 期间执行。
2. **在 `onInitialize` 里 `registerCategory` 会崩**：`REGISTERING` 已置 false。分类只能放 `onRegisterCategories`。
3. **同名模块被替换**：`add` 会移除旧同名模块，重复注册设计成「覆盖」；要并存必须用不同 `name`。
4. **`get(Class)` 依赖类名 map key**：`add` 以 `module.getClass()` 为 key，若用匿名子类/代理，`get(SomeClass)` 可能取不到。
5. **`Systems.init` 前 `Modules.get()` 为 null**：addon 在 `onInitialize`（晚于 `Systems.init`）拿得到，但在 `onRegisterCategories`（早于）拿 `Modules.get()` 只能用 `registerCategory` 静态方法、别碰 `get()`。
6. **排序在 add 之后**：GUI 顺序看 `sortModules` 的 title 字典序，不看你 `add` 的先后。

## 源码依据

- `meteordevelopment/meteorclient/systems/modules/Modules.java` —— `registerCategory`、`add`、`initCombat/Player/...`、`sortModules`、`get`/`getGroup`、`moduleInstances/groups/active`。
- `meteordevelopment/meteorclient/systems/modules/Categories.java` —— 内置 6 类、`REGISTERING` 锁、`init()` 回调 addon。
- `meteordevelopment/meteorclient/systems/modules/Module.java` —— 构造（category/name/title 推导）、`toggle` 生命周期（与「Module生命周期」一致）。
- `meteordevelopment/meteorclient/systems/Systems.java` —— `Systems.init/load`（`Modules` 系统装载）。
- `meteordevelopment/meteorclient/MeteorClient.java` —— `onInitializeClient` 中 Categories→Systems→onInitialize→sortModules 顺序。
- `meteordevelopment/meteorclient/addons/AddonManager.java` —— `onRegisterCategories`/`onInitialize` 的被调用点。
- `src/main/java/com/example/addon/core/AddonTemplate.java`（addon）—— 分类 + `Modules.get().add` 真实用法。