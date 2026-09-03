# Addon

> 源码依据：Meteor原始源码/meteordevelopment/meteorclient/addons/... Meteor Client 26.1.2-SNAPSHOT

Meteor 通过 **Fabric entrypoint** 加载第三方 addon。addon 主类继承 `MeteorAddon`，在 `fabric.mod.json` 的 `entrypoints.meteor` 注册。本文整理 `MeteorAddon` / `GithubRepo` / `AddonManager` 及加载机制，并对照本项目实际 addon 模板。

---

## MeteorAddon

### 类名
`meteordevelopment.meteorclient.addons.MeteorAddon`

### 包名
`meteordevelopment.meteorclient.addons`

### 源码路径
`addons/MeteorAddon.java`

### 继承与接口
`public abstract class MeteorAddon`

### 关键字段（真实，全部 public）
| 字段名 | 类型 | 中文用途 |
| --- | --- | --- |
| `name` | `public String` | addon 名称，**由 `AddonManager` 从 fabric.mod.json 自动注入** |
| `authors` | `public String[]` | 作者数组，**自动注入** |
| `color` | `public final Color` | addon 主题色，从 fabric.mod.json 的 `meteor-client:color` 解析，默认白 |

### 关键方法（真实签名照抄）

```java
public abstract void onInitialize();

public void onRegisterCategories() {}      // 默认空实现

public abstract String getPackage();

public String getWebsite() { return null; }

public GithubRepo getRepo() { return null; }

public String getCommit() { return null; }
```
- `onInitialize()`：**必须实现**。在这里注册模块、命令、HUD 元素等。
- `onRegisterCategories()`：可选覆写。在这里调用 `Modules.registerCategory(...)` 注册自定义分类。
- `getPackage()`：**必须实现**。返回 addon 根包名（用于事件总线 lambda 工厂与资源定位）。
- `getWebsite()` / `getRepo()` / `getCommit()`：可选，供更新检查/信息展示。

### 26.1.2 注意事项
- **没有 `getAuthors()` 抽象方法** —— 作者信息是 `public String[] authors` 字段，由 `AddonManager` 注入。旧版 `getAuthors()` 记忆有误。
- 主类推荐 `extends MeteorAddon`，但注意 `AddonManager` 的要求（见下）。

---

## GithubRepo

### 类名
`meteordevelopment.meteorclient.addons.GithubRepo`

### 包名
`meteordevelopment.meteorclient.addons`

### 源码路径
`addons/GithubRepo.java`

### 继承与接口
`public record GithubRepo(String owner, String name, String branch, @Nullable String accessToken)`

### 构造器
```java
public GithubRepo(String owner, String name, String branch, @Nullable String accessToken)  // record 主构造
public GithubRepo(String owner, String name, @Nullable String accessToken)                 // branch 默认 "master"
public GithubRepo(String owner, String name)                                               // branch 默认 "master"，token 默认 null
```

### 关键方法
```java
public String getOwnerName()          // "owner/name"
public void authenticate(Http.Request request)   // 若带 token 则加 Bearer，否则读环境变量 meteor.github.authorization
```

---

## AddonManager

### 类名
`meteordevelopment.meteorclient.addons.AddonManager`

### 包名
`meteordevelopment.meteorclient.addons`

### 源码路径
`addons/AddonManager.java`

### 关键字段
`public static final List<MeteorAddon> ADDONS`

### 加载机制
```java
public static void init()
```
1. 先创建一个「Meteor 伪 addon」写入 `MeteorClient.ADDON`（`getPackage()` 返回 `meteordevelopment.meteorclient`）。
2. 读取 `fabric.mod.json` 的 `name` / `authors` / 自定义值 `meteor-client:color`，注入到每个 addon。
3. 通过 Fabric Loader 扫描 entrypoint：
   - `FabricLoader.getInstance().getEntrypointContainers("meteor", MeteorAddon.class)`
4. 要求 **至少 1 个作者**：`authors` 为空抛 `RuntimeException("Addon \"...\" requires at least 1 author ...")`。

