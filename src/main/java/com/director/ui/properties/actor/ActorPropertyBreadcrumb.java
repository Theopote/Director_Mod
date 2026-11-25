package com.director.ui.properties.actor;

import com.director.actor.ActorRegistryManager;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.EntityClip;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;

/** Shared selection ancestry for actor clip and key inspectors. */
public final class ActorPropertyBreadcrumb {
    private ActorPropertyBreadcrumb() {}

    public static void render(TimelineTrack owner, EntityClip clip, String asset, List<String> detail) {
        var items = parts(owner, clip, asset, detail);
        if (items.isEmpty()) return;
        ImGui.textDisabled(String.join(" / ", items));
    }

    static List<String> parts(TimelineTrack owner, EntityClip clip, String asset, List<String> detail) {
        var items = new ArrayList<String>();
        if (owner != null && owner.getActorTrackPayload() != null
                && owner.getActorTrackPayload().getActorRef() != null) {
            var id = owner.getActorTrackPayload().getActorRef().getActorId();
            ActorRegistryManager.getInstance().getActor(id).ifPresent(actor -> items.add(actor.getName()));
        }
        if (owner != null) items.add(owner.getName());
        if (clip != null) items.add(clip.getName());
        if (asset != null && !asset.isBlank()) items.add(asset);
        if (detail != null) items.addAll(detail.stream().filter(item -> item != null && !item.isBlank()).toList());
        return List.copyOf(items);
    }
}
