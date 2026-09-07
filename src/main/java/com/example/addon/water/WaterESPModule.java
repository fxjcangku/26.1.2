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
 * 水源显示：以玩家为中心扫描周围水源，渲染灌溉范围与相邻建议位。
 *
 * 已放水渲染蓝色 9×9 灌溉范围；在已有水源的上下左右显示红色建议框，
 * 提示可放水位置，这些位置的覆盖范围刚好和现有水源相连不重叠。
 * 独立于自动农场模块，不开农场也能单独用。
 */
public class WaterESPModule extends YiyiaddonModule {

    /** 每 tick 最多检查多少格，分帧扫描避免掉帧 */
    private static final int BUDGET_PER_TICK = 512;

    /** 建议放水点重算间隔（tick），低频重算避免每帧跑覆盖算法 */
    private static final int SUGGEST_INTERVAL = 40;

    /** 向下扫描的竖直范围，覆盖飞起来 / 站在高处也能看到下方农田的水 */
    private static final int SCAN_DOWN = 12;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWaterRange = settings.createGroup("灌溉范围显示");
    private final SettingGroup sgSuggestion = settings.createGroup("建议放水点");
    private final SettingGroup sgAdvanced = settings.createGroup("高级选项");

    private final Setting<Integer> radius;
    private final Setting<Integer> renderDistance;
    
    private final Setting<Boolean> renderRange;
    private final Setting<SettingColor> rangeColor;
    
    private final Setting<Boolean> renderSuggestion;
    private final Setting<SettingColor> suggestionColor;
    private final Setting<ShapeMode> suggestionShapeMode;
    
    private final Setting<Boolean> renderSource;
    private final Setting<SettingColor> sourceColor;
    private final Setting<ShapeMode> sourceShapeMode;

    /** 已放水坐标缓存 */
    private final Set<BlockPos> waterBlocks = new HashSet<>();
    /** 建议放水点缓存（在已有水源的上下左右四个方向） */
    private final Set<BlockPos> suggestedSpots = new HashSet<>();

    /** 分帧扫描游标与当前扫描范围 */
    private int cursorX, cursorY, cursorZ;
    private int scanMinX, scanMinY, scanMinZ;
    private int scanMaxX, scanMaxY, scanMaxZ;
    private BlockPos lastCenter;
    private int lastRadius = -1;
    private int suggestTimer;
    /** 本 tick 是否发生了扫描区域重置，用于触发建议点立即重算 */
    private boolean regionChanged;

    public WaterESPModule() {
        super(AddonTemplate.CATEGORY, "水源显示",
            "已放水渲染蓝色 9×9 灌溉范围，在已有水源旁显示红色建议框提示可放水位置。");

        // === 基础设置 ===
        radius = sgGeneral.add(new IntSetting.Builder()
            .name("扫描半径")
            .description("扫描玩家周围多少格内的水源")
            .defaultValue(12)
            .min(4).max(32)
            .sliderRange(4, 32)
            .build());

        renderDistance = sgGeneral.add(new IntSetting.Builder()
            .name("渲染距离")
            .description("只渲染玩家周围多少格内的框（可以比扫描半径大）")
            .defaultValue(16)
            .min(4).max(64)
            .sliderRange(4, 64)
            .build());

        // === 灌溉范围显示 ===
        renderRange = sgWaterRange.add(new BoolSetting.Builder()
            .name("显示灌溉范围")
            .description("显示每桶水能覆盖的 9×9 耕地范围（蓝色大框）")
            .defaultValue(true)
            .build());

        rangeColor = sgWaterRange.add(new ColorSetting.Builder()
            .name("范围颜色")
            .description("灌溉范围的颜色")
            .defaultValue(new SettingColor(30, 144, 255, 60))
            .visible(renderRange::get)
            .build());

        // === 建议放水点 ===
        renderSuggestion = sgSuggestion.add(new BoolSetting.Builder()
            .name("显示建议点")
            .description("在已有水源的上下左右显示可放水位置（红色框）")
            .defaultValue(true)
            .build());

        suggestionColor = sgSuggestion.add(new ColorSetting.Builder()
            .name("建议点颜色")
            .description("建议放水点的颜色")
            .defaultValue(new SettingColor(255, 0, 0, 180))
            .visible(renderSuggestion::get)
            .build());

        suggestionShapeMode = sgSuggestion.add(new EnumSetting.Builder<ShapeMode>()
            .name("建议点样式")
            .description("Lines 线框 / Sides 面 / Both 两者")
            .defaultValue(ShapeMode.Both)
            .visible(renderSuggestion::get)
            .build());

        // === 高级选项 ===
        renderSource = sgAdvanced.add(new BoolSetting.Builder()
            .name("显示水源方块")
            .description("用小框标出水源方块本身（通常不需要，主要看灌溉范围即可）")
            .defaultValue(false)
            .build());

        sourceColor = sgAdvanced.add(new ColorSetting.Builder()
            .name("水源方块颜色")
            .defaultValue(new SettingColor(0, 0, 139, 180))
            .visible(renderSource::get)
            .build());

        sourceShapeMode = sgAdvanced.add(new EnumSetting.Builder<ShapeMode>()
            .name("水源方块样式")
            .description("Lines 线框 / Sides 面 / Both 两者")
            .defaultValue(ShapeMode.Both)
            .visible(renderSource::get)
            .build());
    }

