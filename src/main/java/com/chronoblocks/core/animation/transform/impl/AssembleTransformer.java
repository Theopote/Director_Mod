package com.chronoblocks.core.animation.transform.impl;

import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.ui.UIHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 碎片聚合动画变换器
 *
 * <p>方块从随机偏移位置向原位聚拢，同时从透明到不透明。
 * 该模式在 EXIT 阶段会被引擎自动反向播放，从而形成“碎片散射”的退场效果。
 */
public class AssembleTransformer implements IAnimationTransformer {

    @Override
    public TransformResult transform(float progress, Map<String, Object> params, Random random) {
        TransformResult result = new TransformResult();
        float radius = getFloatParam(params, "radius", 2.5f);
        float vertical = getFloatParam(params, "vertical", 1.0f);

        Vec3 dir = randomUnit(random);
        float offset = radius * (1.0f - progress);

        result.translateX = dir.x * offset;
        result.translateY = dir.y * offset * vertical;
        result.translateZ = dir.z * offset;

        result.opacity = progress;
        float scale = Math.max(0.0f, Math.min(1.0f, progress));
        result.scaleX = scale;
        result.scaleY = scale;
        result.scaleZ = scale;

        return result;
    }

    @Override
    public String getDisplayName() {
        return "碎片聚合";
    }

    @Override
    public String getDescription() {
        return "方块从周围聚拢到原位，适合组装/聚合效果（Exit 阶段会形成散射退场）";
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("radius", 2.5f);
        params.put("vertical", 1.0f);
        return params;
    }

    @Override
    public String getRecommendedEasing() {
        return "EASE_OUT_QUINT";
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!UIHelper.isUIAvailable()) return false;
        boolean modified = false;

        UIHelper.label("碎片聚合参数");

        float radius = getFloatParam(params, "radius", 2.5f);
        float[] radiusArr = {radius};
        if (UIHelper.dragFloat("偏移半径", radiusArr, 0.1f, 0.1f, 20.0f, "%.1f")) {
            params.put("radius", radiusArr[0]);
            modified = true;
        }

        float vertical = getFloatParam(params, "vertical", 1.0f);
        float[] verticalArr = {vertical};
        if (UIHelper.dragFloat("垂直权重", verticalArr, 0.05f, 0.0f, 3.0f, "%.2f")) {
            params.put("vertical", verticalArr[0]);
            modified = true;
        }

        return modified;
    }

    @Override
    public void apply(float easedT, Map<String, Object> params, Random random) {
        float radius = getFloatParam(params, "radius", 2.5f);
        float vertical = getFloatParam(params, "vertical", 1.0f);

        Vec3 dir = randomUnit(random);
        float offset = radius * (1.0f - easedT);

        params.put("dx", dir.x * offset);
        params.put("dy", dir.y * offset * vertical);
        params.put("dz", dir.z * offset);

        params.put("yaw", 0f);
        params.put("pitch", 0f);
        params.put("roll", 0f);

        float s = Math.max(0.0f, Math.min(1.0f, easedT));
        params.put("scale", s);
        params.put("alpha", s);
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

    private static Vec3 randomUnit(Random random) {
        float x = random.nextFloat() * 2.0f - 1.0f;
        float y = random.nextFloat() * 2.0f - 1.0f;
        float z = random.nextFloat() * 2.0f - 1.0f;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-4f) {
            return new Vec3(0f, 1f, 0f);
        }
        return new Vec3(x / len, y / len, z / len);
    }

    private record Vec3(float x, float y, float z) {
    }
}


