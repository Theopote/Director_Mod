package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.timeline.model.entity.EntitySpawnClip;

/** Every preset attachment entry validates the same reference and skeleton contract. */
public final class ActorPresetValidation {
    private ActorPresetValidation() {}
    public static void require(Actor actor,EntitySpawnClip spawn,AnimationAssetLibrary library,String id) {
        if(spawn==null || id==null) throw new IllegalArgumentException("请选择演员片段和动作");
        requireReference(actor, library, id, spawn.getBoneAnimationId());
    }

    public static void requireReference(Actor actor, AnimationAssetLibrary library, String id, String manualId) {
        if(id.equals(manualId)) throw new IllegalArgumentException("这是本片段的手工修正动画，不能同时作为预设；请先复制动画或选择其他动作。");
        if(ActorAnimationCatalog.entries(library,AnimationLibrary.getInstance().getAllAnimations(),actor).stream()
                .noneMatch(e->e.id().equals(id) && e.compatible())) throw new IllegalArgumentException("动作不存在或与演员骨架不兼容");
    }
}
