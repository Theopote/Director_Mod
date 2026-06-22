package com.director.i18n;

import com.director.core.animation.easing.EasingFunction;

import java.util.Map;

/**
 * 缓动函数名称与描述的 lang 键解析（{@code gui.director.animation.easing.{fn}.*}）。
 */
public final class EasingFunctionI18n {

    private static final String PREFIX = "gui.director.animation.easing.";

    /** 兼容旧存档中保存的中文显示名 */
    private static final Map<String, EasingFunction> LEGACY_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("线性", EasingFunction.LINEAR),
            Map.entry("缓入（二次）", EasingFunction.EASE_IN_QUAD),
            Map.entry("缓入（三次）", EasingFunction.EASE_IN_CUBIC),
            Map.entry("缓出（二次）", EasingFunction.EASE_OUT_QUAD),
            Map.entry("缓出（三次）", EasingFunction.EASE_OUT_CUBIC),
            Map.entry("缓出（四次）", EasingFunction.EASE_OUT_QUART),
            Map.entry("缓出（五次）", EasingFunction.EASE_OUT_QUINT),
            Map.entry("缓出（弹跳）", EasingFunction.EASE_OUT_BOUNCE),
            Map.entry("缓出（回弹）", EasingFunction.EASE_OUT_BACK),
            Map.entry("缓出（弹性）", EasingFunction.EASE_OUT_ELASTIC),
            Map.entry("缓入缓出（二次）", EasingFunction.EASE_IN_OUT_QUAD),
            Map.entry("缓入缓出（三次）", EasingFunction.EASE_IN_OUT_CUBIC)
    );

    private EasingFunctionI18n() {
    }

    public static String nameKey(EasingFunction fn) {
        return PREFIX + fn.name().toLowerCase() + ".name";
    }

    public static String descKey(EasingFunction fn) {
        return PREFIX + fn.name().toLowerCase() + ".desc";
    }

    public static String name(EasingFunction fn) {
        return DirectorI18n.translate(nameKey(fn));
    }

    public static String description(EasingFunction fn) {
        return DirectorI18n.translate(descKey(fn));
    }

    public static EasingFunction fromLegacyDisplayName(String displayName) {
        return displayName != null ? LEGACY_DISPLAY_NAMES.get(displayName) : null;
    }
}
