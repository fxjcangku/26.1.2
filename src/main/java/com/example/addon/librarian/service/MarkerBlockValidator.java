// 自动图书管理员 交易位验证器
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.VillagerStation;

import java.util.Objects;

public final class MarkerBlockValidator {
    private final MarkerBlockAccess blockAccess;

    public MarkerBlockValidator(MarkerBlockAccess blockAccess) {
        this.blockAccess = Objects.requireNonNull(blockAccess, "blockAccess");
    }

    /** 校验固定交易位是否有效（岩浆块标记 + 讲台朝向） */
    public MarkerBlockValidation validate(VillagerStation station) {
        Objects.requireNonNull(station, "station");
        if (!blockAccess.isMagmaBlock(station.markerBlockPosition())) {
            return MarkerBlockValidation.invalid("固定交易位无效：未检测到岩浆块。");
        }
        if (blockAccess.isLecternFacing(station.lecternPosition(), station.lecternFacing())) {
            return MarkerBlockValidation.success();
        }
        // 不再检测上方方块类型，直接尝试放置，让游戏决定
        return MarkerBlockValidation.success();
    }
}
