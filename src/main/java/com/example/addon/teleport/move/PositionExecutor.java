package com.example.addon.teleport.move;

import com.example.addon.teleport.model.TeleportContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 发送执行器（L2 执行层）：把决策好的目标落点落到「本地位置 + 服务端发包」。
 *
 * <p>26.1.2 官方机制：本体走 ServerboundMovePlayerPacket.Pos；
 * 载具走 ServerboundMoveVehiclePacket.fromEntity（与 LocalPlayer.tick 的
 * 发送路径完全一致）。onGround 恒为 true —— 目标经过安全判据保证脚下
 * 有真实支撑；horizontalCollision 恒为 false。</p>
 */
public final class PositionExecutor {

    private PositionExecutor() {
    }

    /**
     * 执行位移与发包。
     *
     * @return null 表示发送成功；否则返回失败原因（中文）
     */
    public static String execute(LocalPlayer player, ClientLevel level, TeleportContext ctx) {
        Vec3 feet = ctx.target.feet();
        double x = feet.x();
        double y = feet.y();
        double z = feet.z();

        if (player.isPassenger()) {
            Entity vehicle = player.getRootVehicle();
            // 非本机权威载具（如骑乘其他玩家实体）无法由客户端整体传送
            if (vehicle == null || !vehicle.isLocalInstanceAuthoritative()) {
                return "当前乘坐的载具无法由客户端传送";
            }
            // 保持乘客与载具的相对偏移不变，整体平移载具
            Vec3 offset = player.position().subtract(vehicle.position());
            Vec3 vt = new Vec3(x - offset.x, y - offset.y, z - offset.z);
            vehicle.absSnapTo(vt.x, vt.y, vt.z, vehicle.getYRot(), vehicle.getXRot());
            player.connection.send(ServerboundMoveVehiclePacket.fromEntity(vehicle));

            ctx.executedAsVehicle = true;
            ctx.vehicleSnapshot = vehicle;
            ctx.expectedPos = vt;
        } else {
            player.absSnapTo(x, y, z, player.getYRot(), player.getXRot());
            player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, true, false));

            ctx.executedAsVehicle = false;
            ctx.expectedPos = feet;
        }

        ctx.executeTick = level.getGameTime();
        return null;
    }
}