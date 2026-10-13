package com.director.timeline.model;

import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.persistence.TimelineModelPersistence;
import java.util.UUID;

public final class TimelineClipCopies {
    private TimelineClipCopies() {}
    public static TimelineClip copy(TimelineClip source) {
        if (source instanceof EntityTimelineClip wrapper) {
            var dto = TimelineModelPersistence.entityClipToDTO(wrapper.getEntityClip());
            dto.id = UUID.randomUUID().toString();
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
