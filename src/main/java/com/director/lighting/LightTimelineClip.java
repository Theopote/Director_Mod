package com.director.lighting;
import com.director.timeline.model.*;
import com.director.timeline.model.clip.ClipKeyframe;
import java.util.UUID;
import java.util.Objects;
public final class LightTimelineClip extends TimelineClip {
    private final UUID lightId;
    public LightTimelineClip(String label,float start,float end,UUID id) {
        super(label,start,end,TrackType.LIGHT.getClipColor());lightId=Objects.requireNonNull(id);
    }
    public LightTimelineClip(LightTimelineClip other) { super(other);lightId=other.lightId; }
    public record KeyState(LightKeyframe key,float time,PointLight value,TimelineInterpolation interpolation) {}
    public record State(float start,float end,java.util.List<KeyState> keys) {}
    public State captureState() {
        return new State(getStartTime(),getEndTime(),getClipKeyframes().stream().map(k -> {
            var light=(LightKeyframe)k;return new KeyState(light,light.getTime(),light.value(),light.getInterpolation());
        }).toList());
    }
    public void restoreState(State state) {
        clearKeyframes();super.setTimeRange(state.start(),state.end());
        for(var item:state.keys()) {
            item.key().setTime(item.time());item.key().setValue(item.value());item.key().setInterpolation(item.interpolation());addKeyframe(item.key());
        }
    }
    @Override public void addKeyframe(float time) { throw new IllegalArgumentException("Light keyframes require property values"); }
    public UUID lightId() { return lightId; }
    @Override public TimelineClip duplicateShallow() { return new LightTimelineClip(this); }
    @Override public void addKeyframe(ClipKeyframe key) {
        if(!(key instanceof LightKeyframe light) || !light.value().id().equals(lightId))
            throw new IllegalArgumentException("Light clips only accept matching light keyframes");
        super.addKeyframe(key);
    }
    public PointLight sample(PointLight base,float time) {
        var keys=getClipKeyframes().stream().filter(k -> k instanceof LightKeyframe)
                .map(k -> (LightKeyframe)k).sorted(java.util.Comparator.comparing(LightKeyframe::getTime)).toList();
        if(keys.isEmpty()) return base;
        LightKeyframe a=keys.getFirst();
        if(time<=a.getTime()) return a.value();
        for(int i=1;i<keys.size();i++) {
            LightKeyframe b=keys.get(i);
            if(time<=b.getTime()) {
                float t=(time-a.getTime())/(b.getTime()-a.getTime());
                if(a.getInterpolation()==TimelineInterpolation.STEP) return time<b.getTime()?a.value():b.value();
                if(a.getInterpolation()==TimelineInterpolation.SMOOTH) t=t*t*(3-2*t);
                return interpolate(a.value(),b.value(),t);
            }
            a=b;
        }
        return keys.getLast().value();
    }
    public static PointLight interpolate(PointLight a,PointLight b,double t) {
        t=Math.max(0,Math.min(1,t));double u=1-t;
        if(a.type()!=b.type()) return t<1?a:b;
        var anchors=a.anchors();
        if(a.type()==LightType.LINE && anchors.size()==b.anchors().size()) {
            var list=new java.util.ArrayList<LightPoint>();
            for(int i=0;i<anchors.size();i++) { var p=anchors.get(i);var q=b.anchors().get(i);list.add(new LightPoint(u*p.x()+t*q.x(),u*p.y()+t*q.y(),u*p.z()+t*q.z())); }
            anchors=java.util.List.copyOf(list);
        }
        float yaw=a.yaw()+(float)t*angleDelta(a.yaw(),b.yaw());
        return new PointLight(a.id(),a.name(),u*a.x()+t*b.x(),u*a.y()+t*b.y(),u*a.z()+t*b.z(),
                (float)(u*a.brightness()+t*b.brightness()),(float)(u*a.range()+t*b.range()),(float)(u*a.falloff()+t*b.falloff()),
                t<1?a.enabled():b.enabled(),a.type(),anchors,(float)(u*a.width()+t*b.width()),(float)(u*a.height()+t*b.height()),yaw,(float)(u*a.pitch()+t*b.pitch()));
    }
    private static float angleDelta(float a,float b) { return (float)(((b-a+180)%360+360)%360-180); }
    @Override public void setTimeRange(float start,float end) {
        if(!Float.isFinite(start)||!Float.isFinite(end)||start>end) throw new IllegalArgumentException("Invalid clip interval");
        if(Math.abs((end-start)-getLength())<1e-5f) {offsetTime(start-getStartTime());return;}
        var keys=getClipKeyframes();
        var interpolation=TimelineInterpolation.LINEAR;
        for(var key:keys) if(key.getTime()<=start) interpolation=key.getInterpolation();
        PointLight left=keys.isEmpty()?null:sample(null,start),right=keys.isEmpty()?null:sample(null,end);
        super.setTimeRange(start,end);
        if(left!=null && findKeyframeAt(start,1e-5f)==null) {
            var key=new LightKeyframe(start,left);key.setInterpolation(interpolation);addKeyframe(key);
        }
        if(right!=null && findKeyframeAt(end,1e-5f)==null) addKeyframe(new LightKeyframe(end,right));
    }
    @Override public void setStartTime(float start) { setTimeRange(start,getEndTime()); }
    @Override public void setEndTime(float end) { setTimeRange(getStartTime(),end); }
    @Override public void setLength(float length) { setTimeRange(getStartTime(),getStartTime()+Math.max(.05f,length)); }
}
