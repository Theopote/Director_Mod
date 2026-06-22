package com.director.core.animation.sequence.evaluator;

import com.director.i18n.SequenceEnumI18n;

/**
 * 线性轴方向枚举
 *
 * <p>用于线性投影排序算子，定义扫描方向
 */
public enum LinearAxis {
    X,
    MINUS_X,
    Y,
    MINUS_Y,
    Z,
    MINUS_Z,
    XZ,
    XY,
    YZ,
    XYZ;

    public String getDisplayName() {
        return SequenceEnumI18n.linearAxisName(this);
    }

    public String getDescription() {
        return SequenceEnumI18n.linearAxisDescription(this);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * 获取轴向量字符串（用于传递给排序算子）
     */
    public String toAxisString() {
        return switch (this) {
            case X -> "X";
            case MINUS_X -> "-X";
            case Y -> "Y";
            case MINUS_Y -> "-Y";
            case Z -> "Z";
            case MINUS_Z -> "-Z";
            case XZ -> "XZ";
            case XY -> "XY";
            case YZ -> "YZ";
            case XYZ -> "XYZ";
        };
    }
}
