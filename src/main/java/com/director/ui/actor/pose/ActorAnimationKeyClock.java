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
        var asset=AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        if(asset==null || owner==null || owner.getActorTrackPayload()==null || asset.duration()<=0) return List.of();
        if(asset.purpose()!=com.director.actor.animation.AnimationData.Purpose.LOCOMOTION)
            return com.director.ui.timeline.render.ActorKeyframeProjection.times(assetTime,asset.duration(),clip.isLoop()?asset.loopMode():AnimationLoopMode.ONCE,
                    clip.getStartTime(),clip.getEndTime(),clip.getSpeed(),from,to);
        return AnimationEventClock.times(owner.getActorTrackPayload(),clip,assetTime/asset.duration(),from,to);
    }
    public static void seek(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,float assetTime) {
        times(owner,clip,assetTime,clip.getStartTime(),clip.getEndTime()).stream()
                .min(Comparator.comparingDouble(t->Math.abs(t-model.getCurrentTime()))).ifPresent(model::setCurrentTime);
    }
}
