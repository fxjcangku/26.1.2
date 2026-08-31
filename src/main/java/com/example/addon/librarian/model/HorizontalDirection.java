// 附魔交易所 水平方向模型
package com.example.addon.librarian.model;

/**
 * 附魔交易所 · 水平方向。
 *
 * <p>仅包含四个水平朝向（北东南西），用于村民朝向、讲台阅读面、
 * 玩家站位偏移等固定交易位几何计算。每个方向携带 X / Z 轴偏移量。</p>
 */
public enum HorizontalDirection {
    /** 北：Z 轴负方向 */
    NORTH(0, -1),
    /** 东：X 轴正方向 */
    EAST(1, 0),
    /** 南：Z 轴正方向 */
    SOUTH(0, 1),
    /** 西：X 轴负方向 */
    WEST(-1, 0);

    /** X 轴偏移量 */
    private final int offsetX;
    /** Z 轴偏移量 */
    private final int offsetZ;

    HorizontalDirection(int offsetX, int offsetZ) {
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    /** 返回 X 轴偏移量 */
    public int offsetX() {
        return offsetX;
    }

    /** 返回 Z 轴偏移量 */
    public int offsetZ() {
        return offsetZ;
    }

    /** 返回相反方向（用于计算讲台阅读面，阅读面与村民朝向相反） */
    public HorizontalDirection opposite() {
        return values()[(ordinal() + 2) % values().length];
    }
}
