package com.director.actor.rig.skeleton;

import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;

/** Mutable pose instance backed by indexed local bone transforms. */
public final class RigPose {
    private final RigDefinition rig;
    private final BoneTransform[] localTransforms;
    private final BitSet authoredBones;

    public RigPose(RigDefinition rig) {
        this.rig = Objects.requireNonNull(rig, "rig");
        this.localTransforms = new BoneTransform[rig.boneCount()];
        this.authoredBones = new BitSet(rig.boneCount());
        for (int i = 0; i < localTransforms.length; i++) {
            localTransforms[i] = rig.bone(i).restLocal();
        }
    }

    private RigPose(RigDefinition rig, BoneTransform[] localTransforms, BitSet authoredBones) {
        this.rig = rig;
        this.localTransforms = localTransforms;
        this.authoredBones = authoredBones;
    }

    public RigDefinition rig() { return rig; }

    public BoneTransform localTransform(int boneIndex) {
        return localTransforms[checkedIndex(boneIndex)];
    }

    public BoneTransform localTransform(String boneId) {
        return localTransform(rig.requireBoneIndex(boneId));
    }

    public void setLocalTransform(int boneIndex, BoneTransform transform) {
        int index = checkedIndex(boneIndex);
        localTransforms[index] = Objects.requireNonNull(transform, "transform");
        authoredBones.set(index);
    }

    public void setLocalTransform(String boneId, BoneTransform transform) {
        setLocalTransform(rig.requireBoneIndex(boneId), transform);
    }

    public boolean isAuthored(int boneIndex) {
        return authoredBones.get(checkedIndex(boneIndex));
    }

    public BitSet authoredBones() {
        return (BitSet) authoredBones.clone();
    }

    public RigPose copy() {
        return new RigPose(rig, Arrays.copyOf(localTransforms, localTransforms.length),
                (BitSet) authoredBones.clone());
    }

    /** Calculates one model-space matrix per bone, preserving definition order. */
    public Matrix4f[] calculateModelTransforms() {
        Matrix4f[] result = new Matrix4f[localTransforms.length];
        for (int i = 0; i < result.length; i++) {
            calculateModelTransform(i, result);
        }
        return result;
    }

    private Matrix4f calculateModelTransform(int index, Matrix4f[] cache) {
        if (cache[index] != null) {
            return cache[index];
        }
        Matrix4f local = localTransforms[index].toMatrix();
        int parent = rig.bone(index).parentIndex();
        cache[index] = parent == -1
                ? local
                : new Matrix4f(calculateModelTransform(parent, cache)).mul(local);
        return cache[index];
    }

    private int checkedIndex(int index) {
        if (index < 0 || index >= localTransforms.length) {
            throw new IndexOutOfBoundsException("Bone index: " + index);
        }
        return index;
    }
}
