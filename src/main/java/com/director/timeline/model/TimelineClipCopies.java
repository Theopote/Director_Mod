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
        if (clip.spawnIntervals != null) clip.spawnIntervals.forEach(TimelineClipCopies::assignCopyIds);
    }

    public static com.director.timeline.model.entity.EntityClip copyEntityClip(com.director.timeline.model.entity.EntityClip source) {
        var dto = TimelineModelPersistence.entityClipToDTO(source);
        assignCopyIds(dto);
        return TimelineModelPersistence.entityClipFromDTO(dto);
    }

    public static TimelineClip copy(TimelineClip source) {
        return copy(source, false);
    }

    public static TimelineClip copyForCut(TimelineClip source) {
        return copy(source, true);
    }

    private static TimelineClip copy(TimelineClip source, boolean preserveAudioBinding) {
        if (source instanceof EntityTimelineClip wrapper) {
            return new EntityTimelineClip(copyEntityClip(wrapper.getEntityClip()), source.getColor());
        }
        if (source instanceof AudioTimelineClip) {
            var copy = new AudioTimelineClip(source);
            if (!preserveAudioBinding) {
                copy.setBoundContainerId(null);
                copy.setBoundContainerName(null);
            }
            return copy;
        }
        if(source instanceof com.director.lighting.LightTimelineClip light) return new com.director.lighting.LightTimelineClip(light);
        return new TimelineClip(source);
    }
}
