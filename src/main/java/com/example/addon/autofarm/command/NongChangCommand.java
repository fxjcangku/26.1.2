package com.example.addon.autofarm.command;

import com.example.addon.autofarm.AutoFarmMatrix;
import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.model.SiteType;
import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 * .farm expand 格数                   面朝方向把农田向外扩 n 格（东/南/西/北）
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
        builder.then(literal("expand")
            .then(argument("格数", IntegerArgumentType.integer(1, 128)).executes(ctx -> {
                int n = IntegerArgumentType.getInteger(ctx, "格数");
                return expandFarm(n);
            })));
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

    /** 面朝方向把农田向外扩展 n 格（水平方向，仅改 X/Z，Y 保持锚点原值） */
    private int expandFarm(int n) {
        AutoFarmMatrix module = module();
        if (module == null) return SINGLE_SUCCESS;

        if (mc.player == null || mc.level == null) {
            farmError("当前不在游戏世界中，无法扩展农田");
            return SINGLE_SUCCESS;
        }

        if (module.isActive()) {
            farmError("模块运行中无法修改农田范围，请先关闭模块");
            return SINGLE_SUCCESS;
        }

        FarmSite start = module.site(SiteType.START);
        FarmSite end = module.site(SiteType.END);
        if (start == null || end == null) {
            farmError("农田范围未绑定完整，请先用 .farm set 农场点位1 / 农场点位2 框出范围");
            return SINGLE_SUCCESS;
        }

        if (!start.inCurrentDimension() || !end.inCurrentDimension()) {
            farmError("农田锚点不在当前维度，无法按面朝方向扩展");
            return SINGLE_SUCCESS;
        }

        Facing facing = facingOf(mc.player.getYRot());
        BlockPos startPos = start.pos();
        BlockPos endPos = end.pos();
        BlockPos newStart = startPos;
        BlockPos newEnd = endPos;

        // 面朝方向为正时，移动该方向坐标更大的一角；为负时移动更小的一角；
        // 两角同值时移动点位2，保证即使一宽农田也能正确扩展。
        if (facing.stepX() > 0) {
            if (startPos.getX() >= endPos.getX()) newStart = shifted(startPos, n, 0, 0);
            else newEnd = shifted(endPos, n, 0, 0);
        } else if (facing.stepX() < 0) {
            if (startPos.getX() <= endPos.getX()) newStart = shifted(startPos, -n, 0, 0);
            else newEnd = shifted(endPos, -n, 0, 0);
        } else if (facing.stepZ() > 0) {
            if (startPos.getZ() >= endPos.getZ()) newStart = shifted(startPos, 0, 0, n);
            else newEnd = shifted(endPos, 0, 0, n);
        } else {
            if (startPos.getZ() <= endPos.getZ()) newStart = shifted(startPos, 0, 0, -n);
            else newEnd = shifted(endPos, 0, 0, -n);
        }

        module.bindSite(SiteType.START, new FarmSite(newStart, start.dimension()));
        module.bindSite(SiteType.END, new FarmSite(newEnd, end.dimension()));

        FarmSite ns = module.site(SiteType.START);
        FarmSite ne = module.site(SiteType.END);
        int rangeX = Math.abs(ne.pos().getX() - ns.pos().getX()) + 1;
        int rangeZ = Math.abs(ne.pos().getZ() - ns.pos().getZ()) + 1;

        farmInfo("§a§l✓ 已向 " + facing.cn() + " 扩展 " + n + " 格");
        farmInfo("  §7新范围 §8▸ §f" + rangeX + "×" + rangeZ);
        farmInfo("  §a农场点位1 §8▸ §a" + ns.describe("§a"));
        farmInfo("  §e农场点位2 §8▸ §e" + ne.describe("§e"));
        return SINGLE_SUCCESS;
    }

    /** 由偏航角换算水平朝向（东/南/西/北），Minecraft 0° 朝南、90° 朝西 */
    private Facing facingOf(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        if (normalized >= 45f && normalized < 135f) return new Facing(-1, 0, "西");
        if (normalized >= 135f && normalized < 225f) return new Facing(0, -1, "北");
        if (normalized >= 225f && normalized < 315f) return new Facing(1, 0, "东");
        return new Facing(0, 1, "南");
    }

    private BlockPos shifted(BlockPos pos, int dx, int dy, int dz) {
        return new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
    }

    /** 水平朝向：步进向量 + 中文方位名 */
    private record Facing(int stepX, int stepZ, String cn) {}

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
