package com.example.addon.stardew.farming;

import com.example.addon.stardew.adapter.StardewServerAdapter;
import com.example.addon.stardew.config.StardewConfig;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 农田扫描器：在绑定的矩形范围内按 tick 预算分帧扫描星露谷农田底盘。
 *
 * <p>只负责「找出哪里是农田」，具体土地状态（空/已种/成熟）由
 * {@link com.example.addon.stardew.adapter.StardewServerAdapter} 在执行前重新观察判定。
 * 扫描按需 + 缓存，不每 tick 扫全图。</p>
 */
public final class FarmPlotScanner {

    private final StardewServerAdapter adapter;

    private BlockPos min;
    private BlockPos max;
    private BlockPos cursor;
    private boolean bounded;
    private boolean scanComplete;
    private final Set<BlockPos> plots = new LinkedHashSet<>();

    public FarmPlotScanner(StardewServerAdapter adapter) {
        this.adapter = adapter;
    }

    /** 设置扫描范围并重置扫描进度 */
    public void setBounds(BlockPos a, BlockPos b) {
        if (a == null || b == null) return;
        this.min = new BlockPos(
            Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(
            Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        this.cursor = this.min;
        this.bounded = true;
        this.scanComplete = false;
        this.plots.clear();
    }

    /** 重置扫描状态（关闭模块或清空范围时调用） */
    public void reset() {
        this.min = null;
        this.max = null;
        this.cursor = null;
        this.bounded = false;
        this.scanComplete = false;
        this.plots.clear();
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

    /** 已扫描出的农田底盘坐标集合（只读快照） */
    public Set<BlockPos> plots() {
        return plots;
    }

    /** 一次性全量扫描（模块开启时快速拿到全部农田） */
    public void fullScan() {
        if (!bounded) return;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (adapter.isSoil(pos)) plots.add(pos);
                }
            }
        }
        scanComplete = true;
        cursor = null;
    }

    /** 每 tick 推进一次，按预算扫描若干格 */
    public void tick() {
        if (!bounded || scanComplete) return;
        int budget = StardewConfig.SCAN_BUDGET_PER_TICK;
        while (budget-- > 0 && cursor != null) {
            BlockPos pos = cursor;
            advanceCursor();
            if (adapter.isSoil(pos)) plots.add(pos);
        }
        if (cursor == null) scanComplete = true;
    }

    /** x 最快 → z → y 逐格推进，遍历完置 null 表示完成 */
    private void advanceCursor() {
        if (cursor == null) return;
        int x = cursor.getX();
        int y = cursor.getY();
        int z = cursor.getZ();
        if (x < max.getX()) {
            cursor = new BlockPos(x + 1, y, z);
        } else if (z < max.getZ()) {
            cursor = new BlockPos(min.getX(), y, z + 1);
        } else if (y < max.getY()) {
            cursor = new BlockPos(min.getX(), y + 1, min.getZ());
        } else {
            cursor = null;
        }
    }
}
