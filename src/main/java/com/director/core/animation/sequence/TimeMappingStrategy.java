package com.director.core.animation.sequence;

import com.director.i18n.SequenceEnumI18n;

/**
 * 时间映射策略
 *
 * <p>定义如何将排序得分（score）映射到具体的时间（startTime）。
 */
public enum TimeMappingStrategy {
    NORMALIZED_LINEAR,
    GROUP_FIRST,
    BATCH_MAPPING,
    STAGGERED;

    public String getDisplayName() {
        return SequenceEnumI18n.timeMappingName(this);
    }

    public String getDescription() {
        return SequenceEnumI18n.timeMappingDescription(this);
    }
}
