package com.example.addon.mining.recognition;

/**
 * 矿物识别结果。
 *
 * @param known 是否识别为已知矿物
 * @param oreType 矿物类型（diamond/emerald/ancient_debris等）
 * @param reason 未知原因
 */
public record OreResult(boolean known, String oreType, String reason) {

    /**
     * 创建已知矿物结果。
     */
    public static OreResult known(String oreType) {
        return new OreResult(true, oreType, "");
    }

    /**
     * 创建未知结果。
     */
    public static OreResult unknown(String reason) {
        return new OreResult(false, "", reason);
    }
}
