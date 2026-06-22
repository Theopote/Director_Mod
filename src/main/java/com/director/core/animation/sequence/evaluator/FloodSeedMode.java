package com.director.core.animation.sequence.evaluator;

import com.director.i18n.SequenceEnumI18n;

/**
 * 泛洪蔓延起点模式：决定每层泛洪蔓延的起始点选择方式。
 */
public enum FloodSeedMode {
    LAYER_CENTER,
    LAYER_MIN_CORNER,
    LAYER_RANDOM,
    GLOBAL_RANDOM;

    public String getDisplayName() {
        return SequenceEnumI18n.floodSeedName(this);
    }
}
