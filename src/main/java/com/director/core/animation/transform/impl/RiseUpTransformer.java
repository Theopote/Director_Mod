package com.director.core.animation.transform.impl;

import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 升起动画变换器
 * 
 * <p>方块从下方升起，模拟地形隆起或建筑物从地下升起的效果。
 * 
 * @author Director Team
 * @version 1.0
 */
public class RiseUpTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float startDepth = getFloatParam(params, 5.0f);
        
        // 计算 Y 位移：从 -startDepth 到 0
        result.translateY = -startDepth * (1.0f - progress);
        
        // 可选：添加轻微的缩放效果（从下往上生长）
        result.scaleY = 0.8f + 0.2f * progress;
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "升起";
    }
    
    @Override
    public String getDescription() {
        return "方块从下方升起，模拟地形隆起";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("riseHeight", 5.0f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_QUAD"; // 平滑升起
    }
    
    // ========== 工具方法 ==========
    
    private float getFloatParam(Map<String, Object> params, float defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get("riseHeight");
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
        
        // 升起参数
        UIHelper.label("升起参数");
        
        // 起始深度
        float riseHeight = getFloatParam(params, 5.0f);
        float[] heightArray = {riseHeight};
        if (UIHelper.dragFloat("起始深度", heightArray, 0.5f, 0.0f, 20.0f, "%.1f 格")) {
            params.put("riseHeight", heightArray[0]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>从地下升起：Y 位移从 -depth 到 0 + 缩放
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float depth = getFloatParam(params, 2.0f);
        
        // 计算 Y 位移：从 -depth 到 0
        float dy = -depth * (1.0f - easedT);
        
        params.put("dx", 0f);
        params.put("dy", dy);
        params.put("dz", 0f);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", easedT);
        params.put("alpha", easedT);
    }
}

