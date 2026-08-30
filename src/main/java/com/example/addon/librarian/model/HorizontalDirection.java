// 附魔交易所 水平方向模型
package com.example.addon.librarian.model;

public enum HorizontalDirection {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int offsetX;
    private final int offsetZ;

    HorizontalDirection(int offsetX, int offsetZ) {
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetZ() {
        return offsetZ;
    }

    public HorizontalDirection opposite() {
        return values()[(ordinal() + 2) % values().length];
    }
}
