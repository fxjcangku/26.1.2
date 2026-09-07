package com.example.addon.stardew.config;

/**
 * 浇水覆盖形状（配置化，不写死原版星露谷范围）。
 *
 * <p>single=单格、line=线、rectangle=矩形、square=方形、custom=自定义坐标，
 * 由 {@link com.example.addon.stardew.model.WateringToolProfile} 描述具体数值。</p>
 */
public enum WateringShape {

    SINGLE("单格"),
    LINE("线"),
    RECTANGLE("矩形"),
    SQUARE("方形"),
    CUSTOM("自定义");

    private final String cn;

    WateringShape(String cn) {
        this.cn = cn;
    }

    public String cn() {
        return cn;
    }

    /** 按字符串名解析，未知回退单格 */
    public static WateringShape parse(String raw) {
        if (raw == null) return SINGLE;
        for (WateringShape shape : values()) {
            if (shape.name().equalsIgnoreCase(raw)) return shape;
        }
        return SINGLE;
    }
}
