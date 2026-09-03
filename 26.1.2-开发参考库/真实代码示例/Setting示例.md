# Setting 示例

## 概述

26.1.2 的设置系统在 `meteordevelopment.meteorclient.settings` 包下，由 `Setting`（单项）、`SettingGroup`（分组）、`Settings`（模块内集合）三级组成。所有设置都通过 `.add(new XxxSetting.Builder<>()...build())` 挂到分组上。

关键认知：
- **分组来源**：模块内用 `settings.getDefaultGroup()` / `settings.createGroup("名字")`（实例字段），**不是**旧教程里的 `Settings.get().createGroup(...)`。
- **显隐条件**：所有 `Setting.Builder` 都有 `.visible(IVisible)` 方法，`IVisible` 是函数式接口 `boolean isVisible()`，直接传 lambda/方法引用即可。旧版 `VisSetting` 已不存在。
- 设置值读取用 `setting.get()`，运行时写入用 `setting.set(...)`（见本项目 `PacketInstantBreak` 里 `breakMode.set(BreakMode.VANILLA)` 的真实用法）。

---

## 示例 1：分组创建 + Bool / Enum / Int / Double（Meteor 本体 AutoEat）

出处：`Meteor原始源码/meteordevelopment/meteorclient/systems/modules/player/AutoEat.java`（第 39-116 行，各类型各摘 1 个）

```java
import meteordevelopment.meteorclient.settings.*;

public class AutoEat extends Module {
    // Settings groups
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreshold = settings.createGroup("Threshold");

    // Bool
    private final Setting<Boolean> pauseAuras = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-auras")
        .description("Pauses all auras when eating.")
        .defaultValue(true)
        .build()
    );

    // Enum
    private final Setting<Priority> prioritise = sgGeneral.add(new EnumSetting.Builder<Priority>()
        .name("food-priority")
        .description("Which aspect of the food to prioritise selecting for.")
        .defaultValue(Priority.Saturation)
        .build()
    );

    // Double（.range + .sliderRange + .visible）
    private final Setting<Double> healthThreshold = sgThreshold.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("The level of health you eat at.")
        .defaultValue(10)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Hunger)
        .build()
    );

    // Int
    private final Setting<Integer> hungerThreshold = sgThreshold.add(new IntSetting.Builder()
        .name("hunger-threshold")
        .description("The level of hunger you eat at.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Health)
        .build()
    );
}
```

要点：
- `EnumSetting.Builder<T>` 的泛型 `T` 必须是 `enum`（上面 `Priority` 是内部枚举）。`EnumSetting` 依赖 enum 的 `toString()` 显示选项名。
- `IntSetting`/`DoubleSetting` 里的 `.range(min,max)` 是硬边界；`.sliderRange(min,max)` 只影响 GUI 滑块视觉范围；`.sliderMax(...)` 只给滑块上限（见 Fullbright）。

---

## 示例 2：BlockListSetting / ItemListSetting（Meteor 本体）

出处分两处：
- `BlockListSetting`：`Meteor原始源码/.../systems/modules/render/blockesp/BlockESP.java` 第 41-48 行
- `ItemListSetting`：`Meteor原始源码/.../systems/modules/player/AutoEat.java` 第 43-60 行

```java
// BlockListSetting（可带 .onChanged）
private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
    .name("blocks")
    .description("Blocks to search for.")
    .onChanged(_ -> {
        if (isActive() && Utils.canUpdate()) onActivate();
    })
    .build()
);

// ItemListSetting（带 .filter + .bypassFilterWhenSavingAndLoading）
public final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
    .name("blacklist")
    .description("Which items to not eat.")
    .defaultValue(
        Items.ENCHANTED_GOLDEN_APPLE,
        Items.GOLDEN_APPLE,
        Items.CHORUS_FRUIT,
        Items.POISONOUS_POTATO,
        Items.PUFFERFISH,
        Items.CHICKEN,
        Items.ROTTEN_FLESH,
        Items.SPIDER_EYE,
        Items.SUSPICIOUS_STEW
    )
    .filter(Utils::isFood)
    .bypassFilterWhenSavingAndLoading()
    .build()
);
```