### 调用链（`MeteorClient.onInitializeClient`）
`AddonManager.init()` → 为每个 addon 注册 event bus lambda 工厂 → 反射初始化 → `AddonManager.ADDONS.forEach(MeteorAddon::onInitialize)` → `Modules.get().sortModules()`。

---

## addon 注册机制（fabric.mod.json 必须项）

本项目实际 `fabric.mod.json`（`d:\mcaddon\26.1.2\src\main\resources\fabric.mod.json`）关键片段：

```json
{
  "id": "yiyiaddon",
  "version": "${version}",
  "name": "yiyiaddon",
  "authors": [ "yiyijia" ],
  "environment": "client",
  "entrypoints": {
    "meteor": [ "com.example.addon.core.AddonTemplate" ]
  },
  "mixins": [ "addon-template.mixins.json" ],
  "custom": {
    "meteor-client:color": "225,25,25",
    "modmenu": { "parent": { "id": "meteor-client" } }
  },
  "depends": {
    "java": ">=${jdk_version}",
    "minecraft": "${minecraft_version}",
    "meteor-client": "*"
  }
}
```

要点：
1. `entrypoints.meteor`：数组里放 addon 主类全限定名（`extends MeteorAddon`）。
2. `authors`：`AddonManager` 强制要求至少 1 个。
3. `custom["meteor-client:color"]`：`"r,g,b"` 字符串，注入 `MeteorAddon.color`。
4. `depends["meteor-client"]: "*"`：声明对 Meteor 的依赖。
5. `mixins`：指向本 addon 的 mixin 配置 json。
6. `environment: "client"`：addon 是纯客户端。

---

## 本项目实际用法（AddonTemplate.java 对照）

文件：`d:\mcaddon\26.1.2\src\main\java\com\example\addon\core\AddonTemplate.java`（已用源码核实存在）。

```java
public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("§c§lyiyiaddon §a§l工具", () -> DisplayItemUtils.toStack(Items.WRITABLE_BOOK));
    public static final HudGroup HUD_GROUP = new HudGroup("示例");

    @Override
    public void onInitialize() {
        LOG.info("Initializing yiyiaddon");
        Modules.get().add(new FlightBypass());
        Commands.add(new WKCommand());
        Hud.get().register(HudExample.INFO);
        // ...
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        // ...
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
```

对应 API（均在源码中核实存在）：
- `Modules.get()` / `Modules.get().add(Module module)` / `Modules.registerCategory(Category category)`（`systems/modules/Modules.java`）
- `Commands.add(Command command)`（`commands/Commands.java`）
- `Hud.get().register(HudElementInfo<?> info)`（`systems/hud/Hud.java`）
- `new Category(String name, Function<ItemStack, ?> icon)`（`systems/modules/Category.java`）
- `new HudGroup(String title)`（`systems/hud/HudGroup.java`）

---

## 相关类
- `meteordevelopment.meteorclient.MeteorClient`（`ADDON`、`mc`、`EVENT_BUS`、`FOLDER`）
- `meteordevelopment.meteorclient.systems.modules.Modules`
- `meteordevelopment.meteorclient.systems.modules.Category`

---

## 常见坑
1. addon 的 `getPackage()` 必须返回**可被 event bus lambda 工厂定位的包**；混淆/收缩后包名不一致会导致事件订阅失败。
2. `fabric.mod.json` 缺 `authors` 会直接抛异常，别写空数组。
3. `custom["meteor-client:color"]` 格式是 `"r,g,b"`（十进制逗号分隔，无 `#`）。
4. `onInitialize()` 在 `Modules.get().sortModules()` 之前执行，所以分类排序在 all addons 初始化后才统一。
5. `onRegisterCategories()` 默认空实现，但注册分类必须覆写它并在其中调 `Modules.registerCategory(...)`。
6. addon 不要重复调用 `AddonManager.init()`；它由 `MeteorClient.onInitializeClient` 自动调用一次。