package com.chronoblocks.core.selection;

import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * 🎯 SelectionState - 选择状态统一模型
 * 
 * 能容纳：单方块、框选体（AABB）、集合、过滤器（材质/状态）、标注颜色与来源工具（用于高亮）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionState {
    
    // ========== 选择数据 ==========
    private final Set<BlockPos> selectedBlocks = new HashSet<>();
    private final Map<BlockPos, SelectionMetadata> blockMetadata = new HashMap<>();
    
    // ========== 选择属性 ==========
    private final String sourceToolId;
    private int highlightColor = 0xFFFF0000; // 默认红色
    private String name = "Selection";

    public SelectionState(String sourceToolId) {
        this.sourceToolId = sourceToolId;
        long timestamp = System.currentTimeMillis();
    }
    
    // ========== 静态工厂方法 ==========
    
    public static SelectionState empty() {
        return new SelectionState("empty");
    }

    public static SelectionState box(BlockPos start, BlockPos end, String toolId) {
        SelectionState state = new SelectionState(toolId);
        state.addBoxSelection(start, end);
        return state;
    }
    
    public static SelectionState of(Set<BlockPos> blocks) {
        SelectionState state = new SelectionState("unknown");
        // 创建副本以避免并发修改异常
        synchronized (blocks) {
            state.selectedBlocks.addAll(new HashSet<>(blocks));
        }
        return state;
    }
    
    public static SelectionState of(Set<BlockPos> blocks, String toolId) {
        SelectionState state = new SelectionState(toolId);
        // 创建副本以避免并发修改异常
        synchronized (blocks) {
            state.selectedBlocks.addAll(new HashSet<>(blocks));
        }
        return state;
    }
    
    // ========== 方块管理 ==========
    
    public void addBlock(BlockPos pos) {
        selectedBlocks.add(pos);
        blockMetadata.put(pos, new SelectionMetadata());
    }

    public void addBlocks(Collection<BlockPos> blocks) {
        selectedBlocks.addAll(blocks);
        for (BlockPos pos : blocks) {
            blockMetadata.putIfAbsent(pos, new SelectionMetadata());
        }
    }

    public void clear() {
        selectedBlocks.clear();
        blockMetadata.clear();
    }
    
    public void addBoxSelection(BlockPos start, BlockPos end) {
        int minX = Math.min(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        
        int maxX = Math.max(start.getX(), end.getX());
        int maxY = Math.max(start.getY(), end.getY());
        int maxZ = Math.max(start.getZ(), end.getZ());
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    addBlock(new BlockPos(x, y, z));
                }
            }
        }
    }
    
    // ========== 查询方法 ==========
    
    public boolean contains(BlockPos pos) {
        return selectedBlocks.contains(pos);
    }
    
    public boolean isEmpty() {
        return selectedBlocks.isEmpty();
    }
    
    public int size() {
        return selectedBlocks.size();
    }
    
    public Set<BlockPos> getSelectedBlocks() {
        return new HashSet<>(selectedBlocks);
    }

    // ========== 边界框计算 ==========
    
    public Optional<BoundingBox> getBoundingBox() {
        if (selectedBlocks.isEmpty()) {
            return Optional.empty();
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : selectedBlocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        return Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }
    
    // ========== Getters & Setters ==========
    
    public String getSourceToolId() {
        return sourceToolId;
    }

    public void setHighlightColor(int color) {
        this.highlightColor = color;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    // ========== 内部类 ==========
    
    /**
     * 选择元数据
     */
    public static class SelectionMetadata {

        public SelectionMetadata() {
            long selectedAt = System.currentTimeMillis();
        }
        
        public int getPriority() {
            return 0; }
    }
    
    /**
     * 边界框
     */
    public static class BoundingBox {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        
        public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        
        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
        
        public int getWidth() { return maxX - minX + 1; }
        public int getHeight() { return maxY - minY + 1; }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX &&
                   pos.getY() >= minY && pos.getY() <= maxY &&
                   pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}