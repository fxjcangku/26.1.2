// 自动图书管理员 固定交易位服务接口
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.HorizontalDirection;
import com.example.addon.librarian.model.VillagerStation;
import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;

public interface VillagerStationService {
    /** 检测村民当前所在的固定交易位（岩浆块标记） */
    Optional<VillagerStation> detect(VillagerTarget target);

    /** 校验交易位是否有效（岩浆块 + 讲台朝向） */
    MarkerBlockValidation validate(VillagerStation station);

    /** 计算岩浆块标记方块位置（村民朝向方向偏移一格） */
    default BlockPosition calculateMarkerBlockPosition(BlockPosition villagerPosition, HorizontalDirection direction) {
        return villagerPosition.offset(direction);
    }
}
