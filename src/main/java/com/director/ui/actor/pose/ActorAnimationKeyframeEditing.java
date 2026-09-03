package com.director.ui.actor.pose;

import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Undoable editing operations shared by the Actor animation UI surfaces. */
public final class ActorAnimationKeyframeEditing {
    private static final float EPSILON = 1.0e-5f;

    private ActorAnimationKeyframeEditing() {}

    public record RotationKey(BoneTrack track, float time) {}
    public record RotationPaste(BoneTrack track, float time, Quaternionf value,
                                com.director.actor.animation.asset.InterpolationMode interpolation) {
        public RotationPaste {
            value = new Quaternionf(value);
        }
    }

    public static boolean deleteRotation(TimelineModel model, BoneTrack track, float time) {
        return deleteRotations(model, List.of(new RotationKey(track, time)));
    }

    public static boolean deleteRotations(TimelineModel model, List<RotationKey> keys) {
        List<KeyState> removed = resolve(keys);
        if (model == null || removed.isEmpty()) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor keyframe",
                () -> putAll(removed), () -> removeAll(removed)));
        return true;
    }

    public static boolean moveRotation(TimelineModel model, BoneTrack track, float fromTime, float toTime) {
        return moveRotations(model, List.of(new RotationKey(track, fromTime)), toTime - fromTime);
    }

    public static boolean moveRotations(TimelineModel model, List<RotationKey> keys, float delta) {
        if (model == null || !Float.isFinite(delta) || Math.abs(delta) <= EPSILON) return false;
        List<KeyState> source = resolve(keys);
        if (source.isEmpty() || source.size() != keys.size()) return false;
        for (KeyState state : source) {
            float target = state.frame.time() + delta;
            if (target < 0f || occupiedByUnselected(state.track, target, source)) return false;
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Move actor keyframe",
                () -> {
                    removeShifted(source, delta);
                    putAll(source);
                },
                () -> {
                    removeAll(source);
                    putShifted(source, delta);
                }));
        return true;
    }

    public static boolean pasteRotations(TimelineModel model, List<RotationPaste> pasted) {
        if (model == null || pasted == null || pasted.isEmpty()) return false;
        List<KeyState> states = new ArrayList<>();
        Set<PasteTarget> targets = new HashSet<>();
        for (RotationPaste paste : pasted) {
            if (paste == null || paste.track() == null || !Float.isFinite(paste.time()) || paste.time() < 0f
                    || paste.track().rotation().findAt(paste.time()).isPresent()
                    || !targets.add(new PasteTarget(paste.track(), paste.time()))) return false;
            states.add(new KeyState(paste.track(),
                    new ChannelKeyframe<>(paste.time(), paste.value(), paste.interpolation())));
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paste actor keyframes",
                () -> removeAll(states), () -> putAll(states)));
        return true;
    }

    private static List<KeyState> resolve(List<RotationKey> keys) {
        List<KeyState> result = new ArrayList<>();
        if (keys == null) return result;
        for (RotationKey key : keys) {
            if (key == null || key.track() == null) continue;
            key.track().rotation().findAt(key.time()).ifPresent(frame -> result.add(new KeyState(key.track(), frame)));
        }
        return result;
    }

    private static boolean occupiedByUnselected(BoneTrack track, float target, List<KeyState> selected) {
        if (track.rotation().findAt(target).isEmpty()) return false;
        for (KeyState state : selected) {
            if (state.track == track && Math.abs(state.frame.time() - target) <= EPSILON) return false;
        }
        return true;
    }

    private static void removeAll(List<KeyState> states) {
        for (KeyState state : states) state.track.rotation().removeAt(state.frame.time());
    }

    private static void putAll(List<KeyState> states) {
        for (KeyState state : states) state.track.rotation().put(
                state.frame.time(), state.frame.value(), state.frame.interpolation());
    }

    private static void removeShifted(List<KeyState> states, float delta) {
        for (KeyState state : states) state.track.rotation().removeAt(state.frame.time() + delta);
    }

    private static void putShifted(List<KeyState> states, float delta) {
        for (KeyState state : states) state.track.rotation().put(
                state.frame.time() + delta, state.frame.value(), state.frame.interpolation());
    }

    private record KeyState(BoneTrack track, ChannelKeyframe<Quaternionf> frame) {}
    private record PasteTarget(BoneTrack track, float time) {}
}
