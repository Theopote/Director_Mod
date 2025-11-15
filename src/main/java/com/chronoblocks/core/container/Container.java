package com.chronoblocks.core.container;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 容器数据模型
 * 代表一个可被时间线操控的方块集合
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class Container {

    private static final Logger LOGGER = LoggerFactory.getLogger(Container.class);
    
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
    private final BlockStore blockStore = new BlockStore();  // 方块快照存储（v5.0）
    private BoundingBox cachedBounds = null;  // 缓存的边界框（v5.0）
    
    // ========== 世界归属（v5.2 新增） ==========
    private net.minecraft.registry.RegistryKey<net.minecraft.world.World> levelKey;
    
    // ========== 容器状态（v5.0） ==========
    private ContainerState state = ContainerState.VISIBLE;  // 默认可见（v5.2修改：从HIDDEN改为VISIBLE）
    private boolean intakeEnabled = false;  // 实时接收开关
    
    // ========== 动画属性 ==========
    private float opacity = 1.0f;
    private boolean locked = false;
    private ContainerAnimationPreset defaultAnimationPreset = ContainerAnimationPreset.defaultPreset();
    
    // ========== 颜色标记 ==========
    private int color = 0xFFFFFFFF;
    
    // ========== 元数据 ==========
    private final Map<String, Object> metadata = new HashMap<>();
    private long createdTime;
    private long modifiedTime;
    
    public Container(String name) {
        this(UUID.randomUUID(), name, ContainerType.CUSTOM);
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
    
    public boolean isGroup() {
        return type == ContainerType.GROUP;
    }
    
    // ========== 层级管理 ==========
    
    public void addChild(Container child) {
        addChildAt(child, children.size());
    }

    void addChildAt(Container child, int index) {
        if (child == this) {
            throw new IllegalArgumentException("Cannot add container to itself");
        }
        if (child.parent == this && children.contains(child)) {
            // 已存在于当前容器中，仅调整顺序
            children.remove(child);
        } else {
            child.removeFromParent();
            child.parent = this;
        }

        int insertIndex = Math.max(0, Math.min(index, children.size()));
        children.add(insertIndex, child);
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
        if (isGroup()) {
            LOGGER.warn("[Container] 组 {} 不支持存储方块，忽略 addBlock", name);
            return;
        }
        if (blocks.add(pos)) {
            cachedBounds = null;  // 清除边界框缓存
            updateCenter();
            markModified();
            
            // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
            // 新添加的方块会在下次状态切换时自动处理
        }
    }
    
    public void addBlocks(Collection<BlockPos> positions) {
        if (isGroup()) {
            LOGGER.warn("[Container] 组 {} 不支持存储方块，忽略 addBlocks", name);
            return;
        }
        if (blocks.addAll(positions)) {
            cachedBounds = null;  // 清除边界框缓存
            updateCenter();
            markModified();
            
            // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
            // 新添加的方块会在下次状态切换时自动处理
        }
    }
    
    public void removeBlock(BlockPos pos) {
        if (isGroup()) {
            return;
        }
        if (blocks.remove(pos)) {
            cachedBounds = null;  // 清除边界框缓存
            blockStore.remove(pos);  // 同步移除快照
            updateCenter();
            markModified();
        }
    }
    
    public void clearBlocks() {
        if (isGroup()) {
            return;
        }
        blocks.clear();
        blockStore.clear();  // 清除快照存储
        cachedBounds = null;  // 清除边界框缓存
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
     * 计算容器的边界框（使用缓存优化）
     */
    public BoundingBox getBoundingBox() {
        if (cachedBounds != null) {
            return cachedBounds;
        }
        
        if (blocks.isEmpty()) {
            cachedBounds = new BoundingBox(BlockPos.ORIGIN, BlockPos.ORIGIN);
            return cachedBounds;
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
        
        cachedBounds = new BoundingBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        return cachedBounds;
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
        copy.defaultAnimationPreset = this.defaultAnimationPreset != null ? this.defaultAnimationPreset.copy() : null;
        copy.opacity = this.opacity;
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
        if (!(obj instanceof Container other)) return false;
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
        if (this.type != type) {
            this.type = type;
            if (isGroup()) {
                setIntakeEnabled(false);
            }
            markModified();
        }
    }
    
    public Container getParent() { return parent; }
    
    public List<Container> getChildren() { return new ArrayList<>(children); }

    List<Container> getChildrenInternal() { return children; }

    public List<Container> getChildrenView() { return Collections.unmodifiableList(children); }

    public int indexOfChild(Container child) { return children.indexOf(child); }

    public int getChildCount() { return children.size(); }
    
    public Set<BlockPos> getBlocks() { return new HashSet<>(blocks); }
    
    public BlockPos getCenter() { return center; }
    public void setCenter(BlockPos center) { 
        this.center = center; 
        markModified();
    }
    
    public ContainerAnimationPreset getDefaultAnimationPreset() {
        if (defaultAnimationPreset == null) {
            defaultAnimationPreset = ContainerAnimationPreset.defaultPreset();
        }
        return defaultAnimationPreset;
    }

    public void setDefaultAnimationPreset(ContainerAnimationPreset preset) {
        this.defaultAnimationPreset = preset != null ? preset.copy() : ContainerAnimationPreset.defaultPreset();
        markModified();
    }
    
    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { 
        this.opacity = Math.max(0f, Math.min(1f, opacity)); 
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
    
    // ========== 世界归属（v5.2） ==========
    
    public RegistryKey<World> getLevelKey() {
        return levelKey;
    }
    
    public void setLevelKey(RegistryKey<World> levelKey) {
        this.levelKey = levelKey;
        markModified();
    }
    
    // ========== 容器状态（v5.0） ==========
    
    public ContainerState getState() { return state; }
    public void setState(ContainerState state) {
        if (this.state != state) {
            ContainerState oldState = this.state;
            this.state = state;
            markModified();
            
            LOGGER.debug("[Container] 容器 {} 状态变更: {} -> {}，方块数量: {}", 
                name, oldState.getDisplayName(), state.getDisplayName(), getBlockCount());
            
            // 新的可见性系统（v5.2）：使用ContainerVisibilityService
            try {
                var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();
                if (visibilityService != null) {
                    if (state == ContainerState.HIDDEN) {
                        visibilityService.hideContainer(this);
                    } else {
                        visibilityService.showContainer(this);
                    }
                } else {
                    LOGGER.warn("[Container] ContainerVisibilityService未初始化，无法切换可见性");
                }
            } catch (Exception e) {
                LOGGER.error("[Container] 切换容器可见性失败: {}", e.getMessage(), e);
            }
        }
    }
    
    public boolean isHidden() { return state == ContainerState.HIDDEN; }
    public boolean isStateVisible() { return state == ContainerState.VISIBLE; }
    
    public void toggleState() {
        setState(state.toggle());
    }
    
    public boolean isIntakeEnabled() { return intakeEnabled; }
    public void setIntakeEnabled(boolean enabled) {
        if (isGroup()) {
            enabled = false;
        }
        if (this.intakeEnabled != enabled) {
            this.intakeEnabled = enabled;
            markModified();
        }
    }
    
    public BlockStore getBlockStore() { return blockStore; }
    
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

