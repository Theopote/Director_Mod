package com.director.actor.rig.skeleton;

import java.util.*;

/** Persistent layer selection uses bone IDs, never transient skeleton indices. */
public record PoseBlendSettings(PoseLayer.BlendMode mode, float weight, int channels,
                                boolean allBones, Set<String> boneIds) {
    public static final PoseBlendSettings DEFAULT = new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,
            RigPose.ALL_CHANNELS,true,Set.of());
    public PoseBlendSettings {
        Objects.requireNonNull(mode);
        if (!Float.isFinite(weight) || weight < 0 || weight > 1) throw new IllegalArgumentException("Invalid pose weight");
        if ((channels & ~RigPose.ALL_CHANNELS) != 0) throw new IllegalArgumentException("Invalid pose channels");
        boneIds = Set.copyOf(boneIds);
    }
    public boolean includes(String id) { return allBones || boneIds.contains(id); }
    public PoseLayer layer(RigPose source) {
        BitSet mask = new BitSet();
        for (int i=0;i<source.rig().boneCount();i++) if (includes(source.rig().bone(i).id())) mask.set(i);
        return new PoseLayer(source,null,mask,channels,weight,mode);
    }
    public boolean contributes(RigPose source,int bone) {
        return weight > 0 && includes(source.rig().bone(bone).id()) && (source.authoredChannels(bone)&channels)!=0;
    }
}
