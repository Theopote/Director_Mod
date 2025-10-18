package com.chronoblocks.core.container;

/**
 * 动画模式枚举
 * 定义容器的出现/消失方式
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public enum AnimationMode {
    /**
     * 瞬间出现/消失
     */
    INSTANT("瞬间", "立即显示所有方块"),
    
    /**
     * 从底部向上生长
     */
    GROW_UP("向上生长", "方块从底部逐层向上出现"),
    
    /**
     * 从顶部向下生长
     */
    GROW_DOWN("向下生长", "方块从顶部逐层向下出现"),
    
    /**
     * 从中心向外扩散
     */
    EXPAND("扩散", "方块从中心向外扩散出现"),
    
    /**
     * 淡入效果
     */
    FADE_IN("淡入", "方块逐渐变得不透明"),
    
    /**
     * 淡出效果
     */
    FADE_OUT("淡出", "方块逐渐变得透明"),
    
    /**
     * 爆炸效果
     */
    EXPLODE("爆炸", "方块从中心向外爆炸"),
    
    /**
     * 收缩效果
     */
    IMPLODE("收缩", "方块从外向中心收缩"),
    
    /**
     * 按层切片
     */
    SLICE("切片", "按照层次逐层显示"),
    
    /**
     * 随机出现
     */
    RANDOM("随机", "方块随机顺序出现"),
    
    /**
     * 波浪效果
     */
    WAVE("波浪", "方块以波浪形式出现"),
    
    /**
     * 螺旋效果
     */
    SPIRAL("螺旋", "方块以螺旋形式出现");
    
    private final String displayName;
    private final String description;
    
    AnimationMode(String displayName, String description) {
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

