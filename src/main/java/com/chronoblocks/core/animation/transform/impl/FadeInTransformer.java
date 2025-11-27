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
        boolean flashWhite = getBooleanParam(params, "flashWhite", false);
        
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
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).floatValue();
        if (val instanceof String) {
            try {
                return Float.parseFloat((String) val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return defaultValue;
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
}

