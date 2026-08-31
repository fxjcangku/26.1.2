// 附魔交易所 业务动作结果
package com.example.addon.librarian.service;

import java.util.Objects;

/**
 * 附魔交易所 · 业务动作结果。
 *
 * <p>封装一次业务动作的状态与可读原因，是编排器与服务层之间的统一返回类型。
 * 通过静态工厂方法快速构造成功 / 等待 / 重试 / 失败四种结果。</p>
 */
public record ActionResult(
    /** 动作状态 */
    ActionStatus status,
    /** 附加原因（成功或等待时可为空串） */
    String reason
) {
    public ActionResult {
        Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNullElse(reason, "");
    }

    /** 成功结果 */
    public static ActionResult success() {
        return new ActionResult(ActionStatus.SUCCESS, "");
    }

    /** 等待结果（已提交，等待服务端） */
    public static ActionResult waiting() {
        return new ActionResult(ActionStatus.WAITING, "");
    }

    /** 重试结果（携带重试原因） */
    public static ActionResult retry(String reason) {
        return new ActionResult(ActionStatus.RETRY, reason);
    }

    /** 失败结果（携带失败原因） */
    public static ActionResult failed(String reason) {
        return new ActionResult(ActionStatus.FAILED, reason);
    }
}
