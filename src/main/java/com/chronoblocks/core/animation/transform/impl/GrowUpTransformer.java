package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 向上生长动画变换器
 * 
 * <p>方块从底部向上缩放，模拟植物生长效果。
 * 支持不同轴向和锚点。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class GrowUpTransformer implements IAnimationTransformer {
    
    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        
        // 获取参数
        String axis = getStringParam(params, "axis", "Y");
        String pivot = getStringParam(params, "pivot", "CENTER"); // CENTER, BOTTOM
        
        // 计算缩放：从 0 到 1

        // 根据轴向应用缩放
        switch (axis.toUpperCase()) {
            case "X":
                result.scaleX = progress;
                break;
            case "Y":
                result.scaleY = progress;
                break;
            case "Z":
                result.scaleZ = progress;
                break;
            case "XYZ":
            default:
                result.scaleX = progress;
                result.scaleY = progress;
                result.scaleZ = progress;
                break;
        }
        
        // 根据锚点调整位移（Minecraft 方块原点在左下角）
        if ("BOTTOM".equalsIgnoreCase(pivot) && "Y".equalsIgnoreCase(axis)) {
            // 从底部生长：需要向下偏移，使底部固定
            result.translateY = -0.5f * (1.0f - progress);
        }
        
        return result;
    }
    
    @Override
    public String getDisplayName() {
        return "向上生长";
    }
    
    @Override
    public String getDescription() {
        return "方块从底部向上缩放，模拟植物生长";
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("axis", "Y");
        params.put("pivot", "BOTTOM");
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_ELASTIC"; // 弹性效果适合生长
    }
    
    // ========== 工具方法 ==========
    
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
        
        // 生长参数
        UIHelper.label("生长参数");
        
        // 生长轴向
        String axis = getStringParam(params, "axis", "Y");
        String[] axisOptions = {"Y", "X", "Z", "XYZ"};
        int axisIndex = 0;
        for (int i = 0; i < axisOptions.length; i++) {
            if (axisOptions[i].equals(axis)) {
                axisIndex = i;
                break;
            }
        }
        int[] axisIndexArray = {axisIndex};
        if (UIHelper.combo("生长轴向", axisIndexArray, axisOptions)) {
            params.put("axis", axisOptions[axisIndexArray[0]]);
            modified = true;
        }
        
        // 缩放锚点
        String pivot = getStringParam(params, "pivot", "BOTTOM");
        String[] pivotOptions = {"BOTTOM", "CENTER"};
        int pivotIndex = pivot.equals("BOTTOM") ? 0 : 1;
        int[] pivotIndexArray = {pivotIndex};
        if (UIHelper.combo("缩放锚点", pivotIndexArray, pivotOptions)) {
            params.put("pivot", pivotOptions[pivotIndexArray[0]]);
            modified = true;
        }
        
        return modified;
    }
    
    /**
     * 应用变换到参数 Map（新 API）
     * 
     * <p>从下往上生长：Y 轴缩放从 0 到 1，并调整位移使底部固定
     */
    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        String axis = getStringParam(params, "axis", "Y");
        String pivot = getStringParam(params, "pivot", "BOTTOM");

        // 根据轴向应用缩放（统一使用 scale 参数，BlockAnimationEngine 会统一应用）
        // 对于复杂的轴向缩放，可以使用 scaleX/scaleY/scaleZ，但当前约定是统一 scale

        // 根据锚点调整位移
        float dy = 0f;
        if ("BOTTOM".equalsIgnoreCase(pivot) && "Y".equalsIgnoreCase(axis)) {
            // 从底部生长：需要向下偏移，使底部固定
            dy = -0.5f * (1.0f - easedT);
        }
        
        params.put("dx", 0f);
        params.put("dy", dy);
        params.put("dz", 0f);
        
        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);
        
        params.put("scale", easedT);
        params.put("alpha", easedT); // 慢慢出现
    }
}

