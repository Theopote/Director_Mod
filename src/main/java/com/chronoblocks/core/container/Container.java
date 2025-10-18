package com.chronoblocks.core.container;

import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * 容器数据模型
 * 代表一个可被时间线操控的方块集合
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class Container {
    
    // ========== 基础属性 ==========
    private UUID id;
    private String name;
    private ContainerType type;
    
    // ========== 层级结构 ==========
    private Container parent;
    private final List<Container> children = new ArrayList<>();
    
    // ========== 方块数据 ==========
    private final Set<BlockPos> blocks = new HashSet<>();
    private BlockPos center;  // 中心点（用于变换）
    
    // ========== 动画属性 ==========
    private AnimationMode animationMode = AnimationMode.INSTANT;
    private float opacity = 1.0f;
    private boolean visible = true;
    private boolean locked = false;
    
    // ========== 颜色标记 ==========
    private int color = 0xFFFFFFFF;
    
    // ========== 元数据 ==========
    private final Map<String, Object> metadata = new HashMap<>();
    private long createdTime;
    private long modifiedTime;
    
    public Container(String name) {
        this(UUID.randomUUID(), name, ContainerType.GROUP);
    }
    
    public Container(String name, ContainerType type) {
        this(UUID.randomUUID(), name, type);
    }
    
    public Container(UUID id, String name, ContainerType type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.createdTime = System.currentTimeMillis();
        this.modifiedTime = this.createdTime;
    }
    
    // ========== 层级管理 ==========
    
    public void addChild(Container child) {
        if (child == this) {
            throw new IllegalArgumentException("Cannot add container to itself");
        }
        if (children.contains(child)) {
            return;
        }
        children.add(child);
        child.parent = this;
        markModified();
    }
    
    public void removeChild(Container child) {
        if (children.remove(child)) {
            child.parent = null;
            markModified();
        }
    }
    
    public void removeFromParent() {
        if (parent != null) {
            parent.removeChild(this);
        }
    }
    
    public boolean hasChildren() {
        return !children.isEmpty();
    }
    
    public int getDepth() {
        int depth = 0;
        Container p = parent;
        while (p != null) {
            depth++;
            p = p.parent;
        }
        return depth;
    }
    
    public boolean isDescendantOf(Container other) {
        Container p = parent;
        while (p != null) {
            if (p == other) return true;
            p = p.parent;
        }
        return false;
    }
    
    // ========== 方块管理 ==========
    
    public void addBlock(BlockPos pos) {
        if (blocks.add(pos)) {
            updateCenter();
            markModified();
        }
    }
    
    public void addBlocks(Collection<BlockPos> positions) {
        if (blocks.addAll(positions)) {
            updateCenter();
            markModified();
        }
    }
    
    public void removeBlock(BlockPos pos) {
        if (blocks.remove(pos)) {
            updateCenter();
            markModified();
        }
    }
    
    public void clearBlocks() {
        blocks.clear();
        center = null;
        markModified();
    }
    
    public boolean containsBlock(BlockPos pos) {
        return blocks.contains(pos);
    }
    
    public int getBlockCount() {
        return blocks.size();
    }
    
    /**
     * 获取包括所有子容器的总方块数
     */
    public int getTotalBlockCount() {
        int count = blocks.size();
        for (Container child : children) {
            count += child.getTotalBlockCount();
        }
        return count;
    }
    
    /**
     * 计算容器的边界框
     */
    public BoundingBox getBoundingBox() {
        if (blocks.isEmpty()) {
            return new BoundingBox(BlockPos.ORIGIN, BlockPos.ORIGIN);
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        return new BoundingBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }
    
    /**
     * 更新中心点
     */
    private void updateCenter() {
        if (blocks.isEmpty()) {
            center = null;
            return;
        }
        
        long sumX = 0, sumY = 0, sumZ = 0;
        for (BlockPos pos : blocks) {
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
        }
        
        int count = blocks.size();
        center = new BlockPos((int)(sumX / count), (int)(sumY / count), (int)(sumZ / count));
    }
    
    // ========== 元数据 ==========
    
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
        markModified();
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public Object getMetadata(String key, Object defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }
    
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }
    
    public void removeMetadata(String key) {
        metadata.remove(key);
        markModified();
    }
    
    // ========== 工具方法 ==========
    
    private void markModified() {
        this.modifiedTime = System.currentTimeMillis();
    }
    
    public Container clone() {
        Container copy = new Container(UUID.randomUUID(), name + " (副本)", type);
        copy.blocks.addAll(this.blocks);
        copy.center = this.center;
        copy.animationMode = this.animationMode;
        copy.opacity = this.opacity;
        copy.visible = this.visible;
        copy.locked = this.locked;
        copy.color = this.color;
        copy.metadata.putAll(this.metadata);
        
        // 递归克隆子容器
        for (Container child : children) {
            copy.addChild(child.clone());
        }
        
        return copy;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Container)) return false;
        Container other = (Container) obj;
        return id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("Container[%s, blocks=%d, children=%d]", 
            name, blocks.size(), children.size());
    }
    
    // ========== Getters & Setters ==========
    
    public UUID getId() { return id; }
    
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name; 
        markModified();
    }
    
    public ContainerType getType() { return type; }
    public void setType(ContainerType type) { 
        this.type = type; 
        markModified();
    }
    
    public Container getParent() { return parent; }
    
    public List<Container> getChildren() { return new ArrayList<>(children); }
    
    public Set<BlockPos> getBlocks() { return new HashSet<>(blocks); }
    
    public BlockPos getCenter() { return center; }
    public void setCenter(BlockPos center) { 
        this.center = center; 
        markModified();
    }
    
    public AnimationMode getAnimationMode() { return animationMode; }
    public void setAnimationMode(AnimationMode mode) { 
        this.animationMode = mode; 
        markModified();
    }
    
    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { 
        this.opacity = Math.max(0f, Math.min(1f, opacity)); 
        markModified();
    }
    
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { 
        this.visible = visible; 
        markModified();
    }
    
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { 
        this.locked = locked; 
        markModified();
    }
    
    public int getColor() { return color; }
    public void setColor(int color) { 
        this.color = color; 
        markModified();
    }
    
    public long getCreatedTime() { return createdTime; }
    public long getModifiedTime() { return modifiedTime; }
    
    // ========== 内部类 ==========
    
    public static class BoundingBox {
        private final BlockPos min;
        private final BlockPos max;
        
        public BoundingBox(BlockPos min, BlockPos max) {
            this.min = min;
            this.max = max;
        }
        
        public BlockPos getMin() { return min; }
        public BlockPos getMax() { return max; }
        
        public int getWidth() { return max.getX() - min.getX() + 1; }
        public int getHeight() { return max.getY() - min.getY() + 1; }
        public int getDepth() { return max.getZ() - min.getZ() + 1; }
        
        public int getVolume() { return getWidth() * getHeight() * getDepth(); }
        
        public BlockPos getCenter() {
            return new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
            );
        }
        
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }
}

