package com.example.addon.enchant;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.enchant.point.PointType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * .fumo 指令系统 —— 为扩展附魔三种模式设置统一点位节点。
 *
 * <p>所有节点通过 {@link PointType} 统一绑定，GUI 按钮、本指令、自检、状态机
 * 读写同一个点位（同一份数据源），避免「箱子坐标」模糊类型或多套点位数据库。</p>
 *
 * 用法：
 *   .fumo set <书/青晶石/工具护甲箱/附魔台/砂轮/铁砧/挂机位/成品箱/异常装备箱>
 *   .fumo remove <同上>
 *   .fumo status
 *   .fumo clear
 */
public class FumoCommand extends Command {

    public FumoCommand() {
        super("fumo", "扩展附魔坐标设置指令");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {

        // .fumo set <节点>
        builder.then(literal("set")
            .then(literal("书")         .executes(ctx -> { setPos(PointType.BOOK_STORAGE);      return SINGLE_SUCCESS; }))
            .then(literal("青晶石")     .executes(ctx -> { setPos(PointType.LAPIS_STORAGE);     return SINGLE_SUCCESS; }))
            .then(literal("工具护甲箱") .executes(ctx -> { setPos(PointType.EQUIPMENT_STORAGE); return SINGLE_SUCCESS; }))
            .then(literal("附魔台")     .executes(ctx -> { setPos(PointType.ENCHANTING_TABLE);  return SINGLE_SUCCESS; }))
            .then(literal("砂轮")       .executes(ctx -> { setPos(PointType.GRINDSTONE);        return SINGLE_SUCCESS; }))
            .then(literal("铁砧")       .executes(ctx -> { setPos(PointType.ANVIL);             return SINGLE_SUCCESS; }))
            .then(literal("铁砧箱")     .executes(ctx -> { setPos(PointType.ANVIL_BOX);         return SINGLE_SUCCESS; }))
            .then(literal("挂机位")     .executes(ctx -> { setPos(PointType.AFK);               return SINGLE_SUCCESS; }))
            .then(literal("成品箱")     .executes(ctx -> { setPos(PointType.OUTPUT_STORAGE);    return SINGLE_SUCCESS; }))
            .then(literal("异常装备箱") .executes(ctx -> { setPos(PointType.ERROR_STORAGE);     return SINGLE_SUCCESS; }))
        );

        // .fumo remove <节点>
        builder.then(literal("remove")
            .then(literal("书")         .executes(ctx -> { removePos(PointType.BOOK_STORAGE);      return SINGLE_SUCCESS; }))
            .then(literal("青晶石")     .executes(ctx -> { removePos(PointType.LAPIS_STORAGE);     return SINGLE_SUCCESS; }))
            .then(literal("工具护甲箱") .executes(ctx -> { removePos(PointType.EQUIPMENT_STORAGE); return SINGLE_SUCCESS; }))
            .then(literal("附魔台")     .executes(ctx -> { removePos(PointType.ENCHANTING_TABLE);  return SINGLE_SUCCESS; }))
            .then(literal("砂轮")       .executes(ctx -> { removePos(PointType.GRINDSTONE);        return SINGLE_SUCCESS; }))
            .then(literal("铁砧")       .executes(ctx -> { removePos(PointType.ANVIL);             return SINGLE_SUCCESS; }))
            .then(literal("铁砧箱")     .executes(ctx -> { removePos(PointType.ANVIL_BOX);         return SINGLE_SUCCESS; }))
            .then(literal("挂机位")     .executes(ctx -> { removePos(PointType.AFK);               return SINGLE_SUCCESS; }))
            .then(literal("成品箱")     .executes(ctx -> { removePos(PointType.OUTPUT_STORAGE);    return SINGLE_SUCCESS; }))
            .then(literal("异常装备箱") .executes(ctx -> { removePos(PointType.ERROR_STORAGE);     return SINGLE_SUCCESS; }))
        );

        // .fumo status —— 打印当前所有坐标
        builder.then(literal("status").executes(ctx -> {
            printStatus();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            clearPoints();
            return SINGLE_SUCCESS;
        }));
    }

    // ── 坐标设置 ─────────────────────────────────────────────────────────

    private boolean setPos(PointType type) {
        AutoEnchantBook module = getModule();
        if (module == null) return false;
        if (!preparePointContext(module)) return false;

        if (module.getPointPos(type) != null) {
            sendMsg("§c[" + type.title() + "] 已设置，请先使用 §e.fumo remove " + type.node() + " §c后再重新设置！");
            return false;
        }

        BlockPos pos;
        if (type == PointType.AFK) {
            // 挂机位记录玩家脚下站立位置
            pos = mc.player.blockPosition();
        } else {
            // 其他节点记录准星指向的方块
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                sendMsg("§c请将准星对准目标方块！");
                return false;
            }
            pos = ((BlockHitResult) mc.hitResult).getBlockPos();
            if (!isValidTarget(type, pos)) return false;
        }

        module.setPointPos(type, pos);
        if (type == PointType.AFK) {
            module.hangoutYaw = mc.player.getYRot();
            module.hangoutPitch = mc.player.getXRot();
        }
        if (type == PointType.ANVIL) {
            // 保存铁砧朝向，用于铁砧损坏后原样恢复
            module.posAnvilFacing = mc.level.getBlockState(pos).getValue(AnvilBlock.FACING);
        }

        Modules.get().save();

        sendMsg("§a§l✓ 绑定成功§r §8▸ §e[" + type.title() + "] §8▸ §d坐标 (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
            + " §8▸ §7维度 §a" + dimensionName(module.pointDimension)
            + " §8▸ §7服务器 §6" + (module.pointServer == null ? "未知" : module.pointServer));
        return true;
    }

