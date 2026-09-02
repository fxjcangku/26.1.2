package com.example.addon.autofarm.scan;

import com.example.addon.autofarm.model.CropProfile;
import com.example.addon.autofarm.model.FarmTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 农田分帧扫描器。
 *
 * 保留旧实现的分帧思想：一个 200x200 的农场就是 4 万格，每 tick 全量扫一遍必然掉帧，
 * 因此每 tick 只扫固定格数（BUDGET_PER_TICK），游标循环推进，持续刷新缓存。
 *
 * 这里只维护「当前已知的成熟/可补种坐标」两个持续刷新的缓存，不生成任何批次快照。
 * 作业方每次只取一个最近目标，实现「熟一颗收一颗」，扫描没有「一轮」概念。
 */
public final class FarmScanner {

    /** 每 tick 最多检查多少格，超过就留到下一 tick */
    private static final int BUDGET_PER_TICK = 512;

    private BlockPos min = BlockPos.ZERO;
    private BlockPos max = BlockPos.ZERO;
    private boolean bounded;

    /** 扫描游标，按 x → z → y 顺序循环推进 */
    private int cursorX;
    private int cursorY;
    private int cursorZ;

    /** 当前已知的成熟作物坐标缓存，持续刷新 */
    private final Set<BlockPos> matureBlocks = new HashSet<>();
    /** 当前已知的可补种底盘坐标缓存，持续刷新 */
    private final Set<BlockPos> plantableBlocks = new HashSet<>();

    private final Set<CropProfile> enabled = EnumSet.noneOf(CropProfile.class);

    /** 设定扫描范围。两个锚点是对角，内部归一化为 min/max。 */
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

    /** 范围内的总格数，自检时用于拦下「起点终点设成同一格」这类误操作 */
    public long volume() {
        if (!bounded) return 0;
        long dx = (long) max.getX() - min.getX() + 1;
        long dy = (long) max.getY() - min.getY() + 1;
        long dz = (long) max.getZ() - min.getZ() + 1;
        return dx * dy * dz;
    }

    /** 当前是否有已知成熟目标 */
    public boolean hasMature() {
        return !matureBlocks.isEmpty();
    }

    /** 当前是否有已知可补种空地 */
    public boolean hasPlantable() {
        return !plantableBlocks.isEmpty();
    }

    /** 取离玩家最近的一个成熟目标，没有返回 null */
    public FarmTarget nearestHarvest() {
        BlockPos nearest = nearest(matureBlocks);
        if (nearest == null) return null;
        CropProfile profile = CropProfile.byBlock(Minecraft.getInstance().level.getBlockState(nearest).getBlock());
        if (profile == null) return null;
        return FarmTarget.harvest(profile, nearest);
    }

    /** 取离玩家最近的至多 maxCount 个成熟目标，按距离升序；实际数量不足则返回实际数量 */
    public List<FarmTarget> nearestHarvests(int maxCount) {
        List<FarmTarget> result = new ArrayList<>();
        if (maxCount <= 0 || matureBlocks.isEmpty()) return result;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return result;

        List<BlockPos> sorted = new ArrayList<>(matureBlocks);
        sorted.sort(Comparator.comparingDouble(this::distanceSqToPlayer));

        for (BlockPos pos : sorted) {
            if (result.size() >= maxCount) break;
            CropProfile profile = CropProfile.byBlock(mc.level.getBlockState(pos).getBlock());
            if (profile == null) continue;
            result.add(FarmTarget.harvest(profile, pos));
        }
        return result;
    }

    /** 取离玩家最近的一个可补种目标，没有返回 null */
    public FarmTarget nearestPlantable() {
        BlockPos nearest = nearest(plantableBlocks);
        if (nearest == null) return null;
        for (CropProfile profile : enabled) {
            if (profile.isPlantable(Minecraft.getInstance().level, nearest)) {
                return FarmTarget.plant(profile, nearest);
            }
        }
        return null;
    }

    /** 坐标是否落在扫描范围内 */
    public boolean contains(BlockPos pos) {
        if (!bounded) return false;
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** 农场中心上方一格，供返回农场时作为归位点 */
    public BlockPos center() {
        return new BlockPos(
            (min.getX() + max.getX()) / 2,
            min.getY() + 1,
            (min.getZ() + max.getZ()) / 2);
    }

    /** 主动移除某个坐标（目标被处理或失效后调用，避免等下一轮扫描才刷新） */
    public void invalidate(BlockPos pos) {
        matureBlocks.remove(pos);
        plantableBlocks.remove(pos);
    }

    public void reset() {
        bounded = false;
        matureBlocks.clear();
        plantableBlocks.clear();
        restart();
    }

    /** 把扫描游标归位到范围起点 */
    private void restart() {
        cursorX = min.getX();
        cursorY = min.getY();
        cursorZ = min.getZ();
    }

    /** 推进一帧扫描。每 tick 调用一次。 */
    public void tick() {
        if (!bounded || enabled.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < BUDGET_PER_TICK; i++) {
            cursor.set(cursorX, cursorY, cursorZ);
            classify(level, cursor);
            advance();
        }
    }

    /** 游标前进一格，越过边界就回卷到起点，循环推进 */
    private void advance() {
        cursorX++;
        if (cursorX <= max.getX()) return;

        cursorX = min.getX();
        cursorZ++;
        if (cursorZ <= max.getZ()) return;

        cursorZ = min.getZ();
        cursorY++;
        if (cursorY <= max.getY()) return;

        cursorX = min.getX();
        cursorY = min.getY();
        cursorZ = min.getZ();
    }

    /** 判定单格属于成熟目标、可补种空地还是无关方块，并持续刷新缓存 */
    private void classify(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            matureBlocks.remove(pos);
            plantableBlocks.remove(pos);
            return;
        }

        CropProfile profile = CropProfile.byBlock(state.getBlock());
        if (profile != null && enabled.contains(profile) && profile.isHarvestable(state, level, pos)) {
            matureBlocks.add(pos.immutable());
        } else {
            matureBlocks.remove(pos);
        }

        // 该格是底盘时，检查上方能否补种
        boolean plantable = false;
        for (CropProfile candidate : enabled) {
            if (candidate.isPlantable(level, pos)) {
                plantable = true;
                break;
            }
        }
        if (plantable) {
            plantableBlocks.add(pos.immutable());
        } else {
            plantableBlocks.remove(pos);
        }
    }

    /** 在集合里取离玩家眼睛最近的坐标 */
    private BlockPos nearest(Set<BlockPos> positions) {
        Minecraft mc = Minecraft.getInstance();
        if (positions.isEmpty() || mc.player == null) return null;

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double distSq = distanceSqToPlayer(pos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = pos;
            }
        }
        return best;
    }

    /** 计算某坐标到玩家眼睛的平方距离，用于按距离排序选目标 */
    private double distanceSqToPlayer(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getEyeY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
