package com.director.core.animation.transform.impl;

import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.container.AnimationMode;
import com.director.i18n.AnimationModeI18n;

/**
 * 与 {@link AnimationMode} 绑定的变换器基类，统一名称/描述 i18n。
 */
public abstract class AbstractModeTransformer implements IAnimationTransformer {

    private final AnimationMode mode;

    protected AbstractModeTransformer(AnimationMode mode) {
        this.mode = mode;
    }

    protected AnimationMode mode() {
        return mode;
    }

    @Override
    public String getDisplayName() {
        return AnimationModeI18n.name(mode);
    }

    @Override
    public String getDescription() {
        return AnimationModeI18n.description(mode);
    }
}
