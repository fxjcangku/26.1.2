package com.example.addon.stardew.model;

import net.minecraft.core.BlockPos;

/**
 * 洒水器识别信息。
 *
 * <p>{@code known=false} 表示该坐标不是已配置的洒水器。覆盖范围与是否需供能/激活
 * 由 {@link SprinklerProfile} 描述，识别只负责「这是不是洒水器、是哪一个」。</p>
 */
public record SprinklerInfo(
    boolean known,
    String sprinklerId,
    String displayName,
    BlockPos pos,
    SprinklerProfile profile
) {

    /** 非洒水器结果 */
    public static SprinklerInfo none() {
        return new SprinklerInfo(false, null, null, null, null);
    }

    /** 已知洒水器结果 */
    public static SprinklerInfo known(String sprinklerId, String displayName, BlockPos pos, SprinklerProfile profile) {
        return new SprinklerInfo(true, sprinklerId, displayName, pos, profile);
    }
}
