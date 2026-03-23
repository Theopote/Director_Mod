package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolLifecycle;
import com.director.ui.tools.ToolRegistry;

public final class ActorSelectTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorSelectTool() {
        super(ToolRegistry.ACTOR_SELECT_TOOL_ID, "Actor Select", Icons.SELECT, "Select actor and show Gizmo");
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.TRANSLATE);
    }
}
