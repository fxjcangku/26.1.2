package com.example.addon.teleport.geo;

import com.example.addon.teleport.model.TeleportTarget;
import com.example.addon.teleport.safety.CollisionSafety;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

/**
 * TP穿墙 ▸ 三维方向落点扫描器：沿触发瞬间锁定的真实准星射线做等距采样，
 * 在最大穿墙距离内智能寻找「最远、能真实容纳玩家、有支撑、无危险」的落点。
 *
 * <p>核心原则（TP穿墙语义）：
 * <ul>
 *   <li>不要求前方必须有墙——没有墙也能前进，天然承担「方向赶路」用途；</li>
 *   <li>普通方块/门窗/半砖/楼梯等全部不是阻挡，射线直接穿过，
 *       只统计穿过的实体层数供播报，绝不提前停止；</li>
 *   <li>完整三维方向（含上下与斜向）由锁定射线自然支持；</li>
 *   <li>候选容纳判定完全基于玩家真实 EntityDimensions 碰撞箱与
 *       方块真实 VoxelShape（CollisionSafety 统一判据），
 *       不黑名单方块、不用 isFullCube、不要求「两个完整空气方块」；</li>
 *   <li>理想目标（射线上最远可站立采样）不可用时，外层协调整合
 *       SafePositionFinder 做「落点修正范围」内的局部修正，而不是直接失败；</li>
 *   <li>落点允许上限内的竖直下落（maxFall + 台阶/半砖支撑）。</li>
 * </ul>
 */
public final class WallRayScanner {

    /** 射线采样步长（格）：精度与主线程开销的平衡点 */
    private static final double STEP = 0.5;

    /** 采样起点（格）：跳过玩家自身脚下的近区，避免传送回原位 */
    public static final double START = 2.0;

    private WallRayScanner() {
    }

    /** 扫描结果 */
    public static final class Landing {
        /** 主路结果：射线上最远可站立落点；null 表示需要外层局部修正或失败 */
        public TeleportTarget target;
        /** 射线穿过的实体层数（无墙=0，供「穿透 N 层」播报） */
        public int layers;
        /** 射线耗尽仍处于实体内部（障碍太厚，射线没出来过） */
        public boolean exhaustedInsideSolid;
        /** 扫描中途遇到未加载区块（数据不可用，禁止继续跨未知区域） */
        public boolean reachedUnloaded;
        /** 局部修正的中心：扫描终点映射的脚部坐标（永远非空） */
        public Vec3 idealFeet;
    }

    /**
     * 沿锁定射线三维方向扫描落点。
     *
     * <p>候选 = 采样点下方 eyeHeight 处起、向下最多 maxFall 的
     * 第一个「完整安全判据通过」的脚部位置；多个候选取射线进展最远者
     * （dir 一致性好 + 距理想区最近 + 真实容纳，三者在此检索法下天然同时满足）。
     *
     * @param rayOrigin 触发瞬间锁定的视线起点（相机位置）
     * @param rayDir    触发瞬间锁定的视线方向单位向量（完整三维）
     * @param eyeHeight 玩家当前眼高（把视点映射回脚部基准用）
     * @param maxDist   最大穿墙距离（格）
     * @param maxFall   允许自动下落到支撑的最大落差（格）
     */
    public static Landing findLanding(ClientLevel level, EntityDimensions dims,
                                      Vec3 rayOrigin, Vec3 rayDir, double eyeHeight,
                                      double maxDist, int maxFall) {
        Landing landing = new Landing();

        TeleportTarget best = null;
        boolean inSolid = false;
        boolean anySolid = false;
        int solidRuns = 0;
        Vec3 lastFeet = null;

        for (double t = START; t <= maxDist; t += STEP) {
            Vec3 p = rayOrigin.add(rayDir.scale(t));

            // 数据有效性：未知区块一律终止，绝不把未知区域当穿透后的出口
            if (!CollisionSafety.areaLoaded(level, p.x(), p.z())) {
                landing.reachedUnloaded = true;
                break;
            }

            // 实体层数统计（只统计，不阻挡）：普通方块不是 TP穿墙 的禁止目标
            boolean solid = CollisionSafety.cellSolid(level, floor(p.x()), floor(p.y()), floor(p.z()));
            if (solid && !inSolid) {
                solidRuns++;
                anySolid = true;
            }
            inSolid = solid;

            // 脚部基准：视点下方 eyeHeight；从基准向下找第一个通过完整安全判据的高度
            double baseY = p.y() - eyeHeight;
            for (int d = 0; d <= maxFall; d++) {
                double y = baseY - d;
                if (y < level.getMinY()) break;

                // 完整判据：区块可用 + 真实碰撞容纳 + 支撑 + 无岩浆/火焰 + 不悬空
                if (CollisionSafety.checkStand(level, dims, p.x(), y, p.z()) != null) continue;

                // 该采样点命中即取（最浅下落），只保留射线进展最远的候选
                best = new TeleportTarget(
                    new Vec3(p.x(), y, p.z()),
                    new BlockPos(floor(p.x()), floor(y), floor(p.z())),
                    perpendicularDistance(rayOrigin, rayDir, new Vec3(p.x(), y, p.z())));
                break;
            }

            lastFeet = new Vec3(p.x(), baseY, p.z());
        }

        landing.layers = anySolid ? solidRuns : 0;
        landing.exhaustedInsideSolid = best == null && inSolid;
        // 修正中心：扫描终点（或未加载前最后采样点）的脚部映射
        landing.idealFeet = lastFeet != null
            ? lastFeet
            : rayOrigin.add(rayDir.scale(START)).add(0, -eyeHeight, 0);
        landing.target = best;
        return landing;
    }

    /** 点到射线的垂直距离（三维） */
    private static double perpendicularDistance(Vec3 origin, Vec3 dir, Vec3 point) {
        double px = point.x() - origin.x();
        double py = point.y() - origin.y();
        double pz = point.z() - origin.z();
        double ahead = dir.x() * px + dir.y() * py + dir.z() * pz;
        double ox = px - dir.x() * ahead;
        double oy = py - dir.y() * ahead;
        double oz = pz - dir.z() * ahead;
        return Math.sqrt(ox * ox + oy * oy + oz * oz);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}