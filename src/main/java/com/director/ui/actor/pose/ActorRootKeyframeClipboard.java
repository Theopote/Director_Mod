package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;

/** Detached whole-key clipboard. Paste never overwrites keys or creates references. */
public final class ActorRootKeyframeClipboard {
    private EntityClipKeyframe snapshot;
    public boolean copy(EntityClip clip, EntityClipKeyframe key) {
        if (clip == null || key == null || !clip.getKeyframes().contains(key)) return false;
        snapshot = key.copy();
        return true;
    }
    public EntityClipKeyframe paste(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        if (snapshot == null || !ActorRootKeyframeEditing.editable(model, owner, clip)) return null;
        float time = model.getCurrentTime();
        if (clip.getKeyframes().stream().anyMatch(k -> Math.abs(k.getTime() - time) <= 1e-4f)) return null;
        // Converting an endpoint animation needs the explicit insert-key workflow first.
        if (clip instanceof EntityTransformClip && clip.getKeyframes().isEmpty()) return null;
        var key = snapshot.copy(); key.setTime(time);
        if (!key.getPosition().isFinite() || !key.getRotation().isFinite() || !key.getScale().isFinite()
                || key.getScale().x <= 0 || key.getScale().y <= 0 || key.getScale().z <= 0) return null;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paste actor root keyframe",
                () -> clip.removeKeyframe(key.getId()), () -> clip.addKeyframe(key)));
        return key;
    }
}
