package com.director.core.container;

import com.director.i18n.AnimationModeI18n;

/**
 * 动画模式枚举
 * 定义容器的出现/消失方式
 *
 * @author Director Team
 * @version 4.0
 */
public enum AnimationMode {
    INSTANT,
    GROW_UP,
    GROW_DOWN,
    EXPAND,
    FADE_IN,
    FADE_OUT,
    EXPLODE,
    IMPLODE,
    SLICE,
    RANDOM,
    WAVE,
    SPIRAL,
    FALL_DOWN,
    RISE_UP,
    FLY_IN,
    ASSEMBLE,
    SHAKER,
    POP_IN,
    SHRINK_OUT,
    GRAVITY_FALL,
    FLY_OUT;

    public String getDisplayName() {
        return AnimationModeI18n.name(this);
    }

    public String getDescription() {
        return AnimationModeI18n.description(this);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
