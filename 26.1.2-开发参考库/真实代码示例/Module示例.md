# Module 示例

## 概述

26.1.2 中所有功能「模块」都继承自 `meteordevelopment.meteorclient.systems.modules.Module`，核心生命周期只有两个覆写点：

| 覆写点 | 时机 |
| --- | --- |
| `onActivate()` | 模块被开启瞬间（`settings.onActivated()` 之后、订阅事件之后）。适合做启动自检、重置内部状态、注册一次性动作 |
| `onDeactivate()` | 模块被关闭瞬间。适合清理临时状态、撤销 onActivate 的副作用 |

事件监听（`@EventHandler`）在模块 `toggle()` 里由 `MeteorClient.EVENT_BUS.subscribe(this)` 自动订阅、`unsubscribe(this)` 自动退订，**不需要手动注册/注销**。开启/关闭的开关阈值由 `Module.toggle()` 内部处理，见「模式要点」。

下方第一个例子是 Meteor 本体最极简的 Module 全文骨架，直接可抄；后两个例子分别展示「多设置 + Tick 事件」与「addon 分层（本项目基类 `YiyiaddonModule`）」。

---

## 示例 1：最小 Module 全文骨架（Meteor 本体 AutoRespawn）

出处：`Meteor原始源码/meteordevelopment/meteorclient/systems/modules/player/AutoRespawn.java`（全文，第 17-30 行）

```java
package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.WaypointsModule;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screens.DeathScreen;

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

要点：
- 构造函数用 `super(Categories.Player, "名字", "描述")` 挂到分类并设定 kebab-case 名字（mixin 命令/配置 key 都用这个名字）。
- `mc` 是 `Module` 基类字段，来自 `MeteorClient.mc`（`net.minecraft.client.Minecraft.getInstance()`），**不需要在子类里再声明**。
- `event.cancel()` 是 orbit 事件提供的可取消事件通用手段。

---

## 示例 2：带设置组 + Tick 事件的完整 Module（Meteor 本体 Fullbright）

出处：`Meteor原始源码/meteordevelopment/meteorclient/systems/modules/render/Fullbright.java`（第 22-109 行，删去与主题无关的 `getLuminance/getGamma` 与 `disableNightVision` 内的部分实现）

```java
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
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
        .onChanged(mode -> {
            if (isActive()) {
                if (mode != Mode.Potion) disableNightVision();
                if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
            }
        })
        .build()
    );

    private final Setting<Integer> minimumLightLevel = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-light-level")
        .description("Minimum light level when using Luminance mode.")
        .visible(() -> mode.get() == Mode.Luminance)
        .defaultValue(8)
        .range(0, 15)
        .sliderMax(15)
        .build()
    );

    public Fullbright() {
        super(Categories.Render, "fullbright", "Lights up your world!");
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Luminance) mc.levelRenderer.allChanged();
    }

    @Override
    public void onDeactivate() {
        if (mode.get() == Mode.Luminance) mc.levelRenderer.allChanged();
        else if (mode.get() == Mode.Potion) disableNightVision();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mode.get().equals(Mode.Potion)) return;
        // ... 补夜视药水效果，此处省略 ...
    }

    public enum Mode {
        Gamma,
        Potion,
        Luminance
    }
}
```

要点：
- `settings.getDefaultGroup()` 取默认分组（名为 "General"）；`settings.createGroup("名字")` 新增分组。**26.1.2 用实例字段 `settings`，不再是旧版静态 `Settings.get()`**。
- `.visible(() -> ...)` 传 `IVisible` 型 lambda，动态控制设置项显隐。
- 枚举类型 `Mode` 是模块内部私有枚举，直接作为 `EnumSetting.Builder<Mode>` 的泛型。

---

## 示例 3：本项目模块骨架（展示 addon 分层）

出处：`src/main/java/com/example/addon/modules/AutoBoneMeal.java`（类头第 44 行、设置组第 59-62 行、构造函数第 261-264 行、onActivate 第 298-318 行、onDeactivate 第 378-382 行、Tick 事件第 388-392 行）

```java
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.tactical.TacticalFSM;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;

public class AutoBoneMeal extends YiyiaddonModule {

    // ================================================================
    //  设置组
    // ================================================================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("目标方块");
    private final SettingGroup sgBypass  = settings.createGroup("防作弊绕过");
    private final SettingGroup sgEsp     = settings.createGroup("ESP渲染");

    private final Setting<TriggerMode> triggerMode = sgGeneral.add(new EnumSetting.Builder<TriggerMode>()
        .name("触发模式")
        .description("范围自动扫描：自动搜索周围所有目标；准星精准指向：仅对准星看着的方块生效。")
        .defaultValue(TriggerMode.范围自动扫描)
        .build()
    );

    public AutoBoneMeal() {
        super(AddonTemplate.CATEGORY_AUTOMATION, "自动骨粉",
            "自动催熟农作物，补骨粉，ESP高亮，视角静默同步。详细参考下面使用说明。");
    }

