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
    /** A new selection gets its own loop default; prior loop flags must not leak between purposes. */
    public static ActorPresetBinding select(String id,ActorPresetBinding previous) {
        if(id==null) return null;
        var asset=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(id).orElse(null);
        boolean loop=asset!=null?asset.loopMode()!=com.director.actor.animation.asset.AnimationLoopMode.ONCE:
                com.director.actor.animation.AnimationLibrary.getInstance().get(id).map(a->a.purpose().defaultLoop()).orElse(false);
        return new ActorPresetBinding(id,loop,previous==null?1:previous.speed(),previous==null?PoseBlendSettings.DEFAULT:previous.blend());
    }
    public EntityAnimationClip asClip(float start,float end) {
        var clip=new EntityAnimationClip("演员预设",start,end);
        clip.setAnimationId(animationId);clip.setLoop(loop);clip.setSpeed(speed);clip.setBlendSettings(blend);
        return clip;
    }
}
