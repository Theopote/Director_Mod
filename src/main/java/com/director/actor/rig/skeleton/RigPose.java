package com.director.actor.rig.skeleton;

import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;

/** Mutable pose instance backed by indexed local bone transforms. */
public final class RigPose {
    public static final int TRANSLATION = 1;
    public static final int ROTATION = 2;
    public static final int SCALE = 4;
    public static final int ALL_CHANNELS = TRANSLATION | ROTATION | SCALE;
    private final RigDefinition rig;
    private LimbBendMode limbBendMode=LimbBendMode.SMOOTH;
    public LimbBendMode limbBendMode() { return limbBendMode; }
    public void setLimbBendMode(LimbBendMode value) { limbBendMode=Objects.requireNonNull(value); }
    private final BoneTransform[] localTransforms;
    private final BitSet authoredBones;
    private final int[] authoredChannels;

    public RigPose(RigDefinition rig) {
        this.rig = Objects.requireNonNull(rig, "rig");
        this.localTransforms = new BoneTransform[rig.boneCount()];
        this.authoredBones = new BitSet(rig.boneCount());
        this.authoredChannels = new int[rig.boneCount()];
        for (int i = 0; i < localTransforms.length; i++) {
            localTransforms[i] = rig.bone(i).restLocal();
        }
    }

    private RigPose(RigDefinition rig, BoneTransform[] localTransforms, BitSet authoredBones, int[] authoredChannels) {
        this.rig = rig;
        this.localTransforms = localTransforms;
        this.authoredBones = authoredBones;
        this.authoredChannels = authoredChannels;
    }

    public RigDefinition rig() { return rig; }

    public BoneTransform localTransform(int boneIndex) {
        return localTransforms[checkedIndex(boneIndex)];
    }

    public BoneTransform localTransform(String boneId) {
        return localTransform(rig.requireBoneIndex(boneId));
    }

    public void setLocalTransform(int boneIndex, BoneTransform transform) {
        setLocalTransform(boneIndex, transform, ALL_CHANNELS);
    }

    /** Writes only supplied channels; other channels retain their current values. */
    public void setLocalTransform(int boneIndex, BoneTransform transform, int channels) {
        int index = checkedIndex(boneIndex);
        Objects.requireNonNull(transform, "transform");
        if ((channels & ~ALL_CHANNELS) != 0) throw new IllegalArgumentException("Unknown pose channels");
        if (channels == 0) return;
        BoneTransform current = localTransforms[index];
        localTransforms[index] = new BoneTransform(
                (channels & TRANSLATION) != 0 ? transform.translation() : current.translation(),
                (channels & ROTATION) != 0 ? transform.rotation() : current.rotation(),
                (channels & SCALE) != 0 ? transform.scale() : current.scale());
        authoredBones.set(index);
        authoredChannels[index] |= channels;
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

    public int authoredChannels(int boneIndex) {
        return authoredChannels[checkedIndex(boneIndex)];
    }

    public RigPose copy() {
        RigPose result=new RigPose(rig, Arrays.copyOf(localTransforms, localTransforms.length),
                (BitSet) authoredBones.clone(), authoredChannels.clone());
        result.limbBendMode=limbBendMode;
        return result;
    }

    /** Returns a copy with only the authored TRS channels from the higher layer replaced. */
    public RigPose overlay(RigPose higherLayer) {
        Objects.requireNonNull(higherLayer, "higherLayer");
        if (!sameBoneLayout(rig, higherLayer.rig)) {
            throw new IllegalArgumentException("Cannot blend poses from incompatible rigs");
        }
        RigPose result=copy();
        for(int i=higherLayer.authoredBones.nextSetBit(0);i>=0;i=higherLayer.authoredBones.nextSetBit(i+1))
            result.setLocalTransform(i,higherLayer.localTransforms[i],higherLayer.authoredChannels[i]);
        return result;
    }

    private static boolean sameBoneLayout(RigDefinition a,RigDefinition b) {
        return RigPoseComposer.sameLayout(a,b);
    }

    /** Calculates one model-space matrix per bone, preserving definition order. */
    public Matrix4f[] calculateModelTransforms() {
        Matrix4f[] result = new Matrix4f[localTransforms.length];
        for (int i = 0; i < result.length; i++) {
            calculateModelTransform(i, result);
        }
        return result;
    }

    /** Calculates only the requested bone and its ancestors, for repeated constraint queries. */
    public Matrix4f calculateModelTransform(int index) {
        return calculateModelTransform(checkedIndex(index),new Matrix4f[localTransforms.length]);
    }

    private Matrix4f calculateModelTransform(int index, Matrix4f[] cache) {
        if (cache[index] != null) {
            return cache[index];
        }
        Matrix4f local = localTransforms[index].toMatrix();
        if(rig.id().equals(ArticulatedPlayerRig.DEFINITION.id()) && LimbBendTransform.isJoint(rig.bone(index).id())) {
            var value=localTransforms[index]; var bend=new LimbBendTransform(value.rotation(),value.scale(),limbBendMode);
            var offset=bend.lowerOffset().mul(1f/16); offset.y=-offset.y; offset.z=-offset.z;
            local=new Matrix4f().translate(value.translation().add(offset)).rotate(bend.rotation()).scale(value.scale());
        }
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
