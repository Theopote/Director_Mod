package com.director.actor.rig.skeleton;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;

/** Read-only world-space result used by bone picking, highlighting and gizmos. */
public final class RigWorldPose {
    private final RigDefinition rig;
    private final Matrix4f[] transforms;

    private RigWorldPose(RigDefinition rig, Matrix4f[] transforms) {
        this.rig = rig;
        this.transforms = transforms;
    }

    public static RigWorldPose resolve(RigPose pose, Matrix4f actorWorldTransform) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(actorWorldTransform, "actorWorldTransform");
        Matrix4f[] model = pose.calculateModelTransforms();
        Matrix4f[] world = new Matrix4f[model.length];
        for (int i = 0; i < model.length; i++) {
            world[i] = new Matrix4f(actorWorldTransform).mul(model[i]);
        }
        return new RigWorldPose(pose.rig(), world);
    }

    public static RigWorldPose fromTransforms(RigDefinition rig, Matrix4f[] transforms) {
        if (transforms.length != rig.boneCount()) throw new IllegalArgumentException("Bone matrix count mismatch");
        Matrix4f[] copy=new Matrix4f[transforms.length];
        for(int i=0;i<copy.length;i++) copy[i]=new Matrix4f(transforms[i]);
        return new RigWorldPose(rig,copy);
    }

    public RigDefinition rig() { return rig; }

    public Matrix4f transform(int boneIndex) {
        return new Matrix4f(transforms[boneIndex]);
    }

    public Vector3f position(int boneIndex) {
        return transforms[boneIndex].getTranslation(new Vector3f());
    }

    public Vector3f position(String boneId) {
        return position(rig.requireBoneIndex(boneId));
    }
}
