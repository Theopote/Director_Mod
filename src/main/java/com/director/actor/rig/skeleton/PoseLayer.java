package com.director.actor.rig.skeleton;

import java.util.BitSet;
import java.util.Objects;

/** Frozen local-space layer. Source and reference contain absolute local TRS values. */
public final class PoseLayer {
    public enum BlendMode { OVERRIDE, ADDITIVE }
    private final RigPose source;
    private final RigPose reference;
    private final BitSet bones;
    private final int channels;
    private final float weight;
    private final BlendMode mode;

    public PoseLayer(RigPose source,RigPose reference,BitSet bones,int channels,float weight,BlendMode mode) {
        this.source=Objects.requireNonNull(source,"source").copy();
        this.reference=mode==BlendMode.OVERRIDE?null:reference==null?new RigPose(source.rig()):reference.copy();
        this.bones=(BitSet)Objects.requireNonNull(bones,"bones").clone();
        if(this.bones.length()>source.rig().boneCount()) throw new IllegalArgumentException("Unknown layer bone index");
        if((channels&~RigPose.ALL_CHANNELS)!=0) throw new IllegalArgumentException("Unknown layer channels");
        if(!Float.isFinite(weight)) throw new IllegalArgumentException("Layer weight must be finite");
        this.channels=channels;
        this.weight=Math.clamp(weight,0f,1f);
        this.mode=Objects.requireNonNull(mode,"mode");
    }
    public static PoseLayer override(RigPose source,float weight) {
        return new PoseLayer(source,null,allBones(source),RigPose.ALL_CHANNELS,weight,BlendMode.OVERRIDE);
    }
    public static PoseLayer additive(RigPose source,RigPose reference,float weight) {
        return new PoseLayer(source,reference,allBones(source),RigPose.ALL_CHANNELS,weight,BlendMode.ADDITIVE);
    }
    private static BitSet allBones(RigPose source) {
        var result=new BitSet();result.set(0,source.rig().boneCount());return result;
    }
    public RigPose pose() { return source.copy(); }
    public RigPose reference() { return reference==null?new RigPose(source.rig()):reference.copy(); }
    public BitSet boneMask() { return (BitSet)bones.clone(); }
    public int channelMask() { return channels; }
    public float weight() { return weight; }
    public BlendMode mode() { return mode; }
    // Composer reads frozen snapshots; it never writes back into a layer.
    RigPose sourceSnapshot() { return source; }
    RigPose referenceSnapshot() { return reference; }
    boolean includes(int bone) { return bones.get(bone); }
}
