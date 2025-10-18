package com.chronoblocks.core.animation;

/**
 * 插值类型枚举 v4.5 - 定义关键帧之间的插值曲线
 * 
 * <h3>支持的插值类型</h3>
 * <ul>
 *   <li><strong>LINEAR</strong> - 线性插值（默认）</li>
 *   <li><strong>EASE_IN</strong> - 缓入（慢→快）</li>
 *   <li><strong>EASE_OUT</strong> - 缓出（快→慢）</li>
 *   <li><strong>EASE_IN_OUT</strong> - 缓入缓出（慢→快→慢）</li>
 *   <li><strong>BEZIER</strong> - 贝塞尔曲线（自定义控制点）</li>
 *   <li><strong>STEP</strong> - 阶梯（无插值，立即跳变）</li>
 *   <li><strong>SPRING</strong> - 弹簧效果（带回弹）</li>
 *   <li><strong>BOUNCE</strong> - 弹跳效果（多次回弹）</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 为关键帧设置插值类型
 * keyframe.setInterpolationType(InterpolationType.EASE_IN_OUT);
 * 
 * // 为轨道设置默认插值类型
 * track.setDefaultInterpolationType(InterpolationType.BEZIER);
 * 
 * // 使用自定义贝塞尔曲线
 * keyframe.setInterpolationType(InterpolationType.BEZIER);
 * keyframe.setBezierControlPoints(0.42f, 0.0f, 0.58f, 1.0f);
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public enum InterpolationType {
    
    /**
     * 线性插值 - 匀速运动
     * <pre>
     * f(t) = t
     * 
     * 1.0 ┤        ╱
     *     │      ╱
     *     │    ╱
     *     │  ╱
     * 0.0 └╱────────
     *     0.0     1.0
     * </pre>
     */
    LINEAR("线性", "匀速运动"),
    
    /**
     * 缓入 - 慢启动，加速
     * <pre>
     * f(t) = t²
     * 
     * 1.0 ┤      ╱
     *     │    ╱
     *     │   ╱
     *     │ ╱
     * 0.0 └─────────
     *     0.0     1.0
     * </pre>
     */
    EASE_IN("缓入", "慢启动，逐渐加速"),
    
    /**
     * 缓出 - 快启动，减速
     * <pre>
     * f(t) = 1 - (1-t)²
     * 
     * 1.0 ┤   ╱─────
     *     │  ╱
     *     │ ╱
     *     │╱
     * 0.0 └─────────
     *     0.0     1.0
     * </pre>
     */
    EASE_OUT("缓出", "快启动，逐渐减速"),
    
    /**
     * 缓入缓出 - 慢启动，加速，再减速
     * <pre>
     * f(t) = smoothstep(t)
     * 
     * 1.0 ┤    ╱──
     *     │   ╱
     *     │  ╱
     *     │ ╱
     * 0.0 └──────────
     *     0.0     1.0
     * </pre>
     */
    EASE_IN_OUT("缓入缓出", "慢→快→慢"),
    
    /**
     * 贝塞尔曲线 - 自定义控制点
     * <p>使用 4 个控制点定义的三次贝塞尔曲线
     * <p>默认值: (0.25, 0.1, 0.25, 1.0) - CSS ease 标准
     */
    BEZIER("贝塞尔", "自定义曲线"),
    
    /**
     * 阶梯 - 无插值，立即跳变
     * <pre>
     * f(t) = step(t, 0.5)
     * 
     * 1.0 ┤    ┌─────
     *     │    │
     *     │    │
     *     │    │
     * 0.0 └────┘─────
     *     0.0     1.0
     * </pre>
     */
    STEP("阶梯", "立即跳变，无过渡"),
    
    /**
     * 弹簧 - 弹性回弹效果
     * <pre>
     * f(t) = 1 + sin(6πt) * exp(-6t)
     * 
     * 1.2 ┤    ╱╲
     * 1.0 ┤   ╱  ╲╱─
     * 0.8 ┤  ╱
     * 0.0 └──────────
     *     0.0     1.0
     * </pre>
     */
    SPRING("弹簧", "带回弹效果"),
    
    /**
     * 弹跳 - 多次回弹效果（如球落地）
     * <pre>
     * f(t) = bounce(t)
     * 
     * 1.0 ┤╲  ╲ ╲╲──
     *     │ ╲  ╲
     *     │  ╲  ╲
     *     │   ╲  ╲
     * 0.0 └──────────
     *     0.0     1.0
     * </pre>
     */
    BOUNCE("弹跳", "多次回弹效果");
    
    // ==================== 属性 ====================
    
    /** 显示名称 */
    private final String displayName;
    
    /** 描述 */
    private final String description;
    
    // ==================== 构造函数 ====================
    
    InterpolationType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    // ==================== Getter ====================
    
    /**
     * 获取显示名称
     * @return 中文显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取描述
     * @return 插值类型描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 是否需要自定义参数
     * @return true 如果需要额外参数（如 Bezier 的控制点）
     */
    public boolean requiresCustomParameters() {
        return this == BEZIER || this == SPRING || this == BOUNCE;
    }
    
    /**
     * 是否是平滑曲线
     * @return true 如果产生平滑的曲线（排除 STEP）
     */
    public boolean isSmooth() {
        return this != STEP;
    }
    
    /**
     * 获取推荐的贝塞尔控制点（仅用于 Bezier 类型）
     * @return [x1, y1, x2, y2] 格式的控制点数组
     */
    public float[] getDefaultBezierControlPoints() {
        return switch (this) {
            case EASE_IN -> new float[]{0.42f, 0.0f, 1.0f, 1.0f};      // ease-in
            case EASE_OUT -> new float[]{0.0f, 0.0f, 0.58f, 1.0f};     // ease-out
            case EASE_IN_OUT -> new float[]{0.42f, 0.0f, 0.58f, 1.0f}; // ease-in-out
            case BEZIER -> new float[]{0.25f, 0.1f, 0.25f, 1.0f};      // ease (CSS标准)
            default -> new float[]{0.0f, 0.0f, 1.0f, 1.0f};            // linear
        };
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 从字符串解析插值类型
     * @param name 类型名称
     * @return 对应的枚举值，如果未找到则返回 LINEAR
     */
    public static InterpolationType fromString(String name) {
        if (name == null) return LINEAR;
        
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }
    
    /**
     * 获取所有平滑插值类型（用于 UI 选择器）
     * @return 平滑插值类型数组
     */
    public static InterpolationType[] getSmoothTypes() {
        return new InterpolationType[]{
            LINEAR,
            EASE_IN,
            EASE_OUT,
            EASE_IN_OUT,
            BEZIER
        };
    }
    
    /**
     * 获取所有特效插值类型（用于 UI 选择器）
     * @return 特效插值类型数组
     */
    public static InterpolationType[] getEffectTypes() {
        return new InterpolationType[]{
            SPRING,
            BOUNCE
        };
    }
    
    /**
     * 获取适合UI显示的完整信息
     * @return 格式化的字符串
     */
    public String getFullDescription() {
        return String.format("%s - %s", displayName, description);
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

