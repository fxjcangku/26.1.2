// 自动图书管理员 移动状态
package com.example.addon.librarian.service;

/**
 * 自动图书管理员 · 移动状态。
 *
 * <p>描述寻路移动任务在整个生命周期中的当前状态，供编排器轮询判断
 * 是否到达、失败或超时，从而决定是否进入下一环节。</p>
 */
public enum MovementStatus {
    /** 空闲，未发起移动 */
    IDLE,
    /** 移动任务启动中 */
    STARTING,
    /** 正在寻路 */
    PATHING,
    /** 已到达目标 */
    ARRIVED,
    /** 移动失败 */
    FAILED,
    /** 移动被取消 */
    CANCELED,
    /** 移动超时 */
    TIMED_OUT,
    /** 移动服务不可用 */
    UNAVAILABLE
}
