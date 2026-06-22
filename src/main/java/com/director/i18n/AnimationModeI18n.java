package com.director.i18n;

import com.director.core.container.AnimationMode;

/**
 * 动画模式名称与描述的 lang 键解析（{@code gui.director.animation.mode.{mode}.*}）。
 */
public final class AnimationModeI18n {

    private static final String PREFIX = "gui.director.animation.mode.";

    private AnimationModeI18n() {
    }

    public static String nameKey(AnimationMode mode) {
        return PREFIX + mode.name().toLowerCase() + ".name";
    }

    public static String descKey(AnimationMode mode) {
        return PREFIX + mode.name().toLowerCase() + ".desc";
    }

    public static String name(AnimationMode mode) {
        return DirectorI18n.translate(nameKey(mode));
    }

    public static String description(AnimationMode mode) {
        return DirectorI18n.translate(descKey(mode));
    }
}
