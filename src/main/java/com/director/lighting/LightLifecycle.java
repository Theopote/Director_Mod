package com.director.lighting;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TrackType;
import java.util.*;

/** Cleanup belongs to explicit deletion transactions, never temporary track transfers. */
public final class LightLifecycle {
    private LightLifecycle() {}

    public static boolean containsClip(TimelineModel model, LightTimelineClip clip) {
        var track=clip.getParentTrack();
        return model!=null && track!=null && model.getTracks().contains(track) && track.getClips().contains(clip);
    }

    public static boolean isReferenced(TimelineModel model, UUID id) {
        return model.getTracks().stream().filter(t -> t.getType()==TrackType.LIGHT)
            .flatMap(t -> t.getClips().stream())
            .anyMatch(c -> c instanceof LightTimelineClip light && light.lightId().equals(id));
    }

    /** Retain the returned immutable assets in the command's undo state. */
    public static List<PointLight> removeUnreferenced(TimelineModel model, Collection<UUID> candidates) {
        if(model==null) return List.of();
        List<PointLight> removed=new ArrayList<>();
        for(UUID id:new LinkedHashSet<>(candidates)) {
            var light=model.getPointLight(id);
            if(light!=null && !isReferenced(model,id)) {
                removed.add(light);model.replacePointLight(id,null);
            }
        }
        return List.copyOf(removed);
    }

    public static void restore(TimelineModel model, List<PointLight> lights) {
        lights.forEach(light -> model.replacePointLight(light.id(),light));
    }

    /** Called only after a modern project has been fully reconstructed. */
    public static void cleanLoadedProject(TimelineModel model) {
        removeUnreferenced(model,model.getPointLights().stream().map(PointLight::id).toList());
    }
}
