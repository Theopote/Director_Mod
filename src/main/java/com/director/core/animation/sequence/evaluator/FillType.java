package com.director.core.animation.sequence.evaluator;

import com.director.i18n.SequenceEnumI18n;

/**
 * 微观填充：决定在同一个切面内方块的填充细节。
 */
public enum FillType {
    LINEAR_SCAN,
    RASTER,
    RADIAL,
    SPIRAL,
    NOISE,
    FLOOD;

    public String getDisplayName() {
        return SequenceEnumI18n.fillName(this);
    }
}
