package com.example.addon.water;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * 水源显示：以玩家为中心扫描周围水源与耕地，智能规划放水位。
 *
 * 已放水的格子渲染蓝色水源框 + 红色 9×9 灌溉范围；未被任何水源覆盖的耕地，
 * 由贪心覆盖算法算出最优放水点，用红色加深框提示玩家在此放水，
 * 让每一格耕地都被滋润且尽量不重叠。独立于自动农场模块，不开农场也能单独用。
 */
public class WaterESPModule extends YiyiaddonModule {

    /** 每 tick 最多检查多少格，分帧扫描避免掉帧 */
    private static final int BUDGET_PER_TICK = 512;

    /** 建议放水点重算间隔（tick），低频重算避免每帧跑覆盖算法 */
    private static final int SUGGEST_INTERVAL = 40;

    private final SettingGroup sgRender = settings.createGroup("渲染显示", false);

    private final Setting<Integer> radius;
    private final Setting<Boolean> renderSource;
    private final Setting<SettingColor> sourceColor;
    private final Setting<ShapeMode> sourceShapeMode;
    private final Setting<Boolean> renderRange;
    private final Setting<SettingColor> rangeColor;
    private final Setting<Boolean> renderSuggestion;
    private final Setting<SettingColor> suggestionColor;

    /** 已放水坐标缓存 */
    private final Set<BlockPos> waterBlocks = new HashSet<>();
    /** 耕地坐标缓存 */
    private final Set<BlockPos> farmlandBlocks = new HashSet<>();
    /** 建议放水点缓存（未放水、红色加深框提示） */
    private final Set<BlockPos> suggestedSpots = new HashSet<>();

    /** 分帧扫描游标与当前扫描范围 */
    private int cursorX, cursorY, cursorZ;
    private int scanMinX, scanMinY, scanMinZ;
    private int scanMaxX, scanMaxY, scanMaxZ;
    private BlockPos lastCenter;
    private int lastRadius = -1;
    private int suggestTimer;

    public WaterESPModule() {
        super(AddonTemplate.CATEGORY, "水源显示",
            "智能规划放水位：已放水渲染蓝色水源框与红色灌溉范围，未覆盖耕地用红色加深框提示放水点。");

        radius = sgRender.add(new IntSetting.Builder()
            .name("渲染半径")
            .description("以玩家为中心扫描并渲染此距离（格）内的水源与耕地")
            .defaultValue(12)
            .min(4).max(32)
            .noSlider()
            .build());

        renderSource = sgRender.add(new BoolSetting.Builder()
            .name("渲染水源")
            .description("用醒目蓝色框标出已放水的格子")
            .defaultValue(true)
            .build());

        sourceColor = sgRender.add(new ColorSetting.Builder()
            .name("水源颜色")
            .defaultValue(new SettingColor(30, 144, 255, 75))
            .visible(() -> renderSource.get())
            .build());

        sourceShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("水源框样式")
            .description("水源方块的渲染样式：线条 / 侧面 / 两者（侧面为半透明填充色块，更醒目）")
            .defaultValue(ShapeMode.Both)
            .visible(() -> renderSource.get())
            .build());

        renderRange = sgRender.add(new BoolSetting.Builder()
            .name("渲染灌溉范围")
            .description("以每桶已放水为中心画一个 9×9 红色填充框，提示它实际能滋润的耕地范围")
            .defaultValue(true)
            .build());

        rangeColor = sgRender.add(new ColorSetting.Builder()
            .name("灌溉范围颜色")
            .defaultValue(new SettingColor(255, 60, 60, 50))
            .visible(() -> renderRange.get())
            .build());

        renderSuggestion = sgRender.add(new BoolSetting.Builder()
            .name("渲染放水建议点")
            .description("用红色加深框标出尚未放水的最优位置，放完水后该点自动转为水源渲染")
            .defaultValue(true)
            .build());

