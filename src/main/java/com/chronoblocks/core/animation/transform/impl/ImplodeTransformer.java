package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 收缩（退场）动画变换器
 *
 * <p>方块逐渐缩小并淡出（可选轻微下沉），适合作为擦除片段的“收缩”效果。
 */
public class ImplodeTransformer implements IAnimationTransformer {

    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        float sink = getFloatParam(params, "sink", 0.35f);
        float scaleMin = getFloatParam(params, "scaleMin", 0.0f);

        float scale = 1.0f - (1.0f - clamp01(scaleMin)) * progress;

        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;
        result.translateY = -sink * progress;
        result.opacity = 1.0f - progress;
        return result;
    }

    @Override
    public String getDisplayName() {
        return "收缩";
    }

    @Override
    public String getDescription() {
        return "方块缩小并淡出（可选下沉），适合擦除片段的退场效果";
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("sink", 0.35f);
        params.put("scaleMin", 0.0f);
        return params;
    }

    @Override
    public String getRecommendedEasing() {
        return "EASE_IN_CUBIC";
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) return false;
        boolean modified = false;

        UIHelper.label("收缩参数");

        float sink = getFloatParam(params, "sink", 0.35f);
        float[] sinkArr = {sink};
        if (UIHelper.dragFloat("下沉距离", sinkArr, 0.05f, 0.0f, 5.0f, "%.2f")) {
            params.put("sink", sinkArr[0]);
            modified = true;
        }

        float scaleMin = getFloatParam(params, "scaleMin", 0.0f);
        float[] scaleMinArr = {scaleMin};
        if (UIHelper.dragFloat("最小缩放", scaleMinArr, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("scaleMin", scaleMinArr[0]);
            modified = true;
        }

        return modified;
    }

    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float sink = getFloatParam(params, "sink", 0.35f);
        float scaleMin = getFloatParam(params, "scaleMin", 0.0f);

        float scale = 1.0f - (1.0f - clamp01(scaleMin)) * easedT;

        params.put("dx", 0f);
        params.put("dy", -sink * easedT);
        params.put("dz", 0f);

        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);

        params.put("scale", scale);
        params.put("alpha", 1.0f - easedT);
    }

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

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}


