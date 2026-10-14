package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import org.joml.Quaternionf;
import java.util.Collection;

/** Static poses author rotations only; world motion and bone scale stay independent. */
public final class ActorPosePresets {
    public enum Preset {
        NEUTRAL("站立"), SIT("坐姿"), RAISE_HAND("右手举起"), T_POSE("T 字姿态"), CARRY("双手搬运");
        private final String label;
        Preset(String label) { this.label = label; }
        public String label() { return label; }
    }
    private ActorPosePresets() {}

    public static RigPose apply(RigPose source, Preset preset, Collection<String> bones) {
        if (source == null || preset == null || bones == null || !HumanoidTopology.matches(source.rig()))
            throw new IllegalArgumentException("Pose presets require a humanoid rig and selected parts");
        RigPose result = source.copy();
        for (String id : bones) {
            if (!ActorBoneInspector.supports(source.rig(), id, RigPose.ROTATION)) continue;
            Quaternionf rotation = new Quaternionf();
            if (preset == Preset.SIT && (HumanoidRig.LEFT_LEG.equals(id) || HumanoidRig.RIGHT_LEG.equals(id)))
                rotation.rotateX(-(float)Math.PI / 2);
            if (preset == Preset.RAISE_HAND && HumanoidRig.RIGHT_ARM.equals(id))
                rotation.rotateX(-(float)Math.PI * .9f);
            if (preset == Preset.T_POSE && HumanoidRig.LEFT_ARM.equals(id)) rotation.rotateZ(-(float)Math.PI / 2);
            if (preset == Preset.T_POSE && HumanoidRig.RIGHT_ARM.equals(id)) rotation.rotateZ((float)Math.PI / 2);
            if (preset == Preset.CARRY && (HumanoidRig.LEFT_ARM.equals(id) || HumanoidRig.RIGHT_ARM.equals(id)))
                rotation.rotateX(-(float)Math.PI / 2);
            boolean shin=ArticulatedPlayerRig.LEFT_SHIN.equals(id) || ArticulatedPlayerRig.RIGHT_SHIN.equals(id);
            boolean forearm=ArticulatedPlayerRig.LEFT_FOREARM.equals(id) || ArticulatedPlayerRig.RIGHT_FOREARM.equals(id);
            if (preset == Preset.SIT && shin) rotation.rotateX((float)Math.PI/2);
            if (preset == Preset.RAISE_HAND && ArticulatedPlayerRig.RIGHT_FOREARM.equals(id)) rotation.rotateX(-.35f);
            if (preset == Preset.CARRY && forearm) rotation.rotateX(-(float)Math.PI/2);
            if (preset == Preset.CARRY && (HumanoidRig.LEFT_ARM.equals(id) || HumanoidRig.RIGHT_ARM.equals(id))
                    && source.rig().findBoneIndex(ArticulatedPlayerRig.LEFT_FOREARM)>=0)
                rotation.identity();
            result.setLocalTransform(source.rig().requireBoneIndex(id),
                    result.localTransform(id).withRotation(rotation), RigPose.ROTATION);
        }
        return result;
    }
}
