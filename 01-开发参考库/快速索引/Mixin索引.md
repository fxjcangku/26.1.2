# Mixin索引

> 查询顺序提醒：先查本索引 → 命中后到 原始源码 Grep 复核 → 再看 API参考对应文档
>
> 说明：Mixin 是 SpongePowered Mixin + MixinExtras（`com.llamalad7.mixinextras`）对 Minecraft/Meteor 运行时的编译期织入。本项目 addon 的 mixin 目录：`src/main/java/com/example/addon/mixin/`，注册清单 `src/main/resources/addon-template.mixins.json`。

## 一、Mixin 注解速查（Fabric Mixin 库）

| 注解 | 作用 | API参考文档 |
|------|------|-------------|
| @Mixin(目标类.class) | 声明注入目标 | Mixin.md（Minecraft） |
| @Inject | 方法 HEAD/RETURN/TAIL/INVOKE 注入 | Mixin.md（Minecraft） |
| @Redirect | 重定向方法调用/字段访问 | Mixin.md（Minecraft） |
| @ModifyConstant | 修改字面量常量 | Mixin.md（Minecraft） |
| @ModifyVariable | 修改局部变量 | Mixin.md（Minecraft） |
| @ModifyArg | 修改方法实参 | Mixin.md（Minecraft） |
| @Shadow | 访问目标类私有字段/方法 | Mixin.md（Minecraft） |
| @Overwrite | 完全替换目标方法（不推荐） | Mixin.md（Minecraft） |
| @Unique | 注入类私有成员 | Mixin.md（Minecraft） |
| @Accessor | 访问私有字段（接口形式） | Mixin.md（Meteor） |
| @ModifyArgs | 修改方法调用参数 | Mixin.md（Meteor） |
| @ModifyReturnValue | 修改方法返回值 | Mixin.md（Meteor） |
| @ModifyExpressionValue | 修改方法内表达式返回值 | Mixin.md（Meteor） |
| @Local（MixinExtras） | 捕获局部变量 | Mixin.md（Meteor） |

## 二、可用于 mixin 目标的原版类与方法（已核实）

| 目标类（全限定名） | 可用方法 | 经典用途 | 源码相对路径 | API参考文档 |
|-------------------|----------|----------|--------------|-------------|
| net.minecraft.client.Minecraft | tick() / setScreen(Screen) | 每帧钩子 / 界面切换 | net/minecraft/client/Minecraft.java | Mixin.md |
| net.minecraft.client.multiplayer.ClientPacketListener | handleSystemChat(...) / sendCommand(...) | 拦聊天 / 拦命令 | net/minecraft/client/multiplayer/ClientPacketListener.java | Mixin.md |
| net.minecraft.client.player.LocalPlayer | tick() | 本地玩家每帧 | net/minecraft/client/player/LocalPlayer.java | Mixin.md |
| net.minecraft.world.entity.Entity | pick(double, float, boolean) | 射线修改 | net/minecraft/world/entity/Entity.java | Mixin.md |
| net.minecraft.client.gui.screens.Screen | tick() / init() | 界面逻辑 | net/minecraft/client/gui/screens/Screen.java | Mixin.md |

## 三、Meteor 原生 mixin 示例（写法参考）

| 类名 | 目标类 | 注入要点 | 源码相对路径 | API参考文档 |
|------|--------|----------|--------------|-------------|
| DirectionAccessor | net.minecraft.core.Direction | @Accessor 读 BY_2D_DATA | meteordevelopment/meteorclient/mixin/DirectionAccessor.java | Mixin.md |
| CameraMixin | net.minecraft.client.Camera | @Shadow+@Inject+@ModifyArgs+@ModifyReturnValue | meteordevelopment/meteorclient/mixin/CameraMixin.java | Mixin.md |
| AttackRangeMixin | ...AttackRange | @ModifyExpressionValue(FIELD/GETFIELD) | meteordevelopment/meteorclient/mixin/AttackRangeMixin.java | Mixin.md |
| CompassAngleStateMixin | ...CompassAngleState | @ModifyExpressionValue(INVOKE) | meteordevelopment/meteorclient/mixin/CompassAngleStateMixin.java | Mixin.md |

## 四、本项目 addon mixin 清单（addon-template.mixins.json client 数组）

