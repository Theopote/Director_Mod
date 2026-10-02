package com.director.actor.prop;

import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityActionEvent;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.function.Function;

/** Pure replay of prop state. Events occur once at clip/marker time, independent of render history. */
public final class ActorPropTimelineSampler {
    public record Attachment(String propId, float x, float y, float z, float rx, float ry, float rz) {
        public RuntimeProp create(ActorPropSlot slot) {
            var prop = new RuntimeProp(PropRegistry.getInstance().get(propId).orElseThrow(),slot);
            prop.setLocalOffset(x,y,z); prop.setLocalRotation(rx,ry,rz); return prop;
        }
    }
    public record Flight(String id, ActorPropSlot slot, Attachment attachment, Vec3d position) {}
    public record Snapshot(Map<ActorPropSlot,Attachment> held, List<Flight> flights) {}
    private record Event(String id, float time, EntityActionEvent action) {}
    private ActorPropTimelineSampler() {}
    public static Snapshot sample(ActorTrack track, float time, Function<Float,Vec3d> actorPosition) {
        if (!Float.isFinite(time) || time < 0) return new Snapshot(Map.of(),List.of());
        var events = new ArrayList<Event>();
        for (var clip : track.getClips()) {
            if (clip.isMuted() || !Float.isFinite(clip.getStartTime()) || !Float.isFinite(clip.getEndTime())
                    || clip.getStartTime()<0 || clip.getEndTime()<clip.getStartTime()) continue;
            if (clip instanceof EntityActionClip c && PropEventParameters.supports(c.getActionType()))
                events.add(new Event(c.getId().toString(),c.getStartTime(),new EntityActionEvent(c.getActionType(),c.getParams())));
            if (clip instanceof EntityAnimationClip c) {
                int index=0;
                for (var marker : c.getEvents()) {
                    float at=c.getStartTime()+marker.getNormalizedTime()*c.getLength();
                    if (Float.isFinite(at) && PropEventParameters.supports(marker.getAction().type())
                            && activeAnimation(track,at)==c)
                        events.add(new Event(c.getId()+":"+index,at,marker.getAction()));
                    index++;
                }
            }
        }
        // Stable sort: equal-time events follow the persisted clip/marker order.
        events.sort(Comparator.comparingDouble(Event::time));
        var held = new EnumMap<ActorPropSlot,Attachment>(ActorPropSlot.class);
        var flights = new ArrayList<Flight>();
        for (var event : events) {
            if (event.time>time) break;
            var params=event.action.params(); var type=event.action.type();
            try { PropEventParameters.validate(type,params); }
            catch (IllegalArgumentException | NullPointerException malformed) { continue; }
            switch(type) {
                case ATTACH_PROP -> held.put(PropEventParameters.slot(params,"slot"),attachment(params,"propId"));
                case DETACH_PROP -> held.remove(PropEventParameters.slot(params,"slot"));
                case SWITCH_PROP -> {
                    if (params.containsKey("oldSlot")) held.remove(PropEventParameters.slot(params,"oldSlot"));
                    held.put(PropEventParameters.slot(params,"newSlot"),attachment(params,"newPropId"));
                }
                case THROW_PROP -> {
                    var slot=PropEventParameters.slot(params,"slot"); var attachment=held.remove(slot);
                    if (attachment==null) continue;
                    float duration=value(params,"flightTime",1), age=time-event.time;
                    if (age>=duration) continue;
                    Vec3d origin=actorPosition.apply(event.time);
                    if (origin==null) continue;
                    origin=origin.add(attachment.x,attachment.y,attachment.z);
                    var target=new Vec3d(PropEventParameters.number(params,"targetX"),PropEventParameters.number(params,"targetY"),PropEventParameters.number(params,"targetZ"));
                    double progress=age/duration;
                    var position=origin.lerp(target,progress).add(0,Math.sin(progress*Math.PI)*value(params,"arcHeight",1.5f),0);
                    flights.add(new Flight(event.id,slot,attachment,position));
                }
                default -> {}
            }
        }
        return new Snapshot(Collections.unmodifiableMap(held),List.copyOf(flights));
    }
    private static EntityAnimationClip activeAnimation(ActorTrack track,float time) {
        EntityAnimationClip active=null;
        for(var clip:track.getClips()) if(clip instanceof EntityAnimationClip c && !c.isMuted()
                && time>=c.getStartTime() && time<=c.getEndTime()
                && (active==null || c.getStartTime()>=active.getStartTime())) active=c;
        return active;
    }
    private static float value(Map<String,Object> p,String key,float fallback) {
        return p.containsKey(key)?((Number)p.get(key)).floatValue():fallback;
    }
    private static Attachment attachment(Map<String,Object> p,String id) {
        return new Attachment((String)p.get(id),value(p,"offsetX",0),value(p,"offsetY",0),value(p,"offsetZ",0),
                value(p,"rotationX",0),value(p,"rotationY",0),value(p,"rotationZ",0));
    }
}
