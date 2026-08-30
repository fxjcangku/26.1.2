// 附魔交易所 岩浆块访问接口
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.HorizontalDirection;

public interface MarkerBlockAccess {
    /** 指定位置是否为岩浆块 */
    boolean isMagmaBlock(BlockPosition position);

    /** 指定位置是否为空气 */
    boolean isAir(BlockPosition position);

    /** 讲台是否朝向指定方向 */
    boolean isLecternFacing(BlockPosition position, HorizontalDirection facing);

    /** 指定位置能否放置讲台 */
    boolean canPlaceLectern(BlockPosition position);
}
