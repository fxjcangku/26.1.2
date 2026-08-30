package com.example.addon.mixin;

import com.example.addon.modules.AutoMinerModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 快速破坏（秒破）Mixin
 *
 * 注入 MultiPlayerGameMode.startDestroyBlock：
 * 自动挖矿模块开启「快速破坏」时，跳过正常挖掘进度，直接 destroyBlock 秒破方块，
 * 并补发 START_DESTROY_BLOCK + STOP_DESTROY_BLOCK 包模拟完整破坏，绕过反作弊的速度校验。
 *
 * 比 Meteor 自带 SpeedMine 更强：SpeedMine 只在破坏进度过半（getDestroyProgress &gt; 0.5f）才秒破，
 * 对黑曜石/远古残骸等硬方块无效；本 Mixin 无条件秒破所有可破坏方块（配合效率5无需急迫）。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeFastBreakMixin {

    @Shadow
    public abstract boolean destroyBlock(BlockPos pos);

    @Shadow
    public abstract void startPrediction(ClientLevel level, PredictiveAction predictiveAction);

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void yiyiaddon$instantBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        AutoMinerModule module = Modules.get().get(AutoMinerModule.class);
        if (module == null || !module.isActive() || !module.getFastBreak()) return;

        BlockState state = mc.level.getBlockState(pos);
        // 基岩、屏障、命令方块等不可破坏方块跳过（defaultDestroyTime < 0 表示不可破坏）
        if (state.getBlock().defaultDestroyTime() < 0) return;

        // 直接秒破：destroyBlock 内部发 STOP 包 + 客户端预测破坏（触发完整掉落语义）
        if (destroyBlock(pos)) {
            // 补发完整破坏序列，让服务端认可这次破坏（START 带 sequence，STOP 不带，与 FarmPacketOps 规范一致）
            startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));
            startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence));
            // 绕过反作弊：补发 ABORT 包混淆破坏进度（Grim fastbreak 绕过）
            if (module.getBypassAnticheat()) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos.above(), direction));
            }
            cir.setReturnValue(true);
        }
        // destroyBlock 返回 false 时不拦截，交给原版正常破坏流程兜底
    }
}
