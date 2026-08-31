// 附魔交易所 讲台放置服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.HorizontalDirection;
import com.example.addon.librarian.model.VillagerStation;
import com.example.addon.librarian.service.ActionResult;
import com.example.addon.librarian.service.LecternPlacementService;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 附魔交易所 · 讲台放置服务实现（Fabric 客户端）。
 *
 * <p>负责在固定交易位上放置 / 拆除讲台、清除障碍方块。挖掘采用分步状态机
 * （选工具 → 到位 → 持续挖掘），放置前会校验岩浆块底座并同步玩家朝向。</p>
 */
public final class FabricLecternPlacementService implements LecternPlacementService {
    @Override
    public boolean hasObstacle(VillagerStation station) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockPos pos = toPos(station.lecternPosition());
        // 只检查Y+1（讲台位），Y+2是活版门卡位不动
        var state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(Blocks.LECTERN)) return false;
        return true;
    }

    // 挖掘状态：0=未初始化，1=等待工具到位，2=正在挖
    private int breakState = 0;
    private int breakWaitTick = 0;
    private BlockPos activeBreakTarget;
    private boolean breaking;

    /** 返回需要挖的障碍方块（只看Y+1讲台位），无障碍返回null */
    private BlockPos currentBreakTarget(Minecraft mc, VillagerStation station) {
        BlockPos lectern = toPos(station.lecternPosition());
        var state = mc.level.getBlockState(lectern);
        if (state.isAir()) return null;
        if (state.is(Blocks.LECTERN)) return null;
        return lectern;
    }

    @Override
    public ActionResult breakObstacle(VillagerStation station) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) return ActionResult.retry("游戏未就绪");

        BlockPos pos = currentBreakTarget(mc, station);
        if (pos == null) {
            resetBreaking(mc);
            return ActionResult.success();
        }

        var blockState = mc.level.getBlockState(pos);

        if (activeBreakTarget == null || !activeBreakTarget.equals(pos)) {
            resetBreaking(mc);
            activeBreakTarget = pos.immutable();
        }

        if (breakState == 0) {
            int hotbarBest = bestToolInHotbar(mc, blockState);
            if (hotbarBest >= 0) {
                mc.player.getInventory().setSelectedSlot(hotbarBest);
                breakState = 2;
            } else {
                int invBest = bestToolInInventory(mc, blockState);
                if (invBest >= 0) {
                    InvUtils.move().from(invBest).toHotbar(0);
                } else {
                    mc.player.getInventory().setSelectedSlot(0);
                }
                breakState = 1;
                breakWaitTick = 0;
            }
            return ActionResult.waiting();
        }

        if (breakState == 1) {
            breakWaitTick++;
            if (breakWaitTick >= 2) {
                mc.player.getInventory().setSelectedSlot(0);
                breakState = 2;
            }
            return ActionResult.waiting();
        }

        // breakState == 2：当前目标方块切换时重置状态，确保重新选工具
        if (!mc.level.getBlockState(pos).equals(blockState)) {
            resetBreaking(mc);
            return ActionResult.waiting();
        }

        Direction hitFace = toDirection(station.lecternFacing());
        if (!breaking) {
            mc.gameMode.startDestroyBlock(pos, hitFace);
            breaking = true;
        } else if (!mc.gameMode.continueDestroyBlock(pos, hitFace)) {
            mc.gameMode.startDestroyBlock(pos, hitFace);
        }
        return ActionResult.waiting();
    }

    @Override
    public ActionResult place(VillagerStation station) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return ActionResult.retry("游戏世界尚未就绪。");
        }
        BlockPos target = toPos(station.lecternPosition());

        if (mc.level.getBlockState(target).is(Blocks.LECTERN)) {
            return ActionResult.success();
        }
        if (!mc.level.getBlockState(target).isAir()) {
            return ActionResult.failed("讲台目标位置已被占用: " + mc.level.getBlockState(target).getBlock());
        }
        if (!mc.level.getBlockState(target.below()).is(Blocks.MAGMA_BLOCK)) {
            return ActionResult.failed("讲台下方未检测到岩浆块，下方是: " + mc.level.getBlockState(target.below()).getBlock());
        }
        FindItemResult lectern = InvUtils.findInHotbar(Items.LECTERN);
        if (!lectern.found()) {
            lectern = InvUtils.find(Items.LECTERN);
            if (!lectern.found()) return ActionResult.failed("背包中没有讲台。");
            InvUtils.move().from(lectern.slot()).toHotbar(0);
            lectern = InvUtils.findInHotbar(Items.LECTERN);
            if (!lectern.found()) return ActionResult.retry("讲台移至热键栏失败，下次重试。");
        }
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(lectern.slot(), false);
        // 先同步朝向给服务端，单机环境同tick处理有序，确保放置方向正确
        applyFacing(mc, station.lecternFacing());
        BlockHitResult hit = new BlockHitResult(
            Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false
        );
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        InvUtils.swap(previousSlot, false);
        return result == InteractionResult.FAIL
            ? ActionResult.retry("讲台放置交互被拒绝。")
            : ActionResult.waiting();
    }

    @Override
    public ActionResult breakLectern(VillagerStation station) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return ActionResult.retry("游戏世界尚未就绪。");
        }
        BlockPos pos = toPos(station.lecternPosition());
        if (mc.level.getBlockState(pos).isAir()) {
            resetBreaking(mc);
            return ActionResult.success();
        }
        if (!mc.level.getBlockState(pos).is(Blocks.LECTERN)) {
            return ActionResult.failed("固定讲台位置存在非讲台方块，拒绝拆除。");
        }
        var blockState = mc.level.getBlockState(pos);
        if (activeBreakTarget == null || !activeBreakTarget.equals(pos)) {
            resetBreaking(mc);
            activeBreakTarget = pos.immutable();
        }

        if (breakState == 0) {
            int hotbarBest = bestToolInHotbar(mc, blockState);
            if (hotbarBest >= 0) {
                mc.player.getInventory().setSelectedSlot(hotbarBest);
                breakState = 2;
            } else {
                int invBest = bestToolInInventory(mc, blockState);
                if (invBest >= 0) {
                    InvUtils.move().from(invBest).toHotbar(0);
                } else {
                    mc.player.getInventory().setSelectedSlot(0);
                }
                breakState = 1;
                breakWaitTick = 0;
            }
            return ActionResult.waiting();
        }

        if (breakState == 1) {
            breakWaitTick++;
            if (breakWaitTick >= 2) {
                mc.player.getInventory().setSelectedSlot(0);
                breakState = 2;
            }
            return ActionResult.waiting();
        }

        Direction hitFace = toDirection(station.lecternFacing());
        if (!breaking) {
            mc.gameMode.startDestroyBlock(pos, hitFace);
            breaking = true;
        } else if (!mc.gameMode.continueDestroyBlock(pos, hitFace)) {
            mc.gameMode.startDestroyBlock(pos, hitFace);
        }
        return ActionResult.waiting();
    }

    /** 在热键栏0-8中找最快工具，返回槽位号，找不到返回-1 */
    private int bestToolInHotbar(Minecraft mc, BlockState blockState) {
        int best = -1;
        float bestSpeed = 1f;
        for (int i = 0; i < 9; i++) {
            float speed = mc.player.getInventory().getItem(i).getDestroySpeed(blockState);
            if (speed > bestSpeed) { bestSpeed = speed; best = i; }
        }
        return best;
    }

    /** 在背包9-35中找最快工具，返回槽位号，找不到返回-1 */
    private int bestToolInInventory(Minecraft mc, BlockState blockState) {
        int best = -1;
        float bestSpeed = 1f;
        for (int i = 9; i < 36; i++) {
            float speed = mc.player.getInventory().getItem(i).getDestroySpeed(blockState);
            if (speed > bestSpeed) { bestSpeed = speed; best = i; }
        }
        return best;
    }

    @Override
    public boolean validatePlacement(VillagerStation station) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.getBlockState(toPos(station.lecternPosition())).is(Blocks.LECTERN);
    }

    @Override
    public boolean validateRemoval(VillagerStation station) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockState(toPos(station.lecternPosition())).isAir();
    }

    private void applyFacing(Minecraft mc, HorizontalDirection facing) {
        Direction placementPlayerFacing = toDirection(facing);
        float yaw = switch (placementPlayerFacing) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> mc.player.getYRot();
        };
        if (mc.getConnection() != null) {
            mc.getConnection().send(
                new ServerboundMovePlayerPacket.Rot(
                    yaw, mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision
                )
            );
        }
    }

    private Direction toDirection(HorizontalDirection direction) {
        return switch (direction) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
        };
    }

    private BlockPos toPos(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private void resetBreaking(Minecraft mc) {
        if (mc.gameMode != null && breaking) mc.gameMode.stopDestroyBlock();
        breakState = 0;
        breakWaitTick = 0;
        activeBreakTarget = null;
        breaking = false;
    }
}
