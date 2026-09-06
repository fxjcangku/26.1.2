# Setting 系统

> 注意：旧版 `VisSetting` 已移除，可见性统一切到接口 `IVisible`。设置构造参数顺序以源码为准（visible 是最后一个参数，且其前还有一个 `onModuleActivated`）。

## 概述

设置系统的三层结构：

- `Setting<T>`：单个可序列化设置项（抽象基类，`IntSetting`/`BoolSetting`/`EnumSetting`/`DoubleSetting`/`StringSetting`/`KeybindSetting`/`ColorSetting` 等 30+ 子类）。
- `SettingGroup`：一组 `Setting`（如 "General" / "Range"）。
- `Settings`：属于某个模块的容器，持有一组 `SettingGroup`，负责 `toTag`/`fromTag`、颜色注册、GUI tick。

模块持有 `public final Settings settings = new Settings()`，GUI 设置屏即遍历 `module.settings` 渲染。

---

## 1. SettingGroup.create / register 真实链路

`Settings` 没有 `register` 方法，创建组的方法是 `createGroup`：

```java
public SettingGroup createGroup(String name, boolean expanded)
public SettingGroup createGroup(String name)   // 等价 createGroup(name, true)
public SettingGroup getDefaultGroup()          // 懒创建 "General"
```

实时链路：`SettingGroup` 构造函数是包私有 `SettingGroup(String name, boolean sectionExpanded)`，只能经 `Settings.createGroup` 创建。组加入 `Settings.groups`（`ArrayList`）。`SettingGroup.add(Setting)` 把设置追加进组内 `settings` 列表并返回该 setting —— addon 常见写法是链式：

```java
public final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
    .name("enabled").description("...").defaultValue(true).build());
```

## 2. Setting 构造（真实参数序）

```java
public Setting(String name, String description, T defaultValue,
    Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible)
```

**源码事实**：参数顺序是 `name, description, defaultValue, onChanged, onModuleActivated, visible`。`visible` 是最后一个参数（`IVisible`），其前面还有一个 `onModuleActivated`（模块激活时的回调）。旧版记忆/多数教程把 `visible` 放 `onChanged` 后面、且没有 `onModuleActivated`，已过时。

- 构造时调用 `resetImpl()` 把 `value = defaultValue`。
- `title = Utils.nameToTitle(name)`。

行为方法：

```java
public T get()                       // 读当前值
public boolean set(T value)          // 校验 isValueValid 后赋值并 onChanged()，失败返回 false
public void reset()                  // resetImpl + onChanged
public boolean parse(String str)     // 命令/字符串解析赋值
public boolean wasChanged()          // 当前值 != defaultValue
public void onChanged()              // 触发 Consumer<T> onChanged
public void onActivated()            // 触发 Consumer<Setting<T>> onModuleActivated
public boolean isVisible()           // visible == null || visible.isVisible()
protected abstract void save/load   // NBT 序列化
```

`isVisible()` 在 `visible == null` 时恒为可见 —— 即「无条件可见」用 null 即可。

## 3. Settings 中 group 与 module 的绑定

`Settings` 本身不直接持有 module 引用；模块绑定发生在 `Modules.add(module)` 里调用 `module.settings.registerColorSettings(module)`：

```java
public void registerColorSettings(Module module) {
    for (SettingGroup group : this)
        for (Setting<?> setting : group) {
            setting.module = module;                  // 每个 setting 反向持 module
            if (setting instanceof ColorSetting)      RainbowColors.addSetting(...);
            else if (setting instanceof ColorListSetting) RainbowColors.addSettingList(...);
        }
}
```

- `unregisterColorSettings()` 对称移除 RainbowColors 注册（`Modules.add` 移除同名旧模块时调用）。
- `setting.module` 用于 GUI/命令上下文知道设置属于哪个模块。

`settings.onActivated()`（模块 toggle 时调用）遍历所有 group 的所有 setting 调 `setting.onActivated()`。

## 4. SettingBuilder 链式方法（addon 常用）

`Setting` 内嵌 `SettingBuilder<B, V, S>`：

```java
public B name(String) / description(String) / defaultValue(V)
public B visible(IVisible) / onChanged(Consumer<V>) / onModuleActivated(Consumer<Setting<V>>)
public abstract S build();
```

真实子类（节选）：`BoolSetting`、`IntSetting`、`DoubleSetting`、`StringSetting`、`EnumSetting`、`KeybindSetting`、`ColorSetting`、`ItemSetting`、`BlockSetting`、`PotionSetting`、`FileSetting`、`GenericSetting`、`ProvidedStringSetting`、`RandomStringSetting`（各 list 变体：`BlockListSetting`/`ItemListSetting`/`EntityTypeListSetting`/`PacketListSetting` 等）。`IVisible` 只有一个方法 `boolean isVisible()`。

## 5. serialize / deserialize（Tag 类型映射）

