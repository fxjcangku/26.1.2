// 附魔交易所 交易位验证状态
package com.example.addon.librarian.model;

/**
 * 附魔交易所 · 固定交易位验证状态。
 *
 * <p>描述一个村民交易位（岩浆块 + 讲台 + 玩家站位）的校验结果，
 * 只有 {@link #VALID} 的交易位才允许进入放置讲台流程。</p>
 */
public enum StationValidationStatus {
    /** 尚未校验 */
    UNVALIDATED,
    /** 校验通过，交易位可用 */
    VALID,
    /** 校验失败，交易位不可用 */
    INVALID
}
