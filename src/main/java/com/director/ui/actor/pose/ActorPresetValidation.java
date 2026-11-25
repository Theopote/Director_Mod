package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.HumanoidTopology;
import com.director.actor.rig.skeleton.RigPoseComposer;
import com.director.timeline.model.entity.EntitySpawnClip;

/** Every preset attachment entry validates the same reference and skeleton contract. */
public final class ActorPresetValidation {
    private ActorPresetValidation() {}
    public static void require(Actor actor,EntitySpawnClip spawn,AnimationAssetLibrary library,String id) {
        if(spawn==null || id==null) throw new IllegalArgumentException("请选择演员片段和动作");
        requireAction(library, id);
        requireReference(actor, library, id, spawn.getBoneAnimationId());
        requireDistinctFullOverride(id, spawn.getSecondaryPreset());
    }

    public static void requireDistinctFullOverride(String primaryId, com.director.timeline.model.entity.ActorPresetBinding secondary) {
        if (secondary != null && secondary.fullyOverridesSameAction(primaryId))
            throw new IllegalArgumentException("第二预设会完全覆盖同一个主动作。请缩小第二预设的骨骼或通道范围、降低权重，或选择另一个动作。");
    }

    public static void requireAction(AnimationAssetLibrary library, String id) {
        var purpose = library.find(id).map(AnimationAsset::purpose)
                .orElseGet(() -> AnimationLibrary.getInstance().get(id).map(AnimationData::purpose).orElse(null));
        if (purpose == AnimationData.Purpose.STATIC_POSE)
            throw new IllegalArgumentException("静态姿态请在「姿态」中应用到当前帧。");
    }

    public static void requireReference(Actor actor, AnimationAssetLibrary library, String id, String manualId) {
        if(id.equals(manualId)) throw new IllegalArgumentException("这是本片段的手工修正动画，不能同时作为预设；请先复制动画或选择其他动作。");
        var rig = ActorRigCompatibility.resolveRig(actor).orElse(null);
        var asset = library.find(id).orElse(null);
        boolean compatible = asset != null
                ? rig != null && ActorRigCompatibility.accepts(actor, asset.rigId())
                    && RigPoseComposer.sameLayout(rig, asset.rig())
                : AnimationLibrary.getInstance().get(id)
                    .map(animation -> HumanoidTopology.matches(rig))
                    .orElse(false);
        if (!compatible) throw new IllegalArgumentException("动作不存在或与演员骨架不兼容");
    }
}
