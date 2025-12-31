package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 淡入动画变换器
 * 
 * <p>方块逐渐变得不透明，适合全息投影、蓝图展示等效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FadeInTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float startOpacity = getFloatParam(params, "startOpacity", 0.0f);
        boolean flashWhite = getBooleanParam(params);
        
        // 计算透明度：从 startOpacity 到 1.0
        result.opacity = startOpacity + (1.0f - startOpacity) * progress;
        
        // 闪白光效果（可选）
        if (flashWhite && progress < 0.1f) {
            float flashIntensity = 1.0f - (progress / 0.1f);
            result.colorR = 1.0f + flashIntensity * 0.5f;
            result.colorG = 1.0f + flashIntensity * 0.5f;
            result.colorB = 1.0f + flashIntensity * 0.5f;
        }
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "淡入";
    }
    
    @Override
    public String getDescription() {
        return "方块逐渐变得不透明，适合全息投影效果";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("startOpacity", 0.0f);
        params.put("flashWhite", false);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_IN_QUAD"; // 平滑淡入
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
        if (params == null) return false;
        Object val = params.get("flashWhite");
        return switch (val) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> false;
        };
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false; // UI 不可用，使用默认渲染方式
        }
        
        boolean modified = false;
        
        // 淡入参数
        UIHelper.label("淡入参数");
        
        // 起始透明度
        float startOpacity = getFloatParam(params, "startOpacity", 0.0f);
        float[] opacityArray = {startOpacity};
        if (UIHelper.dragFloat("起始透明度", opacityArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("startOpacity", opacityArray[0]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>透明度淡入：从 startAlpha 到 endAlpha
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float startAlpha = getFloatParam(params, "startOpacity", 0.0f);
        // 默认结束透明度为 1.0
        float endAlpha = getFloatParam(params, "endOpacity", 1.0f);
        
        float alpha = startAlpha + (endAlpha - startAlpha) * easedT;
        
        params.put("dx", 0f);
        params.put("dy", 0f);
        params.put("dz", 0f);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", 1f);
        params.put("alpha", alpha);
    }
}

