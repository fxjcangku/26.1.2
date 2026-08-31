// 附魔交易所 当前村民目标状态
package com.example.addon.librarian.model;

/**
 * 附魔交易所 · 村民目标状态。
 *
 * <p>描述一个被选中的失业村民在「搜索 → 解析 → 可用」过程中的当前定位状态，
 * 决定状态机下一步是否能够对其执行放置讲台等操作。</p>
 */
public enum VillagerTargetStatus {
    /** 已被选中为目标村民 */
    SELECTED,
    /** 实体已解析，处于可用状态 */
    AVAILABLE,
    /** 实体 ID 尚未解析（需要后续重新匹配） */
    UNRESOLVED,
    /** 不可用（已消失或无法定位） */
    UNUSABLE
}
