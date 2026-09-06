package com.example.addon.itemid;

/**
 * ID 识别模式。
 *
 * <p>决定「识别物品」后的行为：</p>
 * <ul>
 *   <li>聊天复制/显示：识别结果以完整信息展示，玩家可复制 Item ID / 完整信息，或手动保存。</li>
 *   <li>自动保存：识别后直接写入 ID 配置（{@link ItemIdManager}）。</li>
 * </ul>
 */
public enum IdentifyMode {

    /** 聊天复制/显示：只展示完整识别结果，不自动落盘 */
    CHAT_COPY("聊天复制/显示"),

    /** 自动保存：识别后直接写入 ID 配置 */
    AUTO_SAVE("自动保存");

    private final String displayName;

    IdentifyMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
