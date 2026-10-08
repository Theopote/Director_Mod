package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.skin.ActorSkinTextureManager;
import com.director.timeline.model.TimelineModel;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.properties.PropertyContextService;
import com.director.ui.timeline.commands.*;
import java.util.UUID;

/** All actor browser deletion entries share the same history and preview cleanup. */
public final class ActorDeletionService {
    private ActorDeletionService() {}
    public static void delete(TimelineModel model, ActorRegistry registry, UUID id) {
        if (model == null || id == null || !registry.contains(id)) return;
        boolean selected = id.equals(ActorSelectionHub.getSelectedActorId());
        var actor = registry.getActor(id).orElseThrow();
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
        var gizmo = com.director.ui.actor.gizmo.ActorGizmoController.getInstance();
        if (gizmo.isDragging()) gizmo.cancelGizmoDrag();
        TimelineCommandBridge.execute(new DeleteActorCommand(model, registry, id, deleted -> {
            ActorPreviewManager.getInstance().removePreview(id);
            ActorSkinTextureManager.invalidate(id);
            if (selected) {
                if (deleted) ActorSelectionHub.clearSelection();
                else {
                    ActorSelectionHub.selectActor(id);
                    PropertyContextService.get().inspectActorObject(actor);
                }
            }
            ActorRegistryManager.save();
        }));
    }
}
