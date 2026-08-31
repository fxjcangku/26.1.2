// 附魔交易所 业务动作状态
package com.example.addon.librarian.service;

/**
 * 附魔交易所 · 业务动作状态。
 *
 * <p>表示一次业务动作（放置讲台、打开交易、购买等）的执行结果状态，
 * 供编排器决定下一步推进或重试。</p>
 */
public enum ActionStatus {
    /** 动作成功完成 */
    SUCCESS,
    /** 动作已提交，等待服务端响应 */
    WAITING,
    /** 动作需要重试 */
    RETRY,
    /** 动作失败 */
    FAILED
}
