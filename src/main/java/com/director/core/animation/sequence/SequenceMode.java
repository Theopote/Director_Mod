package com.director.core.animation.sequence;

import com.director.i18n.SequenceEnumI18n;

/**
 * 序列模式枚举
 *
 * <p>定义不同的方块执行顺序模式。
 */
public enum SequenceMode {
    EVALUATOR_BASED;

    public String getDisplayName() {
        return SequenceEnumI18n.sequenceModeName(this);
    }

    public String getDescription() {
        return SequenceEnumI18n.sequenceModeDescription(this);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
