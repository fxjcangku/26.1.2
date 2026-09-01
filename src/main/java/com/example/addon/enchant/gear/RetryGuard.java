package com.example.addon.enchant.gear;

/**
 * 原版装备附魔 · 重试守卫（防死循环）。
 *
 * <p>所有关键循环（附魔失败 / 砂轮循环 / 铁砧规划 / 寻路失败 / 容器找不到 /
 * 装备身份不匹配）都必须限制重试次数，达到上限即暂停当前任务并异常处理，
 * 避免长期挂机陷入死循环。</p>
 */
public final class RetryGuard {

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 3;

    private final int maxRetries;
    private int retries;

    public RetryGuard() {
        this(DEFAULT_MAX_RETRIES);
    }

    public RetryGuard(int maxRetries) {
        this.maxRetries = Math.max(1, maxRetries);
        this.retries = 0;
    }

    /** 是否还能继续重试 */
    public boolean canRetry() {
        return retries < maxRetries;
    }

    /** 记录一次失败（内部递增） */
    public void recordFailure() {
        if (canRetry()) retries++;
    }

    /** 是否已达到最大重试次数（应停止并异常处理） */
    public boolean exceeded() {
        return retries >= maxRetries;
    }

    /** 重置重试计数（任务推进 / 状态切换成功后调用） */
    public void reset() {
        retries = 0;
    }

    public int retries() {
        return retries;
    }

    public int maxRetries() {
        return maxRetries;
    }
}
