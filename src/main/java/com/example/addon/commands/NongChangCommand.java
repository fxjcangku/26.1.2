package com.example.addon.commands;

import com.example.addon.farm.FarmSite;
import com.example.addon.farm.SiteType;
import com.example.addon.modules.AutoFarmMatrix;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 自动物流农场的锚点绑定指令。
 *
 * <pre>
 * .nongchang                       查看当前四个锚点的绑定情况
 * .nongchang set 起点              把准星指向的方块设为农田对角起点
 * .nongchang set 终点              设为农田对角终点
 * .nongchang set 卸货箱            设为卸货总仓（必须是容器）
 * .nongchang set 补货箱            设为种子库（必须是容器）
 * .nongchang remove 卸货箱         解绑
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
        super("nongchang", "绑定自动物流农场的农田范围与物流箱子。", "nc");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // 裸指令：打印当前绑定状态
        builder.executes(_ -> {
            AutoFarmMatrix module = module();
            if (module == null) return SINGLE_SUCCESS;

            info("§b§l——— 农场锚点绑定情况 ———");
            for (SiteType type : SiteType.values()) {
                FarmSite site = module.site(type);
                if (site == null) {
                    info("§7" + type.cn() + "：§c未绑定");
                } else {
                    info("§7" + type.cn() + "：§a" + site.describe());
                }
            }
            return SINGLE_SUCCESS;
        });

        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");

        for (SiteType type : SiteType.values()) {
            // 中英两套字面量都注册，中文输入法切换不过来时可以用英文
            set.then(literal(type.cn()).executes(_ -> bind(type)));
            set.then(literal(type.en()).executes(_ -> bind(type)));
            remove.then(literal(type.cn()).executes(_ -> unbind(type)));
            remove.then(literal(type.en()).executes(_ -> unbind(type)));
        }

        builder.then(set);
        builder.then(remove);

        // 一键清空四个锚点，重设整片农场时省得敲四次 remove
        builder.then(literal("clear").executes(_ -> clearAll()));
        
        // status 指令：显示四个锚点的完整信息
        builder.then(literal("status").executes(_ -> showStatus()));
    }

    private int clearAll() {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        int bound = 0;
        for (SiteType type : SiteType.values()) {
            if (module.site(type) != null) bound++;
        }

        if (bound == 0) {
            error("四个锚点本来就都没有绑定。");
            return SINGLE_SUCCESS;
        }

        module.clearAllSites();
        info("§e已清空全部锚点（共 " + bound + " 个），农田范围同时重置。");
        return SINGLE_SUCCESS;
    }

    private int bind(SiteType type) {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        if (mc.level == null || mc.player == null) {
            error("尚未进入世界。");
            return SINGLE_SUCCESS;
        }

        // 唯一锁：占用中的锚点必须显式 remove 才能重设
        FarmSite existing = module.site(type);
        if (existing != null) {
            error(type.cn() + "已绑定在 " + existing.describe() + "，请先执行 .nongchang remove " + type.cn());
            return SINGLE_SUCCESS;
        }

        BlockPos pos = targetBlock();
        if (pos == null) {
            error("准星没有指向方块，请对准目标后再执行。");
            return SINGLE_SUCCESS;
        }

        // 物流箱子必须真的是容器，否则状态机会卡在等待容器打开
        if (type.requiresContainer() && !isContainer(pos)) {
            error("目标方块不是容器，" + type.cn() + "必须绑定箱子、桶或潜影盒。");
            return SINGLE_SUCCESS;
        }

        FarmSite site = FarmSite.here(pos);
        if (site == null) {
            error("绑定失败，无法读取当前维度。");
            return SINGLE_SUCCESS;
        }

        module.bindSite(type, site);
        info("§a已绑定 " + type.cn() + " → " + site.describe());
        return SINGLE_SUCCESS;
    }

    private int unbind(SiteType type) {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        if (module.site(type) == null) {
            error(type.cn() + "本来就没有绑定。");
            return SINGLE_SUCCESS;
        }

        module.clearSite(type);
        info("§e已解绑 " + type.cn());
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

    private int showStatus() {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        info("§b§l——— 农场锚点绑定情况 ———");
        for (SiteType type : SiteType.values()) {
            FarmSite site = module.site(type);
            if (site == null) {
                info("§7" + type.cn() + "：§c未绑定");
            } else {
                info("§7" + type.cn() + "：§a" + site.describe());
            }
        }
        return SINGLE_SUCCESS;
    }

    private AutoFarmMatrix module() {
        AutoFarmMatrix module = Modules.get().get(AutoFarmMatrix.class);
        if (module == null) error("自动物流农场模块未注册。");
        return module;
    }
}
