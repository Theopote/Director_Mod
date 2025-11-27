package com.chronoblocks.core.animation.sequence.evaluator;

/**
 * 距离类型枚举
 * 
 * <p>用于距离场排序算子，定义距离计算方式
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum DistanceType {
    
    /**
     * 球形距离：欧几里得距离（三维空间距离）
     */
    SPHERICAL("球形 (Spherical)", "欧几里得距离，球形扩散"),
    
    /**
     * 柱状距离：平面距离（忽略高度差）
     */
    CYLINDRICAL("柱状 (Cylindrical)", "平面距离，柱状扩散（忽略高度）"),
    
    /**
     * 菱形距离：曼哈顿距离
     */
    MANHATTAN("菱形 (Manhattan)", "曼哈顿距离，菱形扩散");
    
    private final String displayName;
    private final String description;
    
    DistanceType(String displayName, String description) {
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

