package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import java.util.*;

/** Frozen numeric edit; typing does not change the asset. */
public final class ActorKeyframeTimeDraft {
    private record Source(BoneTrack track,Object frame) {}
    private final ActorAnimationEditContext context;
    private final List<ActorAnimationAuthoringService.Key> keys;
    private final Map<ActorAnimationAuthoringService.Key,Source> sources=new LinkedHashMap<>();
    private final long selectionRevision=ActorBoneSelectionController.getInstance().getRevision();
    private final long settingsRevision=ActorAnimationAuthoringSettings.getInstance().getRevision();
    public ActorKeyframeTimeDraft(ActorAnimationEditContext context,Collection<ActorAnimationAuthoringService.Key> keys) {
        this.context=context; this.keys=List.copyOf(new LinkedHashSet<>(keys));
        for(var key:this.keys) sources.put(key,source(key));
    }
    private Source source(ActorAnimationAuthoringService.Key key) {
        var track=context==null?null:context.asset().findBoneTrack(key.boneId()).orElse(null);
        if(track==null || key.channel()==null) return new Source(track,null);
        Object frame=switch(key.channel()) {
            case TRANSLATION -> track.translation().findAt(key.time()).orElse(null);
            case ROTATION -> track.rotation().findAt(key.time()).orElse(null);
            case SCALE -> track.scale().findAt(key.time()).orElse(null);
        };
        return new Source(track,frame);
    }
    public boolean isCurrent() {
        return context!=null && context.isCurrent() && !keys.isEmpty()
                && selectionRevision==ActorBoneSelectionController.getInstance().getRevision()
                && settingsRevision==ActorAnimationAuthoringSettings.getInstance().getRevision()
                && sources.entrySet().stream().allMatch(e -> e.getValue().frame()!=null && e.getValue().equals(source(e.getKey())));
    }
    public boolean matches(ActorAnimationEditContext target,Collection<ActorAnimationAuthoringService.Key> selected) {
        return Objects.equals(context,target) && new LinkedHashSet<>(keys).equals(new LinkedHashSet<>(selected)) && isCurrent();
    }
    public float delta(float value) { return keys.size()==1?value-keys.getFirst().time():value; }
    public boolean apply(float value) { return isCurrent() && ActorAnimationAuthoringService.moveTime(context,keys,value); }
}
