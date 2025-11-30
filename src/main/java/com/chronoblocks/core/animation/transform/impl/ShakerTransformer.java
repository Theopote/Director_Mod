package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 震动归位动画变换器
 * 
 * <p>方块在出现位置震动后归位，增加落地感和视觉冲击。
 * 支持震动强度和持续时间控制。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ShakerTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float shakeIntensity = getFloatParam(params, "shakeIntensity", 0.5f);
        float shakeDuration = getFloatParam(params, "shakeDuration", 0.3f);
        
        // 震动发生在动画开始阶段
        if (progress < shakeDuration) {
            float shakeProgress = progress / shakeDuration;
            
            // 震动效果：使用随机偏移，但逐渐减小
            float shakeAmount = shakeIntensity * (1.0f - shakeProgress);
            
            // 使用随机数生成器产生震动偏移（但保持一致）
            random.setSeed((long) (shakeProgress * 1000));
            result.translateX = (random.nextFloat() - 0.5f) * 2.0f * shakeAmount;
            result.translateY = (random.nextFloat() - 0.5f) * 2.0f * shakeAmount;
            result.translateZ = (random.nextFloat() - 0.5f) * 2.0f * shakeAmount;
            
            // 可选：添加旋转震动
            result.rotateX = (random.nextFloat() - 0.5f) * 10.0f * shakeAmount;
            result.rotateY = (random.nextFloat() - 0.5f) * 10.0f * shakeAmount;
            result.rotateZ = (random.nextFloat() - 0.5f) * 10.0f * shakeAmount;
        }
        // 震动结束后，方块应该回到原位（所有变换为0，这是默认值）
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "震动归位";
    }
    
    @Override
    public String getDescription() {
        return "方块震动后归位，增加落地感";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("shakeIntensity", 0.5f);
        params.put("shakeDuration", 0.3f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_BACK"; // 回弹效果适合震动归位
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
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        
        boolean modified = false;
        
        // 震动参数
        UIHelper.label("震动参数");
        
        // 震动强度
        float shakeIntensity = getFloatParam(params, "shakeIntensity", 0.5f);
        float[] intensityArray = {shakeIntensity};
        if (UIHelper.dragFloat("震动强度", intensityArray, 0.05f, 0.1f, 2.0f, "%.2f")) {
            params.put("shakeIntensity", intensityArray[0]);
            modified = true;
        }
        
        // 震动时长
        float shakeDuration = getFloatParam(params, "shakeDuration", 0.3f);
        float[] durationArray = {shakeDuration};
        if (UIHelper.dragFloat("震动时长", durationArray, 0.05f, 0.1f, 1.0f, "%.2fs")) {
            params.put("shakeDuration", durationArray[0]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>颤动抖动效果：随机偏移，随着进度收敛到 0
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float intensity = getFloatParam(params, "intensity", 0.1f);
        float shakeDuration = getFloatParam(params, "shakeDuration", 1.0f);
        
        // 震动效果：使用 shakeDuration 控制震动持续时间
        float shakeFactor = 0f;
        if (easedT < shakeDuration) {
            float shakeProgress = easedT / shakeDuration;
            shakeFactor = (1.0f - shakeProgress) * intensity;
        }
        
        // 抖动随着 t -> 1 收敛
        float dx = (random.nextFloat() - 0.5f) * 2.0f * shakeFactor;
        float dy = (random.nextFloat() - 0.5f) * 2.0f * shakeFactor;
        float dz = (random.nextFloat() - 0.5f) * 2.0f * shakeFactor;
        
        params.put("dx", dx);
        params.put("dy", dy);
        params.put("dz", dz);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", 1f);
        params.put("alpha", 1f);
    }
}

