package com.director.core.animation.transform.impl;

import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 气泡弹出动画变换器
 * 
 * <p>方块从中心由 0 放大到 1，带有弹性回弹效果（Over-shoot），适合卡通风格。
 * 使用 EaseOutBack 缓动函数实现弹性效果。
 * 
 * @author Director Team
 * @version 1.0
 */
public class PopInTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float initialScale = getFloatParam(params, "initialScale", 0.0f);
        
        // 计算缩放：从 initialScale 到 1.0
        // 弹性回弹效果由推荐的 EasingFunction (EASE_OUT_BACK) 在外层完成，
        // Transformer 本身只负责线性映射，避免与 Easing 双重叠加
        float scale = initialScale + (1.0f - initialScale) * progress;
        
        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;
        
        // 淡入效果
        result.opacity = Math.min(1.0f, progress * 1.2f);
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "气泡弹出";
    }
    
    @Override
    public String getDescription() {
        return "方块由小变大弹出，带有弹性回弹，适合卡通风格";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("initialScale", 0.0f);
        params.put("elasticity", 1.0f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_BACK"; // 弹性回弹效果
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
        
        UIHelper.label("气泡弹出参数");
        
        // 初始缩放
        float initialScale = getFloatParam(params, "initialScale", 0.0f);
        float[] scaleArray = {initialScale};
        if (UIHelper.dragFloat("初始缩放", scaleArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("initialScale", scaleArray[0]);
            modified = true;
        }
        
        // 弹性程度
        float elasticity = getFloatParam(params, "elasticity", 1.0f);
        float[] elasticityArray = {elasticity};
        if (UIHelper.dragFloat("弹性程度", elasticityArray, 0.1f, 0.0f, 2.0f, "%.1f")) {
            params.put("elasticity", elasticityArray[0]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>缩放动画：从 initialScale 到 1.0，带有弹性回弹
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float initialScale = getFloatParam(params, "initialScale", 0.0f);
        
        // 计算缩放：从 initialScale 到 1.0
        // 注意：弹性效果由 EasingFunction.EASE_OUT_BACK 提供，easedT 已经包含了弹性回弹
        float scale = initialScale + (1.0f - initialScale) * easedT;
        
        // 确保缩放值在合理范围内（EaseOutBack 可能会略微超过 1.0）
        scale = Math.max(0.0f, Math.min(1.1f, scale));
        
        params.put("dx", 0f);
        params.put("dy", 0f);
        params.put("dz", 0f);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", scale);
        
        // 淡入效果
        float alpha = Math.min(1.0f, easedT * 1.2f);
        params.put("alpha", alpha);
    }
}

