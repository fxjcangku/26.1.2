// 自动图书管理员 村民搜索服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;

public interface VillagerSearchService {
    /** 在半径内查找最近的失业村民 */
    Optional<VillagerTarget> findNearestUnemployedVillager(int radiusBlocks);

    /** 目标村民是否仍有效（存在且存活） */
    boolean isValid(VillagerTarget target);

    /** 目标村民是否为失业状态 */
    boolean isUnemployed(VillagerTarget target);

    /** 目标村民是否为图书管理员 */
    boolean isLibrarian(VillagerTarget target);
}
