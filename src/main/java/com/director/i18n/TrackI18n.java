package com.director.i18n;

import com.director.timeline.model.TrackType;

/**
 * 时间线轨道类型名称/描述的 lang 键解析（{@code gui.director.timeline.track.{type}.*}）。
 */
public final class TrackI18n {

    private static final String PREFIX = "gui.director.timeline.track.";

    private TrackI18n() {
    }

    public static String nameKey(TrackType type) {
        return PREFIX + type.name().toLowerCase() + ".name";
    }

    public static String descKey(TrackType type) {
        return PREFIX + type.name().toLowerCase() + ".desc";
    }

    public static String name(TrackType type) {
        return DirectorI18n.translate(nameKey(type));
    }

    public static String description(TrackType type) {
        return DirectorI18n.translate(descKey(type));
    }

    public static String iconAndName(TrackType type) {
        return type.getIcon() + " " + name(type);
    }
}
