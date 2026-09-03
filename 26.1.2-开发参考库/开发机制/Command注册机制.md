# Command 注册机制

> Meteor 的命令系统基于 Mojang **Brigadier**：每个 `Command` 子类在 `build(LiteralArgumentBuilder)` 里用 `literal`/`argument` 声明语法树，`Commands` 统一维护命令列表 + `CommandDispatcher`，玩家以 `Config.get().prefix.get()`（默认 `.`）打头输入即被 `ClientPacketListenerMixin` 拦截并转发到 `Commands.dispatch`。

## 概述

- `Command`（抽象）：单个命令，声明 `name/title/description/aliases`，实现 `build`。
- `Commands`：静态命令注册表 + `CommandDispatcher<ClientSuggestionProvider>`。
- `ClientPacketListenerMixin.sendChat`：拦截聊天、识别前缀、触发 `dispatch`。

---

## 1. Command 抽象类

```java
public abstract class Command {
    protected static CommandBuildContext REGISTRY_ACCESS = Commands.createValidationContext(VanillaRegistries.createLookup());
    protected static final int SINGLE_SUCCESS = com.mojang.brigadier.Command.SINGLE_SUCCESS;
    protected static final Minecraft mc = MeteorClient.mc;

    private final String name;       // 命令名（小写）
    private final String title;      // Utils.nameToTitle(name)
    private final String description;
    private final List<String> aliases;

    public Command(String name, String description, String... aliases) { ... }

    protected static <T> RequiredArgumentBuilder<ClientSuggestionProvider, T> argument(final String name, final ArgumentType<T> type) {...}
    protected static LiteralArgumentBuilder<ClientSuggestionProvider> literal(final String name) {...}

    public final void registerTo(CommandDispatcher<ClientSuggestionProvider> dispatcher) {
        register(dispatcher, name);
        for (String alias : aliases) register(dispatcher, alias);   // 别名也注册
    }
    public void register(CommandDispatcher<ClientSuggestionProvider> dispatcher, String name) {
        LiteralArgumentBuilder<ClientSuggestionProvider> builder = LiteralArgumentBuilder.literal(name);
        build(builder);
        dispatcher.register(builder);
    }

    public abstract void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder);
}
```

`CommandSource` 泛型统一为 `ClientSuggestionProvider`（客户端 suggestions provider，源码里用 `argument`/`literal` 两个静态 helper 免写泛型）。

典型 `build`：

```java
public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
    builder.executes(_ -> { info("hi"); return SINGLE_SUCCESS; });
    builder.then(literal("name").then(argument("nameArgument", StringArgumentType.word()).executes(context -> {
        String s = StringArgumentType.getString(context, "nameArgument");
        info("hi, " + s);
        return SINGLE_SUCCESS;
    })));
}
```

命令里输出信息用 `info/warning/error`（内部 `ChatUtils.forceNextPrefixClass(getClass())` 后走 `sendMsg/infoPrefix/...`，前缀是命令 `title`），`toString()` 返回 `Config.get().prefix.get() + name`（即 `.help` 形式）。

## 2. Commands 注册表

```java
public class Commands {
    public static final List<Command> COMMANDS = new ArrayList<>();
    public static CommandDispatcher<ClientSuggestionProvider> DISPATCHER = new CommandDispatcher<>();

    @PostInit(dependencies = PathManagers.class)
    public static void init() {
        add(new VClipCommand()); add(new HClipCommand()); ... add(new HelpCommand());   // ~40 个内置
        COMMANDS.sort(Comparator.comparing(Command::getName));
        MeteorClient.EVENT_BUS.subscribe(Commands.class);
    }

    public static void add(Command command) {
        COMMANDS.removeIf(existing -> existing.getName().equals(command.getName()));   // 同名替换
        COMMANDS.add(command);
    }

    public static void dispatch(String message) throws CommandSyntaxException {
        DISPATCHER.execute(message, mc.getConnection().getSuggestionsProvider());
    }

    public static Command get(String name) { /* 线性查 name */ }
}
```

- `init()` 用 `@PostInit(dependencies = PathManagers.class)`：依赖 `PathManagers` 初始化完成后才执行（避免命令引用 Baritone 路径管理器时空指针）。
- `add` 也是「同名替换」语义。
- `dispatch` 调 `DISPATCHER.execute(message, mc.getConnection().getSuggestionsProvider())`。

### 为什么每进服重建 DISPATCHER

`@EventHandler private static void onJoin(GameJoinedEvent event)`：

```java
ClientPacketListener networkHandler = mc.getConnection();
Command.REGISTRY_ACCESS = CommandBuildContext.simple(networkHandler.registryAccess(), networkHandler.enabledFeatures());

DISPATCHER = new CommandDispatcher<>();
for (Command command : COMMANDS) command.registerTo(DISPATCHER);
```

源码注释（`@author Crosby`）解释三点：①依赖注册表的 argument type 在 build 时存了 registry wrapper，动态注册表每服不同；②注册表条目用**引用相等**比较，换服后旧 wrapper 变 stale；③`CommandDispatcher` 节点合并只补缺少的子节点、不能替换 stale argument type。所以**每次 `GameJoinedEvent` 都重建 dispatcher**。

