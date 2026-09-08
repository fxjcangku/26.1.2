package com.example.addon.mining.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Set;

/**
 * AutoMiner ESP 渲染器
 * 
 * 功能：
 * 1. 2D悬浮标签 - 矿物箱/食物箱/挂机点
 * 2. 3D方块框线 - 种子预测的矿石位置
 */
public class AutoMinerModule_ESP {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * 渲染 2D 悬浮标签（矿物箱/食物箱/挂机点）
     * 显示格式：[名称] (维度) [距离m]
     */
    public static void renderLabel(Render2DEvent event, BlockPos pos, String text, Color color, float userScale) {
        if (mc.player == null || mc.gameRenderer == null) return;

        Vec3 vec = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        double distance = mc.player.position().distanceTo(vec);
        
        if (distance > 128) return;

        // 使用 NametagUtils 渲染
        Vector3d pos3d = new Vector3d(vec.x, vec.y, vec.z);
        
        if (NametagUtils.to2D(pos3d, userScale)) {
            NametagUtils.begin(pos3d);
            TextRenderer.get().begin(1.0, false, true);
            
            // 获取维度名称
            String dimension = "§7(未知)";
            if (mc.level != null) {
                String dimKey = mc.level.dimension().toString();
                if (dimKey.contains("overworld")) dimension = "§7(主世界)";
                else if (dimKey.contains("nether")) dimension = "§7(下界)";
                else if (dimKey.contains("end")) dimension = "§7(末地)";
                else dimension = "§7(" + dimKey.substring(dimKey.lastIndexOf(':') + 1) + ")";
            }
            
            String distText = String.format("§8[%.0fm]", distance);
            String fullText = text + " " + dimension + " " + distText;
            
            double w = TextRenderer.get().getWidth(fullText);
            TextRenderer.get().render(fullText, -w / 2, 0, color, true);
            
            TextRenderer.get().end();
            NametagUtils.end();
        }
    }

    /**
     * 渲染 3D 方块框线（种子预测的矿石位置）
     * 
     * @param event 3D渲染事件
     * @param predictedOres 预测的矿石位置集合
     * @param color 渲染颜色
     * @param lineWidth 线宽
     */
    public static void renderPredictedOres(Render3DEvent event, Set<BlockPos> predictedOres, Color color, double lineWidth) {
        if (mc.player == null || predictedOres == null || predictedOres.isEmpty()) return;

        for (BlockPos pos : predictedOres) {
            // 只渲染视距内的矿石
            double distSq = mc.player.blockPosition().distSqr(pos);
            if (distSq > 128 * 128) continue;
            
            // 根据距离调整颜色透明度（近处更亮，远处更暗）
            double distance = Math.sqrt(distSq);
            int alpha = (int) Math.max(50, 255 - (distance / 128.0 * 200));
            Color adjustedColor = new Color(color.r, color.g, color.b, alpha);
            
            // 渲染方块框线
            event.renderer.box(pos, adjustedColor, adjustedColor, ShapeMode.Lines, 0);
        }
    }

    /**
     * 渲染单个方块的高亮框线（用于标记当前目标矿石）
     * 
     * @param event 3D渲染事件
     * @param pos 方块位置
     * @param color 渲染颜色
     * @param pulse 是否启用脉冲效果
     */
    public static void renderTargetOre(Render3DEvent event, BlockPos pos, Color color, boolean pulse) {
        if (mc.player == null || pos == null) return;

        // 脉冲效果：颜色亮度随时间变化
        int alpha = color.a;
        if (pulse) {
            long time = System.currentTimeMillis();
            alpha = (int) (128 + Math.sin(time / 200.0) * 127);
        }
        
        Color adjustedColor = new Color(color.r, color.g, color.b, alpha);
        
        // 渲染方块框线
        event.renderer.box(pos, adjustedColor, adjustedColor, ShapeMode.Lines, 0);
    }

    /**
     * 渲染附近岩浆方块（橙红色框线 + 半透明填充，透视岩浆用）
     */
    public static void renderLava(Render3DEvent event, Set<BlockPos> lavaPositions, double lineWidth) {
        if (mc.player == null || lavaPositions == null || lavaPositions.isEmpty()) return;
        for (BlockPos pos : lavaPositions) {
            double distSq = mc.player.blockPosition().distSqr(pos);
            if (distSq > 128 * 128) continue;
            // 岩浆用醒目的橙红色
            event.renderer.box(pos, new Color(255, 90, 0, 190), new Color(255, 50, 0, 40), ShapeMode.Lines, 0);
        }
    }
}
