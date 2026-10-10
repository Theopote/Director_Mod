package com.director.core.animation;

import com.director.core.animation.easing.EasingFunction;
import com.director.core.container.Container;
import java.util.Objects;

/** Authored transforms shared by enter and demolition clips; the sampler supplies the direction. */
public final class WholeContainerAnimationPresets {
    public enum Preset { GROW, FROM_ABOVE, FROM_BELOW, FROM_SIDE, SPIN, SPIN_GROW, SPIN_FLY, FADE, SOFT_GROW }
    private WholeContainerAnimationPresets() {}

    public static void apply(ClipAnimationConfig config, Preset preset, Container.BoundingBox bounds) {
        Objects.requireNonNull(preset);
        var settings = new WholeContainerAnimationConfig();
        settings.copyDustFrom(config.getWholeContainer());
        settings.setScale(1); settings.setOffset(0, 0, 0); settings.setRotation(0, 0, 0);
        settings.setPivot(WholeContainerAnimationConfig.Pivot.CENTER);
        float vertical = bounds == null ? 10 : Math.max(10, bounds.getHeight() * 1.5f);
        float horizontal = bounds == null ? 10 : Math.max(10, bounds.getWidth() * 1.5f);
        EasingFunction easing = EasingFunction.EASE_OUT_CUBIC;
        switch (preset) {
            case GROW -> {
                settings.setEffect(WholeContainerAnimationConfig.Effect.SCALE);
                settings.setPivot(WholeContainerAnimationConfig.Pivot.BOTTOM_CENTER);
                settings.setScale(0);
            }
            case FROM_ABOVE, FROM_BELOW, FROM_SIDE -> {
                settings.setEffect(WholeContainerAnimationConfig.Effect.FLY);
                if (preset == Preset.FROM_SIDE) settings.setOffset(-horizontal, 0, 0);
                else settings.setOffset(0, preset == Preset.FROM_ABOVE ? vertical : -vertical, 0);
            }
            case SPIN -> {
                settings.setEffect(WholeContainerAnimationConfig.Effect.ROTATE);
                settings.setRotation(0, 720, 0);
                easing = EasingFunction.EASE_IN_OUT_CUBIC;
            }
            case SPIN_GROW -> {
                settings.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
                settings.setPivot(WholeContainerAnimationConfig.Pivot.BOTTOM_CENTER);
                settings.setScale(0); settings.setRotation(0, 360, 0);
                easing = EasingFunction.EASE_IN_OUT_CUBIC;
            }
            case FADE, SOFT_GROW -> {
                settings.setEffect(preset == Preset.FADE ? WholeContainerAnimationConfig.Effect.FADE
                        : WholeContainerAnimationConfig.Effect.TRANSFORM);
                settings.setTranslationEnabled(false); settings.setRotationEnabled(false);
                settings.setScaleEnabled(preset == Preset.SOFT_GROW); settings.setOpacityEnabled(true);
                settings.setOpacity(0); settings.setScale(preset == Preset.SOFT_GROW ? 0.8f : 1);
            }
            case SPIN_FLY -> {
                settings.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
                settings.setOffset(0, vertical, 0); settings.setRotation(0, 720, 0);
                easing = EasingFunction.EASE_IN_OUT_CUBIC;
            }
        }
        settings.setTranslationEnabled(settings.getEffect() == WholeContainerAnimationConfig.Effect.FLY || preset == Preset.SPIN_FLY);
        settings.setRotationEnabled(settings.getEffect() == WholeContainerAnimationConfig.Effect.ROTATE
                || preset == Preset.SPIN_GROW || preset == Preset.SPIN_FLY);
        settings.setScaleEnabled(settings.getEffect() == WholeContainerAnimationConfig.Effect.SCALE
                || preset == Preset.SPIN_GROW || preset == Preset.SOFT_GROW);
        // Timing and the hidden per-block configuration belong to the user's clip.
        config.setWholeContainer(settings);
        config.setEasing(easing.name());
    }

    /** Reset only the current effect's transform, including its pivot. */
    public static void resetTransform(ClipAnimationConfig config) {
        var effect = config.getWholeContainer().getEffect();
        var settings = new WholeContainerAnimationConfig();
        settings.copyDustFrom(config.getWholeContainer());
        settings.setEffect(effect); settings.setScale(1);
        settings.setOffset(0, 0, 0); settings.setRotation(0, 0, 0);
        switch (effect) {
            case SCALE -> settings.setScale(0);
            case FLY -> {
                settings.setPivot(WholeContainerAnimationConfig.Pivot.CENTER);
                settings.setOffset(0, 10, 0);
            }
            case ROTATE -> {
                settings.setPivot(WholeContainerAnimationConfig.Pivot.CENTER);
                settings.setRotation(0, 180, 0);
            }
            case FADE -> settings.setOpacity(0);
            case INSTANT -> { }
            case TRANSFORM -> {
                settings.setScale(0); settings.setOffset(0, 10, 0); settings.setRotation(0, 180, 0);
            }
        }
        config.setWholeContainer(settings);
    }
}
