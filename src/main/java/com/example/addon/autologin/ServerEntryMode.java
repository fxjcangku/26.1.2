package com.example.addon.autologin;

/**
 * 服务器进入方式枚举。
 * 决定自动登录模块采用哪种进服策略：直连、菜单传送、子服网络或乐源服定制路线。
 */
public enum ServerEntryMode {
    DIRECT("直接进入"),                    // 直接连接当前服务器
    MENU_TRANSFER("菜单传送"),             // 通过服务器菜单切换
    SUBSERVER_NETWORK("子服网络"),         // 识别并进入目标子服
    LEYUAN_CUSTOM("乐源服定制");           // 乐源服专属定制路线

    private final String title;

    ServerEntryMode(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
