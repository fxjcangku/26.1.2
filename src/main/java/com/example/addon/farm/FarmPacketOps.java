package com.example.addon.farm;

import com.example.addon.mixin.ClientLevelPredictionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 发包层：直接构造并发送 C2S 包，不依赖准星视角的客户端模拟。
 *
 * 关于 sequence 的处理（26.1.2 关键机制）：
 * 服务端会校验每个方块交互包携带的 sequence 序号，客户端必须通过
 * BlockStatePredictionHandler 取号，并在取号前登记本地预测状态。
 * 序号错乱会导致服务端回滚方块，表现为"方块闪烁复原"。
 *
 * 正确姿势是：startPredicting() 开启预测窗口 → retainKnownServerState() 登记原状态
 * → currentSequence() 取号 → 发包 → close() 关闭窗口。
 * 每个包必须独立取号，不能复用同一个 sequence。
 */
public final class FarmPacketOps {

    private FarmPacketOps() {
    }

    /** 取出预测处理器。原版方法是包私有，通过 accessor mixin 暴露。 */
    private static BlockStatePredictionHandler predictionHandler(ClientLevel level) {
        return ((ClientLevelPredictionAccessor) (Object) level).yiyiaddon$getPredictionHandler();
    }

    /**
     * 发送破坏方块包（瞬间破坏路径：START + STOP 同 tick 连发）。
     *
     * 农作物、竹子、甘蔗这类方块硬度为 0，服务端收到 START_DESTROY_BLOCK 即判定破坏完成，
     * 所以不需要走 continueDestroyBlock 的挖掘进度循环。
     *
     * @param face 破坏朝向，作物一般给 UP
     * @return 是否成功发包
     */
    public static boolean breakBlock(BlockPos pos, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        BlockState original = level.getBlockState(pos);
        if (original.isAir()) return false;

        BlockStatePredictionHandler handler = predictionHandler(level);

        // 开启预测窗口：登记服务端已知状态，取号，发包
        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(pos, original, player);
            int sequence = predicting.currentSequence();

            player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, sequence));

            // 本地立刻置空气，保证同 tick 内的后续逻辑（如播种）不会读到旧状态
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        }

        // STOP 包不占用新的 sequence，服务端仅用于确认动作结束
        player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face));

        return true;
    }

    /**
     * 发送对方块使用物品包（播种）。
     *
     * @param hand    使用哪只手，副手补种传 OFF_HAND
     * @param soilPos 底盘坐标，种子会种在其上方
     * @return 是否成功发包
     */
    public static boolean useOnBlock(InteractionHand hand, BlockPos soilPos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        // 命中点取底盘上表面中心，朝向 UP，isInside=false
        Vec3 hitVec = new Vec3(soilPos.getX() + 0.5, soilPos.getY() + 1.0, soilPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, soilPos, false);

        BlockPos plantPos = soilPos.above();
        BlockState original = level.getBlockState(plantPos);

        BlockStatePredictionHandler handler = predictionHandler(level);

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(plantPos, original, player);
            int sequence = predicting.currentSequence();

            player.connection.send(new ServerboundUseItemOnPacket(hand, hitResult, sequence));
        }

        // 摆手动画，让服务端与旁观者看到正常的交互表现
        player.swing(hand);
        return true;
    }

    /**
     * 对方块本体右键（开箱子用）。
     *
     * 与 {@link #useOnBlock} 的区别是预测登记的是方块自身而不是它上方那一格：
     * 开容器不会改变方块状态，但 sequence 依然要照规矩取号，否则服务端拒绝这次交互。
     *
     * @param face 命中面，箱子给 UP 最稳，潜影盒朝向不影响开箱
     */
    public static boolean interactBlock(InteractionHand hand, BlockPos pos, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        Vec3 hitVec = new Vec3(
            pos.getX() + 0.5 + face.getStepX() * 0.5,
            pos.getY() + 0.5 + face.getStepY() * 0.5,
            pos.getZ() + 0.5 + face.getStepZ() * 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, face, pos, false);

        BlockState original = level.getBlockState(pos);
        BlockStatePredictionHandler handler = predictionHandler(level);

        try (BlockStatePredictionHandler predicting = handler.startPredicting()) {
            predicting.retainKnownServerState(pos, original, player);
            int sequence = predicting.currentSequence();
            player.connection.send(new ServerboundUseItemOnPacket(hand, hitResult, sequence));
        }

        player.swing(hand);
        return true;
    }

    /**
     * 读取主手工具的时运等级。
     *
     * 26.x 附魔是动态注册表，Enchantments.FORTUNE 只是 ResourceKey，
     * 必须通过世界注册表解析成 Holder 才能查等级。断线或注册表缺失时返回 0。
     */
    public static int getFortuneLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return 0;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return 0;

        try {
            var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var holder = lookup.get(Enchantments.FORTUNE).orElse(null);
            if (holder == null) return 0;
            return enchantments.getLevel(holder);
        } catch (Exception ignored) {
            // 注册表尚未同步完成时静默返回 0，交由调用方决定是否继续
            return 0;
        }
    }

    /**
     * 计算物品剩余耐久。不可损坏物品返回 Integer.MAX_VALUE。
     * 用于时运防爆锁：剩余耐久低于阈值时切空手停止破坏，防止工具爆掉。
     */
    public static int getRemainingDurability(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!stack.isDamageableItem()) return Integer.MAX_VALUE;
        if (stack.has(DataComponents.UNBREAKABLE)) return Integer.MAX_VALUE;
        return stack.getMaxDamage() - stack.getDamageValue();
    }
}
