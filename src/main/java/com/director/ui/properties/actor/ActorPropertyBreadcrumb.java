package com.director.ui.properties.actor;

import com.director.actor.ActorRegistryManager;
import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.actor.pose.ActorKeyframeSelection;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.timeline.render.ActorRootKeyPropertyUI;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared selection ancestry for actor clip and key inspectors. */
public final class ActorPropertyBreadcrumb {
    public enum Kind { ACTOR, TRACK, CLIP, ASSET, DETAIL }

    public record Segment(Kind kind, String label) {}

    private ActorPropertyBreadcrumb() {}

    /** @return true when a click changed the current selection */
    public static boolean render(TimelineTrack owner, EntityClip clip, String asset, List<String> detail) {
        var items = segments(owner, clip, asset, detail);
        if (items.isEmpty()) return false;
        int lastStructural = lastStructuralIndex(items);
        boolean detailTrail = lastStructural >= 0 && lastStructural < items.size() - 1;
        boolean navigated = false;
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                ImGui.sameLine(0, 0);
                ImGui.textDisabled(" / ");
                ImGui.sameLine(0, 0);
            }
            var item = items.get(i);
            boolean clickable = item.kind() != Kind.DETAIL
                    && (i < lastStructural || (detailTrail && i == lastStructural));
            if (!clickable) {
                ImGui.textDisabled(item.label());
                continue;
            }
            ImGui.pushID("crumb" + i);
            try {
                if (ImGui.smallButton(item.label())) navigated = navigate(item.kind(), owner, clip) || navigated;
            } finally {
                ImGui.popID();
            }
        }
        return navigated;
    }

    public static List<String> parts(TimelineTrack owner, EntityClip clip, String asset, List<String> detail) {
        return segments(owner, clip, asset, detail).stream().map(Segment::label).toList();
    }

    static List<Segment> segments(TimelineTrack owner, EntityClip clip, String asset, List<String> detail) {
        var items = new ArrayList<Segment>();
        if (owner != null && owner.getActorTrackPayload() != null
                && owner.getActorTrackPayload().getActorRef() != null) {
            var id = owner.getActorTrackPayload().getActorRef().getActorId();
            ActorRegistryManager.getInstance().getActor(id)
                    .ifPresent(actor -> items.add(new Segment(Kind.ACTOR, actor.getName())));
        }
        if (owner != null) items.add(new Segment(Kind.TRACK, owner.getName()));
        if (clip != null) items.add(new Segment(Kind.CLIP, clip.getName()));
        if (asset != null && !asset.isBlank()) items.add(new Segment(Kind.ASSET, asset));
        if (detail != null) {
            for (String item : detail) {
                if (item != null && !item.isBlank()) items.add(new Segment(Kind.DETAIL, item));
            }
        }
        return List.copyOf(items);
    }

    static int lastStructuralIndex(List<Segment> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).kind() != Kind.DETAIL) return i;
        }
        return -1;
    }

    static boolean navigate(Kind kind, TimelineTrack owner, EntityClip clip) {
        if (kind == null || kind == Kind.DETAIL) return false;
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        var ui = DirectorServices.get().uiSelection();
        return switch (kind) {
            case ACTOR -> {
                UUID id = owner == null || owner.getActorTrackPayload() == null
                        || owner.getActorTrackPayload().getActorRef() == null
                        ? null : owner.getActorTrackPayload().getActorRef().getActorId();
                var actor = id == null ? null : ActorRegistryManager.getInstance().getActor(id).orElse(null);
                if (actor == null) yield false;
                leaveKeyInspectors();
                ActorSelectionHub.selectActor(id);
                com.director.ui.properties.PropertyContextService.get().inspectActorObject(actor);
                yield true;
            }
            case TRACK -> {
                if (model == null || owner == null || !model.getAllTracks().contains(owner)) yield false;
                leaveKeyInspectors();
                model.selectItem(owner, false);
                ui.selectTrack(owner);
                com.director.ui.properties.PropertyContextService.get().onTimelineSelectionChanged(owner);
                yield true;
            }
            case CLIP, ASSET -> {
                var wrapper = clip == null ? null : clip.getTimelineClip();
                if (model == null || clip == null || wrapper == null || wrapper.getParentTrack() == null
                        || !model.getAllTracks().contains(wrapper.getParentTrack())
                        || !wrapper.getParentTrack().getClips().contains(wrapper)) yield false;
                leaveKeyInspectors();
                model.selectItem(wrapper, false);
                ui.selectClip(clip);
                com.director.ui.properties.PropertyContextService.get().onTimelineSelectionChanged(wrapper);
                yield true;
            }
            case DETAIL -> false;
        };
    }

    private static void leaveKeyInspectors() {
        ActorKeyframeSelection.getInstance().clear();
        ActorRootKeyPropertyUI.leave();
    }
}
