package com.director.core.animation.transform.impl;

import com.director.core.container.AnimationMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 瞬间出现动画变换器
 * 
 * <p>方块立即出现，无任何变换效果。
 * 
 * @author Director Team
 * @version 1.0
 */
public class InstantTransformer extends AbstractModeTransformer {

    public InstantTransformer() {
        super(AnimationMode.INSTANT);
    }
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        // 所有值保持默认（无变换）
        return new TransformResult();
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        return new HashMap<>();
    }
    
    @Override
    public String getRecommendedEasing() {
        return "LINEAR";
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        // 瞬间出现模式无参数需要配置
        return false;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>瞬间出现：所有变换参数为默认值（无变换）
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        params.put("dx", 0f);
        params.put("dy", 0f);
        params.put("dz", 0f);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", 1f);
        params.put("alpha", 1f); // 直接显示
    }
}

