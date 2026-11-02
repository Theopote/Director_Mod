package com.director.timeline.playback.entity;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.runtime.*;
import com.director.timeline.model.entity.*;
import java.util.*;

/** Shared occurrence mapping for transient events and deterministic prop reconstruction. */
public final class AnimationEventClock {
    private AnimationEventClock() {}
    public static List<Float> times(ActorTrack track,EntityAnimationClip clip,AnimationEvent event,float from,float to) {
        float start=clip.getStartTime(),end=Math.min(to,clip.getEndTime());
        if(!Float.isFinite(start) || !Float.isFinite(end) || end<start || from>end || !Float.isFinite(event.getNormalizedTime())) return List.of();
        if(event.getTiming()==AnimationEvent.Timing.CLIP) {
            float at=start+event.getNormalizedTime()*clip.getLength();
            return at>=from && at<=end?List.of(at):List.of();
        }
        return times(track,clip,event.getNormalizedTime(),from,to);
    }
    /** Invert the source clock for both UI keys and events. */
    public static List<Float> times(ActorTrack track,EntityAnimationClip clip,float normalized,float from,float to) {
        float start=clip.getStartTime(),end=Math.min(to,clip.getEndTime());
        if(!Float.isFinite(start)||!Float.isFinite(end)||end<start||from>end||!Float.isFinite(normalized)||normalized<0||normalized>1) return List.of();
        var asset=AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        var builtin=asset==null?AnimationLibrary.getInstance().get(clip.getAnimationId()).orElse(null):null;
        float duration=asset!=null?asset.duration():builtin!=null?builtin.getDuration():clip.getLength();
        if(!Float.isFinite(duration) || duration<=0) return List.of();
        var mode=!clip.isLoop()?AnimationLoopMode.ONCE:asset!=null?asset.loopMode():AnimationLoopMode.LOOP;
        var actor=track.getActorRef()==null?null:ActorRegistryManager.getInstance().getActor(track.getActorRef().getActorId()).orElse(null);
        var profile=actor==null?null:actor.getRigProfile();
        var sampler=profile!=null && ActorPresetClock.purpose(clip.getAnimationId())==AnimationData.Purpose.LOCOMOTION?new EntityClipSampler(track):null;
        var boundaries=new TreeSet<Float>();boundaries.add(Math.max(start,from));boundaries.add(end);
        if(boundaries.first()>end) return List.of();
        if(sampler!=null) for(var c:track.getClips()) {
            if(c.getStartTime()>boundaries.first() && c.getStartTime()<end) boundaries.add(c.getStartTime());
            if(c.getEndTime()>boundaries.first() && c.getEndTime()<end) boundaries.add(c.getEndTime());
        }
        var points=new ArrayList<>(boundaries);if(points.size()==1) points.add(end);
        var result=new TreeSet<Float>();
        for(int i=1;i<points.size();i++) {
            float a=points.get(i-1),b=points.get(i);
            var middle=sampler==null?null:sampler.sampleSnapshot(a+(b-a)*.5f);
            boolean distance=middle!=null && ActorPresetClock.followsDistance(clip.getAnimationId(),middle);
            java.util.function.DoubleUnaryOperator raw=t->distance
                    ?ActorRouteGait.phaseCycles(sampler.sampleSnapshot((float)t),profile)*duration
                    :Math.max(0,t-start)*Math.max(.01f,clip.getSpeed());
            double lo=raw.applyAsDouble(a),hi=raw.applyAsDouble(b);
            if(!Double.isFinite(lo) || !Double.isFinite(hi) || hi<lo) continue;
            var effective=distance?AnimationLoopMode.LOOP:mode;
            double phase=normalized*duration;
            add(result,raw,a,b,lo,hi,phase,effective==AnimationLoopMode.ONCE?0:effective==AnimationLoopMode.LOOP?duration:2d*duration);
            if(effective==AnimationLoopMode.PING_PONG && phase>0 && phase<duration)
                add(result,raw,a,b,lo,hi,2d*duration-phase,2d*duration);
        }
        return List.copyOf(result);
    }
    private static void add(Set<Float> result,java.util.function.DoubleUnaryOperator raw,float a,float b,
                            double lo,double hi,double phase,double period) {
        long first=period==0?0:Math.max(0,(long)Math.ceil((lo-phase)/period));
        long last=period==0?0:(long)Math.floor((hi-phase)/period);
        if(last-first>100000) throw new IllegalArgumentException("动画事件重复次数过多，请缩短片段或降低速度");
        for(long cycle=first;cycle<=last;cycle++) {
            double target=phase+cycle*period;if(target<lo || target>hi) continue;
            double left=a,right=b;
            if(target<=lo) right=a;
            else for(int step=0;step<40;step++) {double mid=(left+right)/2;if(raw.applyAsDouble(mid)>=target) right=mid;else left=mid;}
            result.add((float)right);
        }
    }
}
