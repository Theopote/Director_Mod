package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 瞬间出现动画变换器
 * 
 * <p>方块立即出现，无任何变换效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class InstantTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        // 所有值保持默认（无变换）
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "瞬间出现";
    }
    
    @Override
    public String getDescription() {
        return "方块立即出现，无任何变换效果";
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
}

