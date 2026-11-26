package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.EntityActionClip;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Remembers the intervals present in the previous event-emitting sample. */
final class ActionTriggerCache {
    private final Set<UUID> triggered = new HashSet<>();
    private final Set<UUID> active = new HashSet<>();

    void retainActive(List<EntityActionClip> clips) {
        active.clear();
        for (var clip : clips) active.add(clip.getId());
        triggered.retainAll(active);
    }

    boolean enter(EntityActionClip clip) {
        return triggered.add(clip.getId());
    }

    void clear() {
        triggered.clear();
        active.clear();
    }
}
