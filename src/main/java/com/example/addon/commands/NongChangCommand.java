package com.example.addon.commands;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.farm.FarmSite;
import com.example.addon.farm.SiteType;
import com.example.addon.modules.AutoFarmMatrix;
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
 * .farm                            查看当前四个锚点的绑定情况
 * .farm set 起点                   把准星指向的方块设为农田对角起点
 * .farm set 终点                   设为农田对角终点
 * .farm set 卸货箱                 设为卸货总仓（必须是容器）
 * .farm set 补货箱                 设为种子库（必须是容器）
 * .farm remove 卸货箱              解绑
 * .farm clear                      清空全部锚点
 * .farm status                     显示详细信息（含服务器IP）
 * </pre>
 *
 * 两条硬校验：
 * · 卸货箱与补货箱的目标方块必须带 Container 方块实体，指到石头上直接拒绝，
 *   避免状态机开容器开不出来然后原地卡死；
 * · 唯一锁：已绑定的锚点不允许直接覆盖，必须先 remove。防止手滑把跑了一半的
 *   农场坐标改到别处去。
 */
public class NongChangCommand extends Command {

    public NongChangCommand() {
        super("farm", "绑定自动农场的农田范围与物流箱子。", "nc", "nongchang");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // 裸指令：打印当前绑定状态
        builder.executes(_ -> {
            AutoFarmMatrix module = module();
            if (module == null) return SINGLE_SUCCESS;

            farmInfo("§b§l——— 农场锚点绑定情况 ———");
            for (SiteType type : SiteType.values()) {
                FarmSite site = module.site(type);
                if (site == null) {
                    farmInfo("§7" + type.cn() + "：§c未绑定");
                } else {
                    farmInfo("§7" + type.cn() + "：§a" + site.describe());
                }
            }
            return SINGLE_SUCCESS;
        });

        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");

        for (SiteType type : SiteType.values()) {
            set.then(literal(type.cn()).executes(_ -> bind(type)));
            remove.then(literal(type.cn()).executes(_ -> unbind(type)));
        }

        builder.then(set);
        builder.then(remove);

        // 一键清空四个锚点，重设整片农场时省得敲四次 remove
        builder.then(literal("clear").executes(_ -> clearAll()));
        
        // status 指令：显示四个锚点的完整信息
        builder.then(literal("status").executes(_ -> showStatus()));
    }

    private int showStatus() {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        // 获取服务器信息
        String serverInfo = "单人世界";
        if (mc.getCurrentServer() != null) {
            serverInfo = mc.getCurrentServer().ip;
        }

        farmInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        farmInfo("§b§l         农场锚点详细信息");
        farmInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        farmInfo("");
        farmInfo("  §7服务器: §f" + serverInfo);
            farmInfo("");
        
        for (SiteType type : SiteType.values()) {
            FarmSite site = module.site(type);
            String icon = switch(type) {
                case START -> "▶";
                case END -> "◀";
                case DUMP -> "↓";
                case SUPPLY -> "↑";
            };
            
            if (site == null) {
                farmInfo("  " + icon + " §f§l" + type.cn());
                farmInfo("    §8└─ §c未绑定");
            } else {
                farmInfo("  " + icon + " §f§l" + type.cn());
                farmInfo("    §8├─ §7坐标: §a" + site.pos().getX() + ", " + site.pos().getY() + ", " + site.pos().getZ());
                farmInfo("    §8└─ §7维度: §b" + site.describe().split("@ ")[1]);
            }
            if (type != SiteType.SUPPLY) farmInfo("");
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
            farmError("四个锚点本来就都没有绑定。");
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
        
        String icon = switch(type) {
            case START -> "▶";
            case END -> "◀";
            case DUMP -> "↓";
            case SUPPLY -> "↑";
        };
        
        farmInfo("");
        farmInfo("§a§l✓ 绑定成功");
        farmInfo("  " + icon + " §f" + type.cn() + " §8→ §a" + site.describe());
        farmInfo("");
        
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
        farmInfo("§e已解绑 " + type.cn());
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

    private int handleDefault() {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        farmInfo("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        farmInfo("§6§l           自动农场");
        farmInfo("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        farmInfo("");
        
        for (SiteType type : SiteType.values()) {
            FarmSite site = module.site(type);
            String icon = switch(type) {
                case START -> "▶";
                case END -> "◀";
                case DUMP -> "↓";
                case SUPPLY -> "↑";
            };
            
            if (site == null) {
                farmInfo("  " + icon + " §7" + type.cn() + " §8→ §c未绑定");
            } else {
                farmInfo("  " + icon + " §7" + type.cn() + " §8→ §a" + site.describe());
            }
        }
        
        farmInfo("");
        farmInfo("§e提示: 使用 §e§l.nongchang status §r§e查看详细信息");
        farmInfo("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return SINGLE_SUCCESS;
    }

    private void farmInfo(String message) {
        if (mc.player == null) return;
        if (message == null) return;
        // 去除颜色代码后检查是否为空
        String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (clean.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("自动农场", message)));
    }

    private void farmError(String message) {
        if (mc.player == null) return;
        if (message == null) return;
        String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (clean.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("自动农场", "§6§l" + message)));
    }

    private AutoFarmMatrix module() {
        AutoFarmMatrix module = Modules.get().get(AutoFarmMatrix.class);
        if (module == null) farmError("自动农场模块未注册。");
        return module;
    }
}
