package com.example.addon.core;

// 可刷新接口：用于翻译模块开关时重新加载翻译
// 实现此接口的模块会在 YiyiaddonTranslationModule 启用/禁用时收到通知
public interface YiyiaddonRefreshable {
    // enabled: 翻译模块是否启用
    void yiyiaddon$refresh(boolean enabled);
}
