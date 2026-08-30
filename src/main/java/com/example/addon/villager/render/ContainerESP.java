package com.example.addon.villager.render;

import com.example.addon.commands.CunminCommand;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 容器 ESP 渲染器
 * 
 * 在绑定的绿宝石箱和成品交易箱上方显示文字标签
 */
public class ContainerESP {

    private final Minecraft mc;
    
    public ContainerESP() {
        this.mc = Minecraft.getInstance();
    }
    
    /**
     * 渲染容器标签
     */
    public void render(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        
        CunminCommand.ContainerBinding binding = CunminCommand.getBinding();
        if (binding == null) return;
        
        // 渲染绿宝石箱
        if (binding.getEmeraldBox() != null) {
            renderLabel(event, binding.getEmeraldBox(), "§a绿宝石箱");
        }
        
        // 渲染成品交易箱
        if (binding.getUnloadBox() != null) {
            renderLabel(event, binding.getUnloadBox(), "§b成品交易箱");
        }
    }
    
    /**
     * 渲染单个标签
     */
    private void renderLabel(Render3DEvent event, BlockPos pos, String text) {
        Vec3 playerPos = mc.player.getEyePosition(event.tickDelta);
        Vec3 targetPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        double distance = playerPos.distanceTo(targetPos);
        if (distance > 64) return;
        
        // 渲染发光方块边框
        Color color = text.contains("绿宝石") ? Color.GREEN : Color.CYAN;
        event.renderer.box(pos, color, color, meteordevelopment.meteorclient.renderer.ShapeMode.Lines, 0);
    }
}
