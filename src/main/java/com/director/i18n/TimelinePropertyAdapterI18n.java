package com.director.i18n;

/**
 * Timeline property adapter UI strings ({@code gui.director.properties.adapter.timeline.*}).
 */
public final class TimelinePropertyAdapterI18n {

    private TimelinePropertyAdapterI18n() {
    }

    public static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    public static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    public static String[] projectExportFormats() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_FORMAT_JSON),
                tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_FORMAT_NBT),
                tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_FORMAT_BINARY),
                tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_FORMAT_SCENEPACK)
        };
    }

    public static String[] projectTimeUnits() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineProject.TIME_UNIT_SECONDS),
                tr(DirectorLang.Properties.AdapterTimelineProject.TIME_UNIT_FRAMES),
                tr(DirectorLang.Properties.AdapterTimelineProject.TIME_UNIT_TICKS)
        };
    }

    public static String[] trackBlendModes() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineTrack.BLEND_OVERRIDE),
                tr(DirectorLang.Properties.AdapterTimelineTrack.BLEND_ADDITIVE),
                tr(DirectorLang.Properties.AdapterTimelineTrack.BLEND_MULTIPLY),
                tr(DirectorLang.Properties.AdapterTimelineTrack.BLEND_SUBTRACT),
                tr(DirectorLang.Properties.AdapterTimelineTrack.BLEND_CUSTOM)
        };
    }

    public static String[] trackAlignmentModes() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineTrack.ALIGN_GLOBAL),
                tr(DirectorLang.Properties.AdapterTimelineTrack.ALIGN_CONTAINER),
                tr(DirectorLang.Properties.AdapterTimelineTrack.ALIGN_LOCAL)
        };
    }

    public static String[] sequencePlayModes() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineSequence.PLAY_SEQUENTIAL),
                tr(DirectorLang.Properties.AdapterTimelineSequence.PLAY_LOOP),
                tr(DirectorLang.Properties.AdapterTimelineSequence.PLAY_PINGPONG),
                tr(DirectorLang.Properties.AdapterTimelineSequence.PLAY_RANDOM)
        };
    }

    public static String[] markerPresetColorNames() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_RED),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_ORANGE),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_YELLOW),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_GREEN),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_CYAN),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_BLUE),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_MAGENTA),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_WHITE),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_GRAY),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_DARK_GRAY),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_LIGHT_RED),
                tr(DirectorLang.Properties.AdapterTimelineMarker.COLOR_TEAL)
        };
    }
}
