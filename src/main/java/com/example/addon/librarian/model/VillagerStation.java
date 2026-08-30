// 附魔交易所 固定交易位模型
package com.example.addon.librarian.model;

import java.util.Objects;
import java.util.UUID;

public record VillagerStation(
    UUID villagerUuid,
    BlockPosition villagerPosition,
    HorizontalDirection villagerFacing,
    BlockPosition markerBlockPosition,
    BlockPosition lecternPosition,
    BlockPosition playerStandPosition,
    HorizontalDirection lecternFacing,
    StationValidationStatus validationStatus
) {
    public VillagerStation {
        Objects.requireNonNull(villagerUuid, "villagerUuid");
        Objects.requireNonNull(villagerPosition, "villagerPosition");
        Objects.requireNonNull(villagerFacing, "villagerFacing");
        Objects.requireNonNull(markerBlockPosition, "markerBlockPosition");
        Objects.requireNonNull(lecternPosition, "lecternPosition");
        Objects.requireNonNull(playerStandPosition, "playerStandPosition");
        Objects.requireNonNull(lecternFacing, "lecternFacing");
        Objects.requireNonNull(validationStatus, "validationStatus");
        if (!markerBlockPosition.equals(villagerPosition.offset(villagerFacing))) {
            throw new IllegalArgumentException("岩浆块必须位于村民前方一格");
        }
        if (!lecternPosition.equals(markerBlockPosition.up())) {
            throw new IllegalArgumentException("讲台必须位于岩浆块上方");
        }
        if (!playerStandPosition.equals(markerBlockPosition.offset(villagerFacing))) {
            throw new IllegalArgumentException("玩家站位必须位于岩浆块沿村民朝向继续偏移一格的位置");
        }
        if (lecternFacing != villagerFacing.opposite()) {
            throw new IllegalArgumentException("讲台阅读面必须朝向玩家交互侧");
        }
    }

    public static VillagerStation create(
        UUID villagerUuid,
        BlockPosition villagerPosition,
        HorizontalDirection villagerDirection,
        StationValidationStatus validationStatus
    ) {
        BlockPosition markerBlockPosition = villagerPosition.offset(villagerDirection);
        return new VillagerStation(
            villagerUuid,
            villagerPosition,
            villagerDirection,
            markerBlockPosition,
            markerBlockPosition.up(),
            markerBlockPosition.offset(villagerDirection),
            villagerDirection.opposite(),
            validationStatus
        );
    }

    public VillagerStation withValidationStatus(StationValidationStatus currentStatus) {
        return new VillagerStation(
            villagerUuid,
            villagerPosition,
            villagerFacing,
            markerBlockPosition,
            lecternPosition,
            playerStandPosition,
            lecternFacing,
            currentStatus
        );
    }

    public HorizontalDirection villagerDirection() {
        return villagerFacing;
    }

    public HorizontalDirection lecternDirection() {
        return lecternFacing;
    }
}
