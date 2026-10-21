package com.director.actor.rig.skeleton;

import java.util.*;

/** Persistent layer selection uses bone IDs, never transient skeleton indices. */
public record PoseBlendSettings(PoseLayer.BlendMode mode, float weight, int channels,
                                boolean allBones, Set<String> boneIds, float fadeIn, float fadeOut) {
    public static final PoseBlendSettings DEFAULT = new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,
            RigPose.ALL_CHANNELS,true,Set.of());
    /** Convenience for a layer without timed transitions. */
    public PoseBlendSettings(PoseLayer.BlendMode mode,float weight,int channels,boolean allBones,Set<String> boneIds) {
        this(mode,weight,channels,allBones,boneIds,0,0);
    }
    public PoseBlendSettings {
        Objects.requireNonNull(mode);
        if (!Float.isFinite(weight) || weight < 0 || weight > 1) throw new IllegalArgumentException("Invalid pose weight");
        if ((channels & ~RigPose.ALL_CHANNELS) != 0) throw new IllegalArgumentException("Invalid pose channels");
        if (!Float.isFinite(fadeIn) || fadeIn < 0 || !Float.isFinite(fadeOut) || fadeOut < 0)
            throw new IllegalArgumentException("Fade durations must be finite and nonnegative");
        if(boneIds==null || boneIds.stream().anyMatch(id -> id==null || id.isBlank()))
            throw new IllegalArgumentException("Bone IDs must be a non-null set of nonblank identifiers");
        boneIds = Set.copyOf(boneIds);
    }
    public boolean includes(String id) { return allBones || boneIds.contains(id); }
    /** Timeline seconds, independent of asset playback speed, loop phase and render history.
     * Oversized fades shrink proportionally so short clips still reach the requested weight.
     */
    public float envelopeAt(float localTime,float duration) {
        if (!Float.isFinite(localTime) || !Float.isFinite(duration) || duration <= 0
                || localTime < 0 || localTime > duration) return 0;
        double total=(double)fadeIn+fadeOut;
        double scale=total > duration ? duration/total : 1;
        double in=fadeIn*scale, out=fadeOut*scale;
        double rampIn=in==0?1:Math.clamp(localTime/in,0d,1d);
        double rampOut=out==0?1:Math.clamp(((double)duration-localTime)/out,0d,1d);
        double t=Math.min(rampIn,rampOut);
        return (float)(t*t*(3-2*t));
    }
    public float effectiveWeight(float envelope) {
        if (!Float.isFinite(envelope) || envelope < 0 || envelope > 1)
            throw new IllegalArgumentException("Invalid pose envelope");
        return weight*envelope;
    }
    public PoseLayer layer(RigPose source) { return layer(source,1); }
    public PoseLayer layer(RigPose source,float envelope) {
        BitSet mask = new BitSet();
        for (int i=0;i<source.rig().boneCount();i++) if (includes(source.rig().bone(i).id())) mask.set(i);
        return new PoseLayer(source,null,mask,channels,effectiveWeight(envelope),mode);
    }
    public boolean contributes(RigPose source,int bone) { return contributes(source,bone,1); }
    public boolean contributes(RigPose source,int bone,float envelope) {
        return effectiveWeight(envelope) > 0 && includes(source.rig().bone(bone).id()) && (source.authoredChannels(bone)&channels)!=0;
    }
}
