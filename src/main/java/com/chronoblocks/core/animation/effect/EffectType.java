package com.chronoblocks.core.animation.effect;

/**
 * 特效类型枚举
 * 
 * <p>定义了 ChronoBlocks 支持的所有方块特效类型。
 * 特效系统用于在方块出现过程中添加额外的视觉/听觉装饰层，
 * 不改变主动画的位移/旋转/缩放，只提供装饰效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum EffectType {
    
    /**
     * 出场闪白光 - 方块出现瞬间闪白光，适合科技/传送风格
     */
    FLASH_WHITE("出场闪白光", "在方块出现瞬间闪一下白光，适合科技/传送风格"),
    
    /**
     * 尘土爆裂 - 方块落地时喷出少量尘土/碎屑粒子
     */
    DUST_BURST("尘土爆裂", "方块落地时喷出少量尘土/碎屑粒子"),
    
    /**
     * 魔法光焰 - 环绕方块的小型魔法粒子光焰
     */
    MAGIC_FLARE("魔法光焰", "环绕方块的小型魔法粒子光焰"),
    
    /**
     * 脉冲发光 - 方块出现时短暂发光并渐渐恢复正常
     */
    GLOW_PULSE("脉冲发光", "方块出现时短暂发光并渐渐恢复正常");
    
    private final String displayName;
    private final String description;
    
    EffectType(String displayName, String description) {
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

