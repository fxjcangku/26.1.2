package com.example.addon.autofarm.command;

import com.example.addon.autofarm.AutoFarmMatrix;
import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.model.SiteType;
import com.example.addon.core.YiyiaddonModule;
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
 * 自动农场的锚点绑定指令。
 *
 * <pre>
 * .farm                               查看六个锚点的绑定情况
 * .farm set 农场点位1/农场点位2       绑定农田范围对角
 * .farm set 单作物箱/种子补货箱/多作物箱  绑定对应作物箱（必须是容器）
 * .farm set 杂物箱                绑定杂物独立处理箱（必须是容器）
 * .farm remove 任意锚点               解绑
 * .farm status                        显示详细信息
 * .farm clear                         清空全部锚点
 * </pre>
 *
 * 硬校验：容器类锚点必须命中 Container 方块实体；模块运行中禁止修改；
 * 已绑定必须先 remove 才能覆盖。
 */
public class NongChangCommand extends Command {

    public NongChangCommand() {
        super("farm", "绑定自动农场的农田范围与物流箱子。", "nc", "nongchang");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // 裸指令：打印绑定状态
        builder.executes(_ -> showStatus());

        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");

        for (SiteType type : SiteType.values()) {
            set.then(literal(type.cn()).executes(_ -> bind(type)));
            set.then(literal(type.en()).executes(_ -> bind(type)));
            remove.then(literal(type.cn()).executes(_ -> unbind(type)));
            remove.then(literal(type.en()).executes(_ -> unbind(type)));
        }

        builder.then(set);
        builder.then(remove);
        builder.then(literal("status").executes(_ -> showStatus()));
        builder.then(literal("clear").executes(_ -> clearAll()));
    }

    private int showStatus() {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        String serverInfo = "单人世界";
        if (mc.getCurrentServer() != null) {
            serverInfo = mc.getCurrentServer().ip;
        }

        farmInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        farmInfo("§b§l         自动农场 ▸ 锚点绑定");
        farmInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        farmInfo("  §7服务器 ▸ §f" + serverInfo);

        for (SiteType type : SiteType.values()) {
            FarmSite site = module.site(type);
            if (site == null) {
                farmInfo("  " + color(type) + "■ §f§l" + type.cn() + " §8▸ §c未绑定");
            } else {
                farmInfo("  " + color(type) + "■ §f§l" + type.cn());
                farmInfo("    §8├─ §7坐标 ▸ " + site.describe("§f"));
                farmInfo("    §8└─ §7维度 ▸ §b" + site.describe("§f").split("▸ ")[1]);
            }
        }
        farmInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return SINGLE_SUCCESS;
    }

    private int clearAll() {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        int bound = 0;
        for (SiteType type : SiteType.values()) {
            if (module.site(type) != null) bound++;
        }

        if (bound == 0) {
            farmError("六个锚点本来就都没有绑定。");
            return SINGLE_SUCCESS;
        }

        module.clearAllSites();
        farmInfo("§e已清空全部锚点（共 " + bound + " 个），农田范围同时重置。");
        return SINGLE_SUCCESS;
    }

    private int bind(SiteType type) {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        if (mc.level == null) {
            farmError("当前不在游戏世界中，无法绑定锚点");
            return SINGLE_SUCCESS;
        }

        if (module.isActive()) {
            farmError("模块运行中无法修改锚点，请先关闭模块");
            return SINGLE_SUCCESS;
        }

        // 覆盖保护
        if (module.site(type) != null) {
            farmError(type.cn() + "已绑定，请先删除旧绑定再重新设置");
            farmInfo("§7提示：使用 §e.farm remove " + type.cn() + " §7删除");
            return SINGLE_SUCCESS;
        }

        BlockPos target = targetBlock();
        if (target == null) {
            farmError("准星未对准任何方块，请将准星对准要绑定的方块");
            return SINGLE_SUCCESS;
        }

        if (type.requiresContainer() && !isContainer(target)) {
            farmError("该锚点需要指向容器方块（箱子/桶/潜影盒等），但准星对准的不是容器");
            return SINGLE_SUCCESS;
        }

        FarmSite site = FarmSite.here(target);
        if (site == null) {
            farmError("无法获取当前维度信息");
            return SINGLE_SUCCESS;
        }

        module.bindSite(type, site);

        farmInfo("§a§l✓ 绑定成功");
        farmInfo("  " + color(type) + "■ §f§l" + type.cn() + " §8▸ §a" + site.describe("§a"));
        return SINGLE_SUCCESS;
    }

    private int unbind(SiteType type) {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        if (module.site(type) == null) {
            farmError(type.cn() + "本来就没有绑定。");
            return SINGLE_SUCCESS;
        }

        module.clearSite(type);
        farmInfo("§c§l✗ 已解绑 " + type.cn());
        return SINGLE_SUCCESS;
    }

    /** 取准星命中的方块坐标，没命中方块返回 null */
    private BlockPos targetBlock() {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(hit instanceof BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    /** 该坐标是否带有实现了 Container 的方块实体 */
    private boolean isContainer(BlockPos pos) {
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        return blockEntity instanceof Container;
    }

    private String color(SiteType type) {
        return switch (type) {
            case START -> "§a";
            case END -> "§e";
            case SINGLE_STORAGE -> "§6";
            case MULTI_STORAGE -> "§d";
            case SEED_STORAGE -> "§b";
            case POISON_STORAGE -> "§c";
        };
    }

    private void farmInfo(String message) {
        if (mc.player == null || message == null) return;
        String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (clean.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("自动农场", message)));
    }

    private void farmError(String message) {
        if (mc.player == null || message == null) return;
        String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (clean.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("自动农场", "§6§l" + message)));
    }

    private AutoFarmMatrix module() {
        AutoFarmMatrix module = Modules.get().get(AutoFarmMatrix.class);
        if (module == null) farmError("自动农场模块未注册。");
        return module;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  静态工具（供 GUI 卡片调用）
    // ═══════════════════════════════════════════════════════════════════

    public static boolean hasBinding(SiteType type) {
        AutoFarmMatrix module = Modules.get().get(AutoFarmMatrix.class);
        return module != null && module.site(type) != null;
    }

    public static boolean setBinding(SiteType type) {
        NongChangCommand cmd = new NongChangCommand();
        AutoFarmMatrix module = cmd.module();
        if (module == null) return false;

        if (module.isActive()) {
            cmd.farmError("模块运行中无法修改锚点，请先关闭模块");
            return false;
        }

        if (module.site(type) != null) {
            cmd.farmError(type.cn() + "已绑定，请先删除旧绑定再重新设置");
            return false;
        }

        BlockPos target = cmd.targetBlock();
        if (target == null) {
            cmd.farmError("准星未对准任何方块，请重新设置");
            return false;
        }
        if (type.requiresContainer() && !cmd.isContainer(target)) {
            cmd.farmError("目标方块不是容器（箱子/桶/潜影盒等），请重新设置");
            return false;
        }

        cmd.bind(type);
        return true;
    }

    public static void removeBinding(SiteType type) {
        NongChangCommand cmd = new NongChangCommand();
        AutoFarmMatrix module = cmd.module();
        if (module == null) return;

        if (module.site(type) == null) {
            cmd.farmError(type.cn() + "本来就没有绑定");
        } else {
            module.clearSite(type);
            cmd.farmInfo("§c§l✗ 已删除 " + type.cn() + " 绑定");
        }
    }
}
