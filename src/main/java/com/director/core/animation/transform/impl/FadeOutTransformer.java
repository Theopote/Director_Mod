package com.director.core.animation.transform.impl;

import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 淡出动画变换器
 *
 * <p>方块逐渐变得透明，适合作为擦除片段（退出动画）。
 */
public class FadeOutTransformer implements IAnimationTransformer {

    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        float startOpacity = getFloatParam(params, "startOpacity", 1.0f);
        float endOpacity = getFloatParam(params, "endOpacity", 0.0f);
        result.opacity = startOpacity + (endOpacity - startOpacity) * progress;
        return result;
    }

    @Override
    public String getDisplayName() {
        return "淡出";
    }

    @Override
    public String getDescription() {
        return "方块逐渐变得透明，适合退出/擦除效果";
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("startOpacity", 1.0f);
        params.put("endOpacity", 0.0f);
        return params;
    }

    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_QUAD";
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

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) {
            return false;
        }
        boolean modified = false;

        UIHelper.label("淡出参数");

        float startOpacity = getFloatParam(params, "startOpacity", 1.0f);
        float[] startArr = {startOpacity};
        if (UIHelper.dragFloat("起始透明度", startArr, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("startOpacity", startArr[0]);
            modified = true;
        }

        float endOpacity = getFloatParam(params, "endOpacity", 0.0f);
        float[] endArr = {endOpacity};
        if (UIHelper.dragFloat("结束透明度", endArr, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("endOpacity", endArr[0]);
            modified = true;
        }

        return modified;
    }

    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float startAlpha = getFloatParam(params, "startOpacity", 1.0f);
        float endAlpha = getFloatParam(params, "endOpacity", 0.0f);

        float alpha = startAlpha + (endAlpha - startAlpha) * easedT;

        params.put("dx", 0f);
        params.put("dy", 0f);
        params.put("dz", 0f);

        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);

        params.put("scale", 1f);
        params.put("alpha", alpha);
    }
}


