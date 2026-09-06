// 自动图书管理员 状态定义
package com.example.addon.librarian;

/**
 * 自动图书管理员 · 状态机状态定义。
 *
 * <p>描述「搜索失业村民 → 放置讲台刷新交易 → 命中目标附魔购买 → 完成」的
 * 完整自动化流程，每个状态由 {@link AutoLibrarianStateMachine} 驱动。</p>
 */
public enum AutoLibrarianState {
    /** 空闲（未运行） */
    IDLE,
    /** 启动 */
    START,
    /** 搜索失业村民 */
    SEARCH_VILLAGER,
    /** 选中目标村民 */
    SELECT_TARGET_VILLAGER,
    /** 寻路移动到村民附近 */
    MOVE_TO_VILLAGER,
    /** 探测固定交易位（岩浆块 + 讲台位） */
    FIND_LECTERN_POSITION,
    /** 移动到玩家站位 */
    MOVE_TO_STAND_POSITION,
    /** 清除讲台位障碍方块 */
    BREAK_OBSTACLE,
    /** 放置讲台 */
    PLACE_LECTERN,
    /** 等待村民接受图书管理员职业 */
    WAIT_PROFESSION,
    /** 打开村民交易界面 */
    OPEN_TRADE,
    /** 等待交易界面同步 */
    WAIT_TRADE_SCREEN,
    /** 读取交易报价列表 */
    READ_TRADES,
    /** 检查报价是否命中目标附魔 */
    CHECK_ENCHANTMENT,
    /** 重置（未命中，拆除讲台刷新） */
    RESET,
    /** 拆除讲台 */
    BREAK_LECTERN,
    /** 等待村民恢复失业状态 */
    WAIT_UNEMPLOYED,
    /** 成功命中目标附魔 */
    SUCCESS_FOUND,
    /** 执行交易购买 */
    TRADE_PROCESS,
    /** 选中交易报价 */
    SELECT_TRADE,
    /** 等待交易同步（服务端确认） */
    WAIT_TRADE_SYNC,
    /** 取出交易成品（附魔书） */
    TAKE_TRADE_OUTPUT,
    /** 验证购买结果（库存对比） */
    VERIFY_PURCHASE,
    /** 完成当前目标 */
    COMPLETE_TARGET,
    /** 结束当前村民周期 */
    END_VILLAGER_CYCLE,
    /** 全部目标完成 */
    FINISH,
    /** 发生错误 */
    ERROR
}
