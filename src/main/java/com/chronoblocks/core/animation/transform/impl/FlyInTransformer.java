package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 飞入动画变换器
 * 
 * <p>方块从指定方向飞入，适合科技感动画效果。
 * 支持六个方向（X+/X-/Y+/Y-/Z+/Z-）和飞行距离控制。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FlyInTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        String direction = getStringParam(params, "direction", "X+");
        float flyDistance = getFloatParam(params, "flyDistance", 10.0f);
        
        // 计算飞行偏移：从 flyDistance 到 0
        float offset = flyDistance * (1.0f - progress);
        
        // 根据方向应用位移
        switch (direction.toUpperCase()) {
            case "X+":
                result.translateX = -offset; // 从右侧飞入（先负后正）
                break;
            case "X-":
                result.translateX = offset; // 从左侧飞入
                break;
            case "Y+":
                result.translateY = -offset; // 从上方飞入
                break;
            case "Y-":
                result.translateY = offset; // 从下方飞入
                break;
            case "Z+":
                result.translateZ = -offset; // 从前方飞入
                break;
            case "Z-":
                result.translateZ = offset; // 从后方飞入
                break;
        }
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "飞入";
    }
    
    @Override
    public String getDescription() {
        return "方块从指定方向飞入，适合科技感动画";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("direction", "X+");
        params.put("flyDistance", 10.0f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_QUART"; // 快速飞入，平滑停止
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
    
    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get(key);
        if (val == null) return defaultValue;
        return val.toString();
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        
        boolean modified = false;
        
        // 飞入参数
        UIHelper.label("飞入参数");
        
        // 飞入方向
        String direction = getStringParam(params, "direction", "X+");
        String[] directions = {"X+", "X-", "Y+", "Y-", "Z+", "Z-"};
        int dirIndex = 0;
        for (int i = 0; i < directions.length; i++) {
            if (directions[i].equals(direction)) {
                dirIndex = i;
                break;
            }
        }
        int[] dirIndexArray = {dirIndex};
        if (UIHelper.combo("飞入方向", dirIndexArray, directions)) {
            params.put("direction", directions[dirIndexArray[0]]);
            modified = true;
        }
        
        // 飞行距离
        float flyDistance = getFloatParam(params, "flyDistance", 10.0f);
        float[] distanceArray = {flyDistance};
        if (UIHelper.dragFloat("飞行距离", distanceArray, 1.0f, 5.0f, 50.0f, "%.0f 格")) {
            params.put("flyDistance", distanceArray[0]);
            modified = true;
        }
        
        return modified;
    }
}

