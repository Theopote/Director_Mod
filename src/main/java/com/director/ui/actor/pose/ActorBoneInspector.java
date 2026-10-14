package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import com.director.client.gizmo.GizmoMode;

/** Supported editing channels for the currently selected part. */
final class ActorBoneInspector {
    private ActorBoneInspector() {}
    static boolean supports(RigDefinition rig, String id, int channel) {
        int index = rig.findBoneIndex(id);
        if (index < 0) return false;
        var caps = rig.bone(index).capabilities();
        return switch (channel) {
            case RigPose.TRANSLATION -> caps.translation();
            case RigPose.ROTATION -> caps.rotation() && !(HumanoidTopology.matches(rig) && HumanoidRig.ROOT.equals(id));
            case RigPose.SCALE -> caps.scale();
            default -> false;
        };
    }
    static GizmoMode mode(int channel) {
        return switch (channel) {
            case RigPose.TRANSLATION -> GizmoMode.TRANSLATE;
            case RigPose.ROTATION -> GizmoMode.ROTATE;
            case RigPose.SCALE -> GizmoMode.SCALE;
            default -> throw new IllegalArgumentException("Unknown channel");
        };
    }
    static String label(int channel) {
        return switch (channel) {
            case RigPose.TRANSLATION -> "位置";
            case RigPose.ROTATION -> "旋转";
            case RigPose.SCALE -> "缩放";
            default -> throw new IllegalArgumentException("Unknown channel");
        };
    }
}
