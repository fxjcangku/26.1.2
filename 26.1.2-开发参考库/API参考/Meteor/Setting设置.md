> 源码依据：Meteor原始源码/meteordevelopment/meteorclient/settings/…　Meteor Client 26.1.2-SNAPSHOT

# Setting 设置

Setting 体系是模块可配置能力的核心：`Settings` 容器含多个 `SettingGroup`，每个 group 内含多个 `Setting<T>`。addon 开发里最常用模式是「在模块字段上声明 `Setting<T> xxx = sg.add(new XxxSetting.Builder<>()...build())`」。

---

## 1. Settings（设置容器）

### 类名 / 包名 / 源码路径
`Settings`／`meteordevelopment.meteorclient.settings`／`meteordevelopment/meteorclient/settings/Settings.java`

### 继承
```java
public class Settings implements ISerializable<Settings>, Iterable<SettingGroup>
```

### 关键方法
```java
public void onActivated()                       // 对每个 setting 调用 onActivated()
public Setting<?> get(String name)
public <T> Setting<T> get(String name, Class<T> tClass)
public void reset()
public void invalidate()
public SettingGroup getGroup(String name)
public int sizeGroups()
public SettingGroup getDefaultGroup()           // 惰性创建名为 "General" 的默认分组
public SettingGroup createGroup(String name, boolean expanded)
public SettingGroup createGroup(String name)    // expanded=true
public void registerColorSettings(Module module)
public void unregisterColorSettings()
```

### 26.1.2 说明
- **`getDefaultGroup()` 会自动创建一个叫 `"General"` 的默认分组**。绝大多数模块只需 `settings.getDefaultGroup()` 然后 `add(...)`。
- `createGroup(name)` 创建新分组（默认展开）。分组按加入顺序排列。

---

## 2. SettingGroup（设置分组）

### 类名 / 源码路径
`SettingGroup`／`meteordevelopment/meteorclient/settings/SettingGroup.java`

### 继承
```java
public class SettingGroup implements ISerializable<SettingGroup>, Iterable<Setting<?>>
```

### 关键字段
| 字段 | 类型 | 中文用途 |
|---|---|---|
| `name` | `public final String` | 分组名 |
| `sectionExpanded` | `public boolean` | 是否展开显示 |

### 关键方法
```java
public Setting<?> get(String name)
public <T extends Setting<?>> T add(T setting)   // 添加并返回该 setting（链式字段赋值的核心）
public Setting<?> getByIndex(int index)
public boolean wasChanged()
```
> addon 标准写法：`public final Setting<Boolean> myFlag = sg.add(new BoolSetting.Builder().name("my-flag").build());`

---

## 3. Setting<T>（设置基类）

### 类名 / 源码路径
`Setting<T>`（抽象）／`meteordevelopment/meteorclient/settings/Setting.java`

### 继承
```java
public abstract class Setting<T> implements IGetter<T>, ISerializable<T>
```

### 关键字段
| 字段 | 类型 | 中文用途 |
|---|---|---|
| `name` | `public final String` | 设置名（配置 key） |
| `title` | `public final String` | 显示标题（`Utils.nameToTitle(name)` 自动生成） |
| `description` | `public final String` | 描述 |
| `visible` | `private final IVisible` | 可见性条件 |
| `defaultValue` | `protected final T` | 默认值 |
| `value` | `protected T` | 当前值 |
| `onModuleActivated` | `public final Consumer<Setting<T>>` | 模块激活时回调 |
| `onChanged` | `private final Consumer<T>` | 值变化回调 |
| `module` | `public Module` | 反向引用所属模块（注册颜色设置时写入） |
| `lastWasVisible` | `public boolean` | 上次可见状态（GUI 刷新用） |

