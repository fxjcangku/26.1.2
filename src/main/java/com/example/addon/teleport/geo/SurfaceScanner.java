package com.example.addon.teleport.geo;

import com.example.addon.teleport.model.TeleportTarget;
import com.example.addon.teleport.safety.CollisionSafety;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

/**
 * TP地面 ▸ 真正地表扫描器：以玩家脚底为起点双向扫描竖直列，
 * 寻找「开天 + 可站立」的最近落点。
 *
 * <p>开天判定 = 头顶直通天空（LevelReader.canSeeSky），保证找到的是
 * 真正露天地表而不是洞穴天花板；可站立判定复用统一安全判据。
 * 优先向下（空中/浮空时落回地面），无结果再向上（洞穴中回到地表）。</p>
 */
public final class SurfaceScanner {

    private SurfaceScanner() {
    }

    /** 扫描结果 */
    public static final class Result {
        /** 目标落点；null 表示未找到 */
        public final TeleportTarget target;
        /** 相对起点的竖直位移（负=向下，0=站在原地） */
        public final int rise;
        /** target 为空时的失败原因（中文） */
        public final String failReason;

        Result(TeleportTarget target, int rise, String failReason) {
            this.target = target;
            this.rise = rise;
            this.failReason = failReason;
        }
    }

    /**
     * 双向扫描真正地表。
     *
     * @param feet    当前脚底坐标（主张 x/z 中心由调用方保证）
     * @param maxRise 向上扫描的最大格数
     */
    public static Result scan(ClientLevel level, EntityDimensions dims, Vec3 feet, int maxRise) {
        int fx = (int) Math.floor(feet.x());
        int fz = (int) Math.floor(feet.z());
        int startY = (int) Math.floor(feet.y());

        if (!CollisionSafety.areaLoaded(level, feet.x(), feet.z())) {
            return new Result(null, 0, "脚下区块数据未加载");
        }

        // 当前位置本身就是开天地表：无需移动
        if (passes(level, dims, fx, startY, fz)) {
            return new Result(at(fx, startY, fz), 0, null);
        }

        // 优先向下：浮空/高处时落回最近地面
        for (int y = startY - 1; y >= level.getMinY(); y--) {
            if (passes(level, dims, fx, y, fz)) {
                return new Result(at(fx, y, fz), y - startY, null);
            }
        }

        // 再向上：洞穴中回到真正地表
        int top = Math.min(startY + maxRise, level.getMaxY() - 1);
        for (int y = startY + 1; y <= top; y++) {
            if (passes(level, dims, fx, y, fz)) {
                return new Result(at(fx, y, fz), y - startY, null);
            }
        }

        return new Result(null, 0, "上下扫描范围内未找到真正地表（头顶可能有遮挡）");
    }

    /** 某格是否「开天 + 可安全站立」 */
    private static boolean passes(ClientLevel level, EntityDimensions dims, int x, int y, int z) {
        return level.canSeeSky(new BlockPos(x, y, z))
            && CollisionSafety.checkStand(level, dims, x + 0.5, y, z + 0.5) == null;
    }

    /** 构造格中心落点 */
    private static TeleportTarget at(int x, int y, int z) {
        return new TeleportTarget(new Vec3(x + 0.5, y, z + 0.5), new BlockPos(x, y, z), 0.0);
    }
}