package com.director.core.animation.sequence.evaluator;

import com.director.i18n.SequenceEnumI18n;

/**
 * 宏观流向：决定方块出现的整体波浪走势。
 */
public enum FlowType {
    NONE,
    PLANAR,
    SPHERICAL,
    CYLINDRICAL,
    RANDOM;

    public String getDisplayName() {
        return SequenceEnumI18n.flowName(this);
    }
}
