package com.chronoblocks.core.selection.tools.magicwandtool;

import com.chronoblocks.core.selection.block_filters.BlockFilter;

/**
 * 🪄 MagicWandConfig - 魔棒工具配置
 * <p>
 * 定义魔棒工具的所有参数配置，包括：
 * - 匹配模式（方块类型、状态、材质、标签等）
 * - 连通性（6/18/26邻接）
 * - 传播控制（方向、范围、表层等）
 * - 性能限制（最大方块数）
 * - 高级功能（限定在当前选区内、多种子等）
 * 
 * @author ChronoBlocks Team
 * @version 2.0 Enhanced
 */
public final class MagicWandConfig {
    
    /**
     * 比较模式 - 决定哪些方块被认为是"相同"的
     */
    public enum CompareMode {
        /** 相同方块类型（忽略属性），如所有楼梯 */
        SAME_BLOCK("相同方块", "忽略方块状态属性"),
        
        /** 完全相同的方块状态（包含所有属性） */
        SAME_BLOCKSTATE("相同方块状态", "方块类型和所有属性完全一致"),
        
        /** 相同材质（石头、木头等） */
        SAME_MATERIAL("相同材质", "相同材质类型的方块"),
        
        /** 所有非空气方块 */
        NON_AIR("所有非空气", "选择所有非空气方块"),
        
        /** 按标签匹配（例如 #minecraft:logs） */
        BY_TAG("按标签匹配", "使用方块标签进行匹配"),
        
        /** 使用高级过滤器（与 TypeFilterTool 联动） */
        BY_FILTER("高级过滤", "使用自定义过滤规则");
        
        private final String displayName;
        private final String description;
        
        CompareMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 连通性模式 - 决定哪些方块被认为是"相邻"的
     */
    public enum Connectivity {
        /** 6邻接：上下左右前后（面接触） */
        NEIGHBOR_6("6邻接", "面接触", 6),
        
        /** 18邻接：6面 + 12边 */
        NEIGHBOR_18("18邻接", "面+边接触", 18),
        
        /** 26邻接：6面 + 12边 + 8角（全对角） */
        NEIGHBOR_26("26邻接", "包含对角线", 26);
        
        private final String displayName;
        private final String description;
        private final int count;
        
        Connectivity(String displayName, String description, int count) {
            this.displayName = displayName;
            this.description = description;
            this.count = count;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    // ==================== 基础配置 ====================
    
    /** 比较模式 */
    private CompareMode compareMode = CompareMode.SAME_BLOCK;
    
    /** 连通性模式 */
    private Connectivity connectivity = Connectivity.NEIGHBOR_6;
    
    /** 最大选择方块数（防止性能问题） */
    private int maxBlocks = 100_000;
    
    // ==================== 传播控制 ====================
    
    /** 扩散范围（类似 Axiom 的 Range，控制最大传播步数） */
    private int range = -1; // -1 表示无限制
    
    /** 只选择表层方块（至少一面临空气/外部） */
    private boolean surfaceOnly = false;
    
    /** 允许向上传播 */
    private boolean propagateUp = true;
    
    /** 允许向下传播 */
    private boolean propagateDown = true;
    
    /** 允许水平传播 */
    private boolean propagateHorizontal = true;
    
    /** 允许对角传播（仅在 NEIGHBOR_18/26 时有效） */
    private boolean propagateCorners = true;
    
    // ==================== 高级功能 ====================
    
    /** 只在当前选区内部进行泛洪填充 */
    private boolean limitToCurrentSelection = false;
    
    /** 多种子模式：允许同时从多个起点开始填充 */
    private boolean multiSeedMode = false;
    
    /** 权重模式：基于距离种子的步数进行加权选择 */
    private boolean weightedMode = false;
    
    /** 最大权重步数（仅在 weightedMode=true 时有效） */
    private int maxWeightSteps = 10;
    
    // ==================== 标签与过滤器配置 ====================
    
    /** 标签过滤器（当 compareMode = BY_TAG 时使用） */
    private String tagFilter = "";
    
    /** 自定义过滤器ID（当 compareMode = BY_FILTER 时使用） */
    private String customFilterId = "";
    
    /** 自定义过滤器实例（当 compareMode = BY_FILTER 时使用） */
    private BlockFilter customFilter = null;
    
    // ==================== Getters & Setters ====================
    
    public CompareMode getCompareMode() {
        return compareMode;
    }
    
    public void setCompareMode(CompareMode compareMode) {
        this.compareMode = compareMode;
    }
    
    public Connectivity getConnectivity() {
        return connectivity;
    }
    
    public void setConnectivity(Connectivity connectivity) {
        this.connectivity = connectivity;
    }
    
    public int getMaxBlocks() {
        return maxBlocks;
    }
    
    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = Math.max(1, Math.min(maxBlocks, 1_000_000));
    }
    
