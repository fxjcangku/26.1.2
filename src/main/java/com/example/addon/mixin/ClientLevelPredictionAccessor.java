package com.example.addon.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * ClientLevel#getBlockStatePredictionHandler() 在 26.1.2 是包私有方法，
 * 外部包无法直接调用。这里用 Invoker 暴露出来，供发包层取 sequence 序列号。
 *
 * 用法：((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler()
 */
@Mixin(ClientLevel.class)
public interface ClientLevelPredictionAccessor {

    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler yiyiaddon$getPredictionHandler();
}
