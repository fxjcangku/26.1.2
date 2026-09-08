package com.example.addon.mixin;

import com.example.addon.mining.AutoMinerModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 快速破坏（秒破）Mixin
 *
 * 注入 MultiPlayerGameMode.startDestroyBlock：
 * 自动挖矿模块开启「快速破坏」时，跳过正常挖掘进度直接秒破方块。
 *
 * 修复要点（2026-08-31）：
 * 1. 防假方块/空气墙：不再调用 destroyBlock()（它会客户端预测方块置空，
 *    挖太快时服务端还没确认，客户端已置空导致空气墙）。改用
 *    retainKnownServerState 登记服务端已知状态，客户端在服务端确认前保持方块原状。
 * 2. 防卡死：加节流，按「秒破间隔」限制破坏频率，避免瞬间洪泛打崩服务器。
 *
 * 比 Meteor 自带 SpeedMine 更强：SpeedMine 只在破坏进度过半才秒破，对黑曜石/
 * 远古残骸等硬方块无效；本 Mixin 无条件秒破所有可破坏方块。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeFastBreakMixin {

    /** 上次秒破的时间戳（毫秒），用于节流；跨世界切换也不受影响 */
    @Unique
    private long yiyiaddon$lastBreakTime = 0L;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void yiyiaddon$instantBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        AutoMinerModule module = Modules.get().get(AutoMinerModule.class);
        if (module == null || !module.isActive() || !module.getFastBreak()) return;

        BlockState state = mc.level.getBlockState(pos);
        // 基岩、屏障、命令方块等不可破坏方块跳过（defaultDestroyTime < 0 表示不可破坏）
        if (state.getBlock().defaultDestroyTime() < 0) return;

        // 节流：间隔内跳过本次破坏，返回 false 让调用方下一 tick 重试，防止挖太快卡死
        long now = System.currentTimeMillis();
        if (now - yiyiaddon$lastBreakTime < module.getBreakInterval() * 50L) {
            cir.setReturnValue(false);
            return;
        }

        // 预测同步秒破：登记服务端已知状态，客户端不提前置空，消除假方块
        ClientLevel level = mc.level;
        BlockStatePredictionHandler handler =
            ((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler();

        // START 带 sequence：取号并登记原状态（与 FarmPacketOps 规范一致）
        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(pos, state, mc.player);
            int sequence = predicting.currentSequence();
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));
        }
        // STOP 不占用 sequence，仅确认动作结束
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction));

        // 绕过反作弊：额外补发 ABORT 包混淆破坏时序（Grim fastbreak 绕过）
        if (module.getBypassAnticheat()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos.above(), direction));
        }

        yiyiaddon$lastBreakTime = now;
        cir.setReturnValue(true);
    }
}
