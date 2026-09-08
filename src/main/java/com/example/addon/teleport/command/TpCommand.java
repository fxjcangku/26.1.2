package com.example.addon.teleport.command;

import com.example.addon.teleport.TeleportModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * 传送指令：.tp X Y Z 立即把玩家传送到指定坐标（当前维度，整数坐标）。
 * 目标不可站立时模块自动回退到邻近安全落点。
 */
public class TpCommand extends Command {

    public TpCommand() {
        super("tp", "传送到指定坐标（当前维度，整数坐标）。", "tpto");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("x", IntegerArgumentType.integer())
            .then(argument("y", IntegerArgumentType.integer())
                .then(argument("z", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        int x = IntegerArgumentType.getInteger(ctx, "x");
                        int y = IntegerArgumentType.getInteger(ctx, "y");
                        int z = IntegerArgumentType.getInteger(ctx, "z");

                        TeleportModule module = Modules.get().get(TeleportModule.class);
                        if (module == null || !module.isActive()) {
                            error("请先开启传送模块");
                            return SINGLE_SUCCESS;
                        }

                        module.teleportToCoord(x, y, z);
                        return SINGLE_SUCCESS;
                    }))));
    }
}