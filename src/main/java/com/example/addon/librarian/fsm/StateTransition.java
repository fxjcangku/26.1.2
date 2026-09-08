// 自动图书管理员 状态转换记录
package com.example.addon.librarian.fsm;

import java.util.Objects;

/**
 * 自动图书管理员 · 状态转换记录。
 *
 * <p>描述状态机的一次合法转换（前一状态 → 当前状态），携带触发 tick 与
 * 转换原因，供状态播报与调试诊断使用。</p>
 */
public record StateTransition(
    /** 前一状态 */
    AutoLibrarianState previousState,
    /** 当前状态 */
    AutoLibrarianState currentState,
    /** 转换发生的 tick */
    long tick,
    /** 转换原因（可为空串） */
    String reason
) {
    public StateTransition {
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(currentState, "currentState");
        reason = Objects.requireNonNullElse(reason, "");
        if (tick < 0) throw new IllegalArgumentException("tick 不能小于 0");
    }
}
