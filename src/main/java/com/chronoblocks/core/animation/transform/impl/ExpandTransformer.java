package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 扩散动画变换器
 * 
 * <p>方块从中心向外扩散出现，模拟爆炸或生长效果。
 * 支持基于距离的排序和扩散速度控制。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ExpandTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 扩散效果主要通过序列行为实现（Flow + Fill）
        // 这里只提供基础的缩放或位移效果
        
        // 可以添加从中心点的缩放效果
        float scale = progress;
        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "扩散";
    }
    
    @Override
    public String getDescription() {
        return "方块从中心向外扩散出现";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("expandSpeed", 1.0f);
        params.put("useDistance", true);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_CUBIC"; // 平滑扩散
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
            return false;
        }
        
        boolean modified = false;
        
        // 扩散参数
        UIHelper.label("扩散参数");
        
        // 扩散速度
        float expandSpeed = getFloatParam(params, "expandSpeed", 1.0f);
        float[] speedArray = {expandSpeed};
        if (UIHelper.dragFloat("扩散速度", speedArray, 0.1f, 0.1f, 5.0f, "%.1f")) {
            params.put("expandSpeed", speedArray[0]);
            modified = true;
        }
        
        // 基于距离
        boolean useDistance = getBooleanParam(params, "useDistance", true);
        boolean[] useDistanceArray = {useDistance};
        if (UIHelper.checkbox("基于距离", useDistanceArray)) {
            params.put("useDistance", useDistanceArray[0]);
            modified = true;
        }
        UIHelper.setTooltip("启用后，方块按距离中心点的距离排序出现");
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>从中心扩散并放大：随机方向扩散 + 缩放
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float radius = getFloatParam(params, "radius", 0.5f);
        
        // 如果参数中有 expandSpeed，调整扩散速度
        float expandSpeed = getFloatParam(params, "expandSpeed", 1.0f);
        float effectiveT = Math.min(1.0f, easedT * expandSpeed);
        
        // 随机方向（使用 sequenceEntry.seed，确保稳定）
        float angle = random.nextFloat() * 360f;
        float dx = (float) (Math.cos(Math.toRadians(angle)) * radius * (1.0f - effectiveT));
        float dz = (float) (Math.sin(Math.toRadians(angle)) * radius * (1.0f - effectiveT));
        
        params.put("dx", dx);
        params.put("dy", 0f);
        params.put("dz", dz);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", effectiveT);      // 扩散过程中逐渐变大
        params.put("alpha", effectiveT);      // 逐渐变清晰
    }
}

