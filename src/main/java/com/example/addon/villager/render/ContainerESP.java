package com.example.addon.villager.render;

import com.example.addon.villager.command.CunminCommand;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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
     * 渲染单个箱子标签（自动识别大箱子，渲染合并的 2x1x1 包围盒）
     */
    private void renderLabel(Render3DEvent event, BlockPos pos, String text) {
        Vec3 playerPos = mc.player.getEyePosition(event.tickDelta);
        Vec3 targetPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        double distance = playerPos.distanceTo(targetPos);
        if (distance > 64) return;

        Color color = text.contains("绿宝石") ? Color.GREEN : Color.CYAN;

        // 检测大箱子：箱子方块 TYPE 为 LEFT/RIGHT 时是双箱子的一半，
        // 沿 getConnectedDirection 找到另一半，渲染两个箱子合并的包围盒
        BlockPos other = pos;
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction connected = ChestBlock.getConnectedDirection(state);
                other = pos.relative(connected);
            }
        }

        int minX = Math.min(pos.getX(), other.getX());
        int minY = Math.min(pos.getY(), other.getY());
        int minZ = Math.min(pos.getZ(), other.getZ());
        int maxX = Math.max(pos.getX(), other.getX()) + 1;
        int maxY = Math.max(pos.getY(), other.getY()) + 1;
        int maxZ = Math.max(pos.getZ(), other.getZ()) + 1;

        // 渲染发光方块边框（大箱子渲染 2x1x1，小箱子渲染 1x1x1）
        event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, color, color, ShapeMode.Lines, 0);
    }
}
