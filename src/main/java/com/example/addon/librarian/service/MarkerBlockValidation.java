// 附魔交易所 交易位验证结果
package com.example.addon.librarian.service;

import java.util.Objects;

public record MarkerBlockValidation(boolean valid, String reason) {
    public MarkerBlockValidation {
        reason = Objects.requireNonNullElse(reason, "");
    }

    public static MarkerBlockValidation success() {
        return new MarkerBlockValidation(true, "");
    }

    public static MarkerBlockValidation invalid(String reason) {
        return new MarkerBlockValidation(false, reason);
    }
}
