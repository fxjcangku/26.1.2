package com.example.addon.autofarm.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/**
 * 轻量渲染层：只画农场范围、点位与当前作业目标，本身不参与任何业务决策。
 *
 * 已按规范删除水源渲染、水源覆盖、水源扫描等旧逻辑，Renderer 不再为渲染而扫描整个农场。
 */
public final class FarmRenderer {

    private static final Color LABEL_BG = new Color(0, 0, 0, 130);

    private FarmRenderer() {
    }

    /** 画农场边界外框。min/max 是包含端点的对角格 */
    public static void renderBounds(Render3DEvent event, BlockPos min, BlockPos max,
                                    Color line, Color side, ShapeMode mode) {
        AABB box = new AABB(
            min.getX(), min.getY(), min.getZ(),
            max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        event.renderer.box(box, side, line, mode, 0);
    }

    /** 高亮单个方块（当前作业目标） */
    public static void renderTarget(Render3DEvent event, BlockPos pos, Color line, Color side, ShapeMode mode) {
        event.renderer.box(pos, side, line, mode, 0);
    }

    /**
     * 画方块头顶的 2D 文字标签（点位防呆字牌）。
     * 必须在 Render2DEvent 里调用，Render3DEvent 阶段矩阵还没准备好。
     */
    public static void renderLabel(Render2DEvent event, BlockPos pos, String text, Color color) {
        Vector3d screen = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
        if (!NametagUtils.to2D(screen, 1.5)) return;

        TextRenderer renderer = TextRenderer.get();
        NametagUtils.begin(screen, event.graphics);

        renderer.begin(1.0);
        double width = renderer.getWidth(text);
        double height = renderer.getHeight();

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(-width / 2 - 2, -height - 2, width + 4, height + 4, LABEL_BG);
        Renderer2D.COLOR.render();

        renderer.render(text, -width / 2, -height, color, true);
        renderer.end();

        NametagUtils.end(event.graphics);
    }
}
