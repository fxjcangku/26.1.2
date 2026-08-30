// 附魔交易所 无 Baritone 移动服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;

public final class NoOpMovementService implements MovementService {
    @Override
    public MovementStartResult gotoPosition(BlockPosition position, int radiusBlocks) {
        return MovementStartResult.UNAVAILABLE;
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isPathing() {
        return false;
    }

    @Override
    public boolean hasArrived() {
        return false;
    }

    @Override
    public MovementStatus getStatus() {
        return MovementStatus.UNAVAILABLE;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
