package com.director.actor.query;

import com.director.timeline.model.entity.*;
import java.util.LinkedHashSet;
import java.util.Set;

/** Includes visible animation clips and both references owned by a lifecycle clip. */
public final class ActorAnimationReferences {
    private ActorAnimationReferences() {}
    public static Set<String> ids(ActorTrack track) {
        var result=new LinkedHashSet<String>();
        for(var clip:track.getClips()) {
            if(clip instanceof EntityAnimationClip animation) add(result,animation.getAnimationId());
            if(clip instanceof EntitySpawnClip spawn) {
                add(result,spawn.getBoneAnimationId());
                if(spawn.getPreset()!=null) add(result,spawn.getPreset().animationId());
            }
        }
        return Set.copyOf(result);
    }
    private static void add(Set<String> ids,String id) { if(id!=null && !id.isBlank()) ids.add(id); }
}
