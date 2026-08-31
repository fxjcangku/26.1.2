// 附魔交易所 固定交易位服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.HorizontalDirection;
import com.example.addon.librarian.model.StationValidationStatus;
import com.example.addon.librarian.model.VillagerStation;
import com.example.addon.librarian.model.VillagerTarget;
import com.example.addon.librarian.service.MarkerBlockAccess;
import com.example.addon.librarian.service.MarkerBlockValidation;
import com.example.addon.librarian.service.MarkerBlockValidator;
import com.example.addon.librarian.service.VillagerStationService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 附魔交易所 · 固定交易位服务实现（Fabric 客户端）。
 *
 * <p>根据村民位置与朝向探测可用交易位（岩浆块 + 玩家站位 + 讲台位），
 * 并执行交易位校验与方块查询。优先选择讲台位空闲的岩浆块，避免多余挖掘。</p>
 */
public final class FabricVillagerStationService implements VillagerStationService, MarkerBlockAccess {
    private final MarkerBlockValidator validator = new MarkerBlockValidator(this);

    @Override
    public Optional<VillagerStation> detect(VillagerTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Optional.empty();
        // 先用 entity ID 快速查，失败时用 UUID 扫描兜底
        Villager villager = null;
        if (mc.level.getEntity(target.entityId()) instanceof Villager v
                && target.uuid().equals(v.getUUID())) {
            villager = v;
        } else if (mc.player != null) {
            AABB box = mc.player.getBoundingBox().inflate(64);
            villager = mc.level.getEntitiesOfClass(Villager.class, box,
                    e -> target.uuid().equals(e.getUUID()))
                .stream().findFirst().orElse(null);
        }
        if (villager == null) return Optional.empty();

        BlockPosition villagerPos = new BlockPosition(villager.getBlockX(), villager.getBlockY(), villager.getBlockZ());

        // 检查同Y层和Y-1层（岩浆块在地面时村民站在上方）
        int[] yOffsets = {0, -1};

        // 第一遍：优先选择讲台位无障碍的岩浆块（canPlaceLectern 直接为 true），
        // 避免优先选中被方块覆盖的后方岩浆块导致 Baritone 去挖方块
        for (int dy : yOffsets) {
            BlockPosition scanPos = new BlockPosition(villagerPos.x(), villagerPos.y() + dy, villagerPos.z());
            for (HorizontalDirection dir : HorizontalDirection.values()) {
                BlockPosition adjacent = scanPos.offset(dir);
                BlockPosition rawStand = adjacent.offset(dir);
                BlockPosition standPos = new BlockPosition(rawStand.x(), villagerPos.y(), rawStand.z());
                if (mc.level.getBlockState(toPos(adjacent)).is(Blocks.MAGMA_BLOCK)
                        && isPlayerStandable(mc, standPos)
                        && canPlaceLectern(adjacent.up())) {  // 讲台位已经空闲，无需挖方块
                    BlockPosition effectiveVillagerPos = dy == 0 ? villagerPos
                        : new BlockPosition(villagerPos.x(), villagerPos.y() - 1, villagerPos.z());
                    return Optional.of(VillagerStation.create(
                        target.uuid(), effectiveVillagerPos, dir, StationValidationStatus.UNVALIDATED
                    ));
                }
            }
        }

        // 第二遍：无裸露岩浆块时，接受需要清除障碍的岩浆块（fallback）
        // 收集所有候选，选离玩家最近的，避免因方向枚举顺序优先选中背后被埋的岩浆块
        Vec3 playerPos = mc.player != null
            ? mc.player.position()
            : new Vec3(villagerPos.x(), villagerPos.y(), villagerPos.z());
        BlockPosition bestAdjacentFallback = null;
        HorizontalDirection bestDirFallback = null;
        int bestDyFallback = 0;
        double bestDistFallback = Double.MAX_VALUE;
        for (int dy : yOffsets) {
            BlockPosition scanPos = new BlockPosition(villagerPos.x(), villagerPos.y() + dy, villagerPos.z());
            for (HorizontalDirection dir : HorizontalDirection.values()) {
                BlockPosition adjacent = scanPos.offset(dir);
                BlockPosition rawStand = adjacent.offset(dir);
                BlockPosition standPos = new BlockPosition(rawStand.x(), villagerPos.y(), rawStand.z());
                if (mc.level.getBlockState(toPos(adjacent)).is(Blocks.MAGMA_BLOCK)
                        && isPlayerStandable(mc, standPos)) {
                    // 计算讲台放置位（岩浆块上方）到玩家的距离
                    BlockPosition lecternPos = adjacent.up();
                    Vec3 lecternVec = new Vec3(lecternPos.x(), lecternPos.y(), lecternPos.z());
                    double dist = playerPos.distanceToSqr(lecternVec);
                    if (dist < bestDistFallback) {
                        bestDistFallback = dist;
                        bestAdjacentFallback = adjacent;
                        bestDirFallback = dir;
                        bestDyFallback = dy;
                    }
                }
            }
        }
        if (bestAdjacentFallback != null) {
            BlockPosition effectiveVillagerPos = bestDyFallback == 0 ? villagerPos
                : new BlockPosition(villagerPos.x(), villagerPos.y() - 1, villagerPos.z());
            return Optional.of(VillagerStation.create(
                target.uuid(), effectiveVillagerPos, bestDirFallback, StationValidationStatus.UNVALIDATED
            ));
        }
        return Optional.empty();
    }

    @Override
    public MarkerBlockValidation validate(VillagerStation station) {
        return validator.validate(station);
    }

    @Override
    public boolean isMagmaBlock(BlockPosition position) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockState(toPos(position)).is(Blocks.MAGMA_BLOCK);
    }

    @Override
    public boolean isAir(BlockPosition position) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockState(toPos(position)).isAir();
    }

    @Override
    public boolean isLecternFacing(BlockPosition position, HorizontalDirection facing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        var state = mc.level.getBlockState(toPos(position));
        return state.is(Blocks.LECTERN) && state.getValue(LecternBlock.FACING) == toDirection(facing);
    }

    @Override
    public boolean canPlaceLectern(BlockPosition position) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockPos pos = toPos(position);
        var state = mc.level.getBlockState(pos);
        // 活版门视为可放置位
        boolean placeable = state.isAir() || state.getBlock() instanceof TrapDoorBlock;
        return placeable && mc.level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK);
    }

    private Direction toDirection(HorizontalDirection direction) {
        return switch (direction) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
        };
    }

    /** 检查玩家站位是否可通行：脚和头两格都不能是完整实体方块（空气、铁链、活版门等均可通行） */
    private boolean isPlayerStandable(Minecraft mc, BlockPosition pos) {
        if (mc.level == null) return false;
        BlockPos feet = toPos(pos);
        BlockPos head = feet.above();
        return !mc.level.getBlockState(feet).isCollisionShapeFullBlock(mc.level, feet)
            && !mc.level.getBlockState(head).isCollisionShapeFullBlock(mc.level, head);
    }

    private BlockPos toPos(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
