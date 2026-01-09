package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.client.gizmo.GizmoMode;
import com.chronoblocks.ui.actor.gizmo.ActorGizmoController;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.ToolLifecycle;

public final class ActorLookAtTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorLookAtTool() {
        super("actor_lookat", "演员注视", Icons.TARGET, "点击世界设置演员注视目标。");
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.LOOK_AT);
    }
}
