package com.chronoblocks.core.selection.cache;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 💾 Chunk 缓存系统
 * <p>
 * 缓存已加载的 BlockState，减少 world.getBlockState() 调用次数，
 * 显著提升大范围泛洪填充的性能。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ChunkCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkCache.class);
    
    private final World world;
    private final Map<ChunkPos, ChunkSection> cache = new HashMap<>();
    
    private int hits = 0;
    private int misses = 0;
    
    public ChunkCache(World world) {
        this.world = world;
    }
    
    /**
     * 获取方块状态（优先从缓存）
     */
    public BlockState getBlockState(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkSection section = cache.get(chunkPos);
        
        if (section == null) {
            // 缓存未命中，加载区块
            misses++;
            section = loadChunkSection(chunkPos);
            cache.put(chunkPos, section);
        } else {
            hits++;
        }
        
        return section.getBlockState(pos);
    }
    
    /**
     * 加载 Chunk Section
     */
    private ChunkSection loadChunkSection(ChunkPos chunkPos) {
        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        return new ChunkSection(chunk, chunkPos);
    }
    
    /**
     * 清除缓存
     */
    public void clear() {
        cache.clear();
        LOGGER.debug("[ChunkCache] 清除缓存，命中率: {}/{} ({:.2f}%)", 
            hits, hits + misses, hits * 100.0 / Math.max(1, hits + misses));
        hits = 0;
        misses = 0;
    }
    
    /**
     * 获取缓存统计
     */
    public CacheStats getStats() {
        return new CacheStats(hits, misses, cache.size());
    }
    
    /**
     * 预加载周围的 Chunk
     */
    public void preload(BlockPos center, int radius) {
        ChunkPos centerChunk = new ChunkPos(center);
        int chunkRadius = (radius + 15) / 16; // 转换为 Chunk 半径
        
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                ChunkPos pos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);
                if (!cache.containsKey(pos)) {
                    cache.put(pos, loadChunkSection(pos));
                }
            }
        }
        
        LOGGER.debug("[ChunkCache] 预加载 {} 个 Chunk", cache.size());
    }
    
    /**
     * Chunk Section 包装类
     */
    private static class ChunkSection {
        private final Chunk chunk;
        private final Map<BlockPos, BlockState> blockCache = new HashMap<>();
        
        ChunkSection(Chunk chunk, ChunkPos chunkPos) {
            this.chunk = chunk;
        }
        
        BlockState getBlockState(BlockPos pos) {
            BlockState cached = blockCache.get(pos);
            if (cached != null) {
                return cached;
            }
            
            BlockState state = chunk.getBlockState(pos);
            blockCache.put(pos, state);
            return state;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public record CacheStats(int hits, int misses, int cachedChunks) {
        public double getHitRate() {
            int total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
        
        @Override
        public @NotNull String toString() {
            return String.format("命中率: %.2f%% (%d/%d), 缓存区块: %d", 
                getHitRate() * 100, hits, hits + misses, cachedChunks);
        }
    }
}

