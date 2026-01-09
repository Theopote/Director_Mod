package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.client.gizmo.GizmoMode;
import com.chronoblocks.ui.actor.gizmo.ActorGizmoController;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.ToolLifecycle;

public final class ActorRotateTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorRotateTool() {
        super("actor_rotate", "演员旋转", Icons.ROTATE, "旋转演员朝向并生成 Transform 片段。");
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.ROTATE);
    }
}
