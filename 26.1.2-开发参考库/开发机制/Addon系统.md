# Addon 系统

> Meteor 的「插件」叫 addon：一个实现了 `MeteorAddon` 抽象类、并通过 Fabric `"meteor"` entrypoint 暴露出来的 Mod。`AddonManager` 负责在游戏启动时收集所有 addon，`MeteorClient.onInitializeClient` 按固定顺序驱动它们的分类注册、初始化、事件工厂注册。

## 概述

核心三件套（`meteordevelopment.meteorclient.addons` 包）：

- `MeteorAddon`（抽象类）：addon 的老祖宗，定义 `onInitialize` / `onRegisterCategories` / `getPackage` 等生命周期。
- `AddonManager`：静态收集器 + 初始化器，把 Fabric `"meteor"` entrypoint 全部实例化并塞进 `ADDONS` 列表。
- `GithubRepo`（record）：更新检查用的仓库描述。

---

## 1. MeteorAddon 抽象类

```java
public abstract class MeteorAddon {
    /** 由 fabric.mod.json 自动注入 */
    public String name;
    /** 由 fabric.mod.json 自动注入 */
    public String[] authors;
    /** 由 fabric.mod.json 的 meteor-client:color 属性注入 */
    public final Color color = new Color(255, 255, 255);

    public abstract void onInitialize();                 // 主初始化：注册模块/命令/HUD
    public void onRegisterCategories() {}                // 注册模块分类（Category）
    public abstract String getPackage();                 // addon 根包名
    public String getWebsite() { return null; }          // 官网（可选）
    public GithubRepo getRepo() { return null; }         // 仓库（可选，更新检查用）
    public String getCommit() { return null; }           // 提交号（可选）
}
```

关键点：`name`/`authors`/`color` **不是自己赋值**，由 `AddonManager` 从 `fabric.mod.json` 的 `metadata`（`name`、`authors`、自定义值 `meteor-client:color`）填充。addon 只要在 mod.json 里声明即可。

## 2. AddonManager.init()

```java
public static final List<MeteorAddon> ADDONS = new ArrayList<>();

public static void init() {
    // (1) Meteor 自身的「伪 addon」（getPackage 返回 meteordevelopment.meteorclient）
    MeteorClient.ADDON = new MeteorAddon() { ... };
    // 从 Meteor mod 的 metadata 填 name/authors/color，并 ADDONS.add(MeteorClient.ADDON)

    // (2) 用户 addon：扫 Fabric "meteor" entrypoint
    for (EntrypointContainer<MeteorAddon> entrypoint
         : FabricLoader.getInstance().getEntrypointContainers("meteor", MeteorAddon.class)) {
        ModMetadata metadata = entrypoint.getProvider().getMetadata();
        MeteorAddon addon = entrypoint.getEntrypoint();  // 可能抛异常 → 包装 RuntimeException
        addon.name = metadata.getName();
        if (metadata.getAuthors().isEmpty())
            throw new RuntimeException("Addon \"%s\" requires at least 1 author ...");
        addon.authors = new String[metadata.getAuthors().size()];
        if (metadata.containsCustomValue(MeteorClient.MOD_ID + ":color"))
            addon.color.parse(metadata.getCustomValue(MeteorClient.MOD_ID + ":color").getAsString());
        // 填充 authors 数组 ...
        ADDONS.add(addon);
    }
}
```

- Fabric `"meteor"` entrypoint 是 addon 的「入口契约」，对应 `fabric.mod.json` 里的 `"entrypoints": { "meteor": [...] }`。
- **至少一个 author，否则启动即抛异常**（源码硬性校验）。
- `MeteorClient.MOD_ID = "meteor-client"`，自定义颜色 key 是 `"meteor-client:color"`。

## 3. 初始化顺序（MeteorClient.onInitializeClient）

`MeteorClient` 实现 `ClientModInitializer`，`onInitializeClient()` 顺序（源码证实）：

```
AddonManager.init()                         // 收集所有 addon
↓ AddonManager.ADDONS.forEach(addon -> EVENT_BUS.registerLambdaFactory(addon.getPackage(), ...))   // 注册 lambda 事件工厂
↓ ReflectInit.registerPackages()            // 为每个 addon 包建 Reflections 扫描器（MethodsAnnotated）
↓ ReflectInit.init(PreInit.class)           // 跑所有 @PreInit 静态方法（含 MeteorExecutor/BlockUtils 等）
↓ Categories.init()                          // 注册分类（内部回调 addon.onRegisterCategories）
↓ Systems.init()                             // 加载 Systems（含 Modules 的 init → add 模块）
↓ EVENT_BUS.subscribe(this)                  // MeteorClient 自身订阅事件
↓ AddonManager.ADDONS.forEach(MeteorAddon::onInitialize)   // ★ 逐个 addon 主初始化（注册模块/命令/HUD）
↓ Modules.get().sortModules()                // addon 加完模块后统一按 title 排序
↓ Systems.load()                             // 加载配置/序列化
↓ ReflectInit.init(PostInit.class)           // 跑所有 @PostInit
↓ Runtime.addShutdownHook(保存)                // 退出时 Systems.save() + GuiThemes.save()
```

