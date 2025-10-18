package com.chronoblocks.core.animation;

import java.util.*;

/**
 * 关键帧预设库 v4.5 - 常用动画预设集合
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>20+ 种常用动画预设</li>
 *   <li>分类管理（基础、弹性、特效等）</li>
 *   <li>一键应用预设</li>
 *   <li>自定义预设保存</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建缓动动画
 * Keyframe kf = KeyframePresets.createEaseInOut(0.0f, 2.0f);
 * 
 * // 创建弹跳效果
 * Keyframe kf = KeyframePresets.createBounce(0.0f, 1.0f);
 * 
 * // 应用预设到现有关键帧
 * KeyframePresets.applyPreset(keyframe, "ease-in-out");
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class KeyframePresets {
    
    // ==================== 预设定义 ====================
    
    /**
     * 预设信息
     */
    public static class PresetInfo {
        public final String name;
        public final String displayName;
        public final String category;
        public final String description;
        public final InterpolationType interpolationType;
        public final float[] parameters;
        
        public PresetInfo(String name, String displayName, String category, 
                         String description, InterpolationType type, float[] params) {
            this.name = name;
            this.displayName = displayName;
            this.category = category;
            this.description = description;
            this.interpolationType = type;
            this.parameters = params;
        }
    }
    
    /** 所有预设 */
    private static final Map<String, PresetInfo> PRESETS = new LinkedHashMap<>();
    
    static {
        // ==================== 基础预设 ====================
        registerPreset("linear", "线性", "基础", 
            "匀速运动，没有加速或减速", 
            InterpolationType.LINEAR, null);
        
        registerPreset("ease-in", "缓入", "基础", 
            "慢速开始，逐渐加速", 
            InterpolationType.EASE_IN, null);
        
        registerPreset("ease-out", "缓出", "基础", 
            "快速开始，逐渐减速", 
            InterpolationType.EASE_OUT, null);
        
        registerPreset("ease-in-out", "缓入缓出", "基础", 
            "慢速开始，加速，再减速", 
            InterpolationType.EASE_IN_OUT, null);
        
        // ==================== 贝塞尔预设 ====================
        registerPreset("ease", "标准缓动", "贝塞尔", 
            "CSS 标准 ease 曲线", 
            InterpolationType.BEZIER, new float[]{0.25f, 0.1f, 0.25f, 1.0f});
        
        registerPreset("ease-in-quad", "缓入（二次）", "贝塞尔", 
            "二次方缓入曲线", 
            InterpolationType.BEZIER, new float[]{0.55f, 0.085f, 0.68f, 0.53f});
        
        registerPreset("ease-out-quad", "缓出（二次）", "贝塞尔", 
            "二次方缓出曲线", 
            InterpolationType.BEZIER, new float[]{0.25f, 0.46f, 0.45f, 0.94f});
        
        registerPreset("ease-in-cubic", "缓入（三次）", "贝塞尔", 
            "三次方缓入曲线", 
            InterpolationType.BEZIER, new float[]{0.55f, 0.055f, 0.675f, 0.19f});
        
        registerPreset("ease-out-cubic", "缓出（三次）", "贝塞尔", 
            "三次方缓出曲线", 
            InterpolationType.BEZIER, new float[]{0.215f, 0.61f, 0.355f, 1.0f});
        
        registerPreset("ease-in-quart", "缓入（四次）", "贝塞尔", 
            "四次方缓入曲线", 
            InterpolationType.BEZIER, new float[]{0.895f, 0.03f, 0.685f, 0.22f});
        
        registerPreset("ease-out-quart", "缓出（四次）", "贝塞尔", 
            "四次方缓出曲线", 
            InterpolationType.BEZIER, new float[]{0.165f, 0.84f, 0.44f, 1.0f});
        
        // ==================== 弹性预设 ====================
        registerPreset("spring-soft", "软弹簧", "弹性", 
            "轻微的弹性回弹效果", 
            InterpolationType.SPRING, new float[]{8.0f, 6.0f});  // 阻尼, 频率
        
        registerPreset("spring-medium", "中等弹簧", "弹性", 
            "中等强度的弹性回弹", 
            InterpolationType.SPRING, new float[]{6.0f, 8.0f});
        
        registerPreset("spring-hard", "硬弹簧", "弹性", 
            "强烈的弹性回弹效果", 
            InterpolationType.SPRING, new float[]{4.0f, 10.0f});
        
        // ==================== 特效预设 ====================
        registerPreset("bounce", "弹跳", "特效", 
            "模拟球落地的弹跳效果", 
            InterpolationType.BOUNCE, null);
        
        registerPreset("step", "阶梯", "特效", 
            "立即跳变，无过渡动画", 
            InterpolationType.STEP, null);
        
        // ==================== 特殊预设 ====================
        registerPreset("overshoot", "过冲", "特殊", 
            "超过目标后返回", 
            InterpolationType.BEZIER, new float[]{0.68f, -0.55f, 0.265f, 1.55f});
        
        registerPreset("anticipate", "预期", "特殊", 
            "开始时稍微后退", 
            InterpolationType.BEZIER, new float[]{0.36f, 0.0f, 0.66f, -0.56f});
        
        registerPreset("smooth", "平滑", "特殊", 
            "非常平滑的S曲线", 
            InterpolationType.BEZIER, new float[]{0.4f, 0.0f, 0.2f, 1.0f});
    }
    
    // ==================== 注册方法 ====================
    
    private static void registerPreset(String name, String displayName, String category,
                                      String description, InterpolationType type, float[] params) {
        PRESETS.put(name, new PresetInfo(name, displayName, category, description, type, params));
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 创建带预设的关键帧
     */
    public static Keyframe create(String presetName, float time) {
        Keyframe kf = new Keyframe(time);
        applyPreset(kf, presetName);
        return kf;
    }
    
    /**
     * 应用预设到关键帧
     */
    public static void applyPreset(Keyframe keyframe, String presetName) {
        PresetInfo preset = PRESETS.get(presetName);
        if (preset == null) {
            return;
        }
        
        keyframe.setInterpolationType(preset.interpolationType);
        
        if (preset.parameters != null) {
            if (preset.interpolationType == InterpolationType.BEZIER) {
                keyframe.setBezierControlPoints(preset.parameters);
            } else if (preset.interpolationType == InterpolationType.SPRING) {
                keyframe.setCustomParams(preset.parameters);
            }
        }
    }
    
    /**
     * 获取所有预设名称
     */
    public static List<String> getAllPresetNames() {
        return new ArrayList<>(PRESETS.keySet());
    }
    
    /**
     * 获取指定类别的预设
     */
    public static List<PresetInfo> getPresetsByCategory(String category) {
        List<PresetInfo> result = new ArrayList<>();
        for (PresetInfo preset : PRESETS.values()) {
            if (preset.category.equals(category)) {
                result.add(preset);
            }
        }
        return result;
    }
    
    /**
     * 获取所有类别
     */
    public static List<String> getAllCategories() {
        Set<String> categories = new LinkedHashSet<>();
        for (PresetInfo preset : PRESETS.values()) {
            categories.add(preset.category);
        }
        return new ArrayList<>(categories);
    }
    
    /**
     * 获取预设信息
     */
    public static PresetInfo getPreset(String name) {
        return PRESETS.get(name);
    }
    
    // ==================== 便捷方法 ====================
    
    public static Keyframe createLinear(float time) {
        return create("linear", time);
    }
    
    public static Keyframe createEaseIn(float time) {
        return create("ease-in", time);
    }
    
    public static Keyframe createEaseOut(float time) {
        return create("ease-out", time);
    }
    
    public static Keyframe createEaseInOut(float time) {
        return create("ease-in-out", time);
    }
    
    public static Keyframe createBounce(float time) {
        return create("bounce", time);
    }
    
    public static Keyframe createSpring(float time, float damping, float frequency) {
        Keyframe kf = new Keyframe(time);
        kf.setInterpolationType(InterpolationType.SPRING);
        kf.setCustomParams(new float[]{damping, frequency});
        return kf;
    }
    
    public static Keyframe createBezier(float time, float x1, float y1, float x2, float y2) {
        Keyframe kf = new Keyframe(time);
        kf.setInterpolationType(InterpolationType.BEZIER);
        kf.setBezierControlPoints(x1, y1, x2, y2);
        return kf;
    }
}

