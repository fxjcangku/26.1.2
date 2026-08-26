package com.example.addon.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 矿石位置预测器 - 种子挖矿核心引擎
 * 
 * 根据世界种子预测真实矿石生成位置，用于识别假矿
 * 
 * 原理：
 * Minecraft矿石生成是伪随机的，由种子决定
 * 同一个种子 + 同一个坐标 = 永远生成同样的矿石分布
 * 
 * 使用方式：
 * 1. 填入世界种子
 * 2. 选择目标矿石类型
 * 3. 脚本预测周围真实矿石位置
 * 4. 渲染预测位置 + Baritone只挖预测位置的矿
 * 5. 假矿（手动放置/插件生成）不在预测列表里，直接无视
 */
public class OrePredictor {

    private long worldSeed;
    private Block targetOre;
    
    // 预测结果缓存：区块坐标 -> 该区块内的矿石位置列表
    private final Map<ChunkPos, Set<BlockPos>> predictionCache = new ConcurrentHashMap<>();
    
    // 缓存是否有效
    private boolean cacheValid = false;

    /**
     * 设置世界种子和目标矿石
     * 改变配置会清空缓存
     */
    public void configure(long seed, Block ore) {
        if (this.worldSeed != seed || this.targetOre != ore) {
            this.worldSeed = seed;
            this.targetOre = ore;
            invalidateCache();
        }
        this.cacheValid = true;
    }

    /**
     * 清空预测缓存
     */
    public void invalidateCache() {
        predictionCache.clear();
        cacheValid = false;
    }

    /**
     * 检查指定位置是否应该有目标矿石
     * 
     * @param pos 要检查的方块位置
     * @return true表示此位置应该有矿石（真矿），false表示不应该有（假矿）
     */
    public boolean isPredictedOreAt(BlockPos pos) {
        if (!cacheValid) return false;
        
        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        
        // 如果该区块未预测过，先预测
        if (!predictionCache.containsKey(chunkPos)) {
            predictChunk(chunkPos);
        }
        
        Set<BlockPos> ores = predictionCache.get(chunkPos);
        return ores != null && ores.contains(pos);
    }

