package com.example.addon.commands;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.utils.YiyiaddonWelcomeService;
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
            updateInfo("§a已跳过当前版本的更新提示");
            updateInfo("§7如需恢复提示，请删除配置文件：");
            updateInfo("§7.minecraft/config/yiyiaddon-skip-version.txt");
            return SINGLE_SUCCESS;
        }));
        
        builder.then(literal("check").executes(context -> {
            updateInfo("§e正在检查更新...");
            updateInfo("§7请稍等片刻");
            return SINGLE_SUCCESS;
        }));
        
        builder.executes(context -> {
            updateInfo("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            updateInfo("§6§l           更新管理");
            updateInfo("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            updateInfo("");
            updateInfo("§e可用指令：");
            updateInfo("§f  .yiyiaddon skip  §7- 跳过当前版本更新");
            updateInfo("§f  .yiyiaddon check §7- 手动检查更新");
            updateInfo("");
            updateInfo("§b提示：更新检查每 24 小时自动执行一次");
            updateInfo("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return SINGLE_SUCCESS;
        });
    }

    private void updateInfo(String message) {
        if (message == null || message.trim().isEmpty() || mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("更新管理", message)));
    }
}
