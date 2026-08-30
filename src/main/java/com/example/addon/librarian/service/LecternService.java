// 附魔交易所 讲台服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.VillagerTarget;

import java.util.Optional;

public interface LecternService {
    Optional<BlockPosition> findPlacement(VillagerTarget target);

    ActionResult place(BlockPosition position);

    ActionResult breakLectern(BlockPosition position);

    boolean isLecternPresent(BlockPosition position);
}
