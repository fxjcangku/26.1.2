# Mixin 生命周期

> Meteor 属 Fabric 客户端 mod，mixin 是它注入原版/其他 mod 类的手段。addon 也可以自己声明 mixin，但要理解「类加载期应用」的边界与跨 jar 注入风险。

## 概述

Mixin 在**类加载期**由 Mixin 处理器（`MixinService`/ASM）把 `@Mixin` 类织入目标类，发生在运行时类被首次加载时。fabric 通过 `fabric.mod.json` 的 `mixins` 数组声明 mixin 配置文件，配置文件的 `package` 字段 + `client`/`common` 列表决定哪些 mixin 类参与。

- Meteor 自身 mixin 目录：`meteordevelopment/meteorclient/mixin/`（150+ 文件）。
- 插件：`MixinPlugin.java`（`IMixinConfigPlugin`）做条件开关。
- addon 的 mixin 配置文件：`src/main/resources/addon-template.mixins.json`。

---

## 1. Meteor 自身 mixin 组织（真实文件 + target）

`mixin/` 目录节选 12 个真实文件与其 `@Mixin` 目标：

| Mixin 文件 | @Mixin target | 作用域 |
|---|---|---|
| `ClientPacketListenerMixin.java` | `ClientPacketListener.class` | 聊天前缀/命令派发、GameJoined/GameLeft、包事件 |
| `ConnectionMixin.java` | `Connection.class` | PacketEvent.Receive/Send/Sent、代理、异常捕获 |
| `KeyboardHandlerMixin.java` | `KeyboardHandler.class` | post `KeyInputEvent` |
| `MouseHandlerMixin.java` | `MouseHandler.class` | post `MouseClickEvent` |
| `LocalPlayerMixin.java` | `LocalPlayer.class` | SendMovementPacketsEvent、旋转、NoSlow 等 |
| `MinecraftMixin.java` | `Minecraft.class`（priority=1001） | 主循环/task 钩子 |
| `GameRendererMixin.java` | `GameRenderer.class` | 渲染钩子 |
| `ServerboundMovePlayerPacketMixin.java` | `ServerboundMovePlayerPacket.class` | 发包数据改写/访问器 |
| `ItemStackMixin.java` | `ItemStack.class` | 物品数据 |
| `BlockBehaviourMixin.java` | `BlockBehaviour.class` | 方块行为（碰撞/速度） |
| `KeyMappingAccessor.java` | `KeyMapping.class` | `@Accessor` 读按键 |
| `ClientPacketListenerAccessor.java` | `ClientPacketListener.class` | `@Accessor` 读字段 |

结构约定：以 `Accessor` 结尾的是 `@Accessor`/`@Invoker`（暴露字段/方法），`Mixin` 结尾是一般注入；`mixininterface/` 目录放「mixin 注入的接口」（如 `IVec3`、`ISlot`、`IMultiPlayerGameMode`），配合 `@Implements` 或直接 cast 使用。

## 2. MixinPlugin（条件应用）

`MixinPlugin.onLoad` 检测 optional mod（sodium/iris/origins/lithium/indigo/viafabricplus），`shouldApplyMixin(targetClassName, mixinClassName)` 决定：

```java
if (!mixinClassName.startsWith("meteordevelopment.meteorclient.mixin")) throw ...;   // 包外拒绝
else if (endsWith "PlayerEntityRendererMixin") return !isOriginsPresent;
else if (startsWith mixinPackage + ".sodium") return isSodiumPresent;
else if (".indigo" / ".lithium" / ".viafabricplus") return isXPresent;
return true;   // 默认应用
```

`MeteorClient.onInitializeClient` 开发环境下还会 `MixinEnvironment.getCurrentEnvironment().audit()` 强制加载（IDE 直跑时）。

## 3. fabric.mod.json / meteor-client.mixins.json 如何声明

Meteor 主 mod 的 `fabric.mod.json` 里 `mixins` 数组指向 `meteor-client.mixins.json`；该文件形如（addon 的真实例子 `addon-template.mixins.json`）：

```json
{
  "required": true,
  "package": "com.example.addon.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [ "ExampleMixin", "ModuleTranslationMixin", "...", "ServerboundMovePlayerPacketAccessor" ],
  "injectors": { "defaultRequire": 1 }
}
```

- `package`：mixin 类所在包；`client`/`common`：类名列表（省略 `.java`）；`required: true` 表示加载失败即崩溃。
- `injectors.defaultRequire = 1`：默认要求每个 `@Inject` 至少命中一次。
- `compatibilityLevel`：addon 用 `JAVA_25`（本项目 Java 25）。

addon 在 `fabric.mod.json` 用 `"mixins": ["addon-template.mixins.json"]` 声明。addon 主类（`com.example.addon.core.AddonTemplate`）通过 `entrypoints.meteor` 注册（见 Addon系统.md），与 mixin 声明是两码事。

