package com.example.addon.stardew.command;

import com.example.addon.autofarm.AutoFarmMatrix;
import com.example.addon.core.YiyiaddonModule;
import com.example.addon.stardew.StardewFarmStrategy;
import com.example.addon.stardew.config.StardewSiteType;
import com.example.addon.stardew.model.StardewSeedProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 星露谷农场指令：锚点绑定 / 添加种子 / 登记农田方块 / 查看档案。
 * 归属「自动农场」模块的星露谷模式，非独立顶级模块。
 */
public class StardewCommand extends Command {

    public StardewCommand() {
        super("stardew", "管理星露谷农场的农田范围、种子与档案。", "sdw");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(_ -> showStatus());

        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");
        for (StardewSiteType type : StardewSiteType.values()) {
            set.then(literal(type.cn()).executes(_ -> bind(type)));
            set.then(literal(type.en()).executes(_ -> bind(type)));
            remove.then(literal(type.cn()).executes(_ -> unbind(type)));
            remove.then(literal(type.en()).executes(_ -> unbind(type)));
        }
        builder.then(set);
        builder.then(remove);
        builder.then(literal("clear").executes(_ -> clearAll()));
        builder.then(literal("addseed").executes(_ -> addSeed()));
        builder.then(literal("soiladd").executes(_ -> addSoil()));
        builder.then(literal("list").executes(_ -> list()));
    }

    private int showStatus() {
        StardewFarmStrategy strategy = strategy();
        if (strategy == null) return SINGLE_SUCCESS;

        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        info("§b§l         星露谷农场 ▸ 锚点绑定");
        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (StardewSiteType type : StardewSiteType.values()) {
            var site = strategy.site(type);
            if (site == null) {
                info("  " + color(type) + "■ §f§l" + type.cn() + " §8▸ §c未绑定");
            } else {
                info("  " + color(type) + "■ §f§l" + type.cn() + " §8▸ §a" + site.describe("§a"));
            }
        }
        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return SINGLE_SUCCESS;
    }

    private int list() {
        StardewFarmStrategy strategy = strategy();
        if (strategy == null) return SINGLE_SUCCESS;
        StardewServerProfile p = strategy.currentProfile();
        if (p == null) {
            info("§8暂无档案，先添加种子。");
            return SINGLE_SUCCESS;
        }
        info("§b§l种子列表");
        for (StardewSeedProfile seed : p.seeds()) {
            info("  §a" + seed.displayName() + " §8▸ §7" + seed.minecraftItemId()
                + " §8(" + (seed.enabled() ? "§a启用" : "§8停用") + "§8)");
        }
        if (!p.soilBlockIds().isEmpty()) {
            info("§b§l农田方块");
            for (String blockId : p.soilBlockIds()) info("  §a" + blockId);
        }
        return SINGLE_SUCCESS;
    }

    private int bind(StardewSiteType type) {
        StardewFarmStrategy strategy = strategy();
        AutoFarmMatrix autoFarm = autoFarm();
        if (strategy == null || autoFarm == null) return SINGLE_SUCCESS;
        if (mc.level == null) {
            error("当前不在游戏世界。");
            return SINGLE_SUCCESS;
        }
        if (autoFarm.isActive()) {
            error("模块运行中无法修改锚点，请先关闭模块");
            return SINGLE_SUCCESS;
        }
        if (strategy.site(type) != null) {
            error(type.cn() + "已绑定，请先 remove");
            return SINGLE_SUCCESS;
        }

        BlockPos target = targetBlock();
        if (target == null) {
            error("准星未对准任何方块。");
            return SINGLE_SUCCESS;
        }
        if (type.requiresContainer() && !isContainer(target)) {
            error("该锚点需要指向容器方块。");
            return SINGLE_SUCCESS;
        }

        var site = com.example.addon.autofarm.model.FarmSite.here(target);
        if (site == null) {
            error("无法获取当前维度。");
            return SINGLE_SUCCESS;
        }
        strategy.bindSite(type, site);
        info("§a§l✓ 绑定成功 §8▸ " + site.describe("§a"));
        return SINGLE_SUCCESS;
    }

    private int unbind(StardewSiteType type) {
        StardewFarmStrategy strategy = strategy();
        if (strategy == null) return SINGLE_SUCCESS;
        if (strategy.site(type) == null) {
            error(type.cn() + "本来就没有绑定。");
            return SINGLE_SUCCESS;
        }
        strategy.clearSite(type);
        info("§c§l✗ 已解绑 " + type.cn());
        return SINGLE_SUCCESS;
    }

    private int clearAll() {
        StardewFarmStrategy strategy = strategy();
        if (strategy == null) return SINGLE_SUCCESS;
        strategy.clearAllSites();
        info("§e已清空全部锚点。");
        return SINGLE_SUCCESS;
    }

    private int addSeed() {
        StardewFarmStrategy strategy = strategy();
        if (strategy == null) return SINGLE_SUCCESS;
        strategy.addSeedFromHeld();
        return SINGLE_SUCCESS;
    }

    private int addSoil() {
        StardewFarmStrategy strategy = strategy();
        if (strategy == null) return SINGLE_SUCCESS;
        strategy.addSoilFromCrosshair();
        return SINGLE_SUCCESS;
    }

    private BlockPos targetBlock() {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(hit instanceof BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    private boolean isContainer(BlockPos pos) {
        BlockEntity be = mc.level.getBlockEntity(pos);
        return be instanceof Container;
    }

    private String color(StardewSiteType type) {
        return switch (type) {
            case START -> "§a";
            case END -> "§e";
            case SEED_STORAGE -> "§b";
            case HARVEST_STORAGE -> "§d";
        };
    }

    private void info(String message) {
        if (mc.player == null || message == null) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("自动农场", message)));
    }

    private void error(String message) {
        if (mc.player == null || message == null) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("自动农场", "§6§l" + message)));
    }

    private AutoFarmMatrix autoFarm() {
        return Modules.get().get(AutoFarmMatrix.class);
    }

    private StardewFarmStrategy strategy() {
        AutoFarmMatrix m = autoFarm();
        if (m == null) {
            error("自动农场模块未注册。");
            return null;
        }
        return m.stardew();
    }
}
