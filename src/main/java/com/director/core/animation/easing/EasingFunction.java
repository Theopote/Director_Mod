package com.director.core.animation.easing;

import com.director.i18n.EasingFunctionI18n;

/**
 * 缓动函数枚举
 *
 * <p>定义各种缓动曲线，用于将线性进度转换为平滑的动画效果。
 * 所有函数接受 t (0.0 - 1.0) 并返回缓动后的值 (0.0 - 1.0)。
 *
 * <p>参考：https://easings.net/
 */
public enum EasingFunction {

    LINEAR,
    EASE_IN_QUAD,
    EASE_IN_CUBIC,
    EASE_OUT_QUAD,
    EASE_OUT_CUBIC,
    EASE_OUT_QUART,
    EASE_OUT_QUINT,
    EASE_OUT_BOUNCE,
    EASE_OUT_BACK,
    EASE_OUT_ELASTIC,
    EASE_IN_OUT_QUAD,
    EASE_IN_OUT_CUBIC;

    private static final java.util.Map<String, EasingFunction> LOOKUP_BY_NAME;

    static {
        EasingFunction[] vals = values();
        LOOKUP_BY_NAME = new java.util.HashMap<>(vals.length * 2);
        for (EasingFunction func : vals) {
            LOOKUP_BY_NAME.put(func.name(), func);
            LOOKUP_BY_NAME.put(func.name().toLowerCase(), func);
        }
    }

    public String getDisplayName() {
        return EasingFunctionI18n.name(this);
    }

    public String getDescription() {
        return EasingFunctionI18n.description(this);
    }

    /**
     * 应用缓动函数
     */
    public float apply(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));

        return switch (this) {
            case LINEAR -> t;

            case EASE_IN_QUAD -> t * t;
            case EASE_IN_CUBIC -> t * t * t;

            case EASE_OUT_QUAD -> 1.0f - (1.0f - t) * (1.0f - t);
            case EASE_OUT_CUBIC -> 1.0f - (float) Math.pow(1.0 - t, 3);
            case EASE_OUT_QUART -> 1.0f - (float) Math.pow(1.0 - t, 4);
            case EASE_OUT_QUINT -> 1.0f - (float) Math.pow(1.0 - t, 5);
            case EASE_OUT_BOUNCE -> easeOutBounce(t);
            case EASE_OUT_BACK -> easeOutBack(t);
            case EASE_OUT_ELASTIC -> easeOutElastic(t);

            case EASE_IN_OUT_QUAD -> t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 2) / 2.0f;
            case EASE_IN_OUT_CUBIC -> t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0f;
        };
    }

    private float easeOutBounce(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;

        if (t < 1.0f / d1) {
            return n1 * t * t;
        } else if (t < 2.0f / d1) {
            return n1 * (t -= 1.5f / d1) * t + 0.75f;
        } else if (t < 2.5f / d1) {
            return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        } else {
            return n1 * (t -= 2.625f / d1) * t + 0.984375f;
        }
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0, 3) + c1 * (float) Math.pow(t - 1.0, 2);
    }

    private float easeOutElastic(float t) {
        float c4 = (2.0f * (float) Math.PI) / 3.0f;
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return (float) Math.pow(2.0, -10.0 * t) * (float) Math.sin((t * 10.0 - 0.75) * c4) + 1.0f;
    }

    /**
     * 从字符串名称获取缓动函数（支持枚举名、当前语言显示名、旧版中文显示名）。
     */
    public static EasingFunction fromString(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }

        EasingFunction result = LOOKUP_BY_NAME.get(name);
        if (result != null) return result;
        result = LOOKUP_BY_NAME.get(name.toUpperCase());
        if (result != null) return result;

        result = EasingFunctionI18n.fromLegacyDisplayName(name);
        if (result != null) return result;

        for (EasingFunction func : values()) {
            String displayName = func.getDisplayName();
            if (displayName.equals(name) || displayName.contains(name)) {
                return func;
            }
        }

        return LINEAR;
    }
}
