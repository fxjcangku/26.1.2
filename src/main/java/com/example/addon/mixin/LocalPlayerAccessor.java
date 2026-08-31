package com.example.addon.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 本地玩家字段访问器 Mixin
 *
 * 暴露 LocalPlayer 的 positionReminder 字段。该字段控制客户端每隔多少 tick
 * 自动向服务器重发一次移动包；飞行绕过模块在发包级飞行期间把它压低，
 * 强制客户端高频重发移动包，保证伪造的 Y 坐标 / onGround 持续生效。
 *
 * 影响范围：不改变游戏行为，仅提供字段写入入口。
 *
 * @author yiyijia
 */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    /** 设置位置重发间隔（tick），值越小重发越频繁 */
    @Accessor("positionReminder")
    void yiyiaddon$setPositionReminder(int ticks);
}