要点：
- `BlockListSetting`/`ItemListSetting` 的 `Setting` 泛型是 `List<Block>`/`List<Item>`。
- 读值 `blocks.get()` 返回 `List<Block>`，可用 `.contains(block)`、`.size()`、`.isEmpty()`。
- `.filter(Predicate)` 限制可勾选范围；`.bypassFilterWhenSavingAndLoading()` 让已存档但已被 filter 剔除的项仍能读回来（防配置丢失）。

---

## 示例 3：KeybindSetting（Meteor 本体 BetterTooltips）

出处：`Meteor原始源码/meteordevelopment/meteorclient/systems/modules/render/BetterTooltips.java`（第 82-89 行、第 98-104 行）

```java
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE;

// 按键绑定（键盘）
private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
    .name("keybind")
    .description("The bind for keybind mode.")
    .defaultValue(Keybind.fromKey(GLFW_KEY_LEFT_ALT))
    .visible(() -> displayWhen.get() == DisplayWhen.Keybind)
    .onChanged(_ -> updateTooltips = true)
    .build()
);

// 按键绑定（鼠标）
private final Setting<Keybind> openContentsKey = sgGeneral.add(new KeybindSetting.Builder()
    .name("keybind")
    .description("Key to open contents (containers, books, etc.) when pressed on items.")
    .defaultValue(Keybind.fromButton(GLFW_MOUSE_BUTTON_MIDDLE))
    .visible(openContents::get)
    .build()
);
```

要点：
- `Keybind` 类型在 `meteordevelopment.meteorclient.utils.misc.Keybind`，`Keybind.fromKey(...)`/`Keybind.fromButton(...)` 构造默认值。
- `.visible(openContents::get)` 是方法引用形式的 `IVisible`，等效 `() -> openContents.get()`。

---

## 示例 4：ColorSetting（Meteor 本体 + 本项目）

出处：`src/main/java/com/example/addon/modules/AutoBoneMeal.java`（第 233-243 行）

```java
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

private final Setting<SettingColor> lineColor = sgEsp.add(new ColorSetting.Builder()
    .name("线框颜色")
    .defaultValue(new SettingColor(0, 255, 100, 255))
    .build()
);

private final Setting<SettingColor> fillColor = sgEsp.add(new ColorSetting.Builder()
    .name("填充颜色")
    .defaultValue(new SettingColor(0, 255, 100, 45))
    .build()
);
```

要点：
- `ColorSetting` 的值类型是 `SettingColor`（`utils/render/color` 包），构造参数 `(r, g, b, a)`。
- `ColorSetting` 会被 `Settings.registerColorSettings` 自动接入 Meteor 的「彩虹颜色」联动（`Settings.java` 第 102-114 行），所以字段声明里几乎没别的东西可加。

---

## 示例 5：GenericSetting（Meteor 本体 BlockESP）

出处：`Meteor原始源码/meteordevelopment/meteorclient/systems/modules/render/blockesp/BlockESP.java`（第 50-63 行）

```java
private final Setting<ESPBlockData> defaultBlockConfig = sgGeneral.add(new GenericSetting.Builder<ESPBlockData>()
    .name("default-block-config")
    .description("Default block config.")
    .defaultValue(
        new ESPBlockData(
            ShapeMode.Lines,
            new SettingColor(0, 255, 200),
            new SettingColor(0, 255, 200, 25),
            true,
            new SettingColor(0, 255, 200, 125)
        )
    )
    .build()
);
```

要点：
- `GenericSetting<T>` 约束 `T extends IGeneric<T>`（`IGeneric` 在 `settings/IGeneric.java`）。`ESPBlockData` 实现了 `IGeneric`，所以能作为自定义内嵌设置使用。
- 这是 26.1.2 里少数真正用到 `GenericSetting.Builder` 的地方；普通场景用上面 8 种内建类型就够了。

---

## 示例 6：IVisible 条件显隐（本项目真实用法）

出处：`src/main/java/com/example/addon/modules/AutoBoneMeal.java`（第 66-87 行，`noSlider` + `.visible`）与 `src/main/java/com/example/addon/tactical/ServerDetector.java`（第 100-109 行）

