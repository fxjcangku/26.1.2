package com.example.addon.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 客户端世界预测处理器访问器 Mixin
 * 
 * 暴露 ClientLevel 的包私有方法 getBlockStatePredictionHandler()
 * 该方法在 Minecraft 26.1.2 版本中是包私有的，外部包无法直接调用
 * 
 * 作用：允许发包层代码获取方块状态预测处理器，用于获取 sequence 序列号
 * 用法：((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler()
 * 
 * 影响范围：不修改游戏行为，仅提供访问接口
 */
@Mixin(ClientLevel.class)
public interface ClientLevelPredictionAccessor {

    /**
     * 调用器方法：获取方块状态预测处理器
     * 
     * @return 当前客户端世界的方块状态预测处理器
     */
    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler yiyiaddon$getPredictionHandler();
}
