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
        return projection(track,clip,from,to).times(event);
    }
    /** Invert the source clock for both UI keys and events. */
    public static List<Float> times(ActorTrack track,EntityAnimationClip clip,float normalized,float from,float to) {
        return projection(track,clip,from,to).times(normalized);
    }

    /** A thread-confined batch for one render/dispatch pass. Never retain across edits. */
    public static Projection projection(ActorTrack track,EntityAnimationClip clip,float from,float to) {
        return new Projection(track,clip,from,to,EntityClipSampler::new);
    }

    public static final class Projection {
        private static final int MAX_PHASE_SAMPLES=4096,MAX_RESULTS=1024;
        private final ActorTrack track;
        private final String animationId;
        private final float start,end,from,length,speed;
        private final boolean loop;
        private final java.util.function.Function<ActorTrack,EntityClipSampler> samplerFactory;
        private final Map<Float,Double> phases=new LinkedHashMap<>();
        private final Map<Float,List<Float>> results=new LinkedHashMap<>();
        private List<Segment> segments;
        private float duration;
        private record Segment(float start,float end,double lo,double hi,
                               java.util.function.DoubleUnaryOperator raw,AnimationLoopMode mode) {}

        Projection(ActorTrack track,EntityAnimationClip clip,float from,float to,
                   java.util.function.Function<ActorTrack,EntityClipSampler> samplerFactory) {
            this.track=track;this.from=from;this.start=clip.getStartTime();this.end=Math.min(to,clip.getEndTime());
            this.length=clip.getLength();this.speed=clip.getSpeed();this.loop=clip.isLoop();
            this.animationId=clip.getAnimationId();this.samplerFactory=samplerFactory;
        }

        public List<Float> times(AnimationEvent event) {
            if(!valid() || !Float.isFinite(event.getNormalizedTime()))return List.of();
            if(event.getTiming()==AnimationEvent.Timing.CLIP) {
                float at=start+event.getNormalizedTime()*length;
                return at>=from && at<=end?List.of(at):List.of();
            }
            return times(event.getNormalizedTime());
        }

        /** Map a scene time back to the event phase using the same clock as occurrence lookup. */
        public Optional<Float> phaseAt(AnimationEvent.Timing timing, float time) {
            if (!valid() || !Float.isFinite(time) || time < Math.max(from, start) || time > end || length <= 0)
                return Optional.empty();
            if (timing == AnimationEvent.Timing.CLIP)
                return Optional.of(Math.clamp((time - start) / length, 0f, 1f));
            if (segments == null) prepare();
            for (var segment : segments) {
                if (time < segment.start() || time > segment.end()) continue;
                double cycles = segment.raw().applyAsDouble(time) / duration;
                if (!Double.isFinite(cycles) || cycles < 0) continue;
                switch (segment.mode()) {
                    case ONCE -> {
                        if (cycles <= 1) return Optional.of((float) cycles);
                    }
                    case LOOP -> {
                        float phase = (float) (cycles - Math.floor(cycles));
                        return Optional.of(phase == 1f ? 0f : phase);
                    }
                    case PING_PONG -> {
                        double phase = cycles % 2;
                        return Optional.of((float) Math.min(phase, 2 - phase));
                    }
                }
            }
            return Optional.empty();
        }

        private boolean valid() {
            // Playback's initial event window deliberately starts at -infinity.
            return Float.isFinite(start) && Float.isFinite(end) && !Float.isNaN(from) && end>=start && from<=end;
        }

        public List<Float> times(float normalized) {
            if(!valid() || !Float.isFinite(normalized) || normalized<0 || normalized>1)return List.of();
            var cached=results.get(normalized);if(cached!=null)return cached;
            if(segments==null)prepare();
            var result=new TreeSet<Float>();
            for(var segment:segments) {
                double phase=normalized*duration;
                var mode=segment.mode();
                add(result,segment.raw(),segment.start(),segment.end(),segment.lo(),segment.hi(),phase,
                        mode==AnimationLoopMode.ONCE?0:mode==AnimationLoopMode.LOOP?duration:2d*duration);
                if(mode==AnimationLoopMode.PING_PONG && phase>0 && phase<duration)
                    add(result,segment.raw(),segment.start(),segment.end(),segment.lo(),segment.hi(),2d*duration-phase,2d*duration);
            }
            var answer=List.copyOf(result);
            if(results.size()>=MAX_RESULTS)results.remove(results.keySet().iterator().next());
            results.put(normalized,answer);
            return answer;
        }

        private void prepare() {
            segments=new ArrayList<>();
            var asset=AnimationAssetLibrary.getInstance().find(animationId).orElse(null);
            var builtin=asset==null?AnimationLibrary.getInstance().get(animationId).orElse(null):null;
            duration=asset!=null?asset.duration():builtin!=null?builtin.getDuration():length;
            if(!Float.isFinite(duration) || duration<=0)return;
            var mode=!loop?AnimationLoopMode.ONCE:asset!=null?asset.loopMode():AnimationLoopMode.LOOP;
            var actor=track==null || track.getActorRef()==null?null:ActorRegistryManager.getInstance().getActor(track.getActorRef().getActorId()).orElse(null);
            var profile=actor==null?null:actor.getRigProfile();
            var sampler=profile!=null && ActorPresetClock.purpose(animationId)==AnimationData.Purpose.LOCOMOTION?samplerFactory.apply(track):null;
            var boundaries=new TreeSet<Float>();boundaries.add(Math.max(start,from));boundaries.add(end);
            if(sampler!=null)for(var clip:track.getClips()) {
                if(clip.getStartTime()>boundaries.first() && clip.getStartTime()<end)boundaries.add(clip.getStartTime());
                if(clip.getEndTime()>boundaries.first() && clip.getEndTime()<end)boundaries.add(clip.getEndTime());
            }
            var points=new ArrayList<>(boundaries);if(points.size()==1)points.add(end);
            for(int i=1;i<points.size();i++) {
                float a=points.get(i-1),b=points.get(i);
                float midpoint=a+(b-a)*.5f;
                var middle=sampler==null?null:sampler.sampleSnapshot(midpoint);
                boolean distance=middle!=null && ActorPresetClock.followsDistance(animationId,middle);
                if(distance)rememberPhase(midpoint,(double)(ActorRouteGait.phaseCycles(middle,profile)*duration));
                java.util.function.DoubleUnaryOperator raw=t->{
                    if(!distance)return Math.max(0,t-start)*Math.max(.01f,speed);
                    float time=(float)t;
                    var value=phases.get(time);
                    if(value==null) {
                        value=(double)(ActorRouteGait.phaseCycles(sampler.sampleSnapshot(time),profile)*duration);
                        rememberPhase(time,value);
                    }
                    return value;
                };
                double lo=raw.applyAsDouble(a),hi=raw.applyAsDouble(b);
                if(Double.isFinite(lo) && Double.isFinite(hi) && hi>=lo)
                    segments.add(new Segment(a,b,lo,hi,raw,distance?AnimationLoopMode.LOOP:mode));
            }
        }

        private void rememberPhase(float time,double phase) {
            if(phases.size()>=MAX_PHASE_SAMPLES)phases.remove(phases.keySet().iterator().next());
            phases.put(time,phase);
        }
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
