package com.director.actor.prop;

import com.director.actor.rig.skeleton.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.Optional;

/** Humanoid sockets in bone-local block units. Both hands is a midpoint, not an IK constraint. */
public final class ActorPropSocketResolver {
    private ActorPropSocketResolver() {}
    public static Optional<Matrix4f> resolve(RigPose pose, Matrix4f actorWorld, ActorPropSlot slot,
                                            Vector3f offset, Vector3f rotationDegrees) {
        if (pose==null || actorWorld==null || slot==null || !actorWorld.isFinite()
                || offset==null || !offset.isFinite() || rotationDegrees==null || !rotationDegrees.isFinite()
                || !HumanoidTopology.matches(pose.rig())) return Optional.empty();
        var world=RigWorldPose.resolve(pose,actorWorld);
        Matrix4f socket;
        switch(slot) {
            case MAIN_HAND -> socket=hand(world,HumanoidRig.RIGHT_ARM);
            case OFF_HAND -> socket=hand(world,HumanoidRig.LEFT_ARM);
            case BOTH_HANDS -> {
                var right=hand(world,HumanoidRig.RIGHT_ARM).getTranslation(new Vector3f());
                var left=hand(world,HumanoidRig.LEFT_ARM).getTranslation(new Vector3f());
                socket=world.transform(world.rig().requireBoneIndex(HumanoidRig.BODY));
                socket.setTranslation(right.add(left).mul(.5f));
            }
            case HEAD -> socket=world.transform(world.rig().requireBoneIndex(HumanoidRig.HEAD)).translate(0,.25f,0);
            case BACK -> socket=world.transform(world.rig().requireBoneIndex(HumanoidRig.BODY)).translate(0,0,.2f);
            default -> throw new IllegalArgumentException("Unknown prop slot");
        }
        socket.translate(offset).rotateXYZ((float)Math.toRadians(rotationDegrees.x),
                (float)Math.toRadians(rotationDegrees.y),(float)Math.toRadians(rotationDegrees.z));
        return socket.isFinite()?Optional.of(socket):Optional.empty();
    }
    private static Matrix4f hand(RigWorldPose pose,String bone) {
        String joint=HumanoidRig.LEFT_ARM.equals(bone)?ArticulatedPlayerRig.LEFT_FOREARM:ArticulatedPlayerRig.RIGHT_FOREARM;
        int index=pose.rig().findBoneIndex(joint);
        if(index>=0) return pose.transform(index).translate(0,-.375f,0);
        return pose.transform(pose.rig().requireBoneIndex(bone)).translate(0,-.625f,0);
    }
}
