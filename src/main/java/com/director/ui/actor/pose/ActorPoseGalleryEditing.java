package com.director.ui.actor.pose;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Gallery actions share the existing authoring/undo paths; browsing never creates data. */
public final class ActorPoseGalleryEditing {
    private ActorPoseGalleryEditing() {}

    public static TimelineTrack owner(TimelineModel model, UUID actorId) {
        return ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
    }

    /** A static asset contributes its start pose and only the rotations it actually authors. */
    public static boolean applyAssetPose(TimelineModel model, UUID actorId, Collection<String> selected, String id) {
        var actor = ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        var rig = ActorRigCompatibility.resolveRig(actor).orElse(null);
        if (rig == null) return false;
        var asset = AnimationAssetLibrary.getInstance().find(id).orElse(null);
        RigPose sampled;
        if (asset != null) {
            if (asset.purpose() != AnimationData.Purpose.STATIC_POSE || !ActorRigCompatibility.accepts(actor, asset.rigId())
                    || !RigPoseComposer.sameLayout(rig, asset.rig())) return false;
            sampled = asset.sample(0f, AnimationLoopMode.ONCE);
        } else {
            var data = AnimationLibrary.getInstance().get(id).orElse(null);
            if (data == null || data.purpose() != AnimationData.Purpose.STATIC_POSE) return false;
            sampled = AnimationDataSampler.sampleRig(id, rig, 0f, false, 1f);
            if (sampled == null) return false;
        }
        var bones = selected.stream().filter(bone -> ActorBoneInspector.supports(rig, bone, RigPose.ROTATION))
                .filter(bone -> (sampled.authoredChannels(rig.requireBoneIndex(bone)) & RigPose.ROTATION) != 0)
                .distinct().toList();
        return apply(model, actorId, bones, pose -> {
            for (String bone : bones) {
                int index = pose.rig().requireBoneIndex(bone);
                pose.setLocalTransform(index, pose.localTransform(index).withRotation(sampled.localTransform(bone).rotation()),
                        RigPose.ROTATION);
            }
            return pose;
        });
    }

    public static boolean apply(TimelineModel model, UUID actorId, Collection<String> selected,
                                UnaryOperator<RigPose> operation) {
        var actor = ActorRegistryManager.getInstance().getActor(actorId).orElseThrow();
        var rig = ActorRigCompatibility.resolveRig(actor).orElseThrow();
        var bones = selected.stream().distinct().filter(id -> ActorBoneInspector.supports(rig,id,RigPose.ROTATION)).toList();
        if (bones.isEmpty()) return false;
        var context = ActorAnimationEditContext.resolve(model,actorId);
        var mode = ActorAnimationAuthoringSettings.getInstance().getInterpolation();
        if (context != null) {
            if (!context.isCurrent()) return false;
            var pose = operation.apply(context.asset().sample(context.assetTime(),AnimationLoopMode.ONCE));
            return ActorAnimationAuthoringService.keyPose(context,pose,bones,mode,RigPose.ROTATION);
        }
        var owner = owner(model,actorId);
        var spawn = ActorEmbeddedAnimation.target(model,owner);
        if (spawn == null || spawn.getBoneAnimationId() != null) return false;
        var pose = operation.apply(new RigPose(rig));
        String id = "project:actor_"+UUID.randomUUID();
        var asset = new AnimationAsset(id,spawn.getName()+" 骨骼动作",rig,Math.max(.1f,spawn.getLength()),AnimationLoopMode.ONCE);
        float time = model.getCurrentTime()-spawn.getStartTime();
        for (String bone : bones)
            asset.getOrCreateBoneTrack(bone).rotation().put(time,pose.localTransform(bone).rotation(),mode);
        var library = AnimationAssetLibrary.getInstance();
        TimelineCommandBridge.execute(new ActorAnimationAssetCommand(model,"Apply actor pose",
                () -> {
                    if (!ActorRootKeyframeEditing.editable(model,owner,spawn) || spawn.getBoneAnimationId()!=null
                            || library.contains(id)
                            || ActorRegistryManager.getInstance().getActor(actorId).orElse(null)!=actor
                            || ActorRigCompatibility.resolveRig(actor).orElse(null)!=rig)
                        throw new IllegalArgumentException("演员姿态目标已变化");
                },List.of(
                    new ActorAnimationAssetCommand.Change(() -> library.remove(id),() -> {
                        if(!library.addIfAbsent(asset)) throw new IllegalArgumentException("动画资源冲突");
                    }),
                    new ActorAnimationAssetCommand.Change(() -> spawn.setBoneAnimationId(null),() -> spawn.setBoneAnimationId(id)))));
        return true;
    }

    public static boolean bind(TimelineModel model, UUID actorId, EntitySpawnClip target, String animationId) {
        var owner = owner(model,actorId);
        var actor = ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        if (!ActorRootKeyframeEditing.editable(model,owner,target) || actor==null) return false;
        try {ActorPresetValidation.require(actor,target,AnimationAssetLibrary.getInstance(),animationId);}
        catch(IllegalArgumentException invalid) {return false;}
        var before = target.getPreset();
        var after = ActorPresetBinding.select(animationId,before);
        if (after.equals(before)) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Bind actor action",
                () -> replace(model,owner,target,after,before),() -> replace(model,owner,target,before,after)));
        return true;
    }

    private static void replace(TimelineModel model,TimelineTrack owner,EntitySpawnClip target,
                                ActorPresetBinding expected,ActorPresetBinding next) {
        if (!model.getAllTracks().contains(owner) || owner.getActorTrackPayload()==null
                || !owner.getActorTrackPayload().getClips().contains(target) || !Objects.equals(target.getPreset(),expected))
            throw new IllegalArgumentException("演员动作目标已变化");
        ActorAnimationEditingLifecycle.cancelTransientWork();
        target.setPreset(next);
    }
}
