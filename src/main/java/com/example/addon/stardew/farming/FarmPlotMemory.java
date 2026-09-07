package com.example.addon.stardew.farming;

import com.example.addon.stardew.model.WaterState;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * 农田记忆：缓存每块地的最近观察结果，仅作规划参考。
 *
 * <p>记忆不是事实来源：真正执行收割/种植/浇水/施肥前，必须由
 * {@link com.example.addon.stardew.adapter.StardewServerAdapter} 重新观察真实世界数据。
 * 超过 TTL 的条目会被清理并强制重新观察。</p>
 */
public final class FarmPlotMemory {

    /** 一块地的最近观察快照 */
    public record Entry(
        String cropId,
        String seedId,
        int growthStage,
        boolean lastKnownMature,
        WaterState lastKnownWaterState,
        boolean fertilized,
        long lastObservedTick
    ) {}

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    public void put(BlockPos pos, Entry entry) {
        if (pos != null && entry != null) entries.put(pos, entry);
    }

    public Entry get(BlockPos pos) {
        return pos == null ? null : entries.get(pos);
    }

    public void remove(BlockPos pos) {
        if (pos != null) entries.remove(pos);
    }

    /** 该坐标的记忆是否仍在 TTL 内 */
    public boolean isFresh(BlockPos pos, long nowTick, int ttl) {
        Entry entry = get(pos);
        return entry != null && nowTick - entry.lastObservedTick() <= ttl;
    }

    /** 清理超过 TTL 的条目 */
    public void prune(long nowTick, int ttl) {
        entries.entrySet().removeIf(e -> nowTick - e.getValue().lastObservedTick() > ttl);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
