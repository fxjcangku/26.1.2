package com.example.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * 指令示例类
 * 
 * Meteor Client 指令系统基于 Minecraft 原版的 Brigadier 命令框架
 * 参考文档：https://github.com/Mojang/brigadier
 */
public class CommandExample extends Command {
    
    /**
     * 构造函数
     * 
     * @param name 指令名称（必须使用 kebab-case 格式：小写+连字符）
     * @param description 指令描述
     */
    public CommandExample() {
        super("example", "Sends a message.");
    }

    /**
     * 构建指令语法树
     * 
     * 示例用法：
     * - .example        输出 "hi"
     * - .example name Alice   输出 "hi, Alice"
     */
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
