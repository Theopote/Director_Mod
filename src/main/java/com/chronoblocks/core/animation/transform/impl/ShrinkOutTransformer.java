package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 皱缩消失动画变换器
 * 
 * <p>方块从 1.0 迅速缩小到 0.0 并消失，对应 POP_IN 的退出版本。
 * 适合魔法消除、黑洞吸收等效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ShrinkOutTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float targetScale = getFloatParam(params, "targetScale", 0.0f);
        
        // 计算缩放：从 1.0 到 targetScale
        float scale = 1.0f - (1.0f - targetScale) * progress;
        
        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;
        
        // 同时淡出
        result.opacity = 1.0f - progress;
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "皱缩消失";
    }
    
    @Override
    public String getDescription() {
        return "方块迅速缩小直至消失，适合魔法消除效果";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("targetScale", 0.0f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_IN_CUBIC"; // 快速收缩
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
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        
        boolean modified = false;
        
        UIHelper.label("皱缩消失参数");
        
        // 目标缩放
        float targetScale = getFloatParam(params, "targetScale", 0.0f);
        float[] scaleArray = {targetScale};
        if (UIHelper.dragFloat("目标缩放", scaleArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("targetScale", scaleArray[0]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>缩放动画：从 1.0 到 targetScale
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float targetScale = getFloatParam(params, "targetScale", 0.0f);
        
        // 计算缩放：从 1.0 到 targetScale
        float scale = 1.0f - (1.0f - targetScale) * easedT;
        
        params.put("dx", 0f);
        params.put("dy", 0f);
        params.put("dz", 0f);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", scale);
        
        // 淡出效果
        float alpha = 1.0f - easedT;
        params.put("alpha", alpha);
    }
}

