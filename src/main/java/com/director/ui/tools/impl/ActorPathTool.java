package com.director.ui.tools.impl;

import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.actor.lookat.ActorLookAtGizmoController;
import com.director.ui.actor.path.ActorPathController;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolLifecycle;

public final class ActorPathTool extends AbstractActorTool implements ToolLifecycle {

    public ActorPathTool() {
        super("actor_path", Icons.PATH);
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().clearTarget();
        ActorLookAtGizmoController.getInstance().clearTarget();
        ActorPathController.getInstance().activate();
    }

    @Override
    public void onDeactivate() {
        ActorPathController.getInstance().deactivate();
    }
}
