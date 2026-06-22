package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.container.AnimationMode;

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
                "动画模式与节奏",
                null,
                new AnimationMode[] {
                        AnimationMode.INSTANT, AnimationMode.FADE_IN, AnimationMode.GROW_UP,
                        AnimationMode.EXPAND, AnimationMode.ASSEMBLE, AnimationMode.FALL_DOWN,
                        AnimationMode.RISE_UP, AnimationMode.FLY_IN, AnimationMode.POP_IN, AnimationMode.SHAKER
                },
                AnimationMode.FADE_IN,
                "选择出现片段的入场方式",
                "控制单个方块从开始运动到完全就位所用的时间。\n建议不超过片段总时长。",
                "透明度淡入",
                "勾选后，动画过程中会从完全透明逐渐变为不透明。");
    }

    private static TimelineClipAnimationPolicy demolition() {
        return new TimelineClipAnimationPolicy(
                ClipAnimationConfig.AnimationPhase.EXIT,
                "清除动画模式与节奏",
                "拆除容器片段：方块会先播放退出动画，然后在动画结束后被清除",
                new AnimationMode[] {
                        AnimationMode.INSTANT, AnimationMode.FADE_OUT, AnimationMode.EXPLODE,
                        AnimationMode.IMPLODE, AnimationMode.SHRINK_OUT, AnimationMode.GRAVITY_FALL,
                        AnimationMode.FLY_OUT
                },
                AnimationMode.FADE_OUT,
                "选择擦除片段的退出方式（方块先播放退出动画，再被清除）",
                "控制单个方块从开始运动到完全消失所用的时间。\n建议不超过片段总时长。",
                "透明度淡入",
                "勾选后，动画过程中会从完全透明逐渐变为不透明。");
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
            return "方块向外散射并淡出（由'碎片聚合'自动反向播放得到），适合擦除片段的碎片效果";
        }
        return mode.getDescription();
    }
}
