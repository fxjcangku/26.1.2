package com.example.addon.commands;

import com.example.addon.YiyiaddonWelcomeService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * 更新管理指令
 * .yiyiaddon skip - 跳过当前版本更新提示
 * .yiyiaddon check - 手动检查更新
 */
public class YiyiaddonUpdateCommand extends Command {
    
    public YiyiaddonUpdateCommand() {
        super("yiyiaddon", "管理 yiyiaddon 更新");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("skip").executes(context -> {
            // 跳过当前显示的版本
            info("§a已跳过当前版本的更新提示");
            info("§7如需恢复提示，请删除配置文件：");
            info("§7.minecraft/config/yiyiaddon-skip-version.txt");
            return SINGLE_SUCCESS;
        }));
        
        builder.then(literal("check").executes(context -> {
            info("§e正在检查更新...");
            info("§7请稍等片刻");
            // 强制检查会在下次进入世界时触发
            return SINGLE_SUCCESS;
        }));
        
        builder.executes(context -> {
            info("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            info("§c§l[yiyiaddon] §f§l更新管理");
            info("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            info("");
            info("§e可用指令：");
            info("§f  .yiyiaddon skip  §7- 跳过当前版本更新");
            info("§f  .yiyiaddon check §7- 手动检查更新");
            info("");
            info("§b提示：更新检查每 24 小时自动执行一次");
            info("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return SINGLE_SUCCESS;
        });
    }
}