    public int getRange() {
        return range;
    }
    
    public void setRange(int range) {
        this.range = range;
    }
    
    public boolean isSurfaceOnly() {
        return surfaceOnly;
    }
    
    public void setSurfaceOnly(boolean surfaceOnly) {
        this.surfaceOnly = surfaceOnly;
    }
    
    public boolean isPropagateUp() {
        return propagateUp;
    }
    
    public void setPropagateUp(boolean propagateUp) {
        this.propagateUp = propagateUp;
    }
    
    public boolean isPropagateDown() {
        return propagateDown;
    }
    
    public void setPropagateDown(boolean propagateDown) {
        this.propagateDown = propagateDown;
    }
    
    public boolean isPropagateHorizontal() {
        return propagateHorizontal;
    }
    
    public void setPropagateHorizontal(boolean propagateHorizontal) {
        this.propagateHorizontal = propagateHorizontal;
    }
    
    public boolean isPropagateCorners() {
        return propagateCorners;
    }
    
    public void setPropagateCorners(boolean propagateCorners) {
        this.propagateCorners = propagateCorners;
    }
    
    public boolean isLimitToCurrentSelection() {
        return limitToCurrentSelection;
    }
    
    public void setLimitToCurrentSelection(boolean limitToCurrentSelection) {
        this.limitToCurrentSelection = limitToCurrentSelection;
    }
    
    public boolean isMultiSeedMode() {
        return multiSeedMode;
    }
    
    public void setMultiSeedMode(boolean multiSeedMode) {
        this.multiSeedMode = multiSeedMode;
    }
    
    public boolean isWeightedMode() {
        return weightedMode;
    }
    
    public void setWeightedMode(boolean weightedMode) {
        this.weightedMode = weightedMode;
    }
    
    public int getMaxWeightSteps() {
        return maxWeightSteps;
    }
    
    public void setMaxWeightSteps(int maxWeightSteps) {
        this.maxWeightSteps = Math.max(1, maxWeightSteps);
    }
    
    public String getTagFilter() {
        return tagFilter;
    }
    
    public void setTagFilter(String tagFilter) {
        this.tagFilter = tagFilter != null ? tagFilter : "";
    }
    
    public String getCustomFilterId() {
        return customFilterId;
    }
    
    public void setCustomFilterId(String customFilterId) {
        this.customFilterId = customFilterId != null ? customFilterId : "";
    }
    
    public BlockFilter getCustomFilter() {
        return customFilter;
    }
    
    public void setCustomFilter(BlockFilter customFilter) {
        this.customFilter = customFilter;
    }
    
    /**
     * 创建默认配置
     */
    public static MagicWandConfig createDefault() {
        return new MagicWandConfig();
    }
    
    /**
     * 克隆当前配置
     */
    public MagicWandConfig clone() {
        MagicWandConfig config = new MagicWandConfig();
        config.compareMode = this.compareMode;
        config.connectivity = this.connectivity;
        config.maxBlocks = this.maxBlocks;
        config.range = this.range;
        config.surfaceOnly = this.surfaceOnly;
        config.propagateUp = this.propagateUp;
        config.propagateDown = this.propagateDown;
        config.propagateHorizontal = this.propagateHorizontal;
        config.propagateCorners = this.propagateCorners;
        config.limitToCurrentSelection = this.limitToCurrentSelection;
        config.multiSeedMode = this.multiSeedMode;
        config.weightedMode = this.weightedMode;
        config.maxWeightSteps = this.maxWeightSteps;
        config.tagFilter = this.tagFilter;
        config.customFilterId = this.customFilterId;
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("MagicWandConfig[mode=%s, connectivity=%s, max=%d, range=%d]",
            compareMode, connectivity, maxBlocks, range);
    }
}

