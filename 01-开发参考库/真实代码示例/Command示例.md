# Command 示例

## 概述

26.1.2 Meteor 指令系统基于原版 Brigadier + Meteor 封装基类 `meteordevelopment.meteorclient.commands.Command`。自定义指令三步：

1. 继承 `Command`，构造函数 `super("名字", "描述")`（名字用 kebab-case）。
2. 覆写 `build(LiteralArgumentBuilder<ClientSuggestionProvider> builder)` 搭建语法树。
3. 在 Addon `onInitialize()` 里 `Commands.get().add(new XxxCommand())` 注册（见「注册示例」）。

指令默认前缀是 `.`（如 `.example`），成功执行返回 `SINGLE_SUCCESS`。基类提供 `info(...)`/`warning(...)`/`error(...)` 输出方法，构建辅助方法 `literal(...)`/`argument(...)`。**注意 `Command` 没有 `sendCommand`**；向玩家输出本项目消息走 `YiyiaddonModule.formatMessage(...)` 或 `mc.player.sendSystemMessage(...)`。

---

## 示例 1：最小指令（无参 + 带一个参数）

出处：`src/main/java/com/example/addon/commands/CommandExample.java`（全文）

```java
package com.example.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class CommandExample extends Command {

    public CommandExample() {
        super("example", "Sends a message.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // 无参数版本：.example
        builder.executes(_ -> {
            info("hi");
            return SINGLE_SUCCESS;
        });

        // 带参数版本：.example name <名字>
        builder.then(literal("name").then(argument("nameArgument", StringArgumentType.word()).executes(context -> {
            String argument = StringArgumentType.getString(context, "nameArgument");
            info("hi, " + argument);
            return SINGLE_SUCCESS;
        })));
    }
}
```

要点：
- 构造函数 `super("example", ...)` 定指令名（注册后即时 `.example`）。
- `build()` 里 `builder` 是根节点，`executes(...)` 挂无参分支，`then(literal(...).then(argument(...).executes(...)))` 挂子命令。
- 参数类型来自 `com.mojang.brigadier.arguments.*`（`StringArgumentType`、`IntegerArgumentType` 等），取参用对应 `getString/getInteger(context, "参数名")`。
- 命令成功一律 `return SINGLE_SUCCESS;`（基类常量，值为 1）。

---

## 示例 2：完整多点位管理指令（literal 子命令 + 静态数据 + 持久化）

出处：`src/main/java/com/example/addon/commands/WKCommand.java`（第 78-118 行）

```java
import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class WKCommand extends Command {

    public WKCommand() {
        super("wk", "挖矿坐标绑定（矿物箱、食物箱、挂机修复点）");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // .wk / .wk status
        builder.executes(ctx -> { showStatus(); return SINGLE_SUCCESS; });
        builder.then(literal("status").executes(ctx -> { showStatus(); return SINGLE_SUCCESS; }));

        // .wk set <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");
        set.then(literal("矿物箱").executes(ctx -> bindMineralChest()));
        set.then(literal("食物箱").executes(ctx -> bindFoodChest()));
        set.then(literal("挂机修复点").executes(ctx -> bindAFKPoint()));
        builder.then(set);

        // .wk remove <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");
        remove.then(literal("矿物箱").executes(ctx -> unbind("mineral")));
        remove.then(literal("食物箱").executes(ctx -> unbind("food")));
        remove.then(literal("挂机修复点").executes(ctx -> unbind("afk")));
        builder.then(remove);

        // .wk clear / .wk checkfake
        builder.then(literal("clear").executes(ctx -> clearAll()));
        builder.then(literal("checkfake").executes(ctx -> checkFakeOres()));
    }
}
```

要点：
- `literal("中文子命令")` 完全合法（Brigadier 的 literal 就是字符串），本项目大量用中文作子命令名，与中文 UI 一致。
- 同一命令支持根执行（`builder.executes`）与子命令并行。
- 指令绑定业务方法返回 `int`（成功 `SINGLE_SUCCESS`），静默执行用 `executes(() -> doXxx())`。

---

## 示例 3：指令内输出中文消息（走统一前缀）