### 关键方法
```java
public T get()                   // 取值
public boolean set(T value)      // 设值（先 isValueValid 校验，再触发 onChanged），成功返回 true
public void reset()              // 重置为默认并 onChanged
public T getDefaultValue()
public boolean parse(String str) // 命令行解析
public boolean wasChanged()
public void onChanged()
public void onActivated()
public boolean isVisible()
public Iterable<Identifier> getIdentifierSuggestions()
public List<String> getSuggestions()
public CompoundTag toTag()
public T fromTag(CompoundTag tag)
```
静态工具：
```java
@Nullable public static <T> T parseId(Registry<T> registry, String name)
```

### 26.1.2 说明（重要）
- 26.1.2 **不存在**名为 `VisSetting` 的包装类。控制可见性用的是接口 `IVisible`（`boolean isVisible()`）配合 Builder 的 `.visible(IVisible)` 方法（见下面 Builder）。旧版「用 VisSetting wrap builder」的写法在 26.1.2 已被 `.visible(() -> boolean)` 取代。

### 内部抽象 Builder
```java
public abstract static class SettingBuilder<B, V, S> {
    protected String name = "undefined", description = "";
    protected V defaultValue;
    protected IVisible visible;
    protected Consumer<V> onChanged;
    protected Consumer<Setting<V>> onModuleActivated;

    protected SettingBuilder(V defaultValue) { ... }

    public B name(String name)
    public B description(String description)
    public B defaultValue(V defaultValue)
    public B visible(IVisible visible)          // 见 IVisible
    public B onChanged(Consumer<V> onChanged)
    public B onModuleActivated(Consumer<Setting<V>> onModuleActivated)
    public abstract S build();
}
```
> `IVisible` 是函数式接口（`interface IVisible { boolean isVisible(); }`），所以可直接传 lambda：`.visible(() -> mode.get() == Mode.Luminance)`。

---

## 4. 常用 Setting 实现类

### 4.1 BoolSetting
`meteordevelopment/meteorclient/settings/BoolSetting.java`，`extends Setting<Boolean>`。

构造（私有，走 Builder）：
```java
private BoolSetting(String name, String description, Boolean defaultValue, Consumer<Boolean> onChanged, Consumer<Setting<Boolean>> onModuleActivated, IVisible visible)
```
Builder：`public static class Builder extends SettingBuilder<Builder, Boolean, BoolSetting>`，`super(false)`。
`parseImpl` 支持 `"true"/"1"/"false"/"0"/"toggle"`。建议项 `["true","false","toggle"]`。

### 4.2 EnumSetting
`meteordevelopment/meteorclient/settings/EnumSetting.java`，`extends Setting<T>`（`T extends Enum<?>`）。

构造：
```java
public EnumSetting(String name, String description, T defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible)
```
Builder：`public static class Builder<T extends Enum<?>> extends SettingBuilder<Builder<T>, T, EnumSetting<T>>`，`super(null)`。枚举值由 `defaultValue.getDeclaringClass().getEnumConstants()` 自动推导。

### 4.3 IntSetting
`meteordevelopment/meteorclient/settings/IntSetting.java`，`extends Setting<Integer>`。

字段：`public final int min, max, sliderMin, sliderMax; public final boolean noSlider;`
构造：
```java
private IntSetting(String name, String description, int defaultValue, Consumer<Integer> onChanged, Consumer<Setting<Integer>> onModuleActivated, IVisible visible, int min, int max, int sliderMin, int sliderMax, boolean noSlider)
```
Builder（注意 `range(sliderMin, sliderMax)` 会做 min/max 夹取）：
```java
public Builder min(int min)
public Builder max(int max)
public Builder range(int min, int max)      // 同时设 min/max（内部 Math.min/max 自动排序）
public Builder sliderMin(int min)
public Builder sliderMax(int max)
public Builder sliderRange(int min, int max)
public Builder noSlider()
```

### 4.4 DoubleSetting
`meteordevelopment/meteorclient/settings/DoubleSetting.java`，`extends Setting<Double>`。

