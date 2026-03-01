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
        float distance = getFloatParam(params, "distance", 0.0f);
        
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
            result.translateY = -0.5f * (1.0f - progress) - distance * (1.0f - progress);
        } else if (distance > 0.0f && "Y".equalsIgnoreCase(axis)) {
            // 如果有升起距离，从下方升起
            result.translateY = -distance * (1.0f - progress);
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
        params.put("distance", 0.0f); // 升起距离（从底部向上的额外偏移）
        return params;
    }
    
    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_ELASTIC"; // 弹性效果适合生长
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
        
        // 升起距离
        float distance = getFloatParam(params, "distance", 0.0f);
        float[] distanceArray = {distance};
        if (UIHelper.dragFloat("升起距离", distanceArray, 0.1f, 0.0f, 10.0f, "%.1f 格")) {
            params.put("distance", distanceArray[0]);
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
        float distance = getFloatParam(params, "distance", 0.0f);

        // 根据锚点调整位移
        float dx = 0f, dy = 0f, dz = 0f;
        if ("BOTTOM".equalsIgnoreCase(pivot) && "Y".equalsIgnoreCase(axis)) {
            // 从底部生长：需要向下偏移，使底部固定
            dy = -0.5f * (1.0f - easedT) - distance * (1.0f - easedT);
        } else if (distance > 0.0f && "Y".equalsIgnoreCase(axis)) {
            // 如果有升起距离，从下方升起
            dy = -distance * (1.0f - easedT);
        }

        params.put("dx", dx);
        params.put("dy", dy);
        params.put("dz", dz);

        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);

        // ✅ 修复：使用非均匀缩放（按轴），而不是统一 scale
        // BlockAnimationEngine.applyTransformFromParams 现在支持 scaleX/scaleY/scaleZ
        switch (axis.toUpperCase()) {
            case "X" -> {
                params.put("scaleX", easedT);
                params.put("scaleY", 1.0f);
                params.put("scaleZ", 1.0f);
            }
            case "Z" -> {
                params.put("scaleX", 1.0f);
                params.put("scaleY", 1.0f);
                params.put("scaleZ", easedT);
            }
            case "XYZ" -> {
                params.put("scaleX", easedT);
                params.put("scaleY", easedT);
                params.put("scaleZ", easedT);
            }
            default -> { // "Y"
                params.put("scaleX", 1.0f);
                params.put("scaleY", easedT);
                params.put("scaleZ", 1.0f);
            }
        }
        // 清除统一 scale 键，避免被 applyTransformFromParams 误读
        params.remove("scale");

        params.put("alpha", Math.max(0.0f, Math.min(1.0f, easedT))); // 慢慢出现
    }
}

