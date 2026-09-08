package com.example.addon.autologin.fsm;

/** 自动登入主状态机状态 */
public enum AutoLoginState {
    /** 未连接 / 模块未激活 */
    IDLE,
    /** 资源包已处理，等待世界加载延迟 */
    WORLD_LOAD_WAIT,
    /** 监听聊天，等待注册/登录提示 */
    DETECTING_AUTH,
    /** 正在执行注册流程 */
    REGISTERING,
    /** 正在执行登录流程 */
    LOGGING_IN,
    /** 执行自定义指令 */
    EXECUTING_COMMANDS,
    /** 全部流程完成，正常运行 */
    ACTIVE,
    /** 断线，等待重连倒计时 */
    RECONNECT_WAIT
}