## 4. Mixin 应用时机（类加载期）

1. Fabric Loader 启动时读所有 mod 的 `fabric.mod.json`，收集 `mixins` 配置文件。
2. 每个配置文件的 `package` 下的类在对应目标类**被虚拟机首次加载前/时**由 Mixin Transformer 织入（`@Mixin` 目标）。
3. `MixinPlugin.shouldApplyMixin` 在**每个 mixin 应用前**回调，返回 false 则不织入（条件开关）。
4. 织入后目标类的方法体内多出 `@Inject` 注入的回调（`HEAD`/`TAIL`/`INVOKE`/`RETURN` 等 `At`）。

因此 mixin 只能改「你声明目标的类」，且是按类名（而非运行时实例）决定。

## 5. addon 侧 mixin 滥用风险（跨 jar 注入 Meteor 类边界）

addon（独立 jar，`com.example.addon` 包）**可以直接 mixin `meteordevelopment.meteorclient.**` 或 `net.minecraft.**` 类**——本 addon 就 mixin 了大量 Meteor 类做翻译：`ModuleTranslationMixin`、`SettingTranslationMixin`、`ModulesScreenTranslationMixin`、`MeteorCommandRegistrationMixin`、`ServerboundMovePlayerPacketAccessor`、`ClientLevelPredictionAccessor` 等（见 `addon-template.mixins.json`）。风险与边界：

- **强耦合**：mixin 目标是 Meteor 内部实现，Meteor 升级即可能因字段/方法改名而 `@Inject` 失效（`defaultRequire=1` 时直接崩）。
- **只能注入已知类**：目标必须是类名字符串；对「接口/私有内部类」要看清名字（如 `ClientSuggestionProvider` 是 `net.minecraft.client.multiplayer.ClientSuggestionProvider`）。
- **Accessor 依赖 remap/名称**：`@Accessor("字段名")` 用源码名（本项目 Mojang 官方映射，26.1 起不混淆），写错名直接 `@Accessor` 失败。
- **避免在 onActivate 期间做重型反射/注入**：mixin 在类加载期完成，运行期无「热应用」，想动态切换只能靠 `@Unique` 标记 + 运行期开关。
- **别 mixin 同类目标两次**（Meteor 已注入的类）：会冲突；优先用事件 / util API 而非重复注入。

---

## Addon 用法 / 介入点

1. 建 mixin 类，包名与配置一致（`com.example.addon.mixin`）。
2. `@Mixin(某类.class) public abstract class XxxMixin { @Inject(method="..", at=@At("HEAD")) private void h(CallbackInfo ci){} }`。
3. 在 `resources/*.mixins.json` 的 `client` 列表登记类名，再在 `fabric.mod.json` 的 `mixins` 数组登记该 json。
4. 若要读目标私有字段：写 `@Mixin(...) public interface XxxAccessor { @Accessor("fieldName") Object getX(); }`，运行时 `((XxxAccessor) obj).getX()`。

## 常见坑

1. **忘了在 `client` 数组登记**：mixin 类静默不生效。
2. **`@Inject` 目标方法名/签名写错**：`defaultRequire=1` 下构建期报错或运行期抛 `InvalidInjectionException`。
3. **`@Accessor("字段名")` 名字错**：Mojang 官方名，非 Yarn 名。
4. **跨 jar mixin Meteor 内部**：升级必查（本 addon 用大量 `Meteor*TranslationMixin`，属有意为之，但需跟随 Meteor 版本维护）。
5. **`compatibilityLevel` 低于目标**：Java 25 环境写低了可能不兼容新字节码特性。

## 源码依据

- `meteordevelopment/meteorclient/mixin/` 目录（ClientPacketListenerMixin / ConnectionMixin / KeyboardHandlerMixin / MouseHandlerMixin / LocalPlayerMixin / MinecraftMixin / GameRendererMixin / ServerboundMovePlayerPacketMixin / ItemStackMixin / BlockBehaviourMixin / KeyMappingAccessor / ClientPacketListenerAccessor 等）—— 真实 @Mixin target。
- `meteordevelopment/meteorclient/MixinPlugin.java` —— shouldApplyMixin / onLoad / mixinPackage 校验。
- `meteordevelopment/meteorclient/MeteorClient.java` —— 开发环境 `audit()` 强制加载 mixin。
- `src/main/resources/addon-template.mixins.json`（addon）—— package/compatibilityLevel/client/injectors 真实写法。
- `src/main/resources/fabric.mod.json`（addon）—— `mixins`/`entrypoints`/`custom` 声明。
- `com/example/addon/mixin/`（addon）—— 真实跨 jar mixin（ModuleTranslationMixin、MeteorCommandRegistrationMixin、ServerboundMovePlayerPacketAccessor 等）。