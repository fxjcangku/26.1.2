package com.example.addon.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 移动包字段访问器 Mixin
 *
 * 暴露 ServerboundMovePlayerPacket 的 y 与 onGround 两个 protected final 字段的写入能力，
 * 供飞行绕过模块在发包前改写 Y 坐标（绕过服务端浮空检测）与落地标志（onGround 伪造）。
 *
 * y 与 onGround 都是 final 字段，必须加 @Mutable 才能写入。
 *
 * 影响范围：不改变游戏行为，仅提供字段写入入口。
 *
 * @author yiyijia
 */
@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {

    /** 改写移动包的 Y 坐标 */
    @Mutable
    @Accessor("y")
    void yiyiaddon$setY(double y);

    /** 改写移动包的落地标志 */
    @Mutable
    @Accessor("onGround")
    void yiyiaddon$setOnGround(boolean onGround);
}