    /**
     * 获取指定范围内的所有预测矿石位置
     * 
     * @param center 中心位置
     * @param radius 半径（格）
     * @return 预测的矿石位置集合
     */
    public Set<BlockPos> getPredictedOresInRange(BlockPos center, int radius) {
        if (!cacheValid) return Collections.emptySet();
        
        Set<BlockPos> result = new HashSet<>();
        
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        
        // 预测范围内所有区块
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                
                if (!predictionCache.containsKey(chunkPos)) {
                    predictChunk(chunkPos);
                }
                
                Set<BlockPos> chunkOres = predictionCache.get(chunkPos);
                if (chunkOres != null) {
                    // 只返回在半径内的
                    for (BlockPos pos : chunkOres) {
                        if (pos.distSqr(center) <= radius * radius) {
                            result.add(pos);
                        }
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * 预测单个区块内的矿石分布
     * 
     * 使用简化的噪声算法模拟Minecraft的矿石生成
     * 实际Minecraft使用复杂的噪声函数，这里用简化版保证性能
     */
    private void predictChunk(ChunkPos chunkPos) {
        Set<BlockPos> ores = new HashSet<>();
        
        // 为该区块创建随机数生成器
        RandomSource random = RandomSource.create(
            worldSeed ^ (((long)chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL))
        );
        
        OreConfig config = getOreConfig(targetOre);
        if (config == null) {
            predictionCache.put(chunkPos, ores);
            return;
        }
        
        // 生成矿脉数量（每个区块）
        int veinCount = config.veinsPerChunk + random.nextInt(config.veinCountVariation);
        
        for (int i = 0; i < veinCount; i++) {
            // 矿脉起点（区块内随机位置）
            int x = chunkPos.x * 16 + random.nextInt(16);
            int z = chunkPos.z * 16 + random.nextInt(16);
            int y = config.minY + random.nextInt(config.maxY - config.minY + 1);
            
            // 矿脉大小（随机）
            int veinSize = config.minVeinSize + random.nextInt(config.maxVeinSize - config.minVeinSize + 1);
            
            // 生成矿脉（球形分布）
            generateVein(ores, new BlockPos(x, y, z), veinSize, random);
        }
        
        predictionCache.put(chunkPos, ores);
    }

    /**
     * 生成单个矿脉（球形分布）
     */
    private void generateVein(Set<BlockPos> ores, BlockPos center, int size, RandomSource random) {
        // 椭球半径
        float radiusX = size / 4.0f;
        float radiusY = size / 8.0f;
        float radiusZ = size / 4.0f;
        
        // 遍历椭球范围内的方块
        int minX = (int) Math.floor(center.getX() - radiusX);
        int maxX = (int) Math.ceil(center.getX() + radiusX);
        int minY = (int) Math.floor(center.getY() - radiusY);
        int maxY = (int) Math.ceil(center.getY() + radiusY);
        int minZ = (int) Math.floor(center.getZ() - radiusZ);
        int maxZ = (int) Math.ceil(center.getZ() + radiusZ);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // 检查是否在椭球内
                    float dx = (x - center.getX()) / radiusX;
                    float dy = (y - center.getY()) / radiusY;
                    float dz = (z - center.getZ()) / radiusZ;
                    
                    if (dx * dx + dy * dy + dz * dz <= 1.0f) {
                        // 80%概率生成矿石（模拟不规则边缘）
                        if (random.nextFloat() < 0.8f) {
                            ores.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取矿石生成配置
     * 根据不同矿石类型返回对应的生成参数
     */
    private OreConfig getOreConfig(Block ore) {
        if (ore == null || ore == Blocks.AIR) return null;
        
        String id = ore.toString().toLowerCase();
        
        // 钻石矿（含深层变种）
        if (id.contains("diamond")) {
            return new OreConfig(0, 16, 1, 1, 4, 8);
        }
        
        // 铁矿（含深层变种）
        if (id.contains("iron")) {
            return new OreConfig(0, 64, 2, 2, 6, 12);
        }
        
        // 金矿（含深层变种）
        if (id.contains("gold") && !id.contains("nether")) {
            return new OreConfig(0, 32, 1, 1, 5, 9);
        }
        
        // 煤矿（含深层变种）
        if (id.contains("coal")) {
            return new OreConfig(0, 256, 3, 3, 10, 16);
        }
        
        // 青金石矿（含深层变种）
        if (id.contains("lapis")) {
            return new OreConfig(0, 64, 1, 1, 3, 6);
        }
        
        // 红石矿（含深层变种）
        if (id.contains("redstone")) {
            return new OreConfig(0, 16, 1, 1, 5, 8);
        }
        
        // 绿宝石矿
        if (id.contains("emerald")) {
            return new OreConfig(0, 256, 0, 1, 1, 1);
        }
        
        // 铜矿（含深层变种）
        if (id.contains("copper")) {
            return new OreConfig(0, 96, 2, 2, 8, 14);
        }
        
        // 下界金矿
        if (id.contains("nether") && id.contains("gold")) {
            return new OreConfig(10, 117, 2, 2, 6, 10);
        }
        
        // 下界石英矿
        if (id.contains("quartz")) {
            return new OreConfig(10, 117, 2, 2, 8, 14);
        }
        
        // 远古残骸
        if (id.contains("ancient_debris")) {
            return new OreConfig(8, 119, 0, 1, 1, 2);
        }
        
        // 未知矿石，使用默认配置
        return new OreConfig(0, 64, 1, 1, 4, 8);
    }

    /**
     * 矿石生成配置
     */
    private static class OreConfig {
        final int minY;                  // 最低生成高度
        final int maxY;                  // 最高生成高度
        final int veinsPerChunk;         // 每区块矿脉数量（基础）
        final int veinCountVariation;    // 矿脉数量随机变化
        final int minVeinSize;           // 单个矿脉最小方块数
        final int maxVeinSize;           // 单个矿脉最大方块数
        
        OreConfig(int minY, int maxY, int veinsPerChunk, int veinCountVariation, int minVeinSize, int maxVeinSize) {
            this.minY = minY;
            this.maxY = maxY;
            this.veinsPerChunk = veinsPerChunk;
            this.veinCountVariation = veinCountVariation;
            this.minVeinSize = minVeinSize;
            this.maxVeinSize = maxVeinSize;
        }
    }

    /**
     * 区块坐标（用于缓存键）
     */
    private static class ChunkPos {
        final int x;
        final int z;
        
        ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ChunkPos other)) return false;
            return this.x == other.x && this.z == other.z;
        }
        
        @Override
        public int hashCode() {
            return x * 31 + z;
        }
    }
}
