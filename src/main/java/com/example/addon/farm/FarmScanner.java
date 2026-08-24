package com.example.addon.farm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 农田分帧扫描器。
 *
 * 一个 200x200 的农场就是 4 万格，每 tick 全量扫一遍必然掉帧。
 * 所以这里把整个区域切成若干"批"，每 tick 只扫固定格数，扫完一整轮才提交结果，
 * 期间对外提供的一直是上一轮的稳定快照，状态机读到的队列不会中途变形。
 *
 * 两个输出队列：
 * · harvestQueue —— 已成熟、可以直接发破坏包的坐标
 * · plantQueue   —— 底盘正确但上方为空的坐标（存的是底盘坐标，种子种在其上方）
 */
public final class FarmScanner {

    /** 每 tick 最多检查多少格，超过就留到下一 tick */
    private static final int BUDGET_PER_TICK = 512;

    private BlockPos min = BlockPos.ZERO;
    private BlockPos max = BlockPos.ZERO;
    private boolean bounded;

    /** 扫描游标，按 x → z → y 顺序推进 */
    private int cursorX;
    private int cursorY;
    private int cursorZ;

    private final List<BlockPos> harvestBuffer = new ArrayList<>();
    private final List<BlockPos> plantBuffer = new ArrayList<>();

    /** 对外暴露的稳定快照，只在一整轮扫描结束时整体替换 */
    private List<BlockPos> harvestQueue = List.of();
    private List<BlockPos> plantQueue = List.of();

    private final Set<CropProfile> enabled = EnumSet.noneOf(CropProfile.class);

    /** 已完成的扫描轮数，自检与 HUD 用来确认扫描器真的在跑 */
    private int completedSweeps;

    /**
     * 设定扫描范围。两个锚点是对角，内部会归一化成 min/max。
     * Y 轴按对角实际跨度取，允许多层立体农场。
     */
    public void setBounds(BlockPos a, BlockPos b) {
        min = new BlockPos(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ()));
        max = new BlockPos(
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ()));
        bounded = true;
        restart();
    }

    /** 更新启用的作物集合，会立刻重启扫描避免用旧图鉴的残留结果 */
    public void setEnabledCrops(Set<CropProfile> crops) {
        if (enabled.equals(crops)) return;
        enabled.clear();
        enabled.addAll(crops);
        restart();
    }

    public boolean bounded() {
        return bounded;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    /** 范围内的总格数，自检时用来拦下"起点终点设成同一格"这类误操作 */
    public long volume() {
        if (!bounded) return 0;
        long dx = (long) max.getX() - min.getX() + 1;
        long dy = (long) max.getY() - min.getY() + 1;
        long dz = (long) max.getZ() - min.getZ() + 1;
        return dx * dy * dz;
    }

    public List<BlockPos> harvestQueue() {
        return harvestQueue;
    }

    public List<BlockPos> plantQueue() {
        return plantQueue;
    }

    public int completedSweeps() {
        return completedSweeps;
    }

    /** 坐标是否落在扫描范围内。防踩踏与渲染都要用 */
    public boolean contains(BlockPos pos) {
        if (!bounded) return false;
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public void reset() {
        bounded = false;
        harvestQueue = List.of();
        plantQueue = List.of();
        harvestBuffer.clear();
        plantBuffer.clear();
        completedSweeps = 0;
        restart();
    }

    /** 从头开始新一轮扫描，清空本轮缓冲但保留上一轮快照 */
    private void restart() {
        cursorX = min.getX();
        cursorY = min.getY();
        cursorZ = min.getZ();
        harvestBuffer.clear();
        plantBuffer.clear();
    }

    /**
     * 推进一帧扫描。每 tick 调用一次。
     *
     * @return 本 tick 是否刚好完成了一整轮扫描（快照已更新）
     */
    public boolean tick() {
        if (!bounded || enabled.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return false;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < BUDGET_PER_TICK; i++) {
            cursor.set(cursorX, cursorY, cursorZ);
            classify(level, cursor);

            if (!advance()) {
                // 一整轮扫完，把缓冲提交成新快照
                harvestQueue = List.copyOf(harvestBuffer);
                plantQueue = List.copyOf(plantBuffer);
                completedSweeps++;
                restart();
                return true;
            }
        }
        return false;
    }

    /** 游标前进一格，返回 false 表示整轮已到尾 */
    private boolean advance() {
        cursorX++;
        if (cursorX <= max.getX()) return true;

        cursorX = min.getX();
        cursorZ++;
        if (cursorZ <= max.getZ()) return true;

        cursorZ = min.getZ();
        cursorY++;
        return cursorY <= max.getY();
    }

    /** 判定单格属于收割目标、待补种空地还是无关方块 */
    private void classify(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (!state.isAir()) {
            CropProfile profile = CropProfile.byBlock(state.getBlock());
            if (profile != null && enabled.contains(profile)
                && profile.isHarvestable(state, level, pos)) {
                harvestBuffer.add(pos.immutable());
            }
            // 该格是底盘方块时，检查上方能不能补种
            for (CropProfile candidate : enabled) {
                if (candidate.isPlantable(level, pos)) {
                    plantBuffer.add(pos.immutable());
                    break;
                }
            }
        }
    }
}
