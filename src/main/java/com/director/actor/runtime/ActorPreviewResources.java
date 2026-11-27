package com.director.actor.runtime;

import com.director.actor.model.CustomModelPreviewRenderer;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.prop.ActorPropRuntime;
import com.director.actor.skin.ActorSkinTextureManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns the preview resources shared by an actor's timeline bindings. */
final class ActorPreviewResources {
    private final Map<UUID, ActorPropRuntime> props = new HashMap<>();

    ActorPropRuntime propsFor(UUID actorId) {
        return props.computeIfAbsent(actorId, id -> new ActorPropRuntime());
    }

    void removeProps(UUID actorId) {
        var runtime = props.remove(actorId);
        if (runtime != null) runtime.dispose();
    }

    void clearProps() {
        props.values().forEach(ActorPropRuntime::dispose);
        props.clear();
    }

    void removeEntityAndModel(UUID actorId) {
        ActorPreviewManager.getInstance().removePreview(actorId);
        CustomModelPreviewRenderer.getInstance().remove(actorId);
    }

    void clearEntitiesAndModels() {
        ActorPreviewManager.getInstance().clear();
        CustomModelPreviewRenderer.getInstance().clear();
    }

    void releaseWorld() {
        clearProps();
        CustomModelPreviewRenderer.getInstance().clear();
        ActorSkinTextureManager.clearAll();
    }

    void retainActors(Set<UUID> liveActors) {
        for (var id : ActorPreviewManager.getInstance().previewActorIds()) {
            if (!liveActors.contains(id)) removeEntityAndModel(id);
        }
        for (var id : new ArrayList<>(props.keySet())) {
            if (!liveActors.contains(id)) removeProps(id);
        }
    }
}
