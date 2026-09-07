package com.example.addon.stardew.recognition;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 运行时识别的公共辅助：方块 ID 提取、BlockState 属性读取。
 *
 * <p>统一用 Mojang 官方映射（BuiltInRegistries），避免各识别器重复写 ID 拼接与属性遍历。</p>
 */
public final class RecognizerSupport {

    private RecognizerSupport() {
        // 工具类，禁止实例化
    }

    /** 方块状态的注册表 ID 字符串，如 {@code minecraft:note_block} */
    public static String blockId(BlockState state) {
        if (state == null) return "";
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** 世界某坐标方块的注册表 ID 字符串 */
    public static String blockId(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        return blockId(mc.level.getBlockState(pos));
    }

    /** 读取 BlockState 指定名称属性的字符串值，找不到返回 null */
    public static String propertyValue(BlockState state, String propertyName) {
        if (state == null || propertyName == null) return null;
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return state.getValue(property).toString();
            }
        }
        return null;
    }
}
