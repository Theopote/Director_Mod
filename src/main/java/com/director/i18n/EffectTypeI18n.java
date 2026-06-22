package com.director.i18n;

import com.director.core.animation.effect.EffectType;

/**
 * 特效类型名称与描述的 lang 键解析（{@code gui.director.animation.effect.type.{type}.*}）。
 */
public final class EffectTypeI18n {

    private static final String PREFIX = "gui.director.animation.effect.type.";

    private EffectTypeI18n() {
    }

    public static String nameKey(EffectType type) {
        return PREFIX + type.name().toLowerCase() + ".name";
    }

    public static String descKey(EffectType type) {
        return PREFIX + type.name().toLowerCase() + ".desc";
    }

    public static String name(EffectType type) {
        return DirectorI18n.translate(nameKey(type));
    }

    public static String description(EffectType type) {
        return DirectorI18n.translate(descKey(type));
    }
}
