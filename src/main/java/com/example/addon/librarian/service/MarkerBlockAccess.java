// 附魔交易所 岩浆块访问接口
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.HorizontalDirection;

public interface MarkerBlockAccess {
    boolean isMagmaBlock(BlockPosition position);

    boolean isAir(BlockPosition position);

    boolean isLecternFacing(BlockPosition position, HorizontalDirection facing);

    boolean canPlaceLectern(BlockPosition position);
}
