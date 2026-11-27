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
        return new Projection(track, clip, from, to, actorTrack -> new EntityClipSampler(actorTrack, false));
    }

    static Projection playbackProjection(ActorTrack track, EntityAnimationClip clip, float from, float to) {
        return new Projection(track, clip, from, to, actorTrack -> new EntityClipSampler(actorTrack, false), 256);
    }

    public static final class Projection {
        private static final int MAX_PHASE_SAMPLES = 4096, MAX_RESULTS = 128, BATCH_OCCURRENCE_LIMIT = 16384;
        private final ActorTrack track;
        private final String animationId;
        private final float start,end,from,length,speed;
        private final boolean loop;
        private final java.util.function.Function<ActorTrack,EntityClipSampler> samplerFactory;
        private final Map<Float,Double> phases=new LinkedHashMap<>();
        private final Map<Float,List<Float>> results=new LinkedHashMap<>();
        private List<Segment> segments;
        private float duration;
        private final int occurrenceLimit;
        private boolean truncated;
        private record Segment(float start,float end,double lo,double hi,
                               java.util.function.DoubleUnaryOperator raw,AnimationLoopMode mode) {}

        Projection(ActorTrack track,EntityAnimationClip clip,float from,float to,
                   java.util.function.Function<ActorTrack,EntityClipSampler> samplerFactory) {
            this(track, clip, from, to, samplerFactory, BATCH_OCCURRENCE_LIMIT);
        }

        private Projection(ActorTrack track, EntityAnimationClip clip, float from, float to,
                           java.util.function.Function<ActorTrack, EntityClipSampler> samplerFactory, int occurrenceLimit) {
            this.occurrenceLimit = occurrenceLimit;
            this.track=track;this.from=from;this.start=clip.getStartTime();this.end=Math.min(to,clip.getEndTime());
            this.length=clip.getLength();this.speed=clip.getSpeed();this.loop=clip.isLoop();
            this.animationId=clip.getAnimationId();this.samplerFactory=samplerFactory;
        }

        /**
         * Occurrence times for {@code event} guaranteed inside the projection window
         * {@code [from, end]} (and thus {@code [from, to]} of the factory call).
         */
        public List<Float> times(AnimationEvent event) {
            if(!valid() || !Float.isFinite(event.getNormalizedTime()))return List.of();
            if(event.getTiming()==AnimationEvent.Timing.CLIP) {
                float at=start+event.getNormalizedTime()*length;
                return at>=from && at<=end?List.of(at):List.of();
            }
            return times(event.getNormalizedTime());
        }

        public boolean isTruncated() { return truncated; }

        /** Latest crossing in the window, without enumerating previous cycles. */
        public Optional<Float> latest(AnimationEvent event) {
            float normalized = event.getNormalizedTime();
            if (!valid() || !Float.isFinite(normalized) || normalized < 0 || normalized > 1) return Optional.empty();
            if (event.getTiming() == AnimationEvent.Timing.CLIP) {
                float at = start + normalized * length;
                return at >= from && at <= end ? Optional.of(at) : Optional.empty();
            }
            if (segments == null) prepare();
            for (var segment : segments.reversed()) {
                double phase = normalized * duration;
                double target = lastTarget(segment, phase, period(segment.mode()));
                if (segment.mode() == AnimationLoopMode.PING_PONG && phase > 0 && phase < duration)
                    target = Math.max(target, lastTarget(segment, 2d * duration - phase, 2d * duration));
                if (target >= segment.lo()) return Optional.of(timeAt(segment, target));
            }
            return Optional.empty();
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
            for (var segment : segments.reversed()) {
                double phase = normalized * duration;
                add(result, segment, phase, period(segment.mode()));
                if (segment.mode() == AnimationLoopMode.PING_PONG && phase > 0 && phase < duration)
                    add(result, segment, 2d * duration - phase, 2d * duration);
                if (result.size() > occurrenceLimit) {
                    truncated = true;
                    while (result.size() > occurrenceLimit) result.pollFirst();
                    break;
                }
            }
            var answer=List.copyOf(result);
            if(results.size()>=MAX_RESULTS)results.remove(results.keySet().iterator().next());
            results.put(normalized,answer);
            return answer;
        }

        private double period(AnimationLoopMode mode) {
            return switch (mode) {
                case ONCE -> 0;
                case LOOP -> duration;
                case PING_PONG -> 2d * duration;
            };
        }

        private void add(Set<Float> result, Segment segment, double phase, double period) {
            if (period == 0) {
                if (phase >= segment.lo() && phase <= segment.hi()) result.add(timeAt(segment, phase));
                return;
            }
            double last = Math.floor((segment.hi() - phase) / period);
            double first = Math.max(0, Math.ceil((segment.lo() - phase) / period));
            if (last < first) return;
            double count = last - first + 1;
            if (count > occurrenceLimit) truncated = true;
            int limit = (int) Math.min(count, occurrenceLimit);
            for (int offset = 0; offset < limit; offset++) {
                double target = phase + (last - offset) * period;
                if (target >= segment.lo() && target <= segment.hi()) result.add(timeAt(segment, target));
            }
        }

        private static double lastTarget(Segment segment, double phase, double period) {
            if (period == 0) return phase <= segment.hi() ? phase : Double.NEGATIVE_INFINITY;
            double cycle = Math.floor((segment.hi() - phase) / period);
            if (cycle < 0) return Double.NEGATIVE_INFINITY;
            double target = phase + cycle * period;
            if (target > segment.hi()) {
                if (cycle == 0) return Double.NEGATIVE_INFINITY;
                target = phase + (cycle - 1) * period;
            }
            return target <= segment.hi() ? target : Double.NEGATIVE_INFINITY;
        }

        private static float timeAt(Segment segment, double target) {
            if (target <= segment.lo()) return segment.start();
            double left = segment.start(), right = segment.end();
            for (int step = 0; step < 40; step++) {
                double mid = (left + right) / 2;
                if (segment.raw().applyAsDouble(mid) >= target) right = mid;
                else left = mid;
            }
            return (float) right;
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
                if (clip instanceof com.director.timeline.model.entity.EntitySpawnClip spawn && !spawn.isMuted()) {
                    for (var action : spawn.getActionIntervals()) {
                        if (action.isMuted()) continue;
                        float actionStart = Math.max(spawn.getStartTime(), action.getStartTime());
                        float actionEnd = Math.min(spawn.getEndTime(), action.getEndTime());
                        if (actionEnd <= actionStart) continue;
                        if (actionStart > boundaries.first() && actionStart < end) boundaries.add(actionStart);
                        if (actionEnd > boundaries.first() && actionEnd < end) boundaries.add(actionEnd);
                    }
                }
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
}