```java
// 整数设置 + noSlider + 显隐
private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
    .name("作用半径")
    .description("范围扫描的最大半径（格）。")
    .defaultValue(4).min(1).max(8).noSlider()
    .visible(() -> triggerMode.get() == TriggerMode.范围自动扫描)
    .build()
);

// 依赖「另一个设置」的显隐（ServerDetector）
private final Setting<Integer> downloadRetries = sgResourcePack.add(new IntSetting.Builder()
    .name("重试次数")
    .description("下载失败后的重试次数，每次重试都会尝试断点续传")
    .defaultValue(5)
    .min(1)
    .max(10)
    .noSlider()
    .visible(() -> resourcePackMode.get() == ResourcePackMode.AUTO_DOWNLOAD)
    .build()
);
```

要点：
- `.noSlider()` 是 `IntSetting.Builder`/`DoubleSetting.Builder` 的方法：跳过自动生成滑块，只用文本框输入（本项目大量用于「精确数值」场景）。
- `IVisible` 接口只有一个方法 `boolean isVisible()`（`settings/IVisible.java` 全文），任何 `() -> boolean` lambda 或 `BooleanSupplier` 方法引用都能传。
- 复杂显隐可以叠多个条件：`PacketInstantBreak` 第 201 行 `visible(() -> render.get() && espStyle.get().sides())`。

---

## 模式要点

1. **Builder 链顺序无关**，但必须 `.build()` 收尾；`.name()` 是设置持久化 key，`.description()` 是 GUI tooltip（中文可直接写中文 name/description，本项目大量用中文 key）。
2. **运行时改设置**：`setting.set(值)`，例如 `PacketInstantBreak.java` 第 451 行 `breakMode.set(BreakMode.VANILLA)` 在运行时把设置改成安全值。
3. **读默认值/类型安全取值**：`Settings.get(name)`（名字大小写不敏感，`Settings.java` 第 37 行）、`Settings.get(name, Class)`（带类型校验，第 48 行）。
4. **分组查找**：`settings.getGroup("名字")` 按名取回分组（`Settings.java` 第 74 行）。
5. **onChanged 回调**：`.onChanged(value -> ...)` 在 GUI 里改值时触发；注意回调里访问 `isActive()`/`mc.level` 前要先判空。

## 常见坑

- **用 `VisSetting`（Yarn 时代的旧显隐类）**：26.1.2 里没有 `VisSetting`，显隐统一走 `.visible(IVisible)`。
- **`Settings.get()` 静态调用**：26.1.2 的 `Settings` 没有静态 `get()`，模块内一律用字段 `settings`。
- **Int/Double 设置默认值超出 range**：`.range(min,max)` 会校验，默认值必须在范围内，否则构建时报错。
- **`EnumSetting` 泛型传了非 enum**：编译不通过；且 option 显示名取自 enum 的 `toString()`，想显示中文就在 enum 里覆写 `toString()`（本项目各模块 `TriggerMode`/`BreakMode` 的做法）。
- **读 `List<Block>`/`List<Item>` 设置没判空**：`BlockListSetting.get()` 即便空列表也会返回空 `List`（不会 null），但保险起见本项目都 `targetBlocks.get().isEmpty()` 判断。

## 26.1.2 注意

- 设置系统类名/包名与旧版一致（`BoolSetting`/`IntSetting`/`DoubleSetting`/`EnumSetting`/`KeybindSetting`/`BlockListSetting`/`ItemListSetting`/`ColorSetting`/`GenericSetting`），但**分组创建入口从静态 `Settings.get()` 改成实例字段 `settings`**——这是最容易踩的旧教程雷。
- `GenericSetting` 泛型约束已收紧为 `T extends IGeneric<T>`（旧版是任意可序列化类型）。
- `ItemListSetting` 新增 `.bypassFilterWhenSavingAndLoading()`，配合 `.filter()` 使用，防止过滤器变严后历史配置被清空。
- `SettingGroup` 的 GUI 折叠状态：`settings.createGroup(name)` 默认展开，`createGroup(name, expanded)` 可指定初始折叠。