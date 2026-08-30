package com.example.addon.enchant;

import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * .fumo 指令系统 —— 为扩展附魔设置六个功能坐标节点
 *
 * 用法：
 *   .fumo set <书/青晶石/成品箱/附魔台/砂轮/挂机位>
 *   .fumo remove <书/青晶石/成品箱/附魔台/砂轮/挂机位>
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
            .then(literal("书")        .executes(ctx -> { setPos("书");        return SINGLE_SUCCESS; }))
            .then(literal("青晶石")    .executes(ctx -> { setPos("青晶石");    return SINGLE_SUCCESS; }))
            .then(literal("成品箱")    .executes(ctx -> { setPos("成品箱");    return SINGLE_SUCCESS; }))
            .then(literal("附魔台")    .executes(ctx -> { setPos("附魔台");    return SINGLE_SUCCESS; }))
            .then(literal("砂轮")      .executes(ctx -> { setPos("砂轮");      return SINGLE_SUCCESS; }))
            .then(literal("挂机位")    .executes(ctx -> { setPos("挂机位");    return SINGLE_SUCCESS; }))
        );

        // .fumo remove <节点>
        builder.then(literal("remove")
            .then(literal("书")        .executes(ctx -> { removePos("书");        return SINGLE_SUCCESS; }))
            .then(literal("青晶石")    .executes(ctx -> { removePos("青晶石");    return SINGLE_SUCCESS; }))
            .then(literal("成品箱")    .executes(ctx -> { removePos("成品箱");    return SINGLE_SUCCESS; }))
            .then(literal("附魔台")    .executes(ctx -> { removePos("附魔台");    return SINGLE_SUCCESS; }))
            .then(literal("砂轮")      .executes(ctx -> { removePos("砂轮");      return SINGLE_SUCCESS; }))
            .then(literal("挂机位")    .executes(ctx -> { removePos("挂机位");    return SINGLE_SUCCESS; }))
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

    private void setPos(String node) {
        AutoEnchantBook module = getModule();
        if (module == null) return;
        if (!preparePointContext(module)) return;

        if (hasPosition(module, node)) {
            sendMsg("§c[" + node + "] 已设置，请先使用 §e.fumo remove " + node + " §c后再重新设置！");
            return;
        }

        BlockPos pos;
        if ("挂机位".equals(node)) {
            // 挂机位记录玩家脚下站立位置
            pos = mc.player.blockPosition();
        } else {
            // 其他节点记录准星指向的方块
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                sendMsg("§c请将准星对准目标方块！");
                return;
            }
            pos = ((BlockHitResult) mc.hitResult).getBlockPos();
            if (!isValidTarget(node, pos)) return;
        }

        switch (node) {
            case "书"     -> module.posBook       = pos;
            case "青晶石" -> module.posLapis      = pos;
            case "成品箱" -> module.posOutput     = pos;
            case "附魔台" -> module.posEnchant    = pos;
            case "砂轮"   -> module.posGrindstone = pos;
            case "挂机位" -> {
                module.posHangout = pos;
                module.hangoutYaw = mc.player.getYRot();
                module.hangoutPitch = mc.player.getXRot();
            }
        }

        Modules.get().save();

        sendMsg("§a§l✓ 绑定成功§r §8▸ §e[" + node + "] §8▸ §d坐标 (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
    }

    private void removePos(String node) {
        AutoEnchantBook module = getModule();
        if (module == null) return;

        switch (node) {
            case "书"     -> module.posBook       = null;
            case "青晶石" -> module.posLapis      = null;
            case "成品箱" -> module.posOutput     = null;
            case "附魔台" -> module.posEnchant    = null;
            case "砂轮"   -> module.posGrindstone = null;
            case "挂机位" -> {
                module.posHangout = null;
                module.hangoutYaw = null;
                module.hangoutPitch = null;
            }
        }

        Modules.get().save();

        sendMsg("§c§l✗ 已解绑 §e[" + node + "] §f坐标");
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

    private void printStatus() {
        AutoEnchantBook m = getModule();
        if (m == null) return;

        // 统一「标签 §8▸ 值」结构，与 .wk / .farm / .cunmin 状态面板风格一致
        sendMsg("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMsg("§b§l       扩展附魔 ▸ 坐标状态");
        sendMsg("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendMsg("  §f书本箱   §8▸ " + fmt(m.posBook));
        sendMsg("  §f青金石箱 §8▸ " + fmt(m.posLapis));
        sendMsg("  §f成品箱   §8▸ " + fmt(m.posOutput));
        sendMsg("  §f附魔台   §8▸ " + fmt(m.posEnchant));
        sendMsg("  §f砂轮     §8▸ " + fmt(m.posGrindstone));
        sendMsg("  §f挂机位   §8▸ " + fmt(m.posHangout));
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

    private boolean isValidTarget(String node, BlockPos pos) {
        Block block = mc.level.getBlockState(pos).getBlock();
        if ("附魔台".equals(node)) {
            if (block == Blocks.ENCHANTING_TABLE) return true;
            sendMsg("§c设置失败：准星指向的方块不是附魔台！");
            return false;
        }
        if ("砂轮".equals(node)) {
            if (block == Blocks.GRINDSTONE) return true;
            sendMsg("§c设置失败：准星指向的方块不是砂轮！");
            return false;
        }
        if (mc.level.getBlockEntity(pos) instanceof Container) return true;
        sendMsg("§c设置失败：[" + node + "] 必须设置为箱子、木桶或潜影盒容器！");
        return false;
    }

    private boolean hasPosition(AutoEnchantBook module, String node) {
        return switch (node) {
            case "书" -> module.posBook != null;
            case "青晶石" -> module.posLapis != null;
            case "成品箱" -> module.posOutput != null;
            case "附魔台" -> module.posEnchant != null;
            case "砂轮" -> module.posGrindstone != null;
            case "挂机位" -> module.posHangout != null;
            default -> false;
        };
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

    private void sendMsg(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage("扩展附魔", msg)));
        }
    }
}