源码目录：`src\main\java\com\example\addon\mixin\`。以下按用途分类（类名 = 注册名）。

### 4.1 示例 / 基础设施

| 注册类名 | 用途 |
|----------|------|
| ExampleMixin | Meteor 出厂示例（Minecraft 构造器 TAIL 注入日志） |
| ClientCommandSourceMixin | 客户端命令源注入 |
| ResourcePackPushMixin | 资源包推送相关 |

### 4.2 界面中文化（Screen/Widget 翻译）

| 注册类名 | 用途 |
|----------|------|
| ModulesScreenTranslationMixin | 模块主界面翻译 |
| ModuleScreenTranslationMixin | 模块设置界面翻译 |
| ModuleScreenContextTranslationMixin | 模块界面上下文翻译 |
| PathManagerScreenTranslationMixin | 路径管理界面翻译 |
| JoinMultiplayerScreenTranslationMixin | 多人联机界面翻译 |
| ButtonBuilderTranslationMixin | 按钮构建器翻译 |
| TextTranslationMixin | 文本翻译 |
| WMeteorDropdownTranslationMixin | 下拉控件翻译 |
| WMeteorDropdownValueTranslationMixin | 下拉值翻译 |
| WIntEditRealtimeMixin | 整数编辑实时刷新 |
| WDoubleEditRealtimeMixin | 浮点编辑实时刷新 |

### 4.3 模块 / 设置 / 分类中文化

| 注册类名 | 用途 |
|----------|------|
| ModuleTranslationMixin | 模块名翻译 |
| ModuleToggleMessageMixin | 模块开关消息翻译 |
| SettingTranslationMixin | 设置项翻译 |
| SettingGroupTranslationMixin | 设置分组翻译 |
| CategoryTranslationMixin | 分类翻译 |
| ReachTranslationMixin | 攻击距离（Reach）翻译 |
| ConfigCustomFontDefaultMixin | 配置自定义字体默认值 |
| HudCustomFontDefaultMixin | HUD 自定义字体默认值 |
| MeteorComponentMessageMixin | Meteor 组件消息处理 |

### 4.4 Baritone 命令中文化

| 注册类名 | 用途 |
|----------|------|
| BaritoneHelperTranslationMixin | Baritone 帮助翻译 |
| BaritoneHelpCommandMixin | Baritone help 命令 |
| BaritoneCommandLongDescMixin | Baritone 命令长描述 |
| BaritoneCommandNamesMixin | Baritone 命令名翻译 |
| BaritoneSetCommandMixin | Baritone set 命令 |
| BaritonePaginatorMixin | Baritone 分页器 |

### 4.5 Meteor 命令中文化

| 注册类名 | 用途 |
|----------|------|
| MeteorCommandTranslationMixin | 命令翻译 |
| MeteorCommandsTranslationMixin | 命令管理器翻译 |
| MeteorCommandNamesMixin | 命令名翻译 |
| MeteorCommandRegistrationMixin | 命令注册 |

### 4.6 Accessor / Invoker（接口注入）

| 注册类名 | 用途 |
|----------|------|
| ClientLevelPredictionAccessor | ClientLevel 预测访问器 |
| ServerboundMovePlayerPacketAccessor | 移动包字段访问器 |
| LocalPlayerAccessor | LocalPlayer 访问器 |
| KeyboardInvoker | 键盘调用器 |
| MultiPlayerGameModeFastBreakMixin | 快速破坏（fast break） |

> 登记铁律：所有 `@Mixin` 类必须出现在 `addon-template.mixins.json` 的 `client` 数组，否则 Mixin 静默不生效；`package` 字段 = `com.example.addon.mixin`；`compatibilityLevel = JAVA_25`；`injectors.defaultRequire = 1`。

## 五、mixin 编写规范（要点）

1. 目标类/方法名一律用官方映射（`net.minecraft.client.Minecraft`、`ClientPacketListener`、`LocalPlayer`），禁 Yarn 名。
2. `@Inject(method=...)` 的方法名、参数类型必须与源码逐字一致；重载方法用方法描述符消歧。
3. 优先 `@Inject/@Redirect/@ModifyConstant/@ModifyExpressionValue`，避免 `@Overwrite`。
4. 官方源码**不存在** `net/minecraft/client/mixin` 目录与任何 `*Mixin.java`，不要虚构官方 mixin 路径。
5. `@Local` / `@ModifyExpressionValue` 需 MixinExtras 依赖（mixins.json 无需额外声明）。