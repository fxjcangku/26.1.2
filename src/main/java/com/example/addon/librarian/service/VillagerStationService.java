// 附魔交易所 固定交易位服务接口
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.HorizontalDirection;
import com.example.addon.librarian.model.VillagerStation;
import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;

public interface VillagerStationService {
    Optional<VillagerStation> detect(VillagerTarget target);

    MarkerBlockValidation validate(VillagerStation station);

    default BlockPosition calculateMarkerBlockPosition(BlockPosition villagerPosition, HorizontalDirection direction) {
        return villagerPosition.offset(direction);
    }
}