**顺序要点**：`onRegisterCategories`（分类）早于 `onInitialize`（业务）；`Systems.init` 里 Meteor 自己的模块已 `add` 完，addon 的 `onInitialize` 随后用 `Modules.get().add(...)` 追加自己的模块，最后统一 `sortModules()`。分类必须先在 `onRegisterCategories` 里 `Modules.registerCategory(...)`，否则 `Modules.add` 会抛「category not registered」异常（见「Module注册机制」）。

## 4. GithubRepo

```java
public record GithubRepo(String owner, String name, String branch, @Nullable String accessToken) {
    public GithubRepo(String owner, String name, String accessToken) { this(owner, name, "master", accessToken); }
    public GithubRepo(String owner, String name) { this(owner, name, "master", null); }
    public String getOwnerName() { return owner + "/" + name; }
    public void authenticate(Http.Request request) {
        if (accessToken != null && !accessToken.isBlank()) request.bearer(accessToken);
        else { /* 读环境变量 meteor.github.authorization，非空则 bearer */ }
    }
}
```

供更新检查/下载用，`authenticate` 给 `Http.Request` 塞 `Bearer` 头。

## 运行链路

Fabric 启动 → `MeteorClient.onInitializeClient` → `AddonManager.init` 从 `"meteor"` entrypoint 实例化所有 `MeteorAddon` → 依次注册事件工厂 → `@PreInit` → `Categories.init`（触发 `onRegisterCategories`）→ `Systems.init` → 逐个 `addon.onInitialize`（各自 `Modules.get().add` / `Commands.add` / `Hud.get().register`）→ `sortModules` → `Systems.load` → `@PostInit`。

## Addon 用法 / 介入点

yiyiaddon 真实入口（`src/main/java/com/example/addon/core/AddonTemplate.java`，已读源码）：

```java
public class AddonTemplate extends MeteorAddon {
    public static final Category CATEGORY = new Category("...工具", () -> DisplayItemUtils.toStack(Items.WRITABLE_BOOK));
    public static final HudGroup HUD_GROUP = new HudGroup("示例");

    @Override public void onInitialize() {
        Modules.get().add(new FlightBypass());          // 注册模块
        Commands.add(new CommandExample());             // 注册命令
        Hud.get().register(HudExample.INFO);            // 注册 HUD
        YiyiaddonHeartbeatService.start();              // 启动后台服务
    }
    @Override public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }  // 分类
    @Override public String getPackage() { return "com.example.addon"; }                  // 包名
    @Override public GithubRepo getRepo() { return new GithubRepo("MeteorDevelopment", "meteor-addon-template"); }
}
```

`getPackage()` 决定的包会被 `ReflectInit` 扫描（`@PreInit/@PostInit` 依赖），也是 `addon.onInitialize` 里注册模块能自动 `Modules.registerCategory` 但分类不在此包时的查找依据（mixin 里不涉及）。

## 常见坑

1. **`onRegisterCategories` 必须注册分类，否则 `Modules.add` 抛 `RuntimeException`**：分类注册被 `Categories.REGISTERING` 布尔锁保护，只允许在该回调期间调用。
2. **`getPackage()` 不能为空/错拼**：`ReflectInit.registerPackages` 会 `new Reflections(pkg, Scanners.MethodsAnnotated)`，包不存在则 `@PreInit` 方法不被扫描执行。
3. **fabric.mod.json 缺 author 直接崩**：`AddonManager` 明确「requires at least 1 author」。
4. **`color` 用 `final Color` 且走 `color.parse(...)`**：mod.json 里的 `meteor-client:color` 必须是可解析颜色串，否则解析异常。
5. **不实现 `onRegisterCategories` 时不要自己 `Modules.registerCategory`**（会因 `REGISTERING=false` 抛异常）。
6. **事件订阅别放 `onInitialize` 外自建**：模块用 `autoSubscribe`，addon 级事件处理应在 `onInitialize` 里显式 `MeteorClient.EVENT_BUS.subscribe`。

## 源码依据

- `meteordevelopment/meteorclient/addons/MeteorAddon.java` —— 抽象生命周期与自动注入字段。
- `meteordevelopment/meteorclient/addons/AddonManager.java` —— entrypoint 收集、name/authors/color 注入、伪 addon 创建。
- `meteordevelopment/meteorclient/addons/GithubRepo.java` —— record 与 `authenticate`。
- `meteordevelopment/meteorclient/MeteorClient.java` —— `onInitializeClient` 完整初始化顺序（AddonManager→PreInit→Categories→Systems→onInitialize→sortModules→load→PostInit）。
- `meteordevelopment/meteorclient/systems/modules/Categories.java` —— `REGISTERING` 锁与 `onRegisterCategories` 回调时机。
- `meteordevelopment/meteorclient/utils/ReflectInit.java` —— `registerPackages`/`init` 按包名扫描注解方法。
- `src/main/java/com/example/addon/core/AddonTemplate.java`（addon）—— `getPackage`/`onRegisterCategories`/`onInitialize` 真实用法。