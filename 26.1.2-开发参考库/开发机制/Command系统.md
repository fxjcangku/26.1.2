# Command 系统

> 警告：`ChatUtils` 没有 `sendCommand`（真实发命令走 `mc.player.connection.sendCommand(...)`）；`CommandArgumentType` 已移除（并非 0.5.x 记忆里的东西）。命令基于 Mojang Brigadier。

## 概述

Meteor 命令系统 = Brigadier `CommandDispatcher<ClientSuggestionProvider>` + 一组 `Command` 子类。每个命令「名称 + 描述 + 别名 + 语法树」，聊天框输入 `.<name>` 被截取后进 `Commands.dispatch` 执行。

核心：`Command`（抽象基类）、`Commands`（静态注册表 + DISPATCHER）、Broker 是 Brigadier 原版 `CommandDispatcher`/`LiteralArgumentBuilder`/`RequiredArgumentBuilder`/`ArgumentType`。

---

## 1. Command 构造与 build()

`Command` 字段（真实）：

```java
private final String name;
private final String title;           // Utils.nameToTitle(name)
private final String description;
private final List<String> aliases;   // List.of(aliases)

public Command(String name, String description, String... aliases)
```

关键：`build(LiteralArgumentBuilder<ClientSuggestionProvider> builder)` 是**唯一的抽象方法**，命令作者在里面搭语法树。

`build` 里的两个「虚拟构造器」辅助（static 泛型推断 helper，不是真正构造）：

```java
protected static <T> RequiredArgumentBuilder<ClientSuggestionProvider, T> argument(String name, ArgumentType<T> type)
protected static LiteralArgumentBuilder<ClientSuggestionProvider> literal(String name)
```

还有静态常量：`REGISTRY_ACCESS`（`CommandBuildContext`，进服时重建）、`SINGLE_SUCCESS`（Brigadier 的 `com.mojang.brigadier.Command.SINGLE_SUCCESS`）、`mc = MeteorClient.mc`。

注册：

```java
public final void registerTo(CommandDispatcher<ClientSuggestionProvider> dispatcher) {
    register(dispatcher, name);
    for (String alias : aliases) register(dispatcher, alias);  // 别名同名挂载
}
public void register(CommandDispatcher<ClientSuggestionProvider> dispatcher, String name) {
    LiteralArgumentBuilder<ClientSuggestionProvider> builder = LiteralArgumentBuilder.literal(name);
    build(builder);
    dispatcher.register(builder);
}
```

消息通道同 `Module`：`info/warning/error` 都先 `ChatUtils.forceNextPrefixClass(getClass())` 再 `ChatUtils.infoPrefix/warningPrefix/errorPrefix`，前缀用 `title`。

`toString()` / `toString(String... args)` 输出 `.前缀 + name + 参数`。

## 2. Commands 静态注册表（真实结构）

```java
public class Commands {
    public static final List<Command> COMMANDS = new ArrayList<>();
    public static CommandDispatcher<ClientSuggestionProvider> DISPATCHER = new CommandDispatcher<>();

    @PostInit(dependencies = PathManagers.class)
    public static void init() { /* add 一堆内置命令 */ COMMANDS.sort(Comparator.comparing(Command::getName)); EVENT_BUS.subscribe(Commands.class); }

    public static void add(Command command) {
        COMMANDS.removeIf(existing -> existing.getName().equals(command.getName()));  // 同名覆盖
        COMMANDS.add(command);
    }

    public static void dispatch(String message) throws CommandSyntaxException {
        DISPATCHER.execute(message, mc.getConnection().getSuggestionsProvider());
    }

    public static Command get(String name) { /* 遍历 equals */ }
}
```

- `init()` 里 `add(new VClipCommand())`... 等约 40 个内置命令（VClip/HClip/Damage/Drop/FakePlayer/Friends/Inventory/Nbt/Help/Bind/Setting/Toggle/Macro/Modules/...），随后按 name 排序，`MeteorClient.EVENT_BUS.subscribe(Commands.class)` 订阅静态方法（用 `onJoin`）。
- `COMMANDS` 是线性 `List`，无 Map 结构（与 `Modules` 不同）；`add` 是「先移除同名再追加」。
- `DISPATCHER` 在 `GameJoinedEvent` 时被**整体重建**（见下），不是常驻。

## 3. 前缀 '.' 解析与 dispatch 真实链路

真实入口是 `ClientPacketListenerMixin`（`@Mixin(ClientPacketListener.class)`）里的 `onSendChatMessage`，它注入原版 `ClientPacketListener.sendChat(String message, ...)`：

```java
if (!message.startsWith(Config.get().prefix.get())
        && !(BaritoneUtils.IS_AVAILABLE && message.startsWith(BaritoneUtils.getPrefix()))) {
    // 非命令：作为普通消息 post SendMessageEvent，可 cancel/改写
    return;
}
if (message.startsWith(Config.get().prefix.get())) {       // 前缀(默认 '.')命中
    try { Commands.dispatch(message.substring(Config.get().prefix.get().length())); }
    catch (CommandSyntaxException e) { ChatUtils.error(e.getMessage()); }
    minecraft.gui.getChat().addRecentChat(message);        // 记入最近聊天
    ci.cancel();                                           // 取消原版 sendChat，防止真发服务器
}
```

所以「前缀解析器」的真名是 `ClientPacketListenerMixin`，不是 `CommandHandlers`/`CommandManager`。`ChatScreen(Config.get().prefix.get(), true)` 的「命令模式」打字界面也由 `MeteorClient.onTick` 打开。Brigadier 的补全由 `CommandSuggestionsMixin`（`@Mixin(CommandSuggestions.class)`）接入：`updateCommandInfo` 里若以 prefix 开头，把光标前移 prefix 长度，用 `Commands.DISPATCHER.parse(...)` + `getCompletionSuggestions(...)`。

## 4. 参数类型与自动补全

- 内置 argument 类型在 `commands/arguments/`：`BlockPosArgumentType`、`PlayerArgumentType`、`FriendArgumentType`、`ModuleArgumentType`、`DirectionArgumentType`、`Nbt`(CompoundNbtTagArgumentType)、`MacroArgumentType`、`FakePlayerArgumentType`、`NotebotSongArgumentType`、`Item`/`Block`/`Potion` 等（基于原版 `ArgumentType`）。
- 自动补全：`CommandSuggestionsMixin` 用 `DISPATCHER.getCompletionSuggestions(currentParse, cursor)` 异步返回，再 `updateUsageInfo`。
- 命令执行句柄内用 `context.getArgument("name", Type.class)` 或 `StringArgumentType.getString(context, "x")` 取参。

## 5. 进服重建 DISPATCHER 的真实路径

```java
@EventHandler
private static void onJoin(GameJoinedEvent event) {
    ClientPacketListener networkHandler = mc.getConnection();
    Command.REGISTRY_ACCESS = CommandBuildContext.simple(networkHandler.registryAccess(), networkHandler.enabledFeatures());
    DISPATCHER = new CommandDispatcher<>();
    for (Command command : COMMANDS) command.registerTo(DISPATCHER);
}
```

原因（源码注释）：参数类型靠 `CommandBuildContext` 访问 Minecraft 动态注册表，注册表每服不同，且 `CommandDispatcher` 节点只增不换，必须整体重建。

---

## Addon 用法 / 介入点

```java
public class ExampleCommand extends Command {
    public ExampleCommand() { super("example", "描述", "ex"); }   // "ex" 是别名
    @Override public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> { info("hi"); return SINGLE_SUCCESS; });
        builder.then(literal("name").then(argument("name", StringArgumentType.word())
            .executes(ctx -> { info("hi, " + StringArgumentType.getString(ctx, "name")); return SINGLE_SUCCESS; })));
    }
}
// AddonTemplate.onInitialize(): Commands.add(new ExampleCommand());
```

- 注册入口：`Commands.add(...)`（addon 里直接 `Commands.add(new Xxx())`，见 `AddonTemplate` 大量 `Commands.add`）。
- 命令名建议 kebab-case 且不含空格（`Command` 无显式校验，但 Brigadier literal 与聊天截取会受空格影响）。
- `getRepo`/`sendCommand` 都不要用；发消息用 `info/warning/error` 或 `ChatUtils.sendPlayerMsg`。

## 常见坑

1. **`ChatUtils.sendCommand` 不存在**：真实发命令是 `mc.player.connection.sendCommand(message.substring(1))`（`ChatUtils.sendPlayerMsg` 内部）。
2. **`CommandArgumentType` 已移除**：自定义参数类型请继承原版 ArgumentType 或复用 `commands/arguments/` 现有类型。
3. **同名前缀会被 `add` 覆盖**：`removeIf` 只按 name 不按别名，冲突静默顶替。
4. **DISPATCHER 按名不在 `COMMANDS` 排序后重挂**：`registerTo` 依赖 `onJoin` 重建；若 addon 在 `onJoin` 之后才 `add`，需注意时序（通常 add 都在 `onInitialize` 阶段，早于进服）。
5. **`return SINGLE_SUCCESS`**：Brigadier 要求 execute 返回 int，忘了 return 会编译报错。

## 源码依据

- `meteordevelopment/meteorclient/commands/Command.java` —— 构造、name/title/description/aliases、build/register/registerTo、argument/literal helper、info/warning/error。
- `meteordevelopment/meteorclient/commands/Commands.java` —— COMMANDS/DISPATCHER、init、add、dispatch、get、onJoin 重建。
- `meteordevelopment/meteorclient/mixin/ClientPacketListenerMixin.java` —— `onSendChatMessage` 前缀解析与 dispatch、普通消息 SendMessageEvent。
- `meteordevelopment/meteorclient/mixin/CommandSuggestionsMixin.java` —— 补全接入。
- `meteordevelopment/meteorclient/systems/config/Config.java` 相关（`Config.get().prefix`，前缀配置来源）。
- `meteordevelopment/meteorclient/commands/arguments/` —— 各内置 ArgumentType。
- `com/example/addon/commands/CommandExample.java`（addon）—— 真实命令示例（literal + argument + SINGLE_SUCCESS）。
- `com/example/addon/core/AddonTemplate.java`（addon）—— `Commands.add(...)` 注册入口。