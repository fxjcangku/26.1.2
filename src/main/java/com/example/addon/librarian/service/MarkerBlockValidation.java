// 附魔交易所 交易位验证结果
package com.example.addon.librarian.service;

import java.util.Objects;

/**
 * 附魔交易所 · 交易位验证结果。
 *
 * <p>表示对某个固定交易位（岩浆块 + 讲台 + 玩家站位）的校验结论，
 * 无效时携带具体原因供提示播报。</p>
 */
public record MarkerBlockValidation(
    /** 是否校验通过 */
    boolean valid,
    /** 校验失败原因（通过时为空串） */
    String reason
) {
    public MarkerBlockValidation {
        reason = Objects.requireNonNullElse(reason, "");
    }

    /** 校验通过的结果 */
    public static MarkerBlockValidation success() {
        return new MarkerBlockValidation(true, "");
    }

    /** 校验失败的结果（携带原因） */
    public static MarkerBlockValidation invalid(String reason) {
        return new MarkerBlockValidation(false, reason);
    }
}
