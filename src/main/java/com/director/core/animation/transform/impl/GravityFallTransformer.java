package com.director.core.animation.transform.impl;

import com.director.core.animation.transform.ui.UIHelper;
import com.director.core.container.AnimationMode;
import com.director.i18n.TransformerUiI18n;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 重力坍塌动画变换器
 * 
 * <p>方块受重力影响向下掉落并消失，模拟落沙效果。
 * 对应 FALL_DOWN 的退出版本，但用于擦除轨道。
 * 
 * @author Director Team
 * @version 1.0
 */
public class GravityFallTransformer extends AbstractModeTransformer {

    public GravityFallTransformer() {
        super(AnimationMode.GRAVITY_FALL);
    }
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float fallSpeed = getFloatParam(params, "fallSpeed", 5.0f);
        boolean tumble = getBooleanParam(params);
        float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
        
        // 计算下落距离：使用重力加速度公式（简化）
        // 距离 = 0.5 * g * t^2，这里 g = fallSpeed
        float fallDistance = 0.5f * fallSpeed * progress * progress;
        result.translateY = -fallDistance; // 向下为负
        
        // 旋转效果（翻滚）
        if (tumble) {
            float rotationSpeed = 360.0f * tumbleIntensity;
            result.rotateX = rotationSpeed * progress;
            result.rotateZ = rotationSpeed * progress * 0.7f; // Z 轴旋转稍慢
        }
        
        // 淡出效果
        result.opacity = 1.0f - progress;
        
        return result;
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("fallSpeed", 5.0f);
        params.put("tumble", true);
        params.put("tumbleIntensity", 1.0f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_IN_QUAD"; // 重力加速
    }
    
    // ========== 工具方法 ==========
    
    private float getFloatParam(Map<String, Object> params, String key, float defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get(key);
        switch (val) {
            case null -> {
                return defaultValue;
            }
            case Number number -> {
                return number.floatValue();
            }
            case String s -> {
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            default -> {
            }
        }
        return defaultValue;
    }
    
    private boolean getBooleanParam(Map<String, Object> params) {
        if (params == null) return true;
        Object val = params.get("tumble");
        return switch (val) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> true;
        };
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        
        boolean modified = false;
        
        UIHelper.label(TransformerUiI18n.section(AnimationMode.GRAVITY_FALL));
        
        // 下落速度
        float fallSpeed = getFloatParam(params, "fallSpeed", 5.0f);
        float[] speedArray = {fallSpeed};
        if (UIHelper.dragFloat(TransformerUiI18n.fallSpeed(), speedArray, 0.5f, 1.0f, 20.0f, "%.1f")) {
            params.put("fallSpeed", speedArray[0]);
            modified = true;
        }
        
        // 翻滚效果
        boolean tumble = getBooleanParam(params);
        boolean[] tumbleArray = {tumble};
        if (UIHelper.checkbox(TransformerUiI18n.enableTumble(), tumbleArray)) {
            params.put("tumble", tumbleArray[0]);
            modified = true;
        }
        
        if (tumble) {
            float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
            float[] intensityArray = {tumbleIntensity};
            if (UIHelper.dragFloat(TransformerUiI18n.tumbleIntensity(), intensityArray, 0.1f, 0.0f, 3.0f, "%.1f")) {
                params.put("tumbleIntensity", intensityArray[0]);
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>重力下落：向下移动并旋转，同时淡出
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float fallSpeed = getFloatParam(params, "fallSpeed", 5.0f);
        boolean tumble = getBooleanParam(params);
        float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
        
        // 计算下落距离（使用 easedT，已经应用了缓动函数）
        float fallDistance = 0.5f * fallSpeed * easedT * easedT;
        
        params.put("dx", 0f);
        params.put("dy", -fallDistance); // 向下为负
        params.put("dz", 0f);
        
        // 旋转效果
        if (tumble) {
            float rotationSpeed = 360.0f * tumbleIntensity;
            params.put("pitch", rotationSpeed * easedT);
            params.put("roll", rotationSpeed * easedT * 0.7f);
            params.put("yaw", 0f);
        } else {
            params.put("yaw", 0f);
            params.put("pitch", 0f);
            params.put("roll", 0f);
        }
        
        params.put("scale", 1f);
        
        // 淡出效果
        float alpha = 1.0f - easedT;
        params.put("alpha", alpha);
    }
}

