package com.director.actor.animation;

import com.director.actor.rig.Pose;

import java.util.List;
import java.util.Set;
import com.director.actor.rig.skeleton.HumanoidRig;

/** Minimal built-in content that makes EntityAnimationClip usable without external loaders. */
final class BuiltInAnimations {
    private BuiltInAnimations() {}

    static void registerInto(AnimationLibrary library) {
        library.register(animation("idle", "Idle", 1f, AnimationData.Purpose.STATIC_POSE, AnimationData.POSE_BONES, pose(), pose()));
        library.register(animation("walk", "Walk", 1f, AnimationData.Purpose.LOCOMOTION, Set.of(HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, HumanoidRig.LEFT_LEG, HumanoidRig.RIGHT_LEG),
            pose(0.65f, -0.65f, -0.65f, 0.65f),
            pose(-0.65f, 0.65f, 0.65f, -0.65f),
            pose(0.65f, -0.65f, -0.65f, 0.65f)));

        Pose waveStart = pose();
        waveStart.rightArmPitch = -1.2f;
        Pose waveOut = waveStart.copy();
        waveOut.bodyRoll = 0.08f;
        Pose waveBack = waveStart.copy();
        waveBack.bodyRoll = -0.08f;
        library.register(animation("wave", "Wave", 1.2f, AnimationData.Purpose.PERFORMANCE, Set.of(HumanoidRig.BODY, HumanoidRig.RIGHT_ARM), waveStart, waveOut, waveBack, waveStart));

        Pose attackStart = pose();
        Pose attackHit = pose();
        attackHit.rightArmPitch = -1.7f;
        library.register(animation("attack", "Attack", 0.55f, AnimationData.Purpose.PERFORMANCE, Set.of(HumanoidRig.RIGHT_ARM), attackStart, attackHit, attackStart));
    }

    private static AnimationData animation(String id, String name, float duration, AnimationData.Purpose purpose, Set<String> bones, Pose... poses) {
        java.util.ArrayList<AnimationKeyframe> frames = new java.util.ArrayList<>();
        for (int i = 0; i < poses.length; i++) {
            float time = poses.length == 1 ? 0f : duration * i / (poses.length - 1f);
            frames.add(new AnimationKeyframe(time, poses[i]));
        }
        return new AnimationData(id, name, duration, List.copyOf(frames), AnimationData.AnimationSource.BUILT_IN, bones, purpose);
    }

    private static Pose pose() { return new Pose(); }

    private static Pose pose(float leftArm, float rightArm, float leftLeg, float rightLeg) {
        Pose pose = new Pose();
        pose.leftArmPitch = leftArm;
        pose.rightArmPitch = rightArm;
        pose.leftLegPitch = leftLeg;
        pose.rightLegPitch = rightLeg;
        return pose;
    }
}
