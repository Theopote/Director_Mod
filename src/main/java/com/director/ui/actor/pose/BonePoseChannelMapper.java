package com.director.ui.actor.pose;

import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.client.gizmo.Axis;
import com.director.timeline.model.entity.ActorPoseClip;

/** Maps standard humanoid bones and rotation axes to the legacy pose channels. */
public final class BonePoseChannelMapper {
    private BonePoseChannelMapper() {}

    public static boolean isEditable(String boneId, Axis axis) {
        if (boneId == null || axis == null || HumanoidRig.ROOT.equals(boneId)) return false;
        if (HumanoidRig.HEAD.equals(boneId)) return axis != Axis.Z;
        if (HumanoidRig.LEFT_LEG.equals(boneId) || HumanoidRig.RIGHT_LEG.equals(boneId)) return axis == Axis.X;
        return HumanoidRig.BODY.equals(boneId)
                || HumanoidRig.LEFT_ARM.equals(boneId)
                || HumanoidRig.RIGHT_ARM.equals(boneId);
    }

    public static void enableOverride(ActorPoseClip clip, String boneId) {
        switch (boneId) {
            case HumanoidRig.BODY -> clip.setOverrideBody(true);
            case HumanoidRig.HEAD -> clip.setOverrideHead(true);
            case HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM -> clip.setOverrideArms(true);
            case HumanoidRig.LEFT_LEG, HumanoidRig.RIGHT_LEG -> clip.setOverrideLegs(true);
            default -> { }
        }
    }

    public static float read(ActorPoseClip clip, String boneId, Axis axis) {
        return switch (boneId) {
            case HumanoidRig.BODY -> axisValue(axis, clip.getBodyPitch(), clip.getBodyYaw(), clip.getBodyRoll());
            case HumanoidRig.HEAD -> axisValue(axis, clip.getHeadPitch(), clip.getHeadYaw(), 0f);
            case HumanoidRig.LEFT_ARM -> axisValue(axis, clip.getLeftArmPitch(), clip.getLeftArmYaw(), clip.getLeftArmRoll());
            case HumanoidRig.RIGHT_ARM -> axisValue(axis, clip.getRightArmPitch(), clip.getRightArmYaw(), clip.getRightArmRoll());
            case HumanoidRig.LEFT_LEG -> axis == Axis.X ? clip.getLeftLegPitch() : 0f;
            case HumanoidRig.RIGHT_LEG -> axis == Axis.X ? clip.getRightLegPitch() : 0f;
            default -> 0f;
        };
    }

    public static void write(ActorPoseClip clip, String boneId, Axis axis, float value) {
        if (!isEditable(boneId, axis)) return;
        switch (boneId) {
            case HumanoidRig.BODY -> setBody(clip, axis, value);
            case HumanoidRig.HEAD -> { if (axis == Axis.X) clip.setHeadPitch(value); else clip.setHeadYaw(value); }
            case HumanoidRig.LEFT_ARM -> setLeftArm(clip, axis, value);
            case HumanoidRig.RIGHT_ARM -> setRightArm(clip, axis, value);
            case HumanoidRig.LEFT_LEG -> clip.setLeftLegPitch(value);
            case HumanoidRig.RIGHT_LEG -> clip.setRightLegPitch(value);
            default -> { }
        }
    }

    public static void write(Pose pose, String boneId, Axis axis, float value) {
        if (!isEditable(boneId, axis)) return;
        switch (boneId) {
            case HumanoidRig.BODY -> {
                if (axis == Axis.X) pose.bodyPitch = value;
                else if (axis == Axis.Y) pose.bodyYaw = value;
                else pose.bodyRoll = value;
            }
            case HumanoidRig.HEAD -> { if (axis == Axis.X) pose.headPitch = value; else pose.headYaw = value; }
            case HumanoidRig.LEFT_ARM -> {
                if (axis == Axis.X) pose.leftArmPitch = value;
                else if (axis == Axis.Y) pose.leftArmYaw = value;
                else pose.leftArmRoll = value;
            }
            case HumanoidRig.RIGHT_ARM -> {
                if (axis == Axis.X) pose.rightArmPitch = value;
                else if (axis == Axis.Y) pose.rightArmYaw = value;
                else pose.rightArmRoll = value;
            }
            case HumanoidRig.LEFT_LEG -> pose.leftLegPitch = value;
            case HumanoidRig.RIGHT_LEG -> pose.rightLegPitch = value;
            default -> { }
        }
    }

    private static float axisValue(Axis axis, float x, float y, float z) {
        return switch (axis) { case X -> x; case Y -> y; case Z -> z; };
    }
    private static void setBody(ActorPoseClip c, Axis a, float v) {
        if (a == Axis.X) c.setBodyPitch(v); else if (a == Axis.Y) c.setBodyYaw(v); else c.setBodyRoll(v);
    }
    private static void setLeftArm(ActorPoseClip c, Axis a, float v) {
        if (a == Axis.X) c.setLeftArmPitch(v); else if (a == Axis.Y) c.setLeftArmYaw(v); else c.setLeftArmRoll(v);
    }
    private static void setRightArm(ActorPoseClip c, Axis a, float v) {
        if (a == Axis.X) c.setRightArmPitch(v); else if (a == Axis.Y) c.setRightArmYaw(v); else c.setRightArmRoll(v);
    }
}
