package com.director.timeline.model.entity;

import com.director.actor.rig.skeleton.PoseBlendSettings;
import java.util.Objects;

/** A preset reference owned by a Spawn, with no visible timeline clip or asset ownership. */
public record ActorPresetBinding(String animationId, boolean loop, float speed, PoseBlendSettings blend) {
    public ActorPresetBinding {
        if(animationId==null || animationId.isBlank()) throw new IllegalArgumentException("Preset ID must not be blank");
        if(!Float.isFinite(speed) || speed<.01f || speed>4) throw new IllegalArgumentException("Preset speed must be 0.01–4");
        Objects.requireNonNull(blend);
    }
    public EntityAnimationClip asClip(float start,float end) {
        var clip=new EntityAnimationClip("演员预设",start,end);
        clip.setAnimationId(animationId);clip.setLoop(loop);clip.setSpeed(speed);clip.setBlendSettings(blend);
        return clip;
    }
}
