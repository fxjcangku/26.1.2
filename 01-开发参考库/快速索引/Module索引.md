# Module索引

> 查询顺序提醒：先查本索引 → 命中后到 原始源码 Grep 复核 → 再看 API参考对应文档
>
> 说明：模块 = `Module` 子类，由 `Modules` 管理器统一注册/分类/启停/序列化。Meteor 侧源码相对路径相对 `Meteor原始源码\`；本项目 addon 源码在 `src/main/java/com/example/addon/`。

## 一、Meteor 模块体系

| 类名 | 中文用途 | 源码相对路径 | API参考文档 |
|------|----------|--------------|-------------|
| Module | 功能模块基类 | meteordevelopment/meteorclient/systems/modules/Module.java | Module模块.md |
| Modules | 模块管理器（单例） | meteordevelopment/meteorclient/systems/modules/Modules.java | Module模块.md |
| Category | 模块分类 | meteordevelopment/meteorclient/systems/modules/Category.java | Module模块.md |
| Categories | 预定义分类 + 注册入口 | meteordevelopment/meteorclient/systems/modules/Categories.java | Module模块.md |
| MeteorAddon | addon 主类基类 | meteordevelopment/meteorclient/addons/MeteorAddon.java | Addon.md |

## 二、Module 关键成员

| 成员 | 中文用途 | API参考文档 |
|------|----------|-------------|
| Module(Category, name, description[, aliases]) | 构造（name 无空格，- 连接） | Module模块.md |
| onActivate() / onDeactivate() | 启用/停用回调 | Module模块.md |
| toggle() / enable() / disable() | 切换/幂等启停（勿手改 active） | Module模块.md |
| isActive() | 是否激活（逻辑入口必判） | Module模块.md |
| getInfoString() | GUI 状态行（null=不显示） | Module模块.md |
| getWidget(GuiTheme) | 自定义 GUI（默认 null） | Module模块.md |
| info/warning/error(...) | 模块前缀反馈（中文经 formatMessage 覆写） | Module模块.md |
| settings | 设置容器（getDefaultGroup()） | Module模块.md |
| autoSubscribe / runInMainMenu | 自动订阅 / 主菜单运行开关 | Module模块.md |

## 三、Modules 关键方法

| 方法 | 中文用途 | API参考文档 |
|------|----------|-------------|
| Modules.get() | 获取单例 | Module模块.md |
| get(Class) | 按类取模块（@Nullable） | Module模块.md |
| getOptional(Class) | Optional 包装 | Module模块.md |
| get(String name) | 按名取模块 | Module模块.md |
| isActive(Class) | 判断是否激活 | Module模块.md |
| getAll() / getGroup(Category) / getActive() | 全量/按分类/激活列表 | Module模块.md |
| add(Module) | 注册模块（分类未注册则抛异常） | Module模块.md |
| registerCategory(Category) | 注册分类（仅 onRegisterCategories 内） | Module模块.md |

## 四、内置分类（Categories）

| 常量 | 说明 | API参考文档 |
|------|------|-------------|
| Categories.Combat | 战斗 | Module模块.md |
| Categories.Player | 玩家 | Module模块.md |
| Categories.Movement | 移动 | Module模块.md |
| Categories.Render | 渲染 | Module模块.md |
| Categories.World | 世界 | Module模块.md |
| Categories.Misc | 杂项 | Module模块.md |

## 五、本项目 addon 模块（yiyiaddon）

分类：`AddonTemplate.CATEGORY`（"§c§lyiyiaddon §a§l工具"）。源码目录 `src\main\java\com\example\addon\modules\`。

| 模块类名 | 中文用途 | 源码相对路径（addon） |
|----------|----------|----------------------|
| ThemeModule | 主题/界面样式 | com/example/addon/modules/ThemeModule.java |
| AdminDetectorModule | 管理员检测 | com/example/addon/modules/AdminDetectorModule.java |
| AutoBoneMeal | 自动骨粉 | com/example/addon/modules/AutoBoneMeal.java |
| AutoMinerModule | 自动挖矿 | com/example/addon/modules/AutoMinerModule.java |
| AutoVillagerTradeModule | 自动村民交易 | com/example/addon/modules/AutoVillagerTradeModule.java |
| BaritoneCommandGuideModule | Baritone 命令指南 | com/example/addon/modules/BaritoneCommandGuideModule.java |
| CometDisconnectModule | Comet 断连处理 | com/example/addon/modules/CometDisconnectModule.java |
| MeteorCommandGuideModule | Meteor 命令指南 | com/example/addon/modules/MeteorCommandGuideModule.java |
| UserStatsModule | 用户统计 | com/example/addon/modules/UserStatsModule.java |
| YiyiaddonTranslationModule | 中文化翻译 | com/example/addon/modules/YiyiaddonTranslationModule.java |

核心基类（`src\main\java\com\example\addon\core\`）：

| 类名 | 中文用途 | 源码相对路径（addon） |
|------|----------|----------------------|
| AddonTemplate | addon 主类（extends MeteorAddon，注册分类/模块/命令/HUD） | com/example/addon/core/AddonTemplate.java |
| YiyiaddonModule | yiyiaddon 模块基类（统一 formatMessage/开关提示/中文化） | com/example/addon/core/YiyiaddonModule.java |
| YiyiaddonRefreshable | 可刷新接口 | com/example/addon/core/YiyiaddonRefreshable.java |

## 六、addon 模块骨架（最小监听式）

```java
public class MyModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled").defaultValue(true).build());

    public MyModule() { super(Categories.Player, "my-module", "描述"); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;   // 入口判 isActive + 判空
    }
}
```

## 七、常见坑（精简）

1. 模块名含空格导致命令不兼容，务必 `-` 连接；改 `active` 私有字段无效，用 `toggle()/enable()/disable()`。
2. addon 新增分类必须在 `onRegisterCategories()` 里 `Modules.registerCategory(...)`，否则 `add(Module)` 抛异常。
3. `onActivate()/onDeactivate()` 默认仅在进入世界后真正执行（受 `runInMainMenu` 影响）。
4. `autoSubscribe` 默认 true，启用自动 `subscribe(this)`，外部订阅勿重复。
5. 注册顺序：`onInitialize()` 里 `Modules.get().add(new MyModule())` + `Commands.add(...)` + `Hud.get().register(...)`。