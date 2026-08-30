// 附魔交易所 讲台服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;

public interface LecternService {
    /** 查找讲台的可放置位置 */
    Optional<BlockPosition> findPlacement(VillagerTarget target);

    /** 在指定位置放置讲台 */
    ActionResult place(BlockPosition position);

    /** 拆除指定位置的讲台 */
    ActionResult breakLectern(BlockPosition position);

    /** 指定位置是否存在讲台 */
    boolean isLecternPresent(BlockPosition position);
}