字段：`public final double min, max, sliderMin, sliderMax; public final boolean onSliderRelease, noSlider; public final int decimalPlaces;`
构造：
```java
private DoubleSetting(String name, String description, double defaultValue, Consumer<Double> onChanged, Consumer<Setting<Double>> onModuleActivated, IVisible visible, double min, double max, double sliderMin, double sliderMax, boolean onSliderRelease, int decimalPlaces, boolean noSlider)
```
Builder：
```java
public Builder min(double min)
public Builder max(double max)
public Builder range(double min, double max)
public Builder sliderMin(double min)
public Builder sliderMax(double max)
public Builder sliderRange(double min, double max)
public Builder onSliderRelease()
public Builder decimalPlaces(int decimalPlaces)   // 默认 3
public Builder noSlider()
```

### 4.5 StringSetting
`meteordevelopment/meteorclient/settings/StringSetting.java`，`extends Setting<String>`。

字段：`public final String placeholder; public final Class<? extends WTextBox.Renderer> renderer; public final CharFilter filter; public final boolean wide;`
构造（注意这是**唯一构造函数，public**，其余都是私有）：
```java
public StringSetting(String name, String description, String defaultValue, Consumer<String> onChanged, Consumer<Setting<String>> onModuleActivated, IVisible visible, String placeholder, Class<? extends WTextBox.Renderer> renderer, CharFilter filter, boolean wide)
```
Builder：`placeholder(String)`、`renderer(Class)`、`filter(CharFilter)`、`wide()`。

### 4.6 KeybindSetting
`meteordevelopment/meteorclient/settings/KeybindSetting.java`，`extends Setting<Keybind>`。

字段：`private final Runnable action; public WKeybind widget;`
构造时**内部自动 `MeteorClient.EVENT_BUS.subscribe(this)`**，通过 `@EventHandler` 监听按键/鼠标。
构造：
```java
public KeybindSetting(String name, String description, Keybind defaultValue, Consumer<Keybind> onChanged, Consumer<Setting<Keybind>> onModuleActivated, IVisible visible, Runnable action)
```
Builder：`super(Keybind.none())`，`public Builder action(Runnable action)`。

### 4.7 BlockListSetting
`meteordevelopment/meteorclient/settings/BlockListSetting.java`，`extends Setting<List<Block>>`。

字段：`public final Predicate<Block> filter;`
构造：
```java
public BlockListSetting(String name, String description, List<Block> defaultValue, Consumer<List<Block>> onChanged, Consumer<Setting<List<Block>>> onModuleActivated, Predicate<Block> filter, IVisible visible)
```
Builder：`defaultValue(Block... defaults)`、`filter(Predicate<Block>)`。实现 `getIdentifierSuggestions()` 返回 `BuiltInRegistries.BLOCK.keySet()`。

### 4.8 ItemListSetting
`meteordevelopment/meteorclient/settings/ItemListSetting.java`，`extends Setting<List<Item>>`。

字段：`public final Predicate<Item> filter; private final boolean bypassFilterWhenSavingAndLoading;`
构造：
```java
public ItemListSetting(String name, String description, List<Item> defaultValue, Consumer<List<Item>> onChanged, Consumer<Setting<List<Item>>> onModuleActivated, IVisible visible, Predicate<Item> filter, boolean bypassFilterWhenSavingAndLoading)
```
Builder：`defaultValue(Item... defaults)`、`filter(Predicate<Item>)`、`bypassFilterWhenSavingAndLoading()`。

### 4.9 ColorSetting
`meteordevelopment/meteorclient/settings/ColorSetting.java`，`extends Setting<SettingColor>`。

构造：
```java
public ColorSetting(String name, String description, SettingColor defaultValue, Consumer<SettingColor> onChanged, Consumer<Setting<SettingColor>> onModuleActivated, IVisible visible)
```
Builder：`super(new SettingColor())`，重载 `defaultValue(SettingColor)` / `defaultValue(Color)`。会被 `Settings.registerColorSettings` 注册进彩虹色系统。

