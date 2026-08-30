// 附魔交易所 村民搜索服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;

public interface VillagerSearchService {
    Optional<VillagerTarget> findNearestUnemployedVillager(int radiusBlocks);

    boolean isValid(VillagerTarget target);

    boolean isUnemployed(VillagerTarget target);

    boolean isLibrarian(VillagerTarget target);
}
