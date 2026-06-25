package com.director.i18n;

/**
 * Camera path / keyframe property panel UI strings
 * ({@code gui.director.properties.adapter.camera.*}).
 */
public final class CameraPropertyAdapterI18n {

    private CameraPropertyAdapterI18n() {
    }

    public static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    public static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    public static String[] keyframeTypeLabels() {
        return new String[]{
                tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_STANDARD),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_CORNER),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_HOLD),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_SMOOTH),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_MANUAL_BEZIER)
        };
    }

    public static String[] segmentPathTypeLabels() {
        return new String[]{
                tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_DEFAULT),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_LINEAR),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_CATMULL_ROM),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_BEZIER),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_STEP),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_HOLD)
        };
    }

    public static String[] rotationInterpolationLabels() {
        return new String[]{
                tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_DEFAULT),
                tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_LINEAR),
                tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_BEZIER),
                tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_CATMULL_ROM),
                tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_STEP)
        };
    }

    public static String[] handleModeLabels() {
        return new String[]{
                tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_AUTO),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_FREE),
                tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_ALIGNED)
        };
    }
}