    @Override
    public void onActivate() {
        // 世界就绪判断：selfCheck 会访问 mc.level，必须先确认已进入世界
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            notifyError("必须在进入世界后才能启动模块。");
            mc.execute(this::toggle);
            return;
        }

        // 开机自检：缺项一次列全，配好一项下次就少一条
        if (!reportSelfCheck(selfCheck())) return;

        // 重置内部状态字段...
    }

    @Override
    public void onDeactivate() {
        // 清理临时状态...
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null
                || mc.getConnection() == null) return;

        // 服务器卡顿 / 拉回冷却时暂停，避免顶风作案
        if (respectLag.get() && (TacticalFSM.isServerLagging() || TacticalFSM.isRubberBandCooldown())) {
            return;
        }
        // ...
    }
}
```

要点：
- `YiyiaddonModule`（`src/main/java/com/example/addon/core/YiyiaddonModule.java`）是项目自己的模块基类，继承 `Module` 后统一覆写 `toggle()` 输出「§a§l已开启 / §c§l已关闭」中文提示、提供 `notify/notifyError/reportSelfCheck` 等中文播报工具。
- 分类用 `AddonTemplate.CATEGORY_AUTOMATION` 这种**项目自建分类常量**，而非 Meteor 内置 `Categories.*`——见「注册示例」。
- 自检失败时用 `mc.execute(this::toggle)` 延后到主线程下一帧再关闭，避免在 `onActivate` 里直接 `toggle()` 触发状态机重入。
- 事件方法内部每个都先判 `mc.player == null` 等空值（Tick 在任何屏幕下都可能触发）。

---

## 模式要点

1. **模块生命周期**：`enable()`/`disable()`/`toggle()` 都收口到 `Module.toggle()`（`Module.java` 第 90-110 行）。开启顺序是：置 `active=true` → `Modules.get().addActive(this)` → `settings.onActivated()` → `EVENT_BUS.subscribe(this)`（若 `autoSubscribe=true`）→ `onActivate()`。
2. **事件注册是自动的**：只要模块 `autoSubscribe=true`（默认），开启时自动 `subscribe`，关闭自动 `unsubscribe`。不要手动调用 `MeteorClient.EVENT_BUS.subscribe`。
3. **台阶复用的两个开关字段**：`runInMainMenu`（允许在主菜单开启，默认 false）、`chatFeedback`（开关时是否发提示，默认 true）。本项目 `YiyiaddonModule` 构造函数里强制 `toggleOnBindRelease = false`。
4. **设置字段必须在构造前初始化**：字段初始化器（`sgGeneral.add(...)`）在构造函数体之前执行，而 `settings` 在 `Module` 基类里已经是 `public final Settings settings = new Settings()`，所以字段写 `settings.createGroup(...)` 是安全的。
5. **分类注册**：Meteor 内置分类用 `Categories.Render/Player/...`；addon 自建分类必须走 `Modules.registerCategory(...)`（见「注册示例」）。

## 常见坑

- **在 `onActivate` 里直接关自己**会重入：`toggle()` 先 `addActive` 再 `onActivate`，在 `onActivate` 里又 `toggle()` 会让状态机错乱。正确处理是 `mc.execute(this::toggle)` 延后一帧（`AutoBoneMeal`/`PacketInstantBreak` 都是这么写的）。
- **事件方法不判空**：`TickEvent`、`Render3DEvent` 等在主菜单也可能触发，`mc.player`/`mc.level`/`mc.getConnection()` 都可能为 null，必须判。
- **事件方法必须是 private**：orbit 通过反射订阅，放 public 也不会报错，但项目规范是 `private void onXxx`。
- **字段用了 `this.` 前缀的旧写法**导致找不到：基类 `mc`、`settings` 都是 `protected/public`，子类直接用即可，别写成 `MinecraftClient.getInstance()`（那是 Yarn 名，26.1.2 是官方映射 `Minecraft`）。

## 26.1.2 注意

- `mc` 单例来自 `MeteorClient.mc`，不是 `MinecraftClient.getInstance()`；在 `Module` 子类里直接用基类字段 `mc`，无需 import `MinecraftClient`。
- 设置分组已从旧版静态 `Settings.get().createGroup("...")` 改为**实例字段** `settings.createGroup("...")` / `settings.getDefaultGroup()`（`Settings.java` 第 86-99 行）。按旧教程抄会编译报错。
- `Module.toggle()` 的开关提示默认走 `sendToggledMsg()`；本项目 `YiyiaddonModule` 覆写为中文并置空 `sendToggledMsg()` 防止按键绑定双提示。
- `OpenScreenEvent` / `TickEvent` / `Render3DEvent` 等事件类的位置在 `meteordevelopment.meteorclient.events.*`，与旧版一致，但引包时要确认是在 `events/game`、`events/world` 还是 `events/render` 下。