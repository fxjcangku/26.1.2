// 自动图书管理员 移动服务契约
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;

public interface MovementService {
    /** 寻路到目标位置（进入半径内视为到达） */
    MovementStartResult gotoPosition(BlockPosition position, int radiusBlocks);

    /** 停止当前寻路 */
    void stop();

    /** 是否正在寻路 */
    boolean isPathing();

    /** 是否已到达目标位置 */
    boolean hasArrived();

    /** 获取当前移动状态 */
    MovementStatus getStatus();

    /** 移动服务（Baritone）是否可用 */
    boolean isAvailable();
}
