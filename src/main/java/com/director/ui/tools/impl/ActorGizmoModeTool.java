package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.actor.lookat.ActorLookAtGizmoController;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.tools.ToolLifecycle;

import java.util.UUID;

abstract class ActorGizmoModeTool extends AbstractWorldTool implements ToolLifecycle {
    private final GizmoMode mode;

    protected ActorGizmoModeTool(String id, String displayName, String icon, String description, GizmoMode mode) {
        super(id, displayName, icon, description);
        this.mode = mode != null ? mode : GizmoMode.TRANSLATE;
    }

    @Override
    public void onActivate() {
        UUID selectedActorId = ActorSelectionHub.getSelectedActorId();
        ActorGizmoController gizmoController = ActorGizmoController.getInstance();
        gizmoController.setPreferredMode(mode);

        if (selectedActorId != null) {
            gizmoController.setTargetActor(selectedActorId);
            ActorLookAtGizmoController.getInstance().setTargetActor(selectedActorId);
        }
    }
}
