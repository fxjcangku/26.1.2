# Setting索引

> 查询顺序提醒：先查本索引 → 命中后到 原始源码 Grep 复核 → 再看 API参考对应文档
>
> 说明：Setting 体系 = `Settings`（容器）→ `SettingGroup`（分组）→ `Setting<T>`（单项）。addon 标准写法：`public final Setting<Boolean> myFlag = sg.add(new BoolSetting.Builder().name("my-flag").build());`。源码相对路径相对 `Meteor原始源码\`。

## 一、容器与分组

| 类名 | 中文用途 | 关键方法 | 源码相对路径 | API参考文档 |
|------|----------|----------|--------------|-------------|
| Settings | 设置容器 | getDefaultGroup() / createGroup(name[,expanded]) / get(name) / reset() | meteordevelopment/meteorclient/settings/Settings.java | Setting设置.md |
| SettingGroup | 设置分组 | add(setting) / get(name) / wasChanged() | meteordevelopment/meteorclient/settings/SettingGroup.java | Setting设置.md |
| Setting | 设置基类（抽象） | get() / set(T) / reset() / parse() / wasChanged() | meteordevelopment/meteorclient/settings/Setting.java | Setting设置.md |
| IVisible | 可见性函数式接口 | boolean isVisible()（.visible(() -> ...)） | meteordevelopment/meteorclient/settings/IVisible.java | Setting设置.md |

## 二、常用 Setting 实现类

| 类名 | 值类型 | 关键 Builder 方法 | 源码相对路径 | API参考文档 |
|------|--------|-------------------|--------------|-------------|
| BoolSetting | Boolean | name/description/defaultValue/visible/onChanged | .../settings/BoolSetting.java | Setting设置.md |
| EnumSetting | T extends Enum<?> | defaultValue（枚举自动推导）/ name | .../settings/EnumSetting.java | Setting设置.md |
| IntSetting | Integer | min/max/range/sliderMin/sliderMax/sliderRange/noSlider | .../settings/IntSetting.java | Setting设置.md |
| DoubleSetting | Double | min/max/range/sliderMin/sliderMax/sliderRange/onSliderRelease/decimalPlaces/noSlider | .../settings/DoubleSetting.java | Setting设置.md |
| StringSetting | String | placeholder/renderer/filter/wide | .../settings/StringSetting.java | Setting设置.md |
| KeybindSetting | Keybind | defaultValue(Keybind)/action(Runnable) | .../settings/KeybindSetting.java | Setting设置.md |
| BlockListSetting | List<Block> | defaultValue(Block...)/filter | .../settings/BlockListSetting.java | Setting设置.md |
| ItemListSetting | List<Item> | defaultValue(Item...)/filter/bypassFilterWhenSavingAndLoading | .../settings/ItemListSetting.java | Setting设置.md |
| ColorSetting | SettingColor | defaultValue(SettingColor)/defaultValue(Color) | .../settings/ColorSetting.java | Setting设置.md |
| GenericSetting | T extends IGeneric<T> | defaultValue/createScreen(GuiTheme) | .../settings/GenericSetting.java | Setting设置.md |
| EnchantmentListSetting | Set<ResourceKey<Enchantment>> | vanillaDefaults()/defaultValue(ResourceKey...) | .../settings/EnchantmentListSetting.java | Setting设置.md |

## 三、其它存在 Setting（概览，按需核实）

| 类名 | 值类型 | 源码相对路径 |
|------|--------|--------------|
| ItemSetting | Item | .../settings/ItemSetting.java |
| BlockSetting | Block | .../settings/BlockSetting.java |
| ProvidedStringSetting | String | .../settings/ProvidedStringSetting.java |
| FileSetting | 文件 | .../settings/FileSetting.java |
| PacketListSetting | List | .../settings/PacketListSetting.java |
| ScreenHandlerListSetting | List | .../settings/ScreenHandlerListSetting.java |
| StatusEffectListSetting | List | .../settings/StatusEffectListSetting.java |
| StringListSetting | List<String> | .../settings/StringListSetting.java |
| ColorListSetting | List<SettingColor> | .../settings/ColorListSetting.java |
| BlockPosSetting | BlockPos | .../settings/BlockPosSetting.java |
| Vector3dSetting | Vec3 | .../settings/Vector3dSetting.java |
| ModuleListSetting | List<Module> | .../settings/ModuleListSetting.java |
| PotionSetting | Potion | .../settings/PotionSetting.java |
| FontFaceSetting | 字体 | .../settings/FontFaceSetting.java |
| EntityTypeListSetting | List<EntityType> | .../settings/EntityTypeListSetting.java |
| StatusEffectAmplifierMapSetting | Map | .../settings/StatusEffectAmplifierMapSetting.java |

## 四、Builder 通用链（所有 SettingBuilder 均支持）

```java
.name(String) .description(String) .defaultValue(V) .visible(IVisible)
.onChanged(Consumer<V>) .onModuleActivated(Consumer<Setting<V>>) .build()
```

- `IVisible` 是函数式接口，直接传 lambda：`.visible(() -> mode.get() == Mode.Luminance)`。
- 26.1.2 **不存在** `VisSetting` 包装类，可见性一律用 Builder 的 `.visible(IVisible)`。

## 五、本项目约定（数值控件）

- yiyiaddon 所有模块配置页 `IntSetting`/`DoubleSetting` 一律 `.noSlider()`（加减按钮 + 可输入框），禁止滑块（`sliderRange/sliderMin/sliderMax`）。

## 六、常见坑（精简）

1. `IntSetting`/`DoubleSetting` 的 `range()` 自动交换 min/max；`sliderRange` 在 build 时夹取到 `[min,max]`。
2. `EnumSetting` 默认值必须非 null 且来自真实枚举类。
3. `Setting.set(T)` 先 `isValueValid` 校验，非法值直接返回 false 不生效。
4. 序列化只保存 `wasChanged()` 过的 setting。
5. `KeybindSetting` 构造即自动 `EVENT_BUS.subscribe(this)`，勿重复订阅。
6. `ColorSetting`/`ColorListSetting` 须经 `Settings.registerColorSettings(module)` 接入彩虹系统（`Modules.add()` 已自动调用）。