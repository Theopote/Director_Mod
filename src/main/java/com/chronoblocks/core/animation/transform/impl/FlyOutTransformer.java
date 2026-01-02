package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 向上飞离动画变换器
 * 
 * <p>方块向指定方向飞走消失，对应 FLY_IN 的退出版本。
 * 适合被吸走、数据上传等科技感效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FlyOutTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        String direction = getStringParam(params);
        float flyDistance = getFloatParam(params);
        
        // 计算飞行偏移：从 0 到 flyDistance
        float offset = flyDistance * progress;
        
        // 根据方向应用位移
        switch (direction.toUpperCase()) {
            case "X+":
                result.translateX = offset; // 向右飞走
                break;
            case "X-":
                result.translateX = -offset; // 向左飞走
                break;
            case "Y+":
                result.translateY = offset; // 向上飞走（默认）
                break;
            case "Y-":
                result.translateY = -offset; // 向下飞走
                break;
            case "Z+":
                result.translateZ = offset; // 向前飞走
                break;
            case "Z-":
                result.translateZ = -offset; // 向后飞走
                break;
        }
        
        // 淡出效果
        result.opacity = 1.0f - progress;
        
        // 可选：缩放效果（逐渐变小）
        float scale = 1.0f - progress * 0.3f; // 最多缩小到 0.7
        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "向上飞离";
    }
    
    @Override
    public String getDescription() {
        return "方块向指定方向飞走消失，适合被吸走、数据上传等效果";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("direction", "Y+");
        params.put("flyDistance", 10.0f);
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_IN_CUBIC"; // 加速飞离
    }
    
    // ========== 工具方法 ==========
    
    private float getFloatParam(Map<String, Object> params) {
        if (params == null) return (float) 10.0;
        Object val = params.get("flyDistance");
        switch (val) {
            case null -> {
                return (float) 10.0;
            }
            case Number number -> {
                return number.floatValue();
            }
            case String s -> {
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    return (float) 10.0;
                }
            }
            default -> {
            }
        }
        return (float) 10.0;
    }
    
    private String getStringParam(Map<String, Object> params) {
        if (params == null) return "Y+";
        Object val = params.get("direction");
        if (val == null) return "Y+";
        return val.toString();
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        
        boolean modified = false;
        
        UIHelper.label("飞离参数");
        
        // 飞离方向
        String direction = getStringParam(params);
        String[] directions = {"X+", "X-", "Y+", "Y-", "Z+", "Z-"};
        int dirIndex = 0;
        for (int i = 0; i < directions.length; i++) {
            if (directions[i].equals(direction)) {
                dirIndex = i;
                break;
            }
        }
        int[] dirIndexArray = {dirIndex};
        if (UIHelper.combo("飞离方向", dirIndexArray, directions)) {
            params.put("direction", directions[dirIndexArray[0]]);
            modified = true;
        }
        
        // 飞行距离
        float flyDistance = getFloatParam(params);
        float[] distanceArray = {flyDistance};
        if (UIHelper.dragFloat("飞行距离", distanceArray, 1.0f, 5.0f, 50.0f, "%.0f 格")) {
            params.put("flyDistance", distanceArray[0]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>向指定方向飞离：位移 + 淡出 + 缩放
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        String direction = getStringParam(params);
        float flyDistance = getFloatParam(params);
        
        float dx = 0f, dy = 0f, dz = 0f;
        float offset = flyDistance * easedT;
        
        // 根据方向计算位移
        switch (direction.toUpperCase()) {
            case "X+":
                dx = offset;
                break;
            case "X-":
                dx = -offset;
                break;
            case "Y+":
                dy = offset;
                break;
            case "Y-":
                dy = -offset;
                break;
            case "Z+":
                dz = offset;
                break;
            case "Z-":
                dz = -offset;
                break;
        }
        
        params.put("dx", dx);
        params.put("dy", dy);
        params.put("dz", dz);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        // 缩放效果
        float scale = 1.0f - easedT * 0.3f;
        params.put("scale", scale);
        
        // 淡出效果
        float alpha = 1.0f - easedT;
        params.put("alpha", alpha);
    }
}