        suggestionColor = sgRender.add(new ColorSetting.Builder()
            .name("建议点颜色")
            .defaultValue(new SettingColor(255, 0, 0, 120))
            .visible(() -> renderSuggestion.get())
            .build());
    }

    @Override
    public void onActivate() {
        lastCenter = null;
        lastRadius = -1;
        suggestTimer = 0;
        waterBlocks.clear();
        farmlandBlocks.clear();
        suggestedSpots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        scan();
        // 低频重算建议放水点
        if (renderSuggestion.get() && ++suggestTimer >= SUGGEST_INTERVAL) {
            suggestTimer = 0;
            computeSuggestions();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double r = radius.get();
        double r2 = r * r;
        double px = mc.player.getX();
        double py = mc.player.getEyeY();
        double pz = mc.player.getZ();

        SettingColor src = sourceColor.get();
        SettingColor rng = rangeColor.get();
        SettingColor sug = suggestionColor.get();
        boolean doSource = renderSource.get();
        boolean doRange = renderRange.get();
        boolean doSuggestion = renderSuggestion.get();
        ShapeMode sourceMode = sourceShapeMode.get();

        // 已放水：蓝色水源框 + 红色灌溉范围框
        if (doSource || doRange) {
            for (BlockPos w : waterBlocks) {
                double dx = w.getX() + 0.5 - px;
                double dy = w.getY() + 0.5 - py;
                double dz = w.getZ() + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > r2) continue;

                if (doSource) {
                    event.renderer.box(w, src, src, sourceMode, 0);
                }
                if (doRange) {
                    AABB box = new AABB(
                        w.getX() - 4, w.getY(), w.getZ() - 4,
                        w.getX() + 5, w.getY() + 1, w.getZ() + 5);
                    event.renderer.box(box, rng, rng, ShapeMode.Both, 0);
                }
            }
        }

        // 未放水的建议放水点：红色加深框
        if (doSuggestion) {
            for (BlockPos spot : suggestedSpots) {
                double dx = spot.getX() + 0.5 - px;
                double dy = spot.getY() + 0.5 - py;
                double dz = spot.getZ() + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > r2) continue;
                event.renderer.box(spot, sug, sug, ShapeMode.Both, 0);
            }
        }
    }

    /** 分帧扫描玩家周围立方体内的水与耕地，玩家移动超过 1 格或半径变化时重启游标 */
    private void scan() {
        BlockPos c = mc.player.blockPosition();
        int r = radius.get();

        boolean moved = lastCenter == null
            || lastRadius != r
            || Math.abs(c.getX() - lastCenter.getX()) >= 2
            || Math.abs(c.getY() - lastCenter.getY()) >= 2
            || Math.abs(c.getZ() - lastCenter.getZ()) >= 2;

        if (moved) {
            lastCenter = c;
            lastRadius = r;
            waterBlocks.clear();
            farmlandBlocks.clear();
            suggestedSpots.clear();
            scanMinX = c.getX() - r;
            scanMinY = c.getY() - r;
            scanMinZ = c.getZ() - r;
            scanMaxX = c.getX() + r;
            scanMaxY = c.getY() + r;
            scanMaxZ = c.getZ() + r;
            cursorX = scanMinX;
            cursorY = scanMinY;
            cursorZ = scanMinZ;
        }

        ClientLevel level = mc.level;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < BUDGET_PER_TICK; i++) {
            cursor.set(cursorX, cursorY, cursorZ);
            classify(level, cursor);
            advance();
        }
    }

    /** 判定单格是水、耕地还是无关方块，持续刷新缓存 */
    private void classify(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.WATER)) {
            waterBlocks.add(pos.immutable());
            farmlandBlocks.remove(pos);
        } else if (state.is(Blocks.FARMLAND)) {
            waterBlocks.remove(pos);
            farmlandBlocks.add(pos.immutable());
        } else {
            waterBlocks.remove(pos);
            farmlandBlocks.remove(pos);
        }
    }

    /**
     * 贪心覆盖算法：对未被已有水源覆盖的耕地，反复选出覆盖最多耕地的放水点，
     * 得到一组尽量不重叠、能滋润全部耕地的建议放水点。
     */
    private void computeSuggestions() {
        suggestedSpots.clear();

        // 未被任何已放水覆盖的耕地
        Set<BlockPos> uncovered = new HashSet<>(farmlandBlocks);
        for (BlockPos water : waterBlocks) {
            uncovered.removeIf(farm -> irrigates(water, farm));
        }

        while (!uncovered.isEmpty()) {
            BlockPos best = null;
            int bestCount = 0;
            for (BlockPos candidate : uncovered) {
                int count = 0;
                for (BlockPos farm : uncovered) {
                    if (irrigates(candidate, farm)) count++;
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = candidate;
                }
            }

            if (best == null || bestCount == 0) break;

            suggestedSpots.add(best);
            final BlockPos chosen = best;
            uncovered.removeIf(farm -> irrigates(chosen, farm));
        }
    }

    /** 判定某格水是否能滋润某格耕地：水平 4 格内且竖直相邻 */
    private static boolean irrigates(BlockPos water, BlockPos farm) {
        return Math.abs(water.getX() - farm.getX()) <= 4
            && Math.abs(water.getZ() - farm.getZ()) <= 4
            && Math.abs(water.getY() - farm.getY()) <= 1;
    }

    /** 游标前进一格，越过边界回卷，循环扫描 */
    private void advance() {
        cursorX++;
        if (cursorX <= scanMaxX) return;
        cursorX = scanMinX;
        cursorZ++;
        if (cursorZ <= scanMaxZ) return;
        cursorZ = scanMinZ;
        cursorY++;
        if (cursorY <= scanMaxY) return;
        cursorY = scanMinY;
    }
}
