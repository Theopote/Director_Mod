package com.director.i18n;

import com.director.core.animation.sequence.ConcurrencyMode;
import com.director.core.animation.sequence.SequenceMode;
import com.director.core.animation.sequence.TimeMappingStrategy;
import com.director.core.animation.sequence.evaluator.FillType;
import com.director.core.animation.sequence.evaluator.FlowType;
import com.director.core.animation.sequence.evaluator.FloodSeedMode;
import com.director.core.animation.sequence.evaluator.LinearAxis;
import com.director.core.animation.sequence.evaluator.OriginType;

/**
 * 序列行为相关枚举的 lang 键解析（{@code gui.director.animation.sequence.*}）。
 */
public final class SequenceEnumI18n {

    private static final String FLOW_PREFIX = "gui.director.animation.sequence.flow.";
    private static final String ORIGIN_PREFIX = "gui.director.animation.sequence.origin.";
    private static final String FILL_PREFIX = "gui.director.animation.sequence.fill.";
    private static final String FLOOD_SEED_PREFIX = "gui.director.animation.sequence.flood_seed.";
    private static final String LINEAR_AXIS_PREFIX = "gui.director.animation.sequence.linear_axis.";
    private static final String CONCURRENCY_PREFIX = "gui.director.animation.sequence.concurrency.";
    private static final String TIME_MAPPING_PREFIX = "gui.director.animation.sequence.time_mapping.";
    private static final String MODE_PREFIX = "gui.director.animation.sequence.mode.";

    private SequenceEnumI18n() {
    }

    public static String flowName(FlowType type) {
        return DirectorI18n.translate(FLOW_PREFIX + type.name().toLowerCase());
    }

    public static String originName(OriginType type) {
        return DirectorI18n.translate(ORIGIN_PREFIX + type.name().toLowerCase());
    }

    public static String fillName(FillType type) {
        return DirectorI18n.translate(FILL_PREFIX + type.name().toLowerCase());
    }

    public static String floodSeedName(FloodSeedMode mode) {
        return DirectorI18n.translate(FLOOD_SEED_PREFIX + mode.name().toLowerCase());
    }

    public static String linearAxisName(LinearAxis axis) {
        return DirectorI18n.translate(LINEAR_AXIS_PREFIX + axis.name().toLowerCase() + ".name");
    }

    public static String linearAxisDescription(LinearAxis axis) {
        return DirectorI18n.translate(LINEAR_AXIS_PREFIX + axis.name().toLowerCase() + ".desc");
    }

    public static String concurrencyName(ConcurrencyMode mode) {
        return DirectorI18n.translate(CONCURRENCY_PREFIX + mode.name().toLowerCase() + ".name");
    }

    public static String concurrencyDescription(ConcurrencyMode mode) {
        return DirectorI18n.translate(CONCURRENCY_PREFIX + mode.name().toLowerCase() + ".desc");
    }

    public static String timeMappingName(TimeMappingStrategy strategy) {
        return DirectorI18n.translate(TIME_MAPPING_PREFIX + strategy.name().toLowerCase() + ".name");
    }

    public static String timeMappingDescription(TimeMappingStrategy strategy) {
        return DirectorI18n.translate(TIME_MAPPING_PREFIX + strategy.name().toLowerCase() + ".desc");
    }

    public static String sequenceModeName(SequenceMode mode) {
        return DirectorI18n.translate(MODE_PREFIX + mode.name().toLowerCase() + ".name");
    }

    public static String sequenceModeDescription(SequenceMode mode) {
        return DirectorI18n.translate(MODE_PREFIX + mode.name().toLowerCase() + ".desc");
    }
}
