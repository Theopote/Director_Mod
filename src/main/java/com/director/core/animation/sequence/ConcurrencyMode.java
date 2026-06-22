package com.director.core.animation.sequence;

import com.director.i18n.SequenceEnumI18n;

/**
 * 并发模式
 *
 * <p>控制方块动画的执行方式。
 */
public enum ConcurrencyMode {
    ONE_BY_ONE,
    ALL_AT_ONCE,
    IN_BATCHES,
    PARALLEL_LAYERS;

    public String getDisplayName() {
        return SequenceEnumI18n.concurrencyName(this);
    }

    public String getDescription() {
        return SequenceEnumI18n.concurrencyDescription(this);
    }
}