    private void removePos(PointType type) {
        AutoEnchantBook module = getModule();
        if (module == null) return;

        module.setPointPos(type, null);
        if (type == PointType.AFK) {
            module.hangoutYaw = null;
            module.hangoutPitch = null;
        }
        if (type == PointType.ANVIL) {
            module.posAnvilFacing = null;
        }

        Modules.get().save();

        sendMsg("§c§l✗ 已解绑 §e[" + type.title() + "] §f坐标");
        if (!module.hasAnyPosition()) {
            module.pointServer = null;
            module.pointDimension = null;
            Modules.get().save();
        }
    }

    private void clearPoints() {
        AutoEnchantBook module = getModule();
        if (module == null) return;
        module.clearPoints();
        Modules.get().save();
        sendMsg("§a§l✓ 已清空全部点位、挂机视角及服务器维度绑定");
    }

    // ── 公开静态方法（供模块 GUI 点位卡片按钮调用）─────────────────────────────

    /**
     * 设置点位（供扩展附魔配置页面的卡片按钮调用）。
     * 复用 .fumo set 指令的同一套校验逻辑（容器/附魔台/砂轮/铁砧判定、覆盖保护、世界绑定）。
     *
     * @param type 统一点位业务类型
     * @return true = 设置成功（可关闭 GUI），false = 设置失败（保留 GUI 让玩家重新对准）
     */
    public static boolean setPoint(PointType type) {
        return new FumoCommand().setPos(type);
    }

    /**
     * 删除点位（供扩展附魔配置页面的卡片按钮调用）。
     *
     * @param type 统一点位业务类型
     */
    public static void removePoint(PointType type) {
        new FumoCommand().removePos(type);
    }

    private void printStatus() {
        AutoEnchantBook m = getModule();
        if (m == null) return;

        // 统一「标签 §8▸ 值」结构，与 .wk / .farm / .cunmin 状态面板风格一致
        sendMsg("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMsg("§b§l       扩展附魔 ▸ 坐标状态");
        sendMsg("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        for (PointType type : PointType.all()) {
            sendMsg("  §f" + type.title() + " §8▸ " + fmt(m.getPointPos(type)));
        }
        sendMsg("  §7服务器   §8▸ " + context(m.pointServer));
        sendMsg("  §7维度     §8▸ " + context(m.pointDimension));
        sendMsg("  §7当前状态 §8▸ " + (m.matchesCurrentPointContext() ? "§a匹配" : "§c不匹配"));
        sendMsg("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    private AutoEnchantBook getModule() {
        AutoEnchantBook m = Modules.get().get(AutoEnchantBook.class);
        if (m == null) sendMsg("§c找不到 扩展附魔 模块，请确认已注册！");
        return m;
    }

    private boolean isValidTarget(PointType type, BlockPos pos) {
        Block block = mc.level.getBlockState(pos).getBlock();
        if (type == PointType.ENCHANTING_TABLE) {
            if (block == Blocks.ENCHANTING_TABLE) return true;
            sendMsg("§c设置失败：准星指向的方块不是附魔台！");
            return false;
        }
        if (type == PointType.GRINDSTONE) {
            if (block == Blocks.GRINDSTONE) return true;
            sendMsg("§c设置失败：准星指向的方块不是砂轮！");
            return false;
        }
        if (type == PointType.ANVIL) {
            if (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) return true;
            sendMsg("§c设置失败：准星指向的方块不是铁砧！");
            return false;
        }
        if (mc.level.getBlockEntity(pos) instanceof Container) return true;
        sendMsg("§c设置失败：[" + type.title() + "] 必须设置为箱子、木桶或潜影盒容器！");
        return false;
    }

    private boolean preparePointContext(AutoEnchantBook module) {
        String server = module.currentServer();
        String dimension = module.currentDimension();
        if (server == null || dimension == null) {
            sendMsg("§c无法识别当前服务器或维度，点位未保存！");
            return false;
        }
        if (!module.hasAnyPosition()) {
            module.pointServer = server;
            module.pointDimension = dimension;
            return true;
        }
        if (module.pointServer == null || module.pointDimension == null) {
            sendMsg("§c旧版点位没有世界绑定，请先执行 §e.fumo clear §c后重新设置！");
            return false;
        }
        if (!module.matchesCurrentPointContext()) {
            sendMsg("§c现有点位属于其他服务器或维度，请切回原世界，或执行 §e.fumo clear §c后重设！");
            return false;
        }
        return true;
    }

    private String context(String value) {
        return value == null ? "§c未绑定" : "§a" + value;
    }

    private String fmt(BlockPos pos) {
        if (pos == null) return "§c未设置";
        return "§a(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    /** 维度 ID 转中文名：overworld → 主世界，nether → 下界，end → 末地 */
    private String dimensionName(String dimension) {
        if (dimension == null) return "未知维度";
        if (dimension.contains("overworld")) return "主世界";
        if (dimension.contains("nether")) return "下界";
        if (dimension.contains("end")) return "末地";
        return dimension;
    }

    private void sendMsg(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("扩展附魔", msg)));
        }
    }
}
