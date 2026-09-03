package com.director.ui.actor.pose;

import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import org.joml.Quaternionf;

/** Undoable editing operations shared by the Actor animation UI surfaces. */
public final class ActorAnimationKeyframeEditing {
    private ActorAnimationKeyframeEditing() {}

    public static boolean deleteRotation(TimelineModel model, BoneTrack track, float time) {
        ChannelKeyframe<Quaternionf> removed = track.rotation().findAt(time).orElse(null);
        if (model == null || removed == null) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor keyframe",
                () -> track.rotation().put(removed.time(), removed.value(), removed.interpolation()),
                () -> track.rotation().removeAt(removed.time())));
        return true;
    }

    public static boolean moveRotation(TimelineModel model, BoneTrack track, float fromTime, float toTime) {
        if (model == null || track == null || !Float.isFinite(toTime) || toTime < 0f) return false;
        ChannelKeyframe<Quaternionf> source = track.rotation().findAt(fromTime).orElse(null);
        if (source == null || Math.abs(fromTime - toTime) <= 1.0e-5f
                || track.rotation().findAt(toTime).isPresent()) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Move actor keyframe",
                () -> {
                    track.rotation().removeAt(toTime);
                    track.rotation().put(source.time(), source.value(), source.interpolation());
                },
                () -> {
                    track.rotation().removeAt(source.time());
                    track.rotation().put(toTime, source.value(), source.interpolation());
                }));
        return true;
    }
}
