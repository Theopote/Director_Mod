package com.director.i18n;

import com.director.core.container.AnimationMode;

/**
 * Transformer {@code renderUI} 文案（复用 clip_ui.transformer 键 + animation.transformer.*）。
 */
public final class TransformerUiI18n {

    private TransformerUiI18n() {
    }

    public static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    public static String section(AnimationMode mode) {
        return tr(switch (mode) {
            case FADE_IN -> DirectorLang.Properties.TransformerUi.SECTION_FADE_IN;
            case FADE_OUT -> DirectorLang.Properties.TransformerUi.SECTION_FADE_OUT;
            case FLY_IN -> DirectorLang.Properties.TransformerUi.SECTION_FLY_IN;
            case FLY_OUT -> DirectorLang.Properties.TransformerUi.SECTION_FLY_OUT;
            case FALL_DOWN -> DirectorLang.Properties.TransformerUi.SECTION_FALL_DOWN;
            case GRAVITY_FALL -> DirectorLang.Properties.TransformerUi.SECTION_GRAVITY_FALL;
            case GROW_UP -> DirectorLang.Properties.TransformerUi.SECTION_GROW_UP;
            case RISE_UP -> DirectorLang.Properties.TransformerUi.SECTION_RISE_UP;
            case SHAKER -> DirectorLang.Properties.TransformerUi.SECTION_SHAKER;
            case POP_IN -> DirectorLang.Properties.TransformerUi.SECTION_POP_IN;
            case SHRINK_OUT -> DirectorLang.Properties.TransformerUi.SECTION_SHRINK_OUT;
            case IMPLODE -> DirectorLang.Properties.TransformerUi.SECTION_IMPLODE;
            case EXPLODE -> DirectorLang.Properties.TransformerUi.SECTION_EXPLODE;
            case EXPAND -> DirectorLang.Properties.TransformerUi.SECTION_EXPAND;
            case ASSEMBLE -> DirectorLang.Properties.TransformerUi.SECTION_ASSEMBLE;
            default -> DirectorLang.Properties.TransformerUi.SECTION_GENERIC;
        });
    }

    public static String flyDirection() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_FLY_DIRECTION);
    }

    public static String flyOutDirection() {
        return tr(DirectorLang.Properties.TransformerUi.FLY_OUT_DIRECTION);
    }

    public static String flyDistance() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_FLY_DISTANCE);
    }

    public static String blocksFormat() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_BLOCKS);
    }

    public static String blocksPerSecondFormat() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_BLOCKS_PER_SECOND);
    }

    public static String fallHeight() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_FALL_HEIGHT);
    }

    public static String bounciness() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_BOUNCINESS);
    }

    public static String fallSpeed() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_FALL_SPEED);
    }

    public static String tumble() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_TUMBLE);
    }

    public static String enableTumble() {
        return tr(DirectorLang.Properties.TransformerUi.ENABLE_TUMBLE);
    }

    public static String tumbleIntensity() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_TUMBLE_INTENSITY);
    }

    public static String growAxis() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_GROW_AXIS);
    }

    public static String scalePivot() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_SCALE_PIVOT);
    }

    public static String[] pivotLabels() {
        return new String[]{
                tr(DirectorLang.Properties.TransformerUi.PIVOT_BOTTOM),
                tr(DirectorLang.Properties.TransformerUi.PIVOT_CENTER)
        };
    }

    public static String riseDistance() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_RISE_DISTANCE);
    }

    public static String startDepth() {
        return tr(DirectorLang.Properties.TransformerUi.START_DEPTH);
    }

    public static String shakeIntensity() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_SHAKE_INTENSITY);
    }

    public static String decaySpeed() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_DECAY_SPEED);
    }

    public static String shakeDuration() {
        return tr(DirectorLang.Properties.TransformerUi.SHAKE_DURATION);
    }

    public static String targetScale() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_TARGET_SCALE);
    }

    public static String sinkDistance() {
        return tr(DirectorLang.Properties.TransformerUi.SINK_DISTANCE);
    }

    public static String expandSpeed() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_EXPAND_SPEED);
    }

    public static String useDistance() {
        return tr(DirectorLang.Properties.TransformerUi.USE_DISTANCE);
    }

    public static String useDistanceTooltip() {
        return tr(DirectorLang.Properties.TransformerUi.USE_DISTANCE_TOOLTIP);
    }

    public static String spreadRadius() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_SPREAD_RADIUS);
    }

    public static String verticalWeight() {
        return tr(DirectorLang.Properties.TransformerUi.VERTICAL_WEIGHT);
    }

    public static String scatterRadius() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_SCATTER_RADIUS);
    }

    public static String upBias() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_UP_BIAS);
    }

    public static String scaleMin() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_SCALE_MIN);
    }

    public static String startOpacity() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_START_OPACITY);
    }

    public static String endOpacity() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_END_OPACITY);
    }

    public static String initialScale() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_INITIAL_SCALE);
    }

    public static String elasticity() {
        return tr(DirectorLang.Properties.AdapterTimelineClipUi.TRANSFORMER_ELASTICITY);
    }
}
