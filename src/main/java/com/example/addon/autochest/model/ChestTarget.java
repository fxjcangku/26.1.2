package com.example.addon.autochest.model;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 容器目标：一次「发现到的合法容器 / 用户保存的点位」的不可变快照。
 *
 * <p>由 {@code ContainerScanner} 产出扫描结果，或由 {@code ChestPointManager} 存为用户点位。
 * 持坐标、维度、服务器、容器类型与发现时间，供选择器与状态机消费。
 * 服务器 + 维度 + 坐标共同决定身份，跨服 / 跨维度即便 XYZ 相同也不视为同一容器。</p>
 */
public final class ChestTarget {

    /** 容器所在方块坐标 */
    private final BlockPos pos;

    /** 容器所在维度标识（{@code minecraft:overworld} 等） */
    private final String dimension;

    /** 服务器/世界标识（多人 = IP 净化串，单人 = singleplayer）；扫描结果可为空 */
    private final String server;

    /** 容器类型稳定键（如 {@code chest}）；纯坐标点位可为空 */
    private final String containerType;

    /** 发现/保存时间（毫秒，{@code System.currentTimeMillis()}） */
    private final long discoveredAt;

    public ChestTarget(BlockPos pos, String dimension) {
        this(pos, dimension, null, null);
    }

    public ChestTarget(BlockPos pos, String dimension, String server, String containerType) {
        this.pos = pos.immutable();
        this.dimension = dimension;
        this.server = server;
        this.containerType = containerType;
        this.discoveredAt = System.currentTimeMillis();
    }

    public BlockPos pos() {
        return pos;
    }

    public String dimension() {
        return dimension;
    }

    public String server() {
        return server;
    }

    public String containerType() {
        return containerType;
    }

    public long discoveredAt() {
        return discoveredAt;
    }

    /** 目标是否位于当前所在维度 */
    public boolean inCurrentDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().identifier().toString().equals(dimension);
    }

    /** 目标到给定玩家坐标的直线距离平方（用于就近排序，避免开方） */
    public double distSqr(BlockPos playerPos) {
        return pos.distSqr(playerPos);
    }
}
