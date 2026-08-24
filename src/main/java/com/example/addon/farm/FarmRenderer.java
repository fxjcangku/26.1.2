package com.example.addon.farm;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 渲染辅助层：把状态机内部数据画到屏幕上，本身不参与任何决策。
 *
 * 三块内容：
 * 1. 农田雷达 —— 成熟绿框、待补种黄框
 * 2. 边界外框 —— 整个农场范围的一个大盒子
 * 3. 水源辐射 —— 一桶水能滋润的耕地范围
 * 4. 防呆字牌 —— 卸货箱/种子库头顶的 2D 文字标签
 *
 * 关于水源辐射范围（26.1.2 实测的原版规则）：
 * 耕地方块 F 会被滋润，条件是区域 F+(-4,0,-4) ~ F+(4,1,4) 内存在水。
 * 反推过来，一格水源 W 能滋润的耕地是：水平 9x9（±4 格），
 * 竖直方向只有 W.y 和 W.y-1 两层，也就是最多 162 格耕地。
 * 网上常说的"5x5 / 4 格半径圆形"都是错的，实际是方形范围，没有圆角衰减。
 */
public final class FarmRenderer {

    /** 一桶水的水平辐射半径 */
    public static final int WATER_RADIUS = 4;

    private static final Color LABEL_BG = new Color(0, 0, 0, 130);

    private FarmRenderer() {
    }

    /**
     * 画农田雷达。
     *
     * @param harvest 成熟待收割的坐标
     * @param plant   待补种的底盘坐标（框画在底盘上方那一格）
     */
    public static void renderRadar(Render3DEvent event, List<BlockPos> harvest, List<BlockPos> plant,
                                   Color ripeLine, Color ripeSide, Color emptyLine, Color emptySide,
                                   ShapeMode mode) {
        for (BlockPos pos : harvest) {
            event.renderer.box(pos, ripeSide, ripeLine, mode, 0);
        }
        for (BlockPos soil : plant) {
            event.renderer.box(soil.above(), emptySide, emptyLine, mode, 0);
        }
    }

    /** 画农场边界外框。min/max 是包含端点的对角格 */
    public static void renderBounds(Render3DEvent event, BlockPos min, BlockPos max,
                                    Color line, Color side, ShapeMode mode) {
        AABB box = new AABB(
            min.getX(), min.getY(), min.getZ(),
            max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        event.renderer.box(box, side, line, mode, 0);
    }

    /**
     * 收集范围内的水源方块坐标。
     *
     * 只认水源（isSource），流动水虽然一样能滋润耕地，但会随方块变化随时消失，
     * 画出来只会误导玩家去挖一条注定断流的水渠。
     *
     * @param maxSources 最多收集多少个，超出直接截断，防止大农场一次收出上千格打爆渲染
     */
    public static List<BlockPos> collectWaterSources(BlockPos min, BlockPos max, int maxSources) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return List.of();

        List<BlockPos> sources = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = min.getY(); y <= max.getY() && sources.size() < maxSources; y++) {
            for (int x = min.getX(); x <= max.getX() && sources.size() < maxSources; x++) {
                for (int z = min.getZ(); z <= max.getZ() && sources.size() < maxSources; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.getFluidState().is(FluidTags.WATER)) continue;
                    if (!state.getFluidState().isSource()) continue;
                    sources.add(cursor.immutable());
                }
            }
        }
        return sources;
    }

    /**
     * 画水源辐射范围：一桶水一个外接盒，形状就是玩家关心的"这桶水管多大一片"。
     * 不逐格描边，几十桶水叠出上万个小方框会直接把渲染器拖死。
     */
    public static void renderWaterRange(Render3DEvent event, List<BlockPos> sources,
                                        Color line, Color side, ShapeMode mode) {
        for (BlockPos source : sources) {
            // 水平 ±4，竖直只覆盖 y 与 y-1 两层
            AABB box = new AABB(
                source.getX() - WATER_RADIUS, source.getY() - 1, source.getZ() - WATER_RADIUS,
                source.getX() + WATER_RADIUS + 1.0, source.getY() + 1.0, source.getZ() + WATER_RADIUS + 1.0);
            event.renderer.box(box, side, line, mode, 0);
        }
    }

    /**
     * 统计一格水源实际滋润到的耕地格数。
     * 理论上限是 162，实测值取决于这一片到底铺了多少耕地。
     */
    public static int countMoistenedFarmland(BlockPos source) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0;

        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // 耕地 F 被滋润的条件是 F+(-4,0,-4) ~ F+(4,1,4) 内有水，
        // 反推水源 W 的覆盖层就是 W.y 与 W.y-1 两层
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -WATER_RADIUS; dx <= WATER_RADIUS; dx++) {
                for (int dz = -WATER_RADIUS; dz <= WATER_RADIUS; dz++) {
                    cursor.set(source.getX() + dx, source.getY() + dy, source.getZ() + dz);
                    if (level.getBlockState(cursor).is(Blocks.FARMLAND)) count++;
                }
            }
        }
        return count;
    }

    /** 一桶水理论上能滋润的耕地格数（9 x 9 x 2 层） */
    public static int waterCapacity() {
        int span = WATER_RADIUS * 2 + 1;
        return span * span * 2;
    }

    /**
     * 画方块头顶的 2D 文字标签。
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

        // 半透明底衬，避免在明亮方块上看不清白字
        event.graphics.fill(
            (int) (-width / 2 - 2), (int) (-height - 2),
            (int) (width / 2 + 2), 2,
            LABEL_BG.getPacked());

        renderer.render(text, -width / 2, -height, color, true);
        renderer.end();

        NametagUtils.end(event.graphics);
    }
}
