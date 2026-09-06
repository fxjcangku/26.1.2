package com.example.addon.autochest.model;

/**
 * AutoChest 三种运行模式。
 *
 * <p>三种模式互斥，决定「容器从哪来」：</p>
 * <ul>
 *   <li>玩家控制：玩家自己走位，模块不控制移动，进入触发距离后自动处理。</li>
 *   <li>寻路：模块自动扫描容器、选目标、算站位、调用 Baritone 寻路过去。</li>
 *   <li>标点：只处理用户通过指令保存的点位，不因附近扫描到箱子而自动处理。</li>
 * </ul>
 */
public enum ScanMode {

    /** 玩家控制模式：玩家自己走，进入触发距离自动处理容器 */
    PLAYER_CONTROL("玩家控制模式"),

    /** 寻路模式：自动扫描并寻路到容器面前处理 */
    PATHING("寻路模式"),

    /** 标点模式：只处理用户保存的点位 */
    MARKER("标点模式");

    private final String displayName;

    ScanMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
