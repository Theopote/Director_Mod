package com.director.ui.properties.actor;

import com.director.actor.path.*;
import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.ui.actor.path.ActorPathEditing;
import imgui.ImGui;
import java.util.UUID;

/** Explicit route authoring entry for the inspected actor. */
public final class ActorPathMovementPropertyUI {
    private static UUID actor, path;
    private static Object scope;
    private static String error = "";
    private ActorPathMovementPropertyUI() {}
    private static String tr(String key) { return ActorPropertyAdapterI18n.tr("gui.director.actor.path_movement." + key); }
    public static void render(UUID actorId) {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (!java.util.Objects.equals(actor, actorId) || scope != model) {
            actor = actorId; scope = model; path = null; error = "";
        }
        if (!ImGui.collapsingHeader(tr("header"))) return;
        com.director.timeline.model.TimelineTrack owner;
        try {owner=model==null?null:com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model,actorId);}
        catch(IllegalArgumentException unavailable) {ImGui.textWrapped(unavailable.getMessage());return;}
        if (model != null && owner != null) renderMovementSummary(model, owner);
        com.director.timeline.model.entity.ActorRouteClip existing = null;
        if (model != null && owner != null) {
            var wrapper = model.getSelectedClip();
            if (wrapper instanceof com.director.timeline.model.entity.EntityTimelineClip entity
                    && entity.getParentTrack() == owner && entity.getEntityClip() instanceof com.director.timeline.model.entity.ActorRouteClip route)
                existing = route;
            else existing = com.director.ui.actor.pose.ActorMovementSource.activeRoute(owner, model.getCurrentTime());
        }
        if (existing != null) {
            if (existing.isMuted() || model.getCurrentTime() < existing.getStartTime() || model.getCurrentTime() > existing.getEndTime())
                ImGui.textWrapped(tr("inspected_inactive"));
            float length = com.director.timeline.playback.entity.ActorRouteSampler.computeCurveLength(existing.getWaypoints(), existing.getInterpolationMode());
            boolean once = existing.getLoopMode() == com.director.timeline.model.entity.ActorRouteClip.LoopMode.ONCE;
            ImGui.textWrapped(tr("summary") + String.format("%s · %.2f–%.2f s · %s", existing.getName(), existing.getStartTime(), existing.getEndTime(), existing.getLoopMode()));
            ImGui.textWrapped(tr(once ? "duration_driven" : "speed_driven") + String.format("%.2f blocks/s", once ? length / Math.max(.0001f, existing.getLength()) : existing.getSpeed()));

            if (com.director.ui.actor.pose.ActorMovementSource.transformOverridesRoute(owner, model.getCurrentTime()))
                ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.movement.route_conflict"));
            var bound = existing.getSourcePathId() == null ? null : ActorPathLibraryManager.getPath(existing.getSourcePathId());
            ImGui.textWrapped(tr(bound == null ? "unlinked" : "linked") + (bound == null ? "" : bound.getName()));
        }
        var selected = path == null ? null : ActorPathLibraryManager.getPath(path);
        if (selected == null) path = null;
        if (ImGui.beginCombo(tr("path"), selected == null ? tr("choose") : selected.getName())) {
            try {
                for (var candidate : ActorPathLibraryManager.getAllPaths())
                    if (ImGui.selectable(candidate.getName() + "##" + candidate.getId(), candidate.getId().equals(path))) {
                        path = candidate.getId(); error = "";

                    }
            } finally { ImGui.endCombo(); }
        }
        if (ActorPathLibraryManager.getAllPaths().isEmpty()) ImGui.textWrapped(tr("empty"));
        boolean available = model != null && !model.isPlaying() && !model.isScrubbing() && owner != null
                && !owner.isLocked() && !owner.isMuted() && selected != null && selected.getWaypoints().size() >= 2;
        ImGui.beginDisabled(!available);
        if (ImGui.button(tr(existing == null ? "apply" : "switch"))) {
            try {
                var current = ActorPathLibraryManager.getPath(path);
                if (current == null) throw new IllegalArgumentException(tr("missing"));
                com.director.timeline.model.entity.EntityTimelineClip wrapper;
                if (existing != null) { ActorPathEditing.switchPath(model, owner, existing, current); wrapper = existing.getTimelineClip(); }
                else wrapper = ActorPathEditing.attach(model, owner, current.getWaypoints(), current.getInterpolationMode(), current.getName(), current.getId());
                model.selectItem(wrapper, false);
                DirectorServices.get().uiSelection().setCurrentSelection(wrapper);
                error = "";
            } catch (IllegalArgumentException | IllegalStateException failure) { error = failure.getMessage(); }
        }
        ImGui.endDisabled();
        ImGui.textWrapped(tr("hint"));
        if (owner == null) ImGui.textWrapped(tr("place_first"));
        if (!error.isEmpty()) ImGui.textWrapped(error);
    }
    private static void renderMovementSummary(com.director.timeline.model.TimelineModel model,
            com.director.timeline.model.TimelineTrack owner) {
        ImGui.textWrapped(tr("context") + owner.getName() + String.format(" · %.2f s", model.getCurrentTime()));
        if (owner.isMuted()) { ImGui.textWrapped(tr("track_muted")); return; }
        var sample = new com.director.timeline.playback.entity.EntityClipSampler(owner.getActorTrackPayload()).sampleSnapshot(model.getCurrentTime());
        if (!sample.active) ImGui.textWrapped(tr("not_present"));
        if (sample.relationEnabled) ImGui.textWrapped(tr("relation_constraint"));
        var route = sample.routeClip;
        if (route != null) {
            ImGui.textWrapped(tr("effective_route") + route.getName());
            var wrapper = route.getTimelineClip();
            if (wrapper != null && wrapper.getParentTrack() == owner && owner.getClips().contains(wrapper)
                    && ImGui.smallButton(tr("inspect_route") + "##inspectEffectiveRoute")) {
                model.selectItem(wrapper, false);
                DirectorServices.get().uiSelection().selectClip(route);
                com.director.ui.properties.PropertyContextService.get().onTimelineSelectionChanged(wrapper);
            }
        } else {
            boolean transform = owner.getActorTrackPayload().findClipsAt(model.getCurrentTime()).stream()
                    .anyMatch(c -> c instanceof com.director.timeline.model.entity.EntityTransformClip && !c.isMuted());
            ImGui.textWrapped(tr(transform ? "effective_transform" : "no_effective_route"));
        }
        ImGui.textWrapped(tr("sample_position") + String.format("%.2f, %.2f, %.2f", sample.position.x, sample.position.y, sample.position.z));
        ImGui.textWrapped(tr("sample_note"));
    }

}