## 3. 前缀解析与分发（ClientPacketListenerMixin.sendChat）

```java
@Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
private void onSendChatMessage(String message, CallbackInfo ci, @Local(argsOnly = true, name = "content") LocalRef<String> messageRef) {
    if (!message.startsWith(Config.get().prefix.get()) && !(BaritoneUtils.IS_AVAILABLE && message.startsWith(BaritoneUtils.getPrefix()))) {
        SendMessageEvent event = MeteorClient.EVENT_BUS.post(SendMessageEvent.get(message));   // 普通聊天
        if (!event.isCancelled()) messageRef.set(event.message); else ci.cancel();
        return;
    }
    if (message.startsWith(Config.get().prefix.get())) {
        try { Commands.dispatch(message.substring(Config.get().prefix.get().length())); }
        catch (CommandSyntaxException e) { ChatUtils.error(e.getMessage()); }
        minecraft.gui.getChat().addRecentChat(message);
        ci.cancel();        // 命令不进聊天框发送
    }
}
```

- 以 `Config.get().prefix.get()`（`.`）开头的消息：截掉前缀 → `Commands.dispatch` → 语法错误用 `ChatUtils.error` 输出 → 加入最近聊天历史 → `ci.cancel()` 不让它真正发给服务器。
- Baritone 前缀 `/` 或 `#` 由 `BaritoneUtils` 单独判断，不占用 Meteor 前缀。

## 运行链路

1. 启动：`MeteorClient.onInitializeClient` → `ReflectInit.init(PostInit.class)` → `Commands.init()`（add 内置命令、排序、订阅事件）。
2. addon 在 `onInitialize`（早于 PostInit）调 `Commands.add(new MyCommand())` 加入 `COMMANDS`。
3. 玩家进服：`GameJoinedEvent` → `Commands.onJoin` 重建 `DISPATCHER`，逐个 `command.registerTo(DISPATCHER)`。
4. 玩家输入 `.命令 参数` → `ClientPacketListenerMixin.onSendChatMessage` → `Commands.dispatch` → `DISPATCHER.execute` → 命中节点 → `build` 里注册的 `executes` 回调。

## Addon 用法 / 介入点

```java
// 自定义命令（在 addon.onInitialize 里注册）
public class MyCommand extends Command {
    public MyCommand() { super("mycmd", "我的命令描述", "mc"); }   // 别名 mc
    @Override public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> { info("hello"); return SINGLE_SUCCESS; });
        builder.then(argument("target", StringArgumentType.word())
            .executes(ctx -> { info("arg=%s", StringArgumentType.getString(ctx, "target")); return SINGLE_SUCCESS; }));
    }
}
// AddonTemplate.onInitialize 里：
Commands.add(new MyCommand());
```

`BuildCommand` / `ToggleCommand` / `SettingCommand` 等（`meteordevelopment.meteorclient.commands.commands`）是内置命令真实写法，参考它们如何拿 `Modules.get().get(...)`、`getInfoString`。

## 常见坑

1. **`SINGLE_SUCCESS` 必须返回**：`executes` 回调要 `return SINGLE_SUCCESS`（`com.mojang.brigadier.Command.SINGLE_SUCCESS`），否则 Brigadier 报「command expected to return integer」。
2. **命令注册进 `DISPATCHER` 的时机是进服后**：`Commands.add` 只进 `COMMANDS` 列表，真正 `registerTo` 在 `onJoin`；因此「命令可见」需要进服（`GameJoinedEvent`）之后。
3. **`build` 里别用静态 `Command.REGISTRY_ACCESS` 之外的自建 registry wrapper**：argument type 依赖注册表时用 `Command.REGISTRY_ACCESS`（每服被 `onJoin` 刷新），否则换服后注册表 stale（见上）。
4. **前缀统一走 `Config.get().prefix.get()`**：不要硬编码 `.`，`dispatch` 传入的是**去掉前缀后的字符串**。
5. **`toString()` 默认带前缀**：命令帮助文本里 `command.toString()` 就是 `.name` 形态，别再加一次前缀。
6. **ChatUtils 错误通道**：`dispatch` 抛 `CommandSyntaxException` 由 mixin 捕获用 `ChatUtils.error` 输出，命令内部报错应用 `error(...)` 而非 `System.out`。

## 源码依据

- `meteordevelopment/meteorclient/commands/Command.java` —— 构造、`argument/literal` helper、`registerTo/register/build`、`info/warning/error`、`toString`。
- `meteordevelopment/meteorclient/commands/Commands.java` —— `COMMANDS`/`DISPATCHER`、`init`(`@PostInit`)、`add`、`dispatch`、`get`、`onJoin` 重建 dispatcher。
- `meteordevelopment/meteorclient/mixin/ClientPacketListenerMixin.java` —— `sendChat` 前缀解析、`SendMessageEvent`、命令分发、语法错误输出。
- `meteordevelopment/meteorclient/commands/commands/HelpCommand.java` 等 —— 内置命令 `build` 真实写法。
- `meteordevelopment/meteorclient/systems/config/Config.java` —— `prefix` 设置来源。
- `src/main/java/com/example/addon/commands/CommandExample.java`（addon）—— addon 自定义命令真实用法。