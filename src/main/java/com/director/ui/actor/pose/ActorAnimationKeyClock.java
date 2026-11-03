package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.actor.animation.asset.*;
import com.director.timeline.playback.entity.AnimationEventClock;
import java.util.*;

/** Key projection and seeking use the same source clock as playback events. */
public final class ActorAnimationKeyClock {
    private ActorAnimationKeyClock() {}
    public static List<Float> times(TimelineTrack owner,EntityAnimationClip clip,float assetTime,float from,float to) {
        return projection(owner,clip,from,to).times(assetTime);
    }
    /** Reuse only within one render or navigation operation; the next pass reads all edits afresh. */
    public static Projection projection(TimelineTrack owner,EntityAnimationClip clip,float from,float to) {
        return new Projection(owner,clip,from,to);
    }
    public static final class Projection {
        private final float duration,start,end,speed,from,to;
        private final AnimationLoopMode mode;
        private final AnimationEventClock.Projection source;
        private final Map<Float,List<Float>> results=new LinkedHashMap<>();
        private Projection(TimelineTrack owner,EntityAnimationClip clip,float from,float to) {
            var asset=AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
            duration=asset==null || owner==null || owner.getActorTrackPayload()==null?0:asset.duration();
            mode=asset==null || !clip.isLoop()?AnimationLoopMode.ONCE:asset.loopMode();
            start=clip.getStartTime();end=clip.getEndTime();speed=clip.getSpeed();this.from=from;this.to=to;
            source=duration>0 && asset.purpose()==com.director.actor.animation.AnimationData.Purpose.LOCOMOTION
                    ?AnimationEventClock.projection(owner.getActorTrackPayload(),clip,from,to):null;
        }
        public List<Float> times(float assetTime) {
            if(duration<=0 || !Float.isFinite(assetTime))return List.of();
            var cached=results.get(assetTime);if(cached!=null)return cached;
            var result=source==null?List.copyOf(com.director.ui.timeline.render.ActorKeyframeProjection.times(assetTime,duration,mode,start,end,speed,from,to))
                    :source.times(assetTime/duration);
            if(results.size()>=1024)results.remove(results.keySet().iterator().next());
            results.put(assetTime,result);
            return result;
        }
    }
    public static boolean seek(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,float assetTime) {
        var target=times(owner,clip,assetTime,clip.getStartTime(),clip.getEndTime()).stream()
                .min(Comparator.comparingDouble(t->Math.abs(t-model.getCurrentTime())));
        target.ifPresent(model::setCurrentTime);
        return target.isPresent();
    }
}
