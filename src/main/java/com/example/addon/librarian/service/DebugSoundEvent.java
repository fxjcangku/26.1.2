// 附魔交易所 调试声音事件
package com.example.addon.librarian.service;

/**
 * 附魔交易所 · 调试声音事件。
 *
 * <p>调试模式下各关键节点触发的音效标识，帮助玩家「听声」判断当前自动化
 * 进度处于哪个环节，便于无界面观察运行状态。</p>
 */
public enum DebugSoundEvent {
    /** 模块启动 */
    START,
    /** 搜索村民 */
    SEARCH,
    /** 开始移动 */
    MOVE,
    /** 放置讲台 */
    PLACE,
    /** 刷新交易 */
    REFRESH,
    /** 执行交易 */
    TRADE,
    /** 任务成功 */
    SUCCESS,
    /** 发生错误 */
    ERROR,
    /** 重置 */
    RESET
}
