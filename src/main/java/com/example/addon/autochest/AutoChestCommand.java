package com.example.addon.autochest;

import com.example.addon.autochest.model.ChestTarget;
import com.example.addon.autochest.model.ContainerType;
import com.example.addon.autochest.model.ContainerTypeRegistry;
import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * AutoChest 指令：自动箱子标点管理。
 *
 * <p>标点模式专用：用户通过指令保存容器点位，AutoChest 只处理这些点位，
 * 不因附近扫描到箱子而自动处理。设置点位会对准星指向的方块做「合法容器」校验，
 * 非容器拒绝保存。</p>
 *
 * <p>子命令：</p>
 * <ul>
 *   <li>{@code .autochest add} 对准合法容器添加标点</li>
 *   <li>{@code .autochest remove} 删除对准容器的标点</li>
 *   <li>{@code .autochest clear} 清空全部标点</li>
 *   <li>{@code .autochest status} 查看当前维度标点信息</li>
 * </ul>
 */
public final class AutoChestCommand extends Command {

    public AutoChestCommand() {
        super("autochest", "自动箱子标点管理（add/remove/clear/status）");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("add").executes(ctx -> {
            addPoint();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("remove").executes(ctx -> {
            removePoint();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            clearPoints();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("status").executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        }));
    }

    /** 设置点位：校验准星指向方块为合法容器后保存（服务器/世界 + 维度 + 坐标 + 类型） */
    private void addPoint() {
        AutoChestModule module = module();
        if (module == null || mc.level == null) {
            info("自动箱子模块未加载");
            return;
        }
        BlockPos target = getTargetBlock();
        if (target == null) {
            info("§c准星未对准任何方块");
            return;
        }

        // 判断目标是否为启用的合法容器类型，非容器禁止保存
        ContainerType type = containerTypeAt(target, module);
        if (type == null) {
            info("§c当前目标不是可绑定容器");
            return;
        }

        String dim = WorldIdentity.dimension(mc);
        if (module.pointManager.add(target, dim, type.id())) {
            info("§a§l✓ 已添加标点 §8▸ " + YiyiaddonModule.formatCoords(target.getX(), target.getY(), target.getZ())
                + " §8▸ §a" + type.displayName());
        } else {
            info("§c该标点已存在");
        }
    }

    /** 删除点位：内存与磁盘同步 */
    private void removePoint() {
        AutoChestModule module = module();
        if (module == null || mc.level == null) {
            info("自动箱子模块未加载");
            return;
        }
        BlockPos target = getTargetBlock();
        if (target == null) {
            info("§c准星未对准任何方块");
            return;
        }
        String dim = WorldIdentity.dimension(mc);
        if (module.pointManager.remove(target, dim)) {
            info("§c§l✗ 已删除标点 §8▸ " + YiyiaddonModule.formatCoords(target.getX(), target.getY(), target.getZ()));
        } else {
            info("§c该坐标没有标点");
        }
    }

    /** 清空全部标点 */
    private void clearPoints() {
        AutoChestModule module = module();
        if (module == null) {
            info("自动箱子模块未加载");
            return;
        }
        int count = module.pointManager.size();
        module.pointManager.clear();
        info("§e已清空全部标点（共 " + count + " 个）");
    }

    /** 查看当前维度点位信息：服务器/世界、维度、坐标、容器类型、处理状态 */
    private void showStatus() {
        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        info("§b§l         自动箱子 ▸ 标点管理");
        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        AutoChestModule module = module();
        if (module == null || mc.level == null) {
            info("  §c自动箱子模块未加载");
            return;
        }

        List<ChestTarget> points = module.pointManager.pointsInCurrentDimension();
        if (points.isEmpty()) {
            info("  §7当前维度没有标点");
        } else {
            String server = WorldIdentity.serverDisplayName(mc);
            long expireMs = module.moduleSettings.recordExpireMinutes.get() * 60_000L;
            for (ChestTarget p : points) {
                String typeName = containerTypeName(p.containerType());
                boolean processed = module.recordManager.isProcessed(p.pos(), p.dimension(), p.containerType(), expireMs);
                String status = processed ? "§c已处理" : "§a未处理";

                info("  §d■ §8▸ §f" + typeName + " §8▸ "
                    + YiyiaddonModule.formatCoords(p.pos().getX(), p.pos().getY(), p.pos().getZ()));
                info("     §7服务器 §8▸ §f" + server
                    + " §7维度 §8▸ §f" + WorldIdentity.dimensionDisplayName(p.dimension())
                    + " §7状态 §8▸ " + status);
            }
        }
        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /** 查找对准方块的启用容器类型，非容器返回 null */
    private ContainerType containerTypeAt(BlockPos pos, AutoChestModule module) {
        if (mc.level == null) return null;
        Block block = mc.level.getBlockState(pos).getBlock();
        return ContainerTypeRegistry.match(block, module.moduleSettings.containerTypes.enabledTypes());
    }

    /** 容器类型中文名，未知类型回退为原始键 */
    private String containerTypeName(String typeId) {
        if (typeId == null) return "未知容器";
        ContainerType type = ContainerTypeRegistry.byId(typeId);
        return type == null ? typeId : type.displayName();
    }

    private AutoChestModule module() {
        return Modules.get().get(AutoChestModule.class);
    }

    private BlockPos getTargetBlock() {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(hit instanceof BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    private void info(String message) {
        if (mc.player == null) return;
        if (message == null) return;
        String clean = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (clean.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal(
            YiyiaddonModule.formatMessage("自动箱子", message)));
    }
}
