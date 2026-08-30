// 附魔交易所 方块坐标模型
package com.example.addon.librarian.model;

public record BlockPosition(int x, int y, int z) {
    public BlockPosition offset(HorizontalDirection direction) {
        return new BlockPosition(x + direction.offsetX(), y, z + direction.offsetZ());
    }

    public BlockPosition down() {
        return new BlockPosition(x, y - 1, z);
    }

    public BlockPosition up() {
        return new BlockPosition(x, y + 1, z);
    }
}
