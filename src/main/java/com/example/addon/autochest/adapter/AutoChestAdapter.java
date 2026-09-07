package com.example.addon.autochest.adapter;

import com.example.addon.autochest.model.ContainerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自动箱子适配器接口 - 统一容器识别入口。
 *
 * <p>职责：封装容器类型识别，隔离服务器差异。</p>
 */
public interface AutoChestAdapter {

    /**
     * 识别容器类型。
     *
     * @param pos 容器位置
     * @param state 方块状态
     * @return 容器类型（CHEST/BARREL/SHULKER_BOX等）
     */
    ContainerType recognizeContainerType(BlockPos pos, BlockState state);

    /**
     * 判断是否为合法容器。
     *
     * @param pos 容器位置
     * @param state 方块状态
     * @return true=合法容器，false=非容器
     */
    boolean isValidContainer(BlockPos pos, BlockState state);
}
