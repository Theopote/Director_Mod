package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import java.util.Objects;

/** Frozen authored input for one gizmo gesture. It never registers an asset or writes keys. */
public final class ActorBoneAnimationDraft {
    private final RigPose authored;
    private final String animationId;
    public String animationId() { return animationId; }
    private final PoseBlendSettings settings;
    private final float envelope;
    public ActorBoneAnimationDraft(RigPose source,String bone,BoneTransform value,int channels,
                                   PoseBlendSettings settings,float localTime,float duration) {
        this(source,bone,value,channels,settings,localTime,duration,null);
    }
    public ActorBoneAnimationDraft(RigPose source,String bone,BoneTransform value,int channels,
                                   PoseBlendSettings settings,float localTime,float duration,String animationId) {
        this.animationId=animationId;
        if(channels!=RigPose.TRANSLATION && channels!=RigPose.ROTATION && channels!=RigPose.SCALE)
            throw new IllegalArgumentException("A bone gesture edits one channel");
        authored=Objects.requireNonNull(source).copy();
        authored.setLocalTransform(authored.rig().requireBoneIndex(bone),value,channels);
        this.settings=Objects.requireNonNull(settings);
        envelope=settings.envelopeAt(localTime,duration);
    }
    public RigPose authoredPose() { return authored.copy(); }
    public RigDefinition rig() { return authored.rig(); }
    public PoseBlendSettings settings() { return settings; }
    public float envelope() { return envelope; }
}
