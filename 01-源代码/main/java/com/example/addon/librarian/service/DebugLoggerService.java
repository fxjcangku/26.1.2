// 自动图书管理员 调试日志服务
package com.example.addon.librarian.service;

import com.example.addon.librarian.AutoLibrarianContext;
import com.example.addon.librarian.AutoLibrarianState;

public interface DebugLoggerService {
    /** 状态机状态变化播报 */
    void state(AutoLibrarianState state, AutoLibrarianContext context, MovementStatus movementStatus);

    /** 简单通知（找到附魔、完成目标等），聊天反馈开启时输出 */
    void info(String message);

    /** 详细调试数据（坐标、扫描结果等），调试模式开启时输出 */
    void debug(String message);

    /** 错误信息输出 */
    void error(String message);
}
