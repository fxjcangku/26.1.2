package com.example.addon.autologin;

/**
 * 乐源服欢迎界面入口方式枚举。
 * 决定进入乐源服主城后采用哪种欢迎入口识别策略。
 */
public enum LeyuanWelcomeEntryMode {
    LEYUAN_CITY("乐源城入口"),     // 乐源城欢迎界面入口
    BOOK_DIRECT("书本直达主城");   // 通过书本菜单直达主城

    private final String title;

    LeyuanWelcomeEntryMode(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
