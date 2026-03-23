package com.director.core.selection.tools.magicwandtool;

import com.director.core.selection.SelectionState;
import com.director.core.selection.block_filters.BlockFilterPipeline;
import com.director.core.selection.cache.ChunkCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🪄 MagicWandTask - 魔棒泛洪填充算法核心
 * <p>
 * 执行一次完整的泛洪填充（Flood Fill）操作，从种子点出发，
 * 根据配置的匹配规则和连通性设置，扩散选择符合条件的方块。
 * <p>
 * 特性：
 * - 支持多种比较模式（方块类型、状态、材质、标签等）
 * - 支持多种连通性（6/18/26邻接）
 * - 支持方向传播控制（上/下/水平/对角）
 * - 支持范围限制和表层检测
 * - 支持在现有选区内限定
 * - 高性能 BFS 实现
 * 
 * @author Director Team
 * @version 2.0 Enhanced
 */
public final class MagicWandTask {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicWandTask.class);
    
    // ==================== 输入参数 ====================
    
    private final World world;
    private final BlockPos seed;
    private final MagicWandConfig config;
    private final SelectionState limitRegion; // 可选：限定在此区域内
    private final BlockFilterPipeline filterPipeline;
    
    // ==================== 运行时状态 ====================
    
    private BlockState originState;
    private Block originBlock;
    private MapColor originMapColor;
    
    private final Set<BlockPos> result = new HashSet<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private final Queue<BlockPos> queue = new LinkedList<>();
    
    /** 记录每个方块的 BFS 深度（用于渐变预览） */
    private final Map<BlockPos, Integer> depthMap = new HashMap<>();
    
    private boolean reachedLimit = false;
    private int steps = 0;
    
    // ==================== 性能优化：匹配缓存 ====================
    
    /** 方块匹配缓存（避免重复计算） */
    private final Map<Block, Boolean> blockMatchCache = new HashMap<>();
    private final Map<BlockState, Boolean> stateMatchCache = new HashMap<>();
    
    /** Chunk 缓存（性能优化） */
    private ChunkCache chunkCache;
    
    // ==================== 进度回调接口 ====================
    
    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        /**
         * 报告进度
         * 
         * @param current 当前已处理的方块数
         * @param total 预计总方块数（可能为 -1 表示未知）
         * @param message 进度消息
         * @return true 继续执行，false 取消任务
         */
        boolean onProgress(int current, int total, String message);
    }
    
    /** 进度回调 */
    private ProgressCallback progressCallback = null;
    
    /** 是否已取消 */
    private volatile boolean cancelled = false;
    
    // ==================== 构造函数 ====================
    
    /**
     * 创建魔棒任务
     *
     * @param world 世界实例
     * @param seed 起始种子点
     * @param config 魔棒配置
     * @param limitRegion 可选的限定区域（null 表示不限制）
     */
    public MagicWandTask(World world, BlockPos seed, MagicWandConfig config, SelectionState limitRegion) {
        this(world, seed, config, limitRegion, null);
    }

    public MagicWandTask(World world, BlockPos seed, MagicWandConfig config,
                         SelectionState limitRegion, BlockFilterPipeline filterPipeline) {
        this.world = world;
        this.seed = seed;
        this.config = config;
        this.limitRegion = limitRegion;
        this.filterPipeline = filterPipeline;
    }

    /**
     * 取消任务
     */
    public void cancel() {
        this.cancelled = true;
        LOGGER.debug("[MagicWandTask] 任务已取消");
    }
    
    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    // ==================== 执行入口 ====================
    
    /**
     * 执行泛洪填充
     * 
     * @param maxSteps 最大步数（用于分帧执行，-1 表示一次执行完）
     * @return 任务结果
     */
    public MagicWandTaskResult run(int maxSteps) {
        if (world == null || seed == null) {
            LOGGER.warn("[MagicWandTask] 无效的输入参数");
            return new MagicWandTaskResult(Collections.emptySet(), false, 0, false, Collections.emptyMap());
        }
        
        // 初始化 Chunk 缓存
        chunkCache = new ChunkCache(world);
        if (config.getRange() >= 0) {
            // 如果设置了范围限制，预加载周围的 Chunk
            chunkCache.preload(seed, config.getRange());
        }
        
        // 初始化起点状态
        originState = world.getBlockState(seed);
        originBlock = originState.getBlock();
        originMapColor = originState.getMapColor(world, seed);
        
        if (originState.isAir()) {
            LOGGER.debug("[MagicWandTask] 种子点是空气，跳过");
            return new MagicWandTaskResult(Collections.emptySet(), false, 0, false, Collections.emptyMap());
        }
        
        // 检查是否在限定区域内
        if (limitRegion != null && !limitRegion.contains(seed)) {
            LOGGER.debug("[MagicWandTask] 种子点不在限定区域内");
            return new MagicWandTaskResult(Collections.emptySet(), false, 0, false, Collections.emptyMap());
        }
        
        // 如果存在过滤管线，先判断种子点是否通过
        if (filterPipeline != null && !filterPipeline.isEmpty()) {
            if (!filterPipeline.test(world, seed, originState)) {
                LOGGER.debug("[MagicWandTask] 种子点未通过过滤管线，返回空结果");
                return new MagicWandTaskResult(Collections.emptySet(), false, 0, false, Collections.emptyMap());
            }
        }
        
        // 初始化队列
        queue.offer(seed);
        visited.add(seed);
        result.add(seed);
        depthMap.put(seed, 0);  // 种子点深度为 0
        
        // BFS 泛洪填充
        int stepLimit = maxSteps < 0 ? Integer.MAX_VALUE : maxSteps;
        int progressReportInterval = Math.max(100, config.getMaxBlocks() / 100); // 每 1% 或至少每100步报告一次
        
        while (!queue.isEmpty() && result.size() < config.getMaxBlocks() && steps < stepLimit && !cancelled) {
            BlockPos current = queue.poll();
            int currentDepth = depthMap.getOrDefault(current, 0);  // 获取当前方块的深度
            steps++;
            
            // 定期报告进度
            if (progressCallback != null && steps % progressReportInterval == 0) {
                boolean shouldContinue = progressCallback.onProgress(
                    result.size(),
                    config.getMaxBlocks(),
                    String.format("已扫描 %d 个方块...", steps)
                );
                if (!shouldContinue) {
                    cancelled = true;
                    LOGGER.debug("[MagicWandTask] 用户取消任务");
                    break;
                }
            }
            
            // 检查范围限制
            if (config.getRange() >= 0) {
                int distance = manhattanDistance(seed, current);
                if (distance > config.getRange()) {
                    continue;
                }
            }
            
            // 获取邻居并检查
            List<BlockPos> neighbors = getNeighbors(current);
            
            for (BlockPos neighbor : neighbors) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                visited.add(neighbor);
                
                // 检查是否在限定区域内
                if (limitRegion != null && !limitRegion.contains(neighbor)) {
                    continue;
                }
                
                BlockState neighborState = chunkCache.getBlockState(neighbor);
                
                // 检查是否匹配
                if (!matches(neighbor, neighborState)) {
                    continue;
                }
                
                // 检查是否为表层（如果开启）
                if (config.isSurfaceOnly() && !isSurfaceBlock(neighbor)) {
                    continue;
                }
                
                // 通过所有检查，加入结果
                result.add(neighbor);
                queue.offer(neighbor);
                depthMap.put(neighbor, currentDepth + 1);  // 记录深度（父节点深度+1）
                
                // 检查是否达到上限
                if (result.size() >= config.getMaxBlocks()) {
                    reachedLimit = true;
                    break;
                }
            }
            
            if (reachedLimit) {
                break;
            }
        }
        
        // 最终进度报告
        if (progressCallback != null && !cancelled) {
            progressCallback.onProgress(result.size(), config.getMaxBlocks(), "完成！");
        }
        
        String statusMsg = cancelled ? "（已取消）" : (reachedLimit ? "（达到上限）" : "");
        LOGGER.debug("[MagicWandTask] 完成：找到 {} 个方块，执行 {} 步{}，最大深度={}",
            result.size(), steps, statusMsg, depthMap.values().stream().max(Integer::compareTo).orElse(0));
        
        // 输出缓存统计并清除
        if (chunkCache != null) {
            var stats = chunkCache.getStats();
            LOGGER.debug("[MagicWandTask] 缓存统计: {}", stats);
            chunkCache.clear();
        }
        
        return new MagicWandTaskResult(new HashSet<>(result), reachedLimit, steps, cancelled, new HashMap<>(depthMap));
    }
    
    // ==================== 邻居计算 ====================
    
    /**
     * 根据连通性配置获取邻居方块
     */
    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        MagicWandConfig.Connectivity connectivity = config.getConnectivity();
        
        // 6 邻接：上下左右前后
        // 水平方向
        if (config.isPropagateHorizontal()) {
            neighbors.add(pos.north());
            neighbors.add(pos.south());
            neighbors.add(pos.east());
            neighbors.add(pos.west());
        }

        // 垂直方向
        if (config.isPropagateUp()) {
            neighbors.add(pos.up());
        }
        if (config.isPropagateDown()) {
            neighbors.add(pos.down());
        }

        // 18 邻接：加上 12 条边
        if (connectivity.ordinal() >= MagicWandConfig.Connectivity.NEIGHBOR_18.ordinal()) {
            if (config.isPropagateHorizontal()) {
                // 水平边（4个）
                neighbors.add(pos.north().east());
                neighbors.add(pos.north().west());
                neighbors.add(pos.south().east());
                neighbors.add(pos.south().west());
            }
            
            // 垂直边（8个）
            if (config.isPropagateUp() && config.isPropagateHorizontal()) {
                neighbors.add(pos.up().north());
                neighbors.add(pos.up().south());
                neighbors.add(pos.up().east());
                neighbors.add(pos.up().west());
            }
            
            if (config.isPropagateDown() && config.isPropagateHorizontal()) {
                neighbors.add(pos.down().north());
                neighbors.add(pos.down().south());
                neighbors.add(pos.down().east());
                neighbors.add(pos.down().west());
            }
        }
        
        // 26 邻接：加上 8 个对角
        if (connectivity == MagicWandConfig.Connectivity.NEIGHBOR_26) {
            if (config.isPropagateUp() && config.isPropagateHorizontal()) {
                neighbors.add(pos.up().north().east());
                neighbors.add(pos.up().north().west());
                neighbors.add(pos.up().south().east());
                neighbors.add(pos.up().south().west());
            }
            
            if (config.isPropagateDown() && config.isPropagateHorizontal()) {
                neighbors.add(pos.down().north().east());
                neighbors.add(pos.down().north().west());
                neighbors.add(pos.down().south().east());
                neighbors.add(pos.down().south().west());
            }
        }
        
        return neighbors;
    }
    
    // ==================== 匹配逻辑 ====================
    
    /**
     * 检查目标方块状态是否匹配起点（带缓存优化）
     */
    private boolean matches(BlockPos targetPos, BlockState targetState) {
        if (targetState == null || targetState.isAir()) {
            return false;
        }
        
        if (filterPipeline != null && !filterPipeline.isEmpty()) {
            if (!filterPipeline.test(world, targetPos, targetState)) {
                return false;
            }
        }
        
        Block targetBlock = targetState.getBlock();
        
        // 根据比较模式选择缓存策略
        return switch (config.getCompareMode()) {
            case SAME_BLOCK -> {
                // 使用方块缓存
                Boolean cached = blockMatchCache.get(targetBlock);
                if (cached != null) {
                    yield cached;
                }
                boolean result = targetBlock.equals(originBlock);
                blockMatchCache.put(targetBlock, result);
                yield result;
            }
                
            case SAME_BLOCKSTATE -> {
                // 使用方块状态缓存
                Boolean cached = stateMatchCache.get(targetState);
                if (cached != null) {
                    yield cached;
                }
                boolean result = targetState.equals(originState);
                stateMatchCache.put(targetState, result);
                yield result;
            }
                
            case SAME_MATERIAL -> {
                // 使用 MapColor 作为近似的材质比较（带缓存）
                Boolean cached = blockMatchCache.get(targetBlock);
                if (cached != null) {
                    yield cached;
                }
                BlockPos tempPos = new BlockPos(0, 0, 0); // 临时位置，仅用于获取 MapColor
                MapColor targetMapColor = targetState.getMapColor(world, tempPos);
                boolean result = targetMapColor == originMapColor;
                blockMatchCache.put(targetBlock, result);
                yield result;
            }
                
            case NON_AIR -> 
                true; // 已经检查过不是空气，无需缓存
                
            case BY_TAG -> 
                matchesByTag(targetState);
                
            case BY_FILTER -> 
                matchesByFilter(targetPos, targetState);
        };
    }
    
    /**
     * 按标签匹配（预留实现）
     */
    private boolean matchesByTag(BlockState targetState) {
        String tagFilter = config.getTagFilter();
        if (tagFilter == null || tagFilter.isEmpty()) {
            return targetState.getBlock().equals(originBlock);
        }
        
        try {
            net.minecraft.util.Identifier tagId = net.minecraft.util.Identifier.of(tagFilter.replace("#", ""));
            net.minecraft.registry.tag.TagKey<Block> tagKey = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, tagId);
            return targetState.isIn(tagKey);
        } catch (Exception e) {
            // 解析失败（无论是 Identifier 格式错误还是其他），降级为方块匹配
            // 不频繁打日志避免刷屏，但在调试模式下记录
            return targetState.getBlock().equals(originBlock);
        }
    }
    
    /**
     * 按自定义过滤器匹配
     */
    private boolean matchesByFilter(BlockPos targetPos, BlockState targetState) {
        var customFilter = config.getCustomFilter();
        
        // 如果没有设置自定义过滤器，回退到方块匹配
        if (customFilter == null) {
            LOGGER.debug("[MagicWandTask] 未设置自定义过滤器，回退到方块匹配");
            return targetState.getBlock().equals(originBlock);
        }
        
        try {
            return customFilter.test(world, targetPos, targetState);
        } catch (Exception e) {
            LOGGER.error("[MagicWandTask] 自定义过滤器执行失败", e);
            return false;
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 检查是否为表层方块（至少一面临空气）
     */
    private boolean isSurfaceBlock(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState neighborState = chunkCache.getBlockState(neighbor);
            if (neighborState.isAir() || !neighborState.isOpaque()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 计算曼哈顿距离
     */
    private int manhattanDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) +
               Math.abs(a.getY() - b.getY()) +
               Math.abs(a.getZ() - b.getZ());
    }
    
    // ==================== 结果记录 ====================
    
    /**
     * 任务执行结果
     * 
     * @param blocks 选中的方块集合
     * @param reachedLimit 是否达到数量上限
     * @param steps 执行的步数
     * @param cancelled 是否被取消
     * @param depthMap 方块深度映射（距离种子点的 BFS 步数）
     */
    public record MagicWandTaskResult(
        Set<BlockPos> blocks,
        boolean reachedLimit,
        int steps,
        boolean cancelled,
        Map<BlockPos, Integer> depthMap
    ) {
        public boolean isEmpty() {
            return blocks == null || blocks.isEmpty();
        }
        
        public int size() {
            return blocks != null ? blocks.size() : 0;
        }

        public boolean wasSuccessful() {
            return !cancelled && !isEmpty();
        }

        /**
         * 获取最大深度
         */
        public int getMaxDepth() {
            if (depthMap == null || depthMap.isEmpty()) {
                return 0;
            }
            return depthMap.values().stream().max(Integer::compareTo).orElse(0);
        }
        
        /**
         * 获取方块的深度（归一化到 0.0-1.0）
         */
        public float getNormalizedDepth(BlockPos pos) {
            if (depthMap == null || !depthMap.containsKey(pos)) {
                return 0.0f;
            }
            int maxDepth = getMaxDepth();
            if (maxDepth == 0) {
                return 0.0f;
            }
            return depthMap.get(pos) / (float) maxDepth;
        }
    }
}