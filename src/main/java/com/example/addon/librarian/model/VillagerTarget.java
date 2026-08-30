// 附魔交易所 村民目标模型
package com.example.addon.librarian.model;

import java.util.Objects;
import java.util.UUID;

public record VillagerTarget(
    UUID uuid,
    int entityId,
    BlockPosition position,
    VillagerTargetStatus status,
    LecternStatus lecternStatus,
    BlockPosition lecternPosition,
    HorizontalDirection direction,
    VillagerTradeStatus tradeStatus,
    VillagerStation station
) {
    public static final int UNRESOLVED_ENTITY_ID = -1;

    public VillagerTarget {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(lecternStatus, "lecternStatus");
        Objects.requireNonNull(tradeStatus, "tradeStatus");
        if (entityId < UNRESOLVED_ENTITY_ID) throw new IllegalArgumentException("entityId 不能小于 -1");
        if (status == VillagerTargetStatus.UNRESOLVED && entityId != UNRESOLVED_ENTITY_ID) {
            throw new IllegalArgumentException("未解析村民必须使用未解析实体 ID");
        }
        if (status != VillagerTargetStatus.UNRESOLVED && entityId == UNRESOLVED_ENTITY_ID) {
            throw new IllegalArgumentException("仅未解析村民可使用未解析实体 ID");
        }
        boolean positionRequired = lecternStatus == LecternStatus.LOCATED
            || lecternStatus == LecternStatus.PLACING
            || lecternStatus == LecternStatus.PLACED
            || lecternStatus == LecternStatus.REMOVING;
        if (positionRequired && lecternPosition == null) {
            throw new IllegalArgumentException("当前讲台状态必须包含讲台位置");
        }
        if (lecternStatus == LecternStatus.UNLOCATED && lecternPosition != null) {
            throw new IllegalArgumentException("未定位讲台不能包含讲台位置");
        }
        if (station != null) {
            if (!uuid.equals(station.villagerUuid())) throw new IllegalArgumentException("交易位必须属于当前村民");
            if (direction != station.villagerDirection()) throw new IllegalArgumentException("村民朝向必须与交易位一致");
            if (!station.lecternPosition().equals(lecternPosition)) {
                throw new IllegalArgumentException("当前讲台位置必须与交易位一致");
            }
        }
    }

    public VillagerTarget(
        UUID uuid,
        int entityId,
        BlockPosition position,
        VillagerTargetStatus status,
        LecternStatus lecternStatus,
        BlockPosition lecternPosition
    ) {
        this(
            uuid,
            entityId,
            position,
            status,
            lecternStatus,
            lecternPosition,
            null,
            VillagerTradeStatus.NOT_OPENED,
            null
        );
    }

    public VillagerTarget(UUID uuid, int entityId, BlockPosition position) {
        this(uuid, entityId, position, VillagerTargetStatus.SELECTED, LecternStatus.UNLOCATED, null);
    }

    public VillagerTarget withPosition(BlockPosition currentPosition) {
        return new VillagerTarget(
            uuid, entityId, currentPosition, status, lecternStatus, lecternPosition, direction, tradeStatus, station
        );
    }

    public VillagerTarget withResolvedEntity(int currentEntityId, BlockPosition currentPosition) {
        return new VillagerTarget(
            uuid,
            currentEntityId,
            currentPosition,
            VillagerTargetStatus.AVAILABLE,
            lecternStatus,
            lecternPosition,
            direction,
            tradeStatus,
            station
        );
    }

    public VillagerTarget asUnresolved() {
        return new VillagerTarget(
            uuid,
            UNRESOLVED_ENTITY_ID,
            position,
            VillagerTargetStatus.UNRESOLVED,
            lecternStatus,
            lecternPosition,
            direction,
            tradeStatus,
            station
        );
    }

    public VillagerTarget withStatus(VillagerTargetStatus currentStatus) {
        int currentEntityId = currentStatus == VillagerTargetStatus.UNRESOLVED ? UNRESOLVED_ENTITY_ID : entityId;
        return new VillagerTarget(
            uuid, currentEntityId, position, currentStatus, lecternStatus, lecternPosition, direction, tradeStatus, station
        );
    }

    public VillagerTarget withLectern(LecternStatus currentLecternStatus, BlockPosition currentLecternPosition) {
        VillagerStation currentStation = station;
        if (currentStation != null && !currentStation.lecternPosition().equals(currentLecternPosition)) {
            currentStation = null;
        }
        return new VillagerTarget(
            uuid,
            entityId,
            position,
            status,
            currentLecternStatus,
            currentLecternPosition,
            direction,
            tradeStatus,
            currentStation
        );
    }

    public VillagerTarget withStation(VillagerStation currentStation, LecternStatus currentLecternStatus) {
        Objects.requireNonNull(currentStation, "currentStation");
        return new VillagerTarget(
            uuid,
            entityId,
            position,
            status,
            currentLecternStatus,
            currentStation.lecternPosition(),
            currentStation.villagerDirection(),
            tradeStatus,
            currentStation
        );
    }

    public VillagerTarget withTradeStatus(VillagerTradeStatus currentTradeStatus) {
        return new VillagerTarget(
            uuid, entityId, position, status, lecternStatus, lecternPosition, direction, currentTradeStatus, station
        );
    }
}
