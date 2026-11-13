package com.director.ui.actor.path;

import com.director.actor.path.ActorPathDefinition;
import com.director.timeline.model.TimelineModel;

/** Shared explanation for library mutation controls; browsing remains available. */
final class ActorPathEditAvailability {
    private ActorPathEditAvailability() {}

    static String reason(TimelineModel model, ActorPathDefinition path) {
        if (model == null) return text("gui.director.actor.path.need_project", "Open a timeline project before editing paths.");
        if (model.isPlaying()) return text("gui.director.actor.path.pause", "Pause playback before editing paths.");
        if (model.isScrubbing()) return text("gui.director.actor.path.finish_scrub", "Finish dragging the playhead before editing paths.");
        if (!Float.isFinite(model.getCurrentTime()) || model.getCurrentTime() < 0)
            return text("gui.director.actor.path.bad_time", "The playhead time is invalid. Reset it before editing paths.");
        if (path != null && path.isLocked())
            return text("gui.director.actor.path.locked_edit", "This path is locked. Unlock it before renaming or deleting.");
        return "";
    }

    /** Uses the loaded language, and a stable fallback when unit tests have no language. */
    static String text(String key, String fallback) {
        try {
            String value = net.minecraft.util.Language.getInstance().get(key, fallback);
            if (value == null || value.isBlank() || value.equals(key)) return fallback;
            return value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
