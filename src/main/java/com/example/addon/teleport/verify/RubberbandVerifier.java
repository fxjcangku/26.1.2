package com.example.addon.teleport.verify;

import com.example.addon.teleport.model.TeleportContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

/**
 * 服务器回弹侦测（验证层）：观察服务端位置权威包——
 * 玩家 ClientboundPlayerPositionPacket / 载具 ClientboundMoveVehiclePacket，
 * 与服务端预期位置对比得出「接受 / 回弹」结论。
 *
 * <p>只观察不拦截：包照常交原版处理，保证客户端最终与服务端一致；
 * 传送后若服务端接受则不会回位置包（超时即成功），回位置包即权威修正。</p>
 */
public final class RubberbandVerifier {

    /** 与本传送无关的包（或非验证状态） */
    public static final int IGNORED = 0;
    /** 服务端位置 ≈ 预期：接受本次传送 */
    public static final int CONFIRM = 1;
    /** 服务端位置偏离预期：回弹/拉回 */
    public static final int RUBBERBAND = 2;

    private RubberbandVerifier() {
    }

    /**
     * 包分类：仅在验证窗口内由协调器调用。
     * 命中权威包时会把服务端位置与偏差记入上下文（供播报与渲染）。
     */
    public static int classify(Packet<?> packet, LocalPlayer player, TeleportContext ctx) {
        if (ctx == null || ctx.expectedPos == null) return IGNORED;

        if (packet instanceof ClientboundPlayerPositionPacket p) {
            if (ctx.executedAsVehicle) return IGNORED;
            // 26.1.2 官方换算：相对修正基于客户端当前 PositionMoveRotation
            Vec3 absolute = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player), p.change(), p.relatives()).position();
            return judge(absolute, ctx);
        }

        if (packet instanceof ClientboundMoveVehiclePacket v) {
            if (!ctx.executedAsVehicle) return IGNORED;
            return judge(v.position(), ctx);
        }

        return IGNORED;
    }

    /** 距离裁决并记录观测值 */
    private static int judge(Vec3 serverPos, TeleportContext ctx) {
        double dist = serverPos.distanceTo(ctx.expectedPos);
        ctx.rubberbandPos = serverPos;
        ctx.rubberbandDist = dist;
        return dist <= ctx.verifyThreshold ? CONFIRM : RUBBERBAND;
    }
}