### 4.10 GenericSetting
`meteordevelopment/meteorclient/settings/GenericSetting.java`，`extends Setting<T>`（`T extends IGeneric<T>`，`IGeneric` 接口在 `settings/IGeneric.java`，用于可复制、可打开子屏幕的自定义值类型）。

构造：
```java
public GenericSetting(String name, String description, T defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible)
```
方法：`public WidgetScreen createScreen(GuiTheme theme)`。Builder：`super(null)`。

### 4.11 EnchantmentListSetting
`meteordevelopment/meteorclient/settings/EnchantmentListSetting.java`，`extends Setting<Set<ResourceKey<Enchantment>>>`。

构造：
```java
public EnchantmentListSetting(String name, String description, Set<ResourceKey<Enchantment>> defaultValue, Consumer<Set<ResourceKey<Enchantment>>> onChanged, Consumer<Setting<Set<ResourceKey<Enchantment>>>> onModuleActivated, IVisible visible)
```
Builder：`vanillaDefaults()`（反射枚举 `Enchantments` 里的全部原版 `ResourceKey<Enchantment>`）、`defaultValue(ResourceKey<Enchantment>... defaults)`。注意泛型值是 `Set<ResourceKey<Enchantment>>`（不是旧的 ResourceLocation）。

### 4.12 其它存在于 26.1.2 的 Setting（概览）
`ItemSetting`、`BlockSetting`、`ProvidedStringSetting`、`FileSetting`、`PacketListSetting`、`ScreenHandlerListSetting`、`StatusEffectListSetting`、`StringListSetting`、`ColorListSetting`、`BlockDataSetting`、`BlockPosSetting`、`Vector3dSetting`、`ModuleListSetting`、`PotionSetting`、`SoundEventListSetting`、`StorageBlockListSetting`、`ParticleTypeListSetting`、`FontFaceSetting`、`EntityTypeListSetting`、`StatusEffectAmplifierMapSetting`——均含 `public static class Builder extends SettingBuilder<...>`，命名规律一致。需要时按真实文件核实。

### 相关类
`Settings`、`SettingGroup`、`IVisible`（`settings/IVisible.java`）、`IGetter`（`utils/misc/IGetter.java`）、`IGeneric`（`settings/IGeneric.java`）、`IBlockData`（`settings/IBlockData.java`）。

---

## 26.1.2 `SettingGroup.create/register` 与可见性模式

- 26.1.2 中分组创建统一走 `Settings`：`settings.getDefaultGroup()` 或 `settings.createGroup(name[, expanded])`；然后 `group.add(settingBuilder.build())`。
- **没有**单独的 `SettingGroup.register(setting)` 公共方法 / 没有 `VisSetting` 包装类；可见性用 Builder 的 `.visible(IVisible)`。若不确认可保持「待源码确认」口径：可见性= `IVisible` 函数式接口。

---

## 常见坑
1. `IntSetting`/`DoubleSetting` 的 `range()` 会自动交换 min/max；`sliderRange` 在 build 时会夹取到 `[min,max]`，超出部分无效。
2. `EnumSetting` 的默认值必须非 `null` 且来自真实枚举类（构造里调用 `getDeclaringClass().getEnumConstants()`）。
3. `Setting.set(T)` 会先做 `isValueValid` 校验，非法值**直接返回 false 不生效**（`IntSetting`/`DoubleSetting` 校验 `[min,max]`）。
4. 序列化只保存 `wasChanged()` 过的 setting（`SettingGroup.toTag()` / `Settings.toTag()` 判断）。
5. `KeybindSetting` 构造即自动 `EVENT_BUS.subscribe(this)`，注意不要手动重复订阅导致双触发。
6. `ColorSetting` / `ColorListSetting` 必须通过 `Settings.registerColorSettings(module)` 才接入彩虹色系统；`Modules.add()` 已自动代为调用。
7. 可见性条件是运行期求值的 `IVisible`，编写方式为 lambda：`.visible(() -> someOtherSetting.get() == ...)`。