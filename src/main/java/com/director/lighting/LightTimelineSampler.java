package com.director.lighting;
import com.director.timeline.model.*;
import java.util.*;
public final class LightTimelineSampler {
    private LightTimelineSampler() {}
    public static List<PointLight> sample(TimelineModel model,float time) {
        if(model==null || !Float.isFinite(time)) return List.of();
        boolean solo=model.getTracks().stream().anyMatch(t -> t.getType()==TrackType.LIGHT && t.isSolo() && t.isVisible() && !t.isMuted());
        Map<UUID,LightTimelineClip> active=new LinkedHashMap<>();
        for(var track:model.getTracks()) {
            if(track.getType()!=TrackType.LIGHT || !track.isVisible() || track.isMuted() || solo&&!track.isSolo()) continue;
            for(var clip:track.getClips()) if(clip instanceof LightTimelineClip light && time>=clip.getStartTime() && time<clip.getEndTime()) {
                var previous=active.get(light.lightId());
                if(previous==null || previous.getStartTime()<clip.getStartTime()) active.put(light.lightId(),light);
            }
        }
        List<PointLight> result=new ArrayList<>();
        for(var clip:active.values()) {
            var base=model.getPointLight(clip.lightId());if(base==null || !base.enabled()) continue;
            var value=clip.sample(base,time);if(value.enabled() && value.brightness()>0) result.add(value);
        }
        return List.copyOf(result);
    }
    public static void migrateLegacy(TimelineModel model) {
        for(var light:model.getPointLights()) {
            if(model.getTracks().stream().flatMap(t -> t.getClips().stream())
                    .anyMatch(c -> c instanceof LightTimelineClip clip && clip.lightId().equals(light.id()))) continue;
            var track=new TimelineTrack(light.name(),TrackType.LIGHT);
            track.addClip(new LightTimelineClip(light.name(),0,Math.max(.05f,model.getDuration()),light.id()));model.addTrack(track);
        }
    }
}
