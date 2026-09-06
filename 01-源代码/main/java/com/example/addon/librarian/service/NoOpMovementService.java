// 自动图书管理员 无 Baritone 移动服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;

/**
 * 自动图书管理员 · 无 Baritone 降级移动服务。
 *
 * <p>当客户端未安装 Baritone 时使用的空实现，所有移动请求一律返回
 * 不可用 / 未到达，使自动图书管理员退化为「仅身边村民」的原地交易模式。</p>
 */
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
