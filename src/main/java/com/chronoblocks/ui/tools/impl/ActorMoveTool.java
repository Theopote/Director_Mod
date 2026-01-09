package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.client.gizmo.GizmoMode;
import com.chronoblocks.ui.actor.gizmo.ActorGizmoController;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.ToolLifecycle;

public final class ActorMoveTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorMoveTool() {
        super("actor_move", "演员移动", Icons.MOVE, "移动演员位置并生成 Transform 片段。");
    }

    @Override
    public void onActivate() {
        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.TRANSLATE);
    }
}
