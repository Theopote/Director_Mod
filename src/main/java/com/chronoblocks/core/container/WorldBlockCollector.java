package com.chronoblocks.core.container;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 世界方块采集器
 * 从 Minecraft 世界中选取方块区域并添加到容器
 * 
 * 采集模式：
 * - SINGLE：单个方块
 * - RAYCAST：射线追踪选择
 * - BOX：框选区域
 * - FLOOD_FILL：洪填充（相同方块类型）
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class WorldBlockCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldBlockCollector.class);
    
    private final MinecraftClient client;
    
    // ========== 采集模式 ==========
    private CollectionMode mode = CollectionMode.RAYCAST;
    
    // ========== 框选模式状态 ==========
    private BlockPos selectionStart = null;
    private BlockPos selectionEnd = null;
    private boolean isSelecting = false;
    
    // ========== 限制 ==========
    private int maxBlocksPerCollection = 10000;  // 防止选择过多方块
    
    public WorldBlockCollector() {
        this.client = MinecraftClient.getInstance();
    }
    
    // ========== 主要采集方法 ==========
    
    /**
     * 采集方块到容器
     * 
     * @param container 目标容器
     * @return 采集的方块数量
     */
    public int collectBlocksToContainer(Container container) {
        if (container == null) {
            LOGGER.warn("[Collector] 容器为空，无法采集");
            return 0;
        }
        
        Set<BlockPos> blocks = collectBlocks();
        
        if (blocks.isEmpty()) {
            LOGGER.warn("[Collector] 未找到可采集的方块");
            return 0;
        }
        
        container.addBlocks(blocks);
        LOGGER.info("[Collector] 采集 {} 个方块到容器: {}", blocks.size(), container.getName());
        
        return blocks.size();
    }
    
    /**
     * 根据当前模式采集方块
     */
    public Set<BlockPos> collectBlocks() {
        return switch (mode) {
            case SINGLE -> collectSingleBlock();
            case RAYCAST -> collectRaycastBlock();
            case BOX -> collectBoxSelection();
            case FLOOD_FILL -> collectFloodFill();
        };
    }
    
    // ========== 采集模式实现 ==========
    
    /**
     * 采集单个方块
     */
    private Set<BlockPos> collectSingleBlock() {
        Set<BlockPos> blocks = new HashSet<>();
        
        BlockPos target = getTargetBlock();
        if (target != null) {
            blocks.add(target);
            LOGGER.debug("[Collector] 采集单个方块: {}", target);
        }
        
        return blocks;
    }
    
    /**
     * 射线追踪采集
     */
    private Set<BlockPos> collectRaycastBlock() {
        Set<BlockPos> blocks = new HashSet<>();
        
        BlockPos target = getTargetBlock();
        if (target != null) {
            blocks.add(target);
            LOGGER.debug("[Collector] 射线追踪方块: {}", target);
        }
        
        return blocks;
    }
    
    /**
     * 框选区域采集
     */
    private Set<BlockPos> collectBoxSelection() {
        Set<BlockPos> blocks = new HashSet<>();
        
        if (selectionStart == null || selectionEnd == null) {
            LOGGER.warn("[Collector] 框选未完成，需要设置起点和终点");
            return blocks;
        }
        
        int minX = Math.min(selectionStart.getX(), selectionEnd.getX());
        int minY = Math.min(selectionStart.getY(), selectionEnd.getY());
        int minZ = Math.min(selectionStart.getZ(), selectionEnd.getZ());
        
        int maxX = Math.max(selectionStart.getX(), selectionEnd.getX());
        int maxY = Math.max(selectionStart.getY(), selectionEnd.getY());
        int maxZ = Math.max(selectionStart.getZ(), selectionEnd.getZ());
        
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (count >= maxBlocksPerCollection) {
                        LOGGER.warn("[Collector] 达到最大方块数限制: {}", maxBlocksPerCollection);
                        return blocks;
                    }
                    
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isValidBlock(pos)) {
                        blocks.add(pos);
                        count++;
                    }
                }
            }
        }
        
        LOGGER.info("[Collector] 框选采集: {} 个方块", blocks.size());
        return blocks;
    }
    
    /**
     * 洪填充采集（相同类型方块）
     */
    private Set<BlockPos> collectFloodFill() {
        Set<BlockPos> blocks = new HashSet<>();
        
        BlockPos start = getTargetBlock();
        if (start == null || !isValidBlock(start)) {
            return blocks;
        }
        
        World world = client.world;
        if (world == null) return blocks;
        
        var startState = world.getBlockState(start);
        var toVisit = new java.util.LinkedList<BlockPos>();
        var visited = new HashSet<BlockPos>();
        
        toVisit.add(start);
        visited.add(start);
        
        while (!toVisit.isEmpty() && blocks.size() < maxBlocksPerCollection) {
            BlockPos current = toVisit.poll();
            blocks.add(current);
            
            // 检查6个方向的邻居
            for (var dir : new BlockPos[]{
                current.north(), current.south(),
                current.east(), current.west(),
                current.up(), current.down()
            }) {
                if (!visited.contains(dir) && isValidBlock(dir)) {
                    var state = world.getBlockState(dir);
                    if (state.getBlock() == startState.getBlock()) {
                        visited.add(dir);
                        toVisit.add(dir);
                    }
                }
            }
        }
        
        LOGGER.info("[Collector] 洪填充采集: {} 个方块", blocks.size());
        return blocks;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 获取玩家瞄准的方块
     */
    private BlockPos getTargetBlock() {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        
        BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
        return hit.getBlockPos();
    }
    
    /**
     * 检查方块是否有效（非空气）
     */
    private boolean isValidBlock(BlockPos pos) {
        World world = client.world;
        if (world == null) return false;
        
        var state = world.getBlockState(pos);
        return !state.isAir();
    }
    
    // ========== 框选模式控制 ==========
    
    /**
     * 开始框选
     */
    public void startBoxSelection() {
        selectionStart = getTargetBlock();
        if (selectionStart != null) {
            isSelecting = true;
            LOGGER.info("[Collector] 框选起点: {}", selectionStart);
        }
    }
    
    /**
     * 完成框选
     */
    public void finishBoxSelection() {
        selectionEnd = getTargetBlock();
        if (selectionEnd != null) {
            isSelecting = false;
            LOGGER.info("[Collector] 框选终点: {}", selectionEnd);
        }
    }
    
    /**
     * 取消框选
     */
    public void cancelBoxSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
        LOGGER.info("[Collector] 取消框选");
    }
    
    /**
     * 清除选区
     */
    public void clearSelection() {
        selectionStart = null;
        selectionEnd = null;
        LOGGER.info("[Collector] 清除选区");
    }
    
    // ========== Getters & Setters ==========
    
    public CollectionMode getMode() {
        return mode;
    }
    
    public void setMode(CollectionMode mode) {
        this.mode = mode;
        LOGGER.info("[Collector] 切换采集模式: {}", mode);
    }
    
    public BlockPos getSelectionStart() {
        return selectionStart;
    }
    
    public BlockPos getSelectionEnd() {
        return selectionEnd;
    }
    
    public boolean isSelecting() {
        return isSelecting;
    }
    
    public int getMaxBlocksPerCollection() {
        return maxBlocksPerCollection;
    }
    
    public void setMaxBlocksPerCollection(int max) {
        this.maxBlocksPerCollection = max;
    }
    
    // ========== 采集模式枚举 ==========
    
    public enum CollectionMode {
        /**
         * 单个方块
         */
        SINGLE("单个方块", "选择单个瞄准的方块"),
        
        /**
         * 射线追踪
         */
        RAYCAST("射线追踪", "选择准星瞄准的方块"),
        
        /**
         * 框选区域
         */
        BOX("框选区域", "选择两点之间的立方体区域"),
        
        /**
         * 洪填充
         */
        FLOOD_FILL("洪填充", "选择相同类型的连续方块");
        
        private final String displayName;
        private final String description;
        
        CollectionMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
}

