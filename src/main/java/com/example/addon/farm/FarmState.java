package com.example.addon.farm;

/**
 * 自动物流农场的状态机状态。
 *
 * 流转主线：
 * STANDBY → NUKE_FARMING → COLLECTING → JUDGMENT → (UNLOADING | RESTOCKING) → NUKE_FARMING
 *
 * 每个状态都带一个看门狗上限（tick），超时强制退回 STANDBY，
 * 防止某一环卡死（例如箱子被拆、寻路失败、容器同步不上）导致整个模块静默僵死。
 */
public enum FarmState {

    /** 待机：等待自检通过与扫描结果 */
    STANDBY("待机", 200),

    /** 爆破收割：按扫描队列逐格发破坏包，顺带补种 */
    NUKE_FARMING("收割播种", 6000),

    /** 拾取：原地等掉落物被服务端判定进背包 */
    COLLECTING("拾取掉落", 200),

    /** 决策：判断背包是否需要卸货、种子是否需要补货 */
    JUDGMENT("状态决策", 60),

    /** 卸货：前往卸货总仓，把白名单产物倒进去 */
    UNLOADING("卸货", 1200),

    /** 补货：前往种子库，取回种子安全库存 */
    RESTOCKING("补种子", 1200);

    private final String cn;
    private final int watchdogTicks;

    FarmState(String cn, int watchdogTicks) {
        this.cn = cn;
        this.watchdogTicks = watchdogTicks;
    }

    /** 面向玩家的中文状态名 */
    public String cn() {
        return cn;
    }

    /** 该状态允许的最大停留 tick，超过即判定卡死 */
    public int watchdogTicks() {
        return watchdogTicks;
    }
}
