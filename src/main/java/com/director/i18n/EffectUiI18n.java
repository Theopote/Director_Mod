package com.director.i18n;

import com.director.core.animation.effect.EffectType;

/**
 * 特效 {@code renderUI} 参数面板文案。
 */
public final class EffectUiI18n {

    private EffectUiI18n() {
    }

    public static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    public static String section(EffectType type) {
        return tr(switch (type) {
            case FLASH_WHITE -> DirectorLang.Properties.EffectUi.SECTION_FLASH_WHITE;
            case DUST_BURST -> DirectorLang.Properties.EffectUi.SECTION_DUST_BURST;
            case MAGIC_FLARE -> DirectorLang.Properties.EffectUi.SECTION_MAGIC_FLARE;
            case GLOW_PULSE -> DirectorLang.Properties.EffectUi.SECTION_GLOW_PULSE;
            case IMPACT_RIPPLE -> DirectorLang.Properties.EffectUi.SECTION_IMPACT_RIPPLE;
            case MOTION_TRAIL -> DirectorLang.Properties.EffectUi.SECTION_MOTION_TRAIL;
        });
    }

    public static String intensity() {
        return tr(DirectorLang.Properties.EffectUi.INTENSITY);
    }

    public static String radius() {
        return tr(DirectorLang.Properties.EffectUi.RADIUS);
    }

    public static String scatterRadius() {
        return tr(DirectorLang.Properties.EffectUi.SCATTER_RADIUS);
    }

    public static String spreadRadius() {
        return tr(DirectorLang.Properties.EffectUi.SPREAD_RADIUS);
    }

    public static String density() {
        return tr(DirectorLang.Properties.EffectUi.DENSITY);
    }

    public static String heightJitter() {
        return tr(DirectorLang.Properties.EffectUi.HEIGHT_JITTER);
    }

    public static String spinSpeed() {
        return tr(DirectorLang.Properties.EffectUi.SPIN_SPEED);
    }

    public static String pulseCount() {
        return tr(DirectorLang.Properties.EffectUi.PULSE_COUNT);
    }

    public static String cloud() {
        return tr(DirectorLang.Properties.EffectUi.CLOUD);
    }

    public static String yOffset() {
        return tr(DirectorLang.Properties.EffectUi.Y_OFFSET);
    }

    public static String flashIntensityTooltip() {
        return tr(DirectorLang.Properties.EffectUi.FLASH_INTENSITY_TOOLTIP);
    }

    public static String dustIntensityTooltip() {
        return tr(DirectorLang.Properties.EffectUi.DUST_INTENSITY_TOOLTIP);
    }

    public static String dustRadiusTooltip() {
        return tr(DirectorLang.Properties.EffectUi.DUST_RADIUS_TOOLTIP);
    }
}
