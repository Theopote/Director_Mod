package com.director.core.animation.transform.impl;

import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 震动归位动画变换器
 * 
 * <p>方块在出现位置震动后归位，增加落地感和视觉冲击。
 * 支持震动强度和持续时间控制。
 * 
 * @author Director Team
 * @version 1.0
 */
public class ShakerTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        float intensity = getFloatParam(params, "intensity", 0.5f); // 震动幅度
        float decay = getFloatParam(params, "decay", 1.0f); // 衰减速度（值越大衰减越快）
        float shakeDuration = getFloatParam(params, "shakeDuration", 0.3f);
        
        // 震动发生在动画开始阶段
        if (progress < shakeDuration) {
            float shakeProgress = progress / shakeDuration;
            
            // 震动效果：使用随机偏移，但逐渐减小（衰减速度由 decay 控制）
            float decayFactor = (float) Math.pow(1.0f - shakeProgress, decay);
            float shakeAmount = intensity * decayFactor;
            
            // ✅ 修复：先保存原始种子状态，用离散步生成时变抖动，避免污染传入的 random
            long baseSeed = random.nextLong();
            int step = (int) (shakeProgress * 30);
            Random localRandom = new Random(baseSeed ^ (step * 73856093L));
            result.translateX = (localRandom.nextFloat() - 0.5f) * 2.0f * shakeAmount;
            result.translateY = (localRandom.nextFloat() - 0.5f) * 2.0f * shakeAmount;
            result.translateZ = (localRandom.nextFloat() - 0.5f) * 2.0f * shakeAmount;
            
            // 可选：添加旋转震动
            result.rotateX = (localRandom.nextFloat() - 0.5f) * 10.0f * shakeAmount;
            result.rotateY = (localRandom.nextFloat() - 0.5f) * 10.0f * shakeAmount;
            result.rotateZ = (localRandom.nextFloat() - 0.5f) * 10.0f * shakeAmount;
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
        params.put("intensity", 0.5f); // 震动幅度
        params.put("decay", 1.0f); // 衰减速度
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
        
        // 震动参数
        UIHelper.label("震动参数");
        
        // 震动幅度
        float intensity = getFloatParam(params, "intensity", 0.5f);
        float[] intensityArray = {intensity};
        if (UIHelper.dragFloat("震动幅度", intensityArray, 0.05f, 0.1f, 2.0f, "%.2f")) {
            params.put("intensity", intensityArray[0]);
            modified = true;
        }
        
        // 衰减速度
        float decay = getFloatParam(params, "decay", 1.0f);
        float[] decayArray = {decay};
        if (UIHelper.dragFloat("衰减速度", decayArray, 0.1f, 0.1f, 5.0f, "%.1f")) {
            params.put("decay", decayArray[0]);
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
        float intensity = getFloatParam(params, "intensity", 0.5f);
        float decay = getFloatParam(params, "decay", 1.0f);
        float shakeDuration = getFloatParam(params, "shakeDuration", 0.3f);
        
        // 震动效果：使用 shakeDuration 控制震动持续时间，decay 控制衰减速度
        float shakeFactor;
        float dx = 0f, dy = 0f, dz = 0f;
        if (easedT < shakeDuration) {
            float shakeProgress = easedT / shakeDuration;
            float decayFactor = (float) Math.pow(1.0f - shakeProgress, decay);
            shakeFactor = intensity * decayFactor;

            // ✅ 修复：使用基于进度离散化的种子，让不同帧的震动方向发生变化
            // 将 shakeProgress 量化到约 30 个离散步（模拟 ~30fps 的震动），
            // 同时混合原始 random 种子以保证不同方块有不同的震动模式。
            long baseSeed = random.nextLong();
            int step = (int) (shakeProgress * 30);
            random.setSeed(baseSeed ^ (step * 73856093L));

            dx = (random.nextFloat() - 0.5f) * 2.0f * shakeFactor;
            dy = (random.nextFloat() - 0.5f) * 2.0f * shakeFactor;
            dz = (random.nextFloat() - 0.5f) * 2.0f * shakeFactor;
        }
        
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

