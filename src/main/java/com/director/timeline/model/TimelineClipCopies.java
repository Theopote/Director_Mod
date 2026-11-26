package com.director.timeline.model;

import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.persistence.TimelineModelPersistence;
import java.util.UUID;

public final class TimelineClipCopies {
    private TimelineClipCopies() {}
    private static void assignCopyIds(TimelineModelPersistence.EntityClipDTO clip) {
        clip.id = UUID.randomUUID().toString();
        clip.keyframes.forEach(key -> key.id = UUID.randomUUID().toString());
        if (clip.spawnLookTargets != null) clip.spawnLookTargets.forEach(TimelineClipCopies::assignCopyIds);
        if (clip.spawnStateIntervals != null) clip.spawnStateIntervals.forEach(TimelineClipCopies::assignCopyIds);
        if (clip.spawnActionIntervals != null) clip.spawnActionIntervals.forEach(TimelineClipCopies::assignCopyIds);
    }

    public static TimelineClip copy(TimelineClip source) {
        if (source instanceof EntityTimelineClip wrapper) {
            var dto = TimelineModelPersistence.entityClipToDTO(wrapper.getEntityClip());
            assignCopyIds(dto);
            return new EntityTimelineClip(TimelineModelPersistence.entityClipFromDTO(dto), source.getColor());
        }
        if (source instanceof AudioTimelineClip) {
            var copy = new AudioTimelineClip(source);
            copy.setBoundContainerId(null);
            copy.setBoundContainerName(null);
            return copy;
        }
        if(source instanceof com.director.lighting.LightTimelineClip light) return new com.director.lighting.LightTimelineClip(light);
        return new TimelineClip(source);
    }
}
