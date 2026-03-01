package com.chronoblocks.core.animation.easing;

/**
 * 缓动函数枚举
 * 
 * <p>定义各种缓动曲线，用于将线性进度转换为平滑的动画效果。
 * 所有函数接受 t (0.0 - 1.0) 并返回缓动后的值 (0.0 - 1.0)。
 * 
 * <p>参考：https://easings.net/
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum EasingFunction {
    
    /**
     * 线性：无缓动
     */
    LINEAR("线性", "无缓动，匀速运动"),
    
    /**
     * 缓入：开始慢，结束快
     */
    EASE_IN_QUAD("缓入（二次）", "开始慢，逐渐加速"),
    EASE_IN_CUBIC("缓入（三次）", "开始很慢，逐渐加速"),
    
    /**
     * 缓出：开始快，结束慢
     */
    EASE_OUT_QUAD("缓出（二次）", "开始快，逐渐减速"),
    EASE_OUT_CUBIC("缓出（三次）", "开始很快，逐渐减速"),
    EASE_OUT_QUART("缓出（四次）", "开始快，后段更柔和地减速"),
    EASE_OUT_QUINT("缓出（五次）", "开始快，后段非常柔和地减速"),
    EASE_OUT_BOUNCE("缓出（弹跳）", "结束时弹跳，适合坠落动画"),
    EASE_OUT_BACK("缓出（回弹）", "结束时略微超过目标再回来"),
    EASE_OUT_ELASTIC("缓出（弹性）", "结束时弹性振荡，适合生长动画"),
    
    /**
     * 缓入缓出：开始和结束都慢
     */
    EASE_IN_OUT_QUAD("缓入缓出（二次）", "开始和结束都慢，中间快"),
    EASE_IN_OUT_CUBIC("缓入缓出（三次）", "开始和结束都很慢，中间快");
    
    private final String displayName;
    private final String description;
    
    EasingFunction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    // ✅ 优化：预构建查找表，避免在热路径上使用 valueOf() + catch 异常
    private static final java.util.Map<String, EasingFunction> LOOKUP_BY_NAME;
    private static final java.util.Map<String, EasingFunction> LOOKUP_BY_DISPLAY_NAME;

    static {
        EasingFunction[] vals = values();
        LOOKUP_BY_NAME = new java.util.HashMap<>(vals.length * 2);
        LOOKUP_BY_DISPLAY_NAME = new java.util.HashMap<>(vals.length * 2);
        for (EasingFunction func : vals) {
            LOOKUP_BY_NAME.put(func.name(), func);
            LOOKUP_BY_NAME.put(func.name().toLowerCase(), func);
            LOOKUP_BY_DISPLAY_NAME.put(func.displayName, func);
        }
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 应用缓动函数
     * 
     * @param t 输入值 (0.0 - 1.0)
     * @return 缓动后的值 (0.0 - 1.0)
     */
    public float apply(float t) {
        // 限制输入范围
        t = Math.max(0.0f, Math.min(1.0f, t));
        
        return switch (this) {
            case LINEAR -> t;
            
            case EASE_IN_QUAD -> t * t;
            case EASE_IN_CUBIC -> t * t * t;
            
            case EASE_OUT_QUAD -> 1.0f - (1.0f - t) * (1.0f - t);
            case EASE_OUT_CUBIC -> 1.0f - (float) Math.pow(1.0 - t, 3);
            case EASE_OUT_QUART -> 1.0f - (float) Math.pow(1.0 - t, 4);
            case EASE_OUT_QUINT -> 1.0f - (float) Math.pow(1.0 - t, 5);
            case EASE_OUT_BOUNCE -> easeOutBounce(t);
            case EASE_OUT_BACK -> easeOutBack(t);
            case EASE_OUT_ELASTIC -> easeOutElastic(t);
            
            case EASE_IN_OUT_QUAD -> t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 2) / 2.0f;
            case EASE_IN_OUT_CUBIC -> t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0f;
        };
    }
    
    /**
     * 缓出弹跳
     */
    private float easeOutBounce(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        
        if (t < 1.0f / d1) {
            return n1 * t * t;
        } else if (t < 2.0f / d1) {
            return n1 * (t -= 1.5f / d1) * t + 0.75f;
        } else if (t < 2.5f / d1) {
            return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        } else {
            return n1 * (t -= 2.625f / d1) * t + 0.984375f;
        }
    }
    
    /**
     * 缓出回弹
     */
    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0, 3) + c1 * (float) Math.pow(t - 1.0, 2);
    }
    
    /**
     * 缓出弹性
     */
    private float easeOutElastic(float t) {
        float c4 = (2.0f * (float) Math.PI) / 3.0f;
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return (float) Math.pow(2.0, -10.0 * t) * (float) Math.sin((t * 10.0 - 0.75) * c4) + 1.0f;
    }
    
    /**
     * 从字符串名称获取缓动函数
     * <p>✅ 优化：使用预构建 Map 查找，避免在热路径上抛异常
     */
    public static EasingFunction fromString(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }

        // 1. 精确匹配（大小写不敏感）
        EasingFunction result = LOOKUP_BY_NAME.get(name);
        if (result != null) return result;
        result = LOOKUP_BY_NAME.get(name.toUpperCase());
        if (result != null) return result;

        // 2. 中文显示名精确匹配
        result = LOOKUP_BY_DISPLAY_NAME.get(name);
        if (result != null) return result;

        // 3. 模糊匹配（中文显示名包含）
        for (EasingFunction func : values()) {
            if (func.displayName.contains(name)) {
                return func;
            }
        }

        return LINEAR;
    }
}

