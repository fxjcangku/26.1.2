// 自动图书管理员 移动启动结果
package com.example.addon.librarian.service;

/**
 * 自动图书管理员 · 移动启动结果。
 *
 * <p>表示发起一次寻路移动请求后的即时结果，用于判断移动服务是否真正接手，
 * 以及是否需要重试或降级。</p>
 */
public enum MovementStartResult {
    /** 移动已启动 */
    STARTED,
    /** 已有移动任务在运行 */
    ALREADY_RUNNING,
    /** 移动服务不可用 */
    UNAVAILABLE,
    /** 移动请求被拒绝 */
    REJECTED,
    /** 启动失败 */
    FAILED
}