- `Setting.toTag()`：`CompoundTag` 里 `putString("name", name)`，再 `save(tag)` 写具体值（子类 `save` 决定键值类型）。
- `Setting.fromTag(CompoundTag)`：`load(tag)` 后立即 `onChanged()`。
- `SettingGroup.toTag()`：写 `name`、`sectionExpanded`，只把 `wasChanged()` 的 setting 写进 `ListTag("settings")`。
- `SettingGroup.fromTag()`：读 `sectionExpanded`，遍历 settings 列表按 name 匹配后 `setting.fromTag`。
- `Settings.toTag()`：只把 `wasChanged()` 的 group 写进 `ListTag("groups")`。
- `Settings.fromTag()`：**先 `reset()` 全部重置**，再按 name 恢复已改项。

**「只存改动项」是核心策略**：默认值不落盘，减小配置体积、兼容字段增删。`Settings.reset()` 遍历所有 setting `reset()` 并 `invalidate()`。

## 6. GUI 绑定（settings 屏）

`Settings.tick(WContainer settings, GuiTheme theme)` 是设置屏每帧刷新入口：

```java
public void tick(WContainer settings, GuiTheme theme) {
    if (settings == null) return;
    for (SettingGroup group : groups)
        for (Setting<?> setting : group) {
            boolean visible = setting.isVisible();
            if (visible != setting.lastWasVisible) invalidate();  // 可见性变化才重建
            setting.lastWasVisible = visible;
        }
    if (invalidate) { settings.clear(); settings.add(theme.settings(this)).expandX(); invalidate=false; }
}
```

即：可见性（`IVisible`）改变时触发 `invalidate`，重建整个设置面板控件树。`theme.settings(this)` 由各 GUI 主题负责把 `Settings` 渲染成 `WContainer`（含分组折叠、`sectionExpanded` 持久化）。

---

## Addon 用法 / 介入点

```java
public final Settings sg = settings.createGroup("Range");   // 或 settings.getDefaultGroup()
public final Setting<Boolean> enabled = sg.add(new BoolSetting.Builder()
    .name("enabled").description("是否启用").defaultValue(true).build());
public final Setting<Integer> radius = sg.add(new IntSetting.Builder()
    .name("radius").description("半径").defaultValue(5).min(1).max(16).visible(() -> radius.get() > 0).build());
```

- `visible(IVisible)` 传 lambda `() -> ...`（`IVisible` 是函数式接口）。
- 需要模块激活时刷新用 `onModuleActivated(s -> /* s.get() */)`。
- 自定义设置：继承 `Setting<T>` 并实现 `parseImpl/isValueValid/save/load`（或看 addon `ItemQuantitySetting`/`ItemTargetSetting`/`ContainerTypeSetting`/`EnchantmentSelectSetting` 的实现，均走 `Setting.register` 式扩展）。

## 常见坑

1. **参数顺序记错**：`visible` 是第 6 个参数，第 5 个是 `onModuleActivated`；用 Builder 链式则无此问题（推荐）。
2. **`set` 不生效**：`isValueValid` 校验失败静默返回 false；边界值看各子类 min/max。
3. **`wasChanged` 才落盘**：默认值永远不会写进 nbt；改动后 `reset()` 即恢复默认。
4. **可见性缓存**：`visible` 每次 tick 都重新求值，返回 true/false 频繁抖动会导致反复重建控件（性能）。
5. **重名 add 覆盖**：同名 setting 在 group 内可 get 到，但 add 不会去重；组别乱了会导致存档读不回去。

## 源码依据

- `meteordevelopment/meteorclient/settings/Setting.java` —— 构造参数序、字段、set/reset/parse/onChanged/onActivated/isVisible、SettingBuilder。
- `meteordevelopment/meteorclient/settings/SettingGroup.java` —— 私有构造、add/get/iterator、toTag/fromTag、wasChanged。
- `meteordevelopment/meteorclient/settings/Settings.java` —— createGroup/getDefaultGroup、register/unregisterColorSettings、onActivated、tick、toTag/fromTag、reset/invalidate。
- `meteordevelopment/meteorclient/settings/IVisible.java` —— `boolean isVisible()`。
- `meteordevelopment/meteorclient/systems/modules/Module.java` —— `public final Settings settings`、`settings.onActivated()`、`settings.toTag()`。
- `meteordevelopment/meteorclient/systems/modules/Modules.java` —— `add()` 里 `registerColorSettings(module)`、`unregisterColorSettings()`。
- `meteordevelopment/meteorclient/settings/` 子类目录（BoolSetting/IntSetting/EnumSetting/KeybindSetting/ColorSetting/GenericSetting 等）—— save/load/isValueValid 具体实现。
- `com/example/addon/autochest/ContainerTypeSetting.java` / `ItemQuantitySetting.java` / `itemid/ItemTargetSetting.java`（addon 扩展 Setting 的真实用法）。