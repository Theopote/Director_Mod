package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Explicit pose keying, independent of the auto-key switch. */
public final class ActorPoseKeyframeEditing {
    private ActorPoseKeyframeEditing() {}

    public static void key(TimelineModel model, AnimationAsset asset, float time,
                           RigPose pose, Collection<String> bones, InterpolationMode mode) {
        keyChannels(model, asset, time, pose, bones, mode, RigPose.ALL_CHANNELS);
    }

    public static void keyChannels(TimelineModel model, AnimationAsset asset, float time,
                                   RigPose pose, Collection<String> bones, InterpolationMode mode, int channels) {
        Objects.requireNonNull(model);
        if (!Float.isFinite(time) || time < 0 || time > asset.duration())
            throw new IllegalArgumentException("关键帧时间超出动画范围");
        if (!pose.rig().id().equals(asset.rigId())) throw new IllegalArgumentException("骨架不兼容");
        List<Runnable> before = new ArrayList<>();
        List<Runnable> after = new ArrayList<>();
        for (String id : new LinkedHashSet<>(bones)) {
            var bone = asset.rig().bone(asset.rig().requireBoneIndex(id));
            var transform = pose.localTransform(id);
            var scale = transform.scale();
            if ((channels & RigPose.SCALE) != 0 && (scale.x <= 0 || scale.y <= 0 || scale.z <= 0))
                throw new IllegalArgumentException("缩放必须大于零");
            boolean existed = asset.findBoneTrack(id).isPresent();
            BoneTrack track = asset.findBoneTrack(id).orElseGet(BoneTrack::new);
            var t = track.translation().findAt(time).orElse(null);
            var r = track.rotation().findAt(time).orElse(null);
            var s = track.scale().findAt(time).orElse(null);
            boolean translation = (channels & RigPose.TRANSLATION) != 0 && bone.capabilities().translation();
            // Root rotation is not consumed by the current humanoid render binding.
            boolean rotation = (channels & RigPose.ROTATION) != 0 && bone.capabilities().rotation() && !HumanoidRig.ROOT.equals(id);
            boolean scaling = (channels & RigPose.SCALE) != 0 && bone.capabilities().scale();
            if (!translation && !rotation && !scaling) continue;
            before.add(() -> {
                BoneTrack target = asset.getOrCreateBoneTrack(id);
                if (translation) restore(target.translation(), time, t);
                if (rotation) restore(target.rotation(), time, r);
                if (scaling) restore(target.scale(), time, s);
                if (!existed && target.isEmpty()) asset.removeBoneTrack(id);
            });
            after.add(() -> {
                BoneTrack target = asset.getOrCreateBoneTrack(id);
                if (translation) target.translation().put(time, transform.translation(), mode);
                if (rotation) target.rotation().put(time, transform.rotation(), mode);
                if (scaling) target.scale().put(time, transform.scale(), mode);
            });
        }
        if (after.isEmpty()) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Key actor pose",
                () -> before.forEach(Runnable::run), () -> after.forEach(Runnable::run)));
    }

    private static <T> void restore(KeyframeTrack<T> track, float time, ChannelKeyframe<T> frame) {
        track.removeAt(time);
        if (frame != null) track.put(frame.time(), frame.value(), frame.interpolation());
    }
}
