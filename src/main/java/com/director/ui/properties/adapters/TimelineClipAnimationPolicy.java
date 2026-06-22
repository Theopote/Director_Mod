package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.container.AnimationMode;
import com.director.i18n.ClipPropertyUiI18n;
import com.director.i18n.DirectorLang;

/**
 * Phase-specific animation UI policy for container (ENTER) vs demolition (EXIT) clips.
 */
public final class TimelineClipAnimationPolicy {

    private final ClipAnimationConfig.AnimationPhase phase;
    private final String sectionTitle;
    private final String sectionHint;
    private final AnimationMode[] availableModes;
    private final AnimationMode invalidModeFallback;
    private final String modeComboTooltip;
    private final String durationTooltip;
    private final String opacityFadeLabel;
    private final String opacityFadeTooltip;

    private TimelineClipAnimationPolicy(
            ClipAnimationConfig.AnimationPhase phase,
            String sectionTitle,
            String sectionHint,
            AnimationMode[] availableModes,
            AnimationMode invalidModeFallback,
            String modeComboTooltip,
            String durationTooltip,
            String opacityFadeLabel,
            String opacityFadeTooltip) {
        this.phase = phase;
        this.sectionTitle = sectionTitle;
        this.sectionHint = sectionHint;
        this.availableModes = availableModes;
        this.invalidModeFallback = invalidModeFallback;
        this.modeComboTooltip = modeComboTooltip;
        this.durationTooltip = durationTooltip;
        this.opacityFadeLabel = opacityFadeLabel;
        this.opacityFadeTooltip = opacityFadeTooltip;
    }

    public static TimelineClipAnimationPolicy forVariant(TimelineClipUiVariant variant) {
        return switch (variant) {
            case CONTAINER -> container();
            case DEMOLITION -> demolition();
        };
    }

    private static TimelineClipAnimationPolicy container() {
        return new TimelineClipAnimationPolicy(
                ClipAnimationConfig.AnimationPhase.ENTER,
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_CONTAINER_SECTION),
                null,
                new AnimationMode[] {
                        AnimationMode.INSTANT, AnimationMode.FADE_IN, AnimationMode.GROW_UP,
                        AnimationMode.EXPAND, AnimationMode.ASSEMBLE, AnimationMode.FALL_DOWN,
                        AnimationMode.RISE_UP, AnimationMode.FLY_IN, AnimationMode.POP_IN, AnimationMode.SHAKER
                },
                AnimationMode.FADE_IN,
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_CONTAINER_MODE_TOOLTIP),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_CONTAINER_DURATION_TOOLTIP),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_CONTAINER_OPACITY_FADE),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_CONTAINER_OPACITY_FADE_TOOLTIP));
    }

    private static TimelineClipAnimationPolicy demolition() {
        return new TimelineClipAnimationPolicy(
                ClipAnimationConfig.AnimationPhase.EXIT,
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_DEMOLITION_SECTION),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_DEMOLITION_HINT),
                new AnimationMode[] {
                        AnimationMode.INSTANT, AnimationMode.FADE_OUT, AnimationMode.EXPLODE,
                        AnimationMode.IMPLODE, AnimationMode.SHRINK_OUT, AnimationMode.GRAVITY_FALL,
                        AnimationMode.FLY_OUT
                },
                AnimationMode.FADE_OUT,
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_DEMOLITION_MODE_TOOLTIP),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_DEMOLITION_DURATION_TOOLTIP),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_DEMOLITION_OPACITY_FADE),
                ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_DEMOLITION_OPACITY_FADE_TOOLTIP));
    }

    public ClipAnimationConfig.AnimationPhase phase() {
        return phase;
    }

    public String sectionTitle() {
        return sectionTitle;
    }

    public String sectionHint() {
        return sectionHint;
    }

    public AnimationMode[] availableModes() {
        return availableModes;
    }

    public AnimationMode invalidModeFallback() {
        return invalidModeFallback;
    }

    public String modeComboTooltip() {
        return modeComboTooltip;
    }

    public String durationTooltip() {
        return durationTooltip;
    }

    public String opacityFadeLabel() {
        return opacityFadeLabel;
    }

    public String opacityFadeTooltip() {
        return opacityFadeTooltip;
    }

    public String labelForMode(AnimationMode mode) {
        return mode.getDisplayName();
    }

    public String descriptionForMode(AnimationMode mode) {
        if (phase == ClipAnimationConfig.AnimationPhase.EXIT && mode == AnimationMode.ASSEMBLE) {
            return ClipPropertyUiI18n.tr(DirectorLang.Properties.AdapterTimelineClipUi.ANIMATION_POLICY_ASSEMBLE_EXIT_DESC);
        }
        return mode.getDescription();
    }
}