    @Override
    public void onActivate() {
        lastCenter = null;
        lastRadius = -1;
        suggestTimer = 0;
        regionChanged = false;
        cursorX = cursorY = cursorZ = 0;
        waterBlocks.clear();
        suggestedSpots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        scan();
        // 建议点重算：扫描区域变化时立即重算，否则低频重算，避免移动后红色建议框消失闪烁
        if (renderSuggestion.get()) {
            suggestTimer++;
            if (regionChanged || suggestTimer >= SUGGEST_INTERVAL) {
                regionChanged = false;
                suggestTimer = 0;
                computeSuggestions();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double renderDist = renderDistance.get();
        double renderDist2 = renderDist * renderDist;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        SettingColor src = sourceColor.get();
        SettingColor rng = rangeColor.get();
        SettingColor sug = suggestionColor.get();
        boolean doSource = renderSource.get();
        boolean doRange = renderRange.get();
        boolean doSuggestion = renderSuggestion.get();
        ShapeMode sourceMode = sourceShapeMode.get();

        // 已放水：可选小蓝框（水源方块） + 蓝色 9×9 灌溉范围
        if (doSource || doRange) {
            for (BlockPos w : waterBlocks) {
                double dx = w.getX() + 0.5 - px;
                double dz = w.getZ() + 0.5 - pz;
                if (dx * dx + dz * dz > renderDist2) continue;

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

        // 建议放水点：红色放置框（线框 / 面 / 两者，默认两者）
        if (doSuggestion) {
            ShapeMode suggestionMode = suggestionShapeMode.get();
            for (BlockPos spot : suggestedSpots) {
                double dx = spot.getX() + 0.5 - px;
                double dz = spot.getZ() + 0.5 - pz;
                if (dx * dx + dz * dz > renderDist2) continue;
                event.renderer.box(spot, sug, sug, suggestionMode, 0);
            }
        }
    }

    /**
     * 分帧扫描玩家周围「地表水层」的水源。
     *
     * 只扫玩家脚下两层（脚下一格 + 脚下），不扫整条竖直立方体，避免把海底 / 地下
     * 连片水体全部纳入导致渲染卡死；同时只保留四周都是非水源的「孤立灌溉水源」，
     * 过滤掉海、湖、河等连片水体。玩家移动或半径变化时只剪掉移出范围的缓存块，
     * 不清空，避免已放水框一闪一闪。
     */
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

            int newMinX = c.getX() - r;
            int newMaxX = c.getX() + r;
            int newMinZ = c.getZ() - r;
            int newMaxZ = c.getZ() + r;
            // 向下多扫几格，飞起来 / 站在高处也能看到下方农田的水；
            // 连片的海 / 湖 / 河由「孤立水源」过滤兜底，不会渲染卡死
            int newMinY = c.getY() - SCAN_DOWN;
            int newMaxY = c.getY();

            // 只剪掉移出扫描范围的缓存块，不清空，避免玩家移动时已放水框一闪一闪
            if (!waterBlocks.isEmpty()) {
                waterBlocks.removeIf(p ->
                    p.getX() < newMinX || p.getX() > newMaxX
                    || p.getY() < newMinY || p.getY() > newMaxY
                    || p.getZ() < newMinZ || p.getZ() > newMaxZ);
            }

            scanMinX = newMinX;
            scanMinY = newMinY;
            scanMinZ = newMinZ;
            scanMaxX = newMaxX;
            scanMaxY = newMaxY;
            scanMaxZ = newMaxZ;

            // 移动时保留游标进度，只把越界游标钳制回新范围，
            // 避免每次移动都从角落重扫导致「走动时水框消失、停下才出现」
            cursorX = Math.max(scanMinX, Math.min(cursorX, scanMaxX));
            cursorY = Math.max(scanMinY, Math.min(cursorY, scanMaxY));
            cursorZ = Math.max(scanMinZ, Math.min(cursorZ, scanMaxZ));

            // 区域重置后触发建议点立即重算
            regionChanged = true;
        }

        ClientLevel level = mc.level;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < BUDGET_PER_TICK; i++) {
            cursor.set(cursorX, cursorY, cursorZ);
            BlockState state = level.getBlockState(cursor);
            // 只记录「静止且孤立」的水源：过滤流动水，也过滤海 / 湖 / 河等连片水体
            if (state.is(Blocks.WATER) && state.getFluidState().isSource() && isIsolatedSource(level, cursor)) {
                waterBlocks.add(cursor.immutable());
            } else {
                waterBlocks.remove(cursor);
            }
            advance();
        }
    }

    /** 判断某格是否为静止水源方块 */
    private boolean isSourceWater(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /**
     * 判断该水源是否为「孤立灌溉水源」：四周（东 / 南 / 西 / 北）没有其它静止水源。
     * 用于过滤掉连片的海、湖、河水体，只保留耕地上一格一坑的灌溉水源。
     */
    private boolean isIsolatedSource(ClientLevel level, BlockPos pos) {
        return !isSourceWater(level, pos.offset(1, 0, 0))   // 东
            && !isSourceWater(level, pos.offset(-1, 0, 0))  // 西
            && !isSourceWater(level, pos.offset(0, 0, 1))   // 南
            && !isSourceWater(level, pos.offset(0, 0, -1)); // 北
    }

    /**
     * 计算建议放水点：在每个已放水源的上下左右四个方向（水平距离 9 格，刚好不重叠）
     * 找到可以放水的位置，这些位置的灌溉范围刚好和现有水源相连。
     */
    private void computeSuggestions() {
        suggestedSpots.clear();
        if (mc.level == null) return;

        ClientLevel level = mc.level;
        
        // 对每个已放水源，检查上下左右四个方向的建议位置
        for (BlockPos water : waterBlocks) {
            // 四个方向：X+9, X-9, Z+9, Z-9（水平距离 9 格，覆盖范围刚好相连不重叠）
            BlockPos[] candidates = new BlockPos[] {
                water.offset(9, 0, 0),   // 东
                water.offset(-9, 0, 0),  // 西
                water.offset(0, 0, 9),   // 南
                water.offset(0, 0, -9)   // 北
            };

            for (BlockPos candidate : candidates) {
                // 检查该位置是否已有水源
                if (waterBlocks.contains(candidate)) continue;
                
                // 检查该位置是否已在建议列表中
                if (suggestedSpots.contains(candidate)) continue;
                
                // 只要该位置是「地表」（上方是空气/水/可替换）就能挖坑放水；
                // 耕地/泥土/草方块上方是空气，同样显示建议，不再要求该格本身是空气
                BlockState stateAbove = level.getBlockState(candidate.above());
                if (stateAbove.isAir() || stateAbove.is(Blocks.WATER) || stateAbove.canBeReplaced()) {
                    suggestedSpots.add(candidate);
                }
            }
        }
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
