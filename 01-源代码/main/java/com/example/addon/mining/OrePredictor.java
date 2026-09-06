package com.example.addon.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * 矿石实测扫描器 - 种子挖矿核心引擎
 * 
 * 诚实说明（重要）：
 * 客户端无法仅凭世界种子复刻服务器矿物分布——原版地物生成依赖
 * 完整的装饰管线（每区块随机数 + 数据驱动 placed_feature 配置），
 * 第三方服务端还有自定义 datapack，任何「种子 → 坐标」推算都是伪预测。
 * 
 * 因此本模块改为「实测扫描」：
 * 直接读取服务器已下发区块的真实方块数据，缓存目标矿位置。
 * 渲染的每一个框、寻路走的每一个点，都是服务器上真实存在的矿，
 * 不存在「预测到空气/石头」的问题，也不再把任意数字误判成有效种子。
 * 
 * 使用方式：
 * 1. 选择目标矿石，启用种子挖矿（世界种子选填，仅作记录）
 * 2. 玩家周围的已加载区块自动分帧扫描，渐进出结果
 * 3. 渲染实测矿点 + 采集循环只挖实测矿位
 * 4. 「检测假矿」对比两次快照，标记新出现的矿点（临时放置的诱饵矿）
 */
public class OrePredictor {

    /** 每帧最大扫描区块数（分帧扫描，避免主线程卡顿） */
    public static final int SCAN_BUDGET_PER_FRAME = 4;

    private long worldSeed;              // 世界种子（仅记录，不参与坐标计算）
    private Block targetOre;             // 目标矿石锚点
    private String targetBaseId;         // 去掉 deepslate_ 前缀的基础 ID（家族匹配）

    // 已扫描区块缓存：区块长包（cx<<32|cz）-> 该区块实测矿位集合
    private final Map<Long, Set<BlockPos>> chunkCache = new ConcurrentHashMap<>();
    // 待扫描队列 + 去重集合
    private final Deque<Long> pendingScans = new ArrayDeque<>();
    private final Set<Long> queuedKeys = ConcurrentHashMap.newKeySet();

    /**
     * 配置扫描目标。种子仅作记录用，改变目标或种子会清空缓存重扫。
     */
    public void configure(long seed, Block ore) {
        if (this.worldSeed != seed || this.targetOre != ore) {
            invalidateCache();
        }
        this.worldSeed = seed;
        this.targetOre = ore;
        this.targetBaseId = (ore == null)
            ? ""
            : BuiltInRegistries.BLOCK.getKey(ore).getPath().replace("deepslate_", "");
    }

    /** 清空全部缓存与扫描队列 */
    public void invalidateCache() {
        chunkCache.clear();
        pendingScans.clear();
        queuedKeys.clear();
    }

    /** 世界里的方块是否属于目标矿家族（含深层变种） */
    public boolean isTargetFamily(Block block) {
        if (block == null || targetOre == null) return false;
        if (block == targetOre) return true;
        if (targetBaseId.isEmpty()) return false;
        return BuiltInRegistries.BLOCK.getKey(block).getPath()
            .replace("deepslate_", "").equals(targetBaseId);
    }

    /** 区块是否已被扫描过（含扫过但为空的区块） */
    public boolean isChunkScanned(int cx, int cz) {
        return chunkCache.containsKey(packChunk(cx, cz));
    }

    /** 获取该区块的实测矿位缓存；未扫描过返回 null */
    public Set<BlockPos> getCached(int cx, int cz) {
        return chunkCache.get(packChunk(cx, cz));
    }

    /** 同步扫描单个已加载区块（新鲜快照），结果写入缓存并返回 */
    public Set<BlockPos> scanChunkNow(int cx, int cz) {
        Set<BlockPos> ores = scanChunk(cx, cz);
        chunkCache.put(packChunk(cx, cz), ores);
        return ores;
    }

    /** 每帧由模块 onTick 调用：处理最多 SCAN_BUDGET_PER_FRAME 个待扫区块 */
    public void processScanQueue() {
        int budget = SCAN_BUDGET_PER_FRAME;
        while (budget-- > 0) {
            Long key = pendingScans.poll();
            if (key == null) break;
            queuedKeys.remove(key);
            long packed = key; // Long 无法直接强转 int，先拆箱
            int cx = (int) (packed >> 32);
            int cz = (int) packed;
            chunkCache.put(key, scanChunk(cx, cz));
        }
    }

