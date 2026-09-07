package com.example.addon.stardew.debug;

import java.util.function.Consumer;

/**
 * 星露谷调试日志：复用模块现有 notify 日志机制，不新增 HUD / watchdog。
 *
 * <p>只在开启调试时输出未知对象（未知种子/作物/农田/成熟状态）的定位信息，
 * 帮助用户判断需要配置什么，不影响正常自动化流程。</p>
 */
public final class StardewDebugLogger {

    private final Consumer<String> sink;
    private boolean enabled;

    public StardewDebugLogger(Consumer<String> sink) {
        this.sink = sink;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void log(String message) {
        if (enabled && sink != null && message != null) {
            sink.accept("§8[星露谷调试] §7" + message);
        }
    }

    /** 记录未知对象，供用户定位缺配项 */
    public void unknown(String what, Object detail) {
        log("未知" + what + " §8▸ §f" + detail);
    }
}
