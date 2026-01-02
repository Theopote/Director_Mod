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
    SPIRAL("螺旋", "方块以螺旋形式出现"),
    
    /**
     * 坠落效果（新增）
     */
    FALL_DOWN("坠落", "方块从上方坠落，模拟重力效果"),
    
    /**
     * 升起效果（新增）
     */
    RISE_UP("升起", "方块从下方升起，模拟地形隆起"),
    
    /**
     * 飞入效果（新增）
     */
    FLY_IN("飞入", "方块从指定方向飞入，适合科技感动画"),
    
    /**
     * 碎片聚合效果（新增）
     */
    ASSEMBLE("碎片聚合", "方块从周围聚拢，适合组装动画"),
    
    /**
     * 震动归位效果（新增）
     */
    SHAKER("震动归位", "方块震动后归位，增加落地感"),
    
    /**
     * 气泡弹出效果（新增）
     * 缩放动画：从 0 放大到 1，带有弹性回弹效果，适合卡通风格
     */
    POP_IN("气泡弹出", "方块由小变大弹出，带有弹性回弹，适合卡通风格"),
    
    /**
     * 皱缩消失效果（新增）
     * 缩放动画：从 1 缩小到 0 并消失
     */
    SHRINK_OUT("皱缩消失", "方块迅速缩小直至消失"),
    
    /**
     * 重力坍塌效果（新增）
     * 物理动画：模拟方块受重力影响向下掉落并消失
     */
    GRAVITY_FALL("重力坍塌", "方块受重力影响向下掉落并消失"),
    
    /**
     * 向上飞离效果（新增）
     * 方块向指定方向飞走消失，对应 FLY_IN 的退出版本
     */
    FLY_OUT("向上飞离", "方块向指定方向飞走消失");
    
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