    /** 是否还有未完成的扫描任务（采集循环据此等待，避免误判无矿） */
    public boolean hasPendingScans() {
        return !pendingScans.isEmpty();
    }

    /**
     * 范围内是否仍有未加载的区块。
     * RTP 传送后服务端分帧下发区块，若立即判「挖完」会在区块加载完成前误 RTP。
     * 采集循环用此方法等待区块到位再判空。
     */
    public boolean hasUnloadedChunksInRange(BlockPos center, int radius) {
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!isChunkLoaded(cx, cz)) return true;
            }
        }
        return false;
    }

    /**
     * 获取范围内所有实测矿位。
     * 未扫描的已加载区块会入队（每次调用最多入队 32 个），由 processScanQueue 分帧消化。
     */
    public Set<BlockPos> getPredictedOresInRange(BlockPos center, int radius) {
        Set<BlockPos> result = new HashSet<>();

        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;

        int enqueueBudget = 32;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = packChunk(cx, cz);
                Set<BlockPos> cached = chunkCache.get(key);
                if (cached == null) {
                    // 未扫描 → 已加载则入队（未加载区块玩家看不到，也没数据）
                    if (enqueueBudget-- > 0 && isChunkLoaded(cx, cz)) {
                        enqueue(key);
                    }
                    continue;
                }
                for (BlockPos pos : cached) {
                    if (pos.distSqr(center) <= radius * radius) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 检查指定位置是否为目标矿（以最近一次实扫缓存为准，无缓存则隐式补扫该区块）。
     * 用于「检测假矿」快照对比与兜底双检。
     */
    public boolean isPredictedOreAt(BlockPos pos) {
        if (targetOre == null) return false;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = packChunk(cx, cz);

        Set<BlockPos> cached = chunkCache.get(key);
        if (cached == null) {
            cached = scanChunkNow(cx, cz);
        }
        return cached.contains(pos);
    }

    /**
     * 移除某位置的实测矿缓存（挖掉/预测失误/不可达后调用）。
     * 采用 copy-on-write 替换整集合：渲染线程可能正持有旧集合引用遍历，
     * 就地 remove 会抛 ConcurrentModificationException，旧引用替换后自然废弃。
     */
    public void forgetOre(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        chunkCache.computeIfPresent(packChunk(cx, cz), (key, ores) -> {
            if (!ores.contains(pos)) return ores;
            Set<BlockPos> copy = new HashSet<>(ores);
            copy.remove(pos);
            return copy;
        });
    }

    /** 获取所有已缓存的实测矿位（仅已扫描区块） */
    public Set<BlockPos> getAllPredictedOres() {
        Set<BlockPos> result = new HashSet<>();
        for (Set<BlockPos> ores : chunkCache.values()) {
            result.addAll(ores);
        }
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部实现
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private void enqueue(long key) {
        if (queuedKeys.add(key)) {
            pendingScans.offer(key);
        }
    }

    private boolean isChunkLoaded(int cx, int cz) {
        return mc.level != null && mc.level.getChunk(cx, cz) instanceof LevelChunk;
    }

    /**
     * 扫描单个区块的真实方块数据（跳过全空气段，性能友好）。
     * 区块未加载时返回空集合。
     */
    private Set<BlockPos> scanChunk(int cx, int cz) {
        Set<BlockPos> ores = new HashSet<>();
        if (mc.level == null || targetOre == null) return ores;

        ChunkAccess access = mc.level.getChunk(cx, cz);
        if (!(access instanceof LevelChunk chunk)) return ores;

        // Yarn 映射：getSections() 数组（索引0=最底部段）+ getSectionIndex(y) 线性换算，
        // 与 Meteor TunnelESP 同款用法。用 y=0 的段索引反推出数组起始段坐标，
        // 不依赖维度高度 getter（各版本映射名不一致，这里数学关系恒定）。
        LevelChunkSection[] sections = chunk.getSections();
        int zeroIndex = chunk.getSectionIndex(0);

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            int yBase = (i - zeroIndex) * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        if (isTargetFamily(state.getBlock())) {
                            ores.add(new BlockPos((cx << 4) + x, yBase + y, (cz << 4) + z));
                        }
                    }
                }
            }
        }
        return ores;
    }
}