出处：`src/main/java/com/example/addon/commands/WKCommand.java`（第 475-494 行）

```java
import com.example.addon.core.YiyiaddonModule;
import net.minecraft.network.chat.Component;

private void wkInfo(String message) {
    if (mc.player == null) return;
    if (message == null) return;
    String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    if (clean.isEmpty()) return;
    mc.player.sendSystemMessage(Component.literal(
        YiyiaddonModule.formatMessage("自动挖矿", message)));
}

private void wkError(String message) {
    if (mc.player == null) return;
    if (message == null) return;
    String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    if (clean.isEmpty()) return;
    mc.player.sendSystemMessage(Component.literal(
        YiyiaddonModule.formatMessage("自动挖矿", "§6§l" + message)));
}
```

要点：
- 指令上下文没有模块实例，无法用基类 `notify`；本项目统一用 `YiyiaddonModule.formatMessage("模块名", 消息)` 生成带 `[yiyiaddon][模块名]` 前缀的消息，再 `mc.player.sendSystemMessage(Component.literal(...))` 输出。
- `formatMessage` 是 `YiyiaddonModule` 的静态方法（`core/YiyiaddonModule.java`），规避指令里硬编码前缀。

---

## 模式要点

1. **指令树结构**：`build(builder)` 是唯一覆写点；`builder.executes` 挂根、`builder.then(...)` 挂子命令，子命令可无限 `then` 嵌套。
2. **返回码**：所有成功路径返回 `SINGLE_SUCCESS`；需要失败语义时返回 0（Brigadier 约定非 0 表示成功）。
3. **中文 literal 可用**：literal 是任意字符串，无需为中文转拼音；与 `.name(...)` 的 kebab-case 不同。
4. **输出统一前缀**：指令不用模块基类的 `notify`，用 `YiyiaddonModule.formatMessage("模块", msg)` 拼前缀。
5. **静态共享数据 + 模块联动**：指令类可用 static 字段存数据（`WKCommand.DATA_STORE`），模块配页按钮调用 `WKCommand.setBinding(...)` 静态方法实现「按钮 vs 指令」双入口。

## 常见坑

- **`info()` 是基类方法，别自己重名**：`Command` 基类已有 `info/warning/error`，指令里直接 `info("...")` 会带 Meteor 前缀（英文），中文化要用 `formatMessage` + `sendSystemMessage`。
- **忘记 `return SINGLE_SUCCESS`**：executes 回调要求返回 int，漏返回会抛 NPE/爆炸；`executes(ctx -> { ... })` lambda 最后必须 `return SINGLE_SUCCESS;`。
- **参数名与取参名不一致**：`argument("nameArgument", ...)` 注册的名字必须和 `getString(context, "nameArgument")` 完全一致。
- **用 `mc.player.connection.sendCommand` 发指令**：在指令执行业务里再发指令会触发递归/重复，避免；虽然合法但不建议。
- **注册错过 Addon 入口**：指令必须在 `AddonTemplate.onInitialize()` 里 `Commands.add(new XxxCommand())`，否则命令不存在（见「注册示例」）。

## 26.1.2 注意

- **`ClientSuggestionProvider` 泛型**：`LiteralArgumentBuilder<ClientSuggestionProvider>` 是 26.1.2 类型实参，旧教程写 `CommandSourceStack`（服务端）编译不过。
- **`Command` 基类没有 `sendCommand`**：发指令走 `ChatUtils.sendPlayerMsg("/xxx")` 或 `mc.player.connection.sendCommand(...)`，不要把命令类当发送器。
- **`.name(...)` 必须 kebab-case**：注册/配置文件 key 都依赖它；中文显示名建议放 `description` 或子命令里，而不是 name。
- **Brigadier 依赖**：`com.mojang.brigadier.arguments.*` 与 `com.mojang.brigadier.builder.LiteralArgumentBuilder` 由 Fabric 平台注入，无需手动声明依赖。
- **`separator` / 更高版本 `literal` 重载**：本项目统一用 `builder.then(literal("..."))` 与 `builder.executes(...)`，不要抄旧版 `builder.argument(...)` 顶层注册方式。