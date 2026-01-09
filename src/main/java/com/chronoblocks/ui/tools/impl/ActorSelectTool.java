package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.client.gizmo.GizmoMode;
import com.chronoblocks.ui.actor.gizmo.ActorGizmoController;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.ToolLifecycle;
import com.chronoblocks.ui.tools.ToolRegistry;

public final class ActorSelectTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorSelectTool() {
        super(ToolRegistry.ACTOR_SELECT_TOOL_ID, "Actor Select", Icons.SELECT, "Select actor and show Gizmo");
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.TRANSLATE);
    }
}
