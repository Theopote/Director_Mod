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
        var owner = model == null ? null : com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
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
        if (ImGui.button(tr("apply"))) {
            try {
                var current = ActorPathLibraryManager.getPath(path);
                if (current == null) throw new IllegalArgumentException(tr("missing"));
                var wrapper = ActorPathEditing.attach(model, owner, current.getWaypoints(), current.getInterpolationMode(), current.getName(), current.getId());
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
}
