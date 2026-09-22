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

    /** Returns a copy with authored transforms from {@code higherLayer} replacing this pose. */
    public RigPose overlay(RigPose higherLayer) {
        Objects.requireNonNull(higherLayer, "higherLayer");
        if (!sameBoneLayout(rig, higherLayer.rig)) {
            throw new IllegalArgumentException("Cannot blend poses from incompatible rigs");
        }
        RigPose result = copy();
        for (int i = higherLayer.authoredBones.nextSetBit(0); i >= 0;
             i = higherLayer.authoredBones.nextSetBit(i + 1)) {
            result.setLocalTransform(i, higherLayer.localTransforms[i]);
        }
        return result;
    }

    /** Index-wise bone id/parent match: overlay only needs matching layout, not the same rig id. */
    private static boolean sameBoneLayout(RigDefinition a, RigDefinition b) {
        if (a == b) return true;
        if (a.boneCount() != b.boneCount()) return false;
        for (int i = 0; i < a.boneCount(); i++) {
            BoneDefinition boneA = a.bone(i);
            BoneDefinition boneB = b.bone(i);
            if (!boneA.id().equals(boneB.id()) || boneA.parentIndex() != boneB.parentIndex()) return false;
        }
        return true;
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
