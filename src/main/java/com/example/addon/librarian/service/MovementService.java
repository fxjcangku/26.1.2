// 附魔交易所 移动服务契约
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;

public interface MovementService {
    MovementStartResult gotoPosition(BlockPosition position, int radiusBlocks);

    void stop();

    boolean isPathing();

    boolean hasArrived();

    MovementStatus getStatus();

    boolean isAvailable();
}
