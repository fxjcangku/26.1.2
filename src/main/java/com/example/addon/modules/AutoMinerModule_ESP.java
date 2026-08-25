package com.example.addon.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * AutoMiner 2D ESP 渲染器
 * 
 * 独立类，负责渲染矿物箱/食物箱/挂机点的悬浮标签
 */
public class AutoMinerModule_ESP {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * 渲染 2D 悬浮标签
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
            
            String distText = String.format("%.0fm", distance);
            String fullText = text + " " + distText;
            
            double w = TextRenderer.get().getWidth(fullText);
            TextRenderer.get().render(fullText, -w / 2, 0, color, true);
            
            TextRenderer.get().end();
            NametagUtils.end();
        }
    }
}
