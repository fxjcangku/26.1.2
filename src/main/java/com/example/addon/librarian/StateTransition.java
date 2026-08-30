// 附魔交易所 状态转换记录
package com.example.addon.librarian;

import java.util.Objects;

public record StateTransition(
    AutoLibrarianState previousState,
    AutoLibrarianState currentState,
    long tick,
    String reason
) {
    public StateTransition {
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(currentState, "currentState");
        reason = Objects.requireNonNullElse(reason, "");
        if (tick < 0) throw new IllegalArgumentException("tick 不能小于 0");
    }
}
