package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 爆炸（退场）动画变换器
 *
 * <p>方块向随机方向散射并逐渐淡出。适合擦除片段的“爆炸/碎片散射”效果。
 */
public class ExplodeTransformer implements IAnimationTransformer {

    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        float radius = getFloatParam(params, "radius", 2.0f);
        float upBias = getFloatParam(params, "upBias", 0.25f);
        float scaleMin = getFloatParam(params, "scaleMin", 0.7f);

        Vec3 dir = randomUnit(random, upBias);
        float t = progress;
        result.translateX = dir.x * radius * t;
        result.translateY = dir.y * radius * t;
        result.translateZ = dir.z * radius * t;

        float scale = 1.0f - (1.0f - clamp01(scaleMin)) * t;
        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;

        result.opacity = 1.0f - t;
        return result;
    }

    @Override
    public String getDisplayName() {
        return "爆炸";
    }

    @Override
    public String getDescription() {
        return "方块向外散射并淡出，适合作为擦除片段的退场效果";
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("radius", 2.0f);
        params.put("upBias", 0.25f);
        params.put("scaleMin", 0.7f);
        return params;
    }

    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_CUBIC";
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) return false;
        boolean modified = false;

        UIHelper.label("爆炸参数");

        float radius = getFloatParam(params, "radius", 2.0f);
        float[] radiusArr = {radius};
        if (UIHelper.dragFloat("散射半径", radiusArr, 0.1f, 0.1f, 20.0f, "%.1f")) {
            params.put("radius", radiusArr[0]);
            modified = true;
        }

        float upBias = getFloatParam(params, "upBias", 0.25f);
        float[] upBiasArr = {upBias};
        if (UIHelper.dragFloat("上抛偏置", upBiasArr, 0.05f, -1.0f, 2.0f, "%.2f")) {
            params.put("upBias", upBiasArr[0]);
            modified = true;
        }

        float scaleMin = getFloatParam(params, "scaleMin", 0.7f);
        float[] scaleMinArr = {scaleMin};
        if (UIHelper.dragFloat("最小缩放", scaleMinArr, 0.05f, 0.0f, 1.0f, "%.2f")) {
            params.put("scaleMin", scaleMinArr[0]);
            modified = true;
        }

        return modified;
    }

    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float radius = getFloatParam(params, "radius", 2.0f);
        float upBias = getFloatParam(params, "upBias", 0.25f);
        float scaleMin = getFloatParam(params, "scaleMin", 0.7f);

        Vec3 dir = randomUnit(random, upBias);
        float t = easedT;

        params.put("dx", dir.x * radius * t);
        params.put("dy", dir.y * radius * t);
        params.put("dz", dir.z * radius * t);

        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);

        float scale = 1.0f - (1.0f - clamp01(scaleMin)) * t;
        params.put("scale", scale);
        params.put("alpha", 1.0f - t);
    }

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

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static Vec3 randomUnit(Random random, float upBias) {
        // 简单稳定：随机球面方向 + Y 偏置
        float x = random.nextFloat() * 2.0f - 1.0f;
        float y = random.nextFloat() * 2.0f - 1.0f;
        float z = random.nextFloat() * 2.0f - 1.0f;
        y += upBias;

        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-4f) {
            return new Vec3(0f, 1f, 0f);
        }
        return new Vec3(x / len, y / len, z / len);
    }

    private record Vec3(float x, float y, float z) {
    }
}


