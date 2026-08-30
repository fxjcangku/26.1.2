// 附魔交易所 业务动作结果
package com.example.addon.librarian.service;

import java.util.Objects;

public record ActionResult(ActionStatus status, String reason) {
    public ActionResult {
        Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNullElse(reason, "");
    }

    public static ActionResult success() {
        return new ActionResult(ActionStatus.SUCCESS, "");
    }

    public static ActionResult waiting() {
        return new ActionResult(ActionStatus.WAITING, "");
    }

    public static ActionResult retry(String reason) {
        return new ActionResult(ActionStatus.RETRY, reason);
    }

    public static ActionResult failed(String reason) {
        return new ActionResult(ActionStatus.FAILED, reason);
    }
}
