package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationLoopMode;
import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.actor.animation.asset.InterpolationMode;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigPose;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Undoable editing operations shared by the Actor animation UI surfaces. */
public final class ActorAnimationKeyframeEditing {
    private static final float EPSILON = 1.0e-5f;

    private ActorAnimationKeyframeEditing() {}

    public record RotationKey(BoneTrack track, float time) {}
    public record TranslationKey(BoneTrack track, float time) {}
    public record ScaleKey(BoneTrack track, float time) {}
    public record RotationPaste(BoneTrack track, float time, Quaternionf value,
                                com.director.actor.animation.asset.InterpolationMode interpolation) {
        public RotationPaste {
            value = new Quaternionf(value);
        }
    }
    public record TranslationPaste(BoneTrack track, float time, Vector3f value,
                                   InterpolationMode interpolation) {
        public TranslationPaste { value = new Vector3f(value); }
    }
    public record ScalePaste(BoneTrack track, float time, Vector3f value,
                             InterpolationMode interpolation) {
        public ScalePaste { value = new Vector3f(value); }
    }

    public static boolean deleteScales(TimelineModel model, List<ScaleKey> keys) {
        List<ScaleState> removed = resolveScales(keys);
        if (model == null || removed.isEmpty()) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor scale keyframe",
                () -> putScales(removed, 0f), () -> removeScales(removed, 0f)));
        return true;
    }

    public static boolean moveScales(TimelineModel model, List<ScaleKey> keys, float delta) {
        if (model == null || !Float.isFinite(delta) || Math.abs(delta) <= EPSILON) return false;
        List<ScaleState> source = resolveScales(keys);
        if (source.isEmpty() || source.size() != keys.size()) return false;
        for (ScaleState state : source) {
            float target = state.frame.time() + delta;
            if (target < 0f || occupiedScale(state.track, target, source)) return false;
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Move actor scale keyframe",
                () -> { removeScales(source, delta); putScales(source, 0f); },
                () -> { removeScales(source, 0f); putScales(source, delta); }));
        return true;
    }

    public static boolean pasteScales(TimelineModel model, List<ScalePaste> pasted) {
        if (model == null || pasted == null || pasted.isEmpty()) return false;
        List<ScaleState> states = new ArrayList<>();
        Set<PasteTarget> targets = new HashSet<>();
        for (ScalePaste paste : pasted) {
            if (paste == null || paste.track() == null || paste.time() < 0f || !Float.isFinite(paste.time())
                    || paste.track().scale().findAt(paste.time()).isPresent()
                    || !targets.add(new PasteTarget(paste.track(), paste.time()))) return false;
            states.add(new ScaleState(paste.track(),
                    new ChannelKeyframe<>(paste.time(), paste.value(), paste.interpolation())));
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paste actor scale keyframes",
                () -> removeScales(states, 0f), () -> putScales(states, 0f)));
        return true;
    }

    public static boolean deleteTranslations(TimelineModel model, List<TranslationKey> keys) {
        List<TranslationState> removed = resolveTranslations(keys);
        if (model == null || removed.isEmpty()) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor translation keyframe",
                () -> putTranslations(removed, 0f), () -> removeTranslations(removed, 0f)));
        return true;
    }

    public static boolean moveTranslations(TimelineModel model, List<TranslationKey> keys, float delta) {
        if (model == null || !Float.isFinite(delta) || Math.abs(delta) <= EPSILON) return false;
        List<TranslationState> source = resolveTranslations(keys);
        if (source.isEmpty() || source.size() != keys.size()) return false;
        for (TranslationState state : source) {
            float target = state.frame.time() + delta;
            if (target < 0f || occupiedTranslation(state.track, target, source)) return false;
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Move actor translation keyframe",
                () -> { removeTranslations(source, delta); putTranslations(source, 0f); },
                () -> { removeTranslations(source, 0f); putTranslations(source, delta); }));
        return true;
    }

    public static boolean pasteTranslations(TimelineModel model, List<TranslationPaste> pasted) {
        if (model == null || pasted == null || pasted.isEmpty()) return false;
        List<TranslationState> states = new ArrayList<>();
        Set<PasteTarget> targets = new HashSet<>();
        for (TranslationPaste paste : pasted) {
            if (paste == null || paste.track() == null || paste.time() < 0f || !Float.isFinite(paste.time())
                    || paste.track().translation().findAt(paste.time()).isPresent()
                    || !targets.add(new PasteTarget(paste.track(), paste.time()))) return false;
            states.add(new TranslationState(paste.track(),
                    new ChannelKeyframe<>(paste.time(), paste.value(), paste.interpolation())));
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paste actor translation keyframes",
                () -> removeTranslations(states, 0f), () -> putTranslations(states, 0f)));
        return true;
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

    /** Mirrors complete humanoid poses at the supplied asset-local times across the X axis. */
    public static boolean mirrorHumanoidPoses(TimelineModel model, AnimationAsset asset,
                                               Collection<Float> times, InterpolationMode interpolation) {
        if (model == null || asset == null || times == null || times.isEmpty()
                || !HumanoidRig.DEFINITION.id().equals(asset.rigId())) return false;
        Set<Float> uniqueTimes = new LinkedHashSet<>();
        for (Float time : times) {
            if (time != null && Float.isFinite(time) && time >= 0f && time <= asset.duration()) uniqueTimes.add(time);
        }
        if (uniqueTimes.isEmpty()) return false;
        List<MirrorState> changes = new ArrayList<>();
        for (float time : uniqueTimes) {
            RigPose sampled = asset.sample(time, AnimationLoopMode.ONCE);
            addMirrored(changes, asset, HumanoidRig.BODY, HumanoidRig.BODY, time, sampled, interpolation);
            addMirrored(changes, asset, HumanoidRig.HEAD, HumanoidRig.HEAD, time, sampled, interpolation);
            addMirrored(changes, asset, HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, time, sampled, interpolation);
            addMirrored(changes, asset, HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_ARM, time, sampled, interpolation);
            addMirrored(changes, asset, HumanoidRig.LEFT_LEG, HumanoidRig.RIGHT_LEG, time, sampled, interpolation);
            addMirrored(changes, asset, HumanoidRig.RIGHT_LEG, HumanoidRig.LEFT_LEG, time, sampled, interpolation);
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Mirror actor pose",
                () -> restoreMirror(changes), () -> applyMirror(changes)));
        return true;
    }

    static Quaternionf mirrorAcrossX(Quaternionf rotation) {
        return new Quaternionf(rotation.x, -rotation.y, -rotation.z, rotation.w).normalize();
    }

    private static void addMirrored(List<MirrorState> changes, AnimationAsset asset,
                                    String targetBone, String sourceBone, float time, RigPose sampled,
                                    InterpolationMode fallbackInterpolation) {
        BoneTrack target = asset.getOrCreateBoneTrack(targetBone);
        ChannelKeyframe<Quaternionf> before = target.rotation().findAt(time).orElse(null);
        InterpolationMode mode = asset.findBoneTrack(sourceBone)
                .flatMap(track -> track.rotation().findAt(time))
                .map(ChannelKeyframe::interpolation)
                .orElse(fallbackInterpolation == null ? InterpolationMode.SMOOTH : fallbackInterpolation);
        Quaternionf mirrored = mirrorAcrossX(sampled.localTransform(sourceBone).rotation());
        changes.add(new MirrorState(target, time, before, new ChannelKeyframe<>(time, mirrored, mode)));
    }

    private static void restoreMirror(List<MirrorState> changes) {
        for (MirrorState state : changes) {
            if (state.before == null) state.track.rotation().removeAt(state.time);
            else state.track.rotation().put(state.before.time(), state.before.value(), state.before.interpolation());
        }
    }

    private static void applyMirror(List<MirrorState> changes) {
        for (MirrorState state : changes) state.track.rotation().put(
                state.after.time(), state.after.value(), state.after.interpolation());
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

    private static List<TranslationState> resolveTranslations(List<TranslationKey> keys) {
        List<TranslationState> result = new ArrayList<>();
        if (keys == null) return result;
        for (TranslationKey key : keys) if (key != null && key.track() != null)
            key.track().translation().findAt(key.time()).ifPresent(
                    frame -> result.add(new TranslationState(key.track(), frame)));
        return result;
    }

    private static List<ScaleState> resolveScales(List<ScaleKey> keys) {
        List<ScaleState> result = new ArrayList<>();
        if (keys == null) return result;
        for (ScaleKey key : keys) if (key != null && key.track() != null)
            key.track().scale().findAt(key.time()).ifPresent(
                    frame -> result.add(new ScaleState(key.track(), frame)));
        return result;
    }

    private static boolean occupiedScale(BoneTrack track, float target, List<ScaleState> selected) {
        if (track.scale().findAt(target).isEmpty()) return false;
        for (ScaleState state : selected)
            if (state.track == track && Math.abs(state.frame.time() - target) <= EPSILON) return false;
        return true;
    }

    private static void removeScales(List<ScaleState> states, float offset) {
        for (ScaleState state : states) state.track.scale().removeAt(state.frame.time() + offset);
    }

    private static void putScales(List<ScaleState> states, float offset) {
        for (ScaleState state : states) state.track.scale().put(
                state.frame.time() + offset, state.frame.value(), state.frame.interpolation());
    }

    private static boolean occupiedTranslation(BoneTrack track, float target, List<TranslationState> selected) {
        if (track.translation().findAt(target).isEmpty()) return false;
        for (TranslationState state : selected)
            if (state.track == track && Math.abs(state.frame.time() - target) <= EPSILON) return false;
        return true;
    }

    private static void removeTranslations(List<TranslationState> states, float offset) {
        for (TranslationState state : states) state.track.translation().removeAt(state.frame.time() + offset);
    }

    private static void putTranslations(List<TranslationState> states, float offset) {
        for (TranslationState state : states) state.track.translation().put(
                state.frame.time() + offset, state.frame.value(), state.frame.interpolation());
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
    private record TranslationState(BoneTrack track, ChannelKeyframe<Vector3f> frame) {}
    private record ScaleState(BoneTrack track, ChannelKeyframe<Vector3f> frame) {}
    private record PasteTarget(BoneTrack track, float time) {}
    private record MirrorState(BoneTrack track, float time, ChannelKeyframe<Quaternionf> before,
                               ChannelKeyframe<Quaternionf> after) {}
}
