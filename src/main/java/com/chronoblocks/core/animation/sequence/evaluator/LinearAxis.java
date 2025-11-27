package com.chronoblocks.core.animation.sequence.evaluator;

/**
 * 线性轴方向枚举
 * 
 * <p>用于线性投影排序算子，定义扫描方向
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum LinearAxis {
    
    X("X", "沿 X 轴正方向"),
    MINUS_X("-X", "沿 X 轴负方向"),
    Y("Y", "沿 Y 轴正方向（向上）"),
    MINUS_Y("-Y", "沿 Y 轴负方向（向下）"),
    Z("Z", "沿 Z 轴正方向"),
    MINUS_Z("-Z", "沿 Z 轴负方向"),
    XZ("XZ", "沿 XZ 对角线"),
    XY("XY", "沿 XY 对角线"),
    YZ("YZ", "沿 YZ 对角线"),
    XYZ("XYZ", "沿 XYZ 对角线");
    
    private final String displayName;
    private final String description;
    
    LinearAxis(String displayName, String description) {
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
    
    /**
     * 获取轴向量字符串（用于传递给排序算子）
     */
    public String toAxisString() {
        return switch (this) {
            case X -> "X";
            case MINUS_X -> "-X";
            case Y -> "Y";
            case MINUS_Y -> "-Y";
            case Z -> "Z";
            case MINUS_Z -> "-Z";
            case XZ -> "XZ";
            case XY -> "XY";
            case YZ -> "YZ";
            case XYZ -> "XYZ";
        };
    }
}

