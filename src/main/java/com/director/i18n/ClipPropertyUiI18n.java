package com.director.i18n;

import com.director.ui.theme.Icons;

/**
 * Timeline clip property panel UI strings ({@code gui.director.properties.clip_ui.*}).
 */
public final class ClipPropertyUiI18n {

    private ClipPropertyUiI18n() {
    }

    public static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    public static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    public static String[] sequencePresetLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineClipUi.SEQUENCE_PRESET_ORDER),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.SEQUENCE_PRESET_RANDOM),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.SEQUENCE_PRESET_RADIAL, Icons.ARROW_RIGHT),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.SEQUENCE_PRESET_SPIRAL),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.SEQUENCE_PRESET_SLICE),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.SEQUENCE_PRESET_WAVE)
        };
    }

    public static String[] effectPhaseLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_PHASE_ENTER),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_PHASE_EXIT),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_PHASE_BOTH)
        };
    }

    public static String[] effectTriggerLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_TRIGGER_CONTINUOUS),
                tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_TRIGGER_ONE_SHOT)
        };
    }

    public static String effectPhaseName(EffectPhaseKind phase) {
        return switch (phase) {
            case ENTER -> tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_PHASE_ENTER);
            case EXIT -> tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_PHASE_EXIT);
            case BOTH -> tr(DirectorLang.Properties.AdapterTimelineClipUi.EFFECTS_PHASE_BOTH);
        };
    }

    public enum EffectPhaseKind {
        ENTER, EXIT, BOTH
    }
}
