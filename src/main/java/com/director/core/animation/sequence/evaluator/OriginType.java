package com.director.core.animation.sequence.evaluator;

import com.director.i18n.SequenceEnumI18n;

/**
 * 起始锚点：决定波浪从哪个位置开始扩散。
 */
public enum OriginType {
    MIN_CORNER,
    CENTER,
    BOTTOM_CENTER,
    TOP_CENTER,
    CUSTOM;

    public String getDisplayName() {
        return SequenceEnumI18n.originName(this);
    }
}
