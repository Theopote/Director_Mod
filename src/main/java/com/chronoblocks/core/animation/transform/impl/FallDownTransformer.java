package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 坠落动画变换器
 * 
 * <p>方块从上方坠落，模拟重力效果。
 * 支持旋转（翻滚）和弹跳效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FallDownTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float startHeight = getFloatParam(params, "height", 10.0f);
        boolean tumble = getBooleanParam(params, "tumble", true);
        float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
        
        // 计算 Y 位移：从 startHeight 到 0
        result.translateY = startHeight * (1.0f - progress);
        
        // 计算旋转（翻滚效果）
        if (tumble && progress < 0.95f) { // 落地前旋转
            float rotScale = (1.0f - progress) * tumbleIntensity;
            
            // 使用随机轴旋转，让每个方块旋转不同
            result.rotateX = rotScale * 360.0f * (0.5f + random.nextFloat());
            result.rotateY = rotScale * 360.0f * (0.5f + random.nextFloat());
            result.rotateZ = rotScale * 180.0f * (0.3f + random.nextFloat() * 0.4f);
        }
        
        // 透明度：可以边坠落边淡入（可选）
        boolean fadeIn = getBooleanParam(params, "fadeIn", false);
        if (fadeIn) {
            result.opacity = progress;
        }
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "坠落";
    }
    
    @Override
    public String getDescription() {
        return "方块从上方坠落，支持旋转和弹跳效果";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("height", 10.0f);
        params.put("tumble", true);
        params.put("tumbleIntensity", 1.0f);
        params.put("fadeIn", false);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_BOUNCE"; // 弹跳效果适合坠落
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
    
    private boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get(key);
        return switch (val) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> defaultValue;
        };
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        
        boolean modified = false;
        
        // 坠落参数
        UIHelper.label("坠落参数");
        
        // 起始高度
        float height = getFloatParam(params, "height", 10.0f);
        float[] heightArray = {height};
        if (UIHelper.dragFloat("起始高度", heightArray, 0.5f, 0.0f, 50.0f, "%.1f 格")) {
            params.put("height", heightArray[0]);
            modified = true;
        }
        
        // 翻滚旋转
        boolean tumble = getBooleanParam(params, "tumble", true);
        boolean[] tumbleArray = {tumble};
        if (UIHelper.checkbox("翻滚旋转", tumbleArray)) {
            params.put("tumble", tumbleArray[0]);
            modified = true;
        }
        
        // 旋转强度（仅在启用翻滚时显示）
        if (tumbleArray[0]) {
            float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
            float[] intensityArray = {tumbleIntensity};
            if (UIHelper.dragFloat("旋转强度", intensityArray, 0.1f, 0.0f, 3.0f, "%.1f")) {
                params.put("tumbleIntensity", intensityArray[0]);
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>从空中掉落：Y 位移 + 随机旋转（翻滚）
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float height = getFloatParam(params, "height", 10.0f);
        boolean tumble = getBooleanParam(params, "tumble", true);
        float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
        
        // 计算 Y 位移：从 height 到 0
        float dy = height * (1.0f - easedT);
        
        params.put("dx", 0f);
        params.put("dy", dy);
        params.put("dz", 0f);
        
        // 随机旋转（翻滚效果）
        if (tumble && easedT < 0.95f) { // 落地前旋转
            float rotateAmount = (1.0f - easedT) * tumbleIntensity;
            
            params.put("yaw", rotateAmount * 360.0f * (0.5f + random.nextFloat()));
            params.put("pitch", rotateAmount * 360.0f * (0.5f + random.nextFloat()));
            params.put("roll", rotateAmount * 180.0f * (0.3f + random.nextFloat() * 0.4f));
        } else {
            params.put("yaw", 0f);
            params.put("pitch", 0f);
            params.put("roll", 0f);
        }
        
        params.put("scale", 1f);
        
        // 透明度：如果启用了 fadeIn，边坠落边淡入
        boolean fadeIn = getBooleanParam(params, "fadeIn", false);
        params.put("alpha", fadeIn ? easedT : 1.0f);
    }
}

