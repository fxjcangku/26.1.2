package com.example.addon.teleport.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 传送目标落点：脚底坐标 + 站位格 + 与锁定视线的垂直偏离（仅穿墙模式非零）。
 * feet 为脚底中心（玩家碰撞箱左下角放于该点时可安全站立）。
 */
public record TeleportTarget(Vec3 feet, BlockPos standPos, double deviation) {

    /** 播报用的坐标文本（x, y, z） */
    public String posText() {
        return feet.x() + ", " + feet.y() + ", " + feet.z();
    }
}