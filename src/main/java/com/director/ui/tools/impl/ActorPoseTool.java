package com.director.ui.tools.impl;

import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.actor.lookat.ActorLookAtGizmoController;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolLifecycle;

import java.util.UUID;

public final class ActorPoseTool extends AbstractWorldTool implements ToolLifecycle {
    public static final String ID = "actor_pose";

    public ActorPoseTool() {
        super(ID, Icons.ANIMATION);
    }

    @Override
    public void onActivate() {
        UUID actorId = ActorSelectionHub.getSelectedActorId();
        if (actorId != null) {
            ActorGizmoController.getInstance().setTargetActor(actorId);
            ActorLookAtGizmoController.getInstance().setTargetActor(actorId);
        }
    }
}
