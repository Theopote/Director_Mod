package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.client.gizmo.GizmoMode;
import com.chronoblocks.ui.actor.gizmo.ActorGizmoController;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.ToolLifecycle;

public final class ActorScaleTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorScaleTool() {
        super("actor_scale", "演员缩放", Icons.SCALE, "缩放演员大小并生成 Transform 片段。");
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.SCALE);
    }
}
