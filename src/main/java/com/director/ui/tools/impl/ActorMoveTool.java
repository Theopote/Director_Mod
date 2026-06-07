package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;

public final class ActorMoveTool extends ActorGizmoModeTool {
    public static final String ID = "actor_move";

    public ActorMoveTool() {
        super(ID, "Actor Move", Icons.MOVE, "Move the selected actor and write a Transform clip.", GizmoMode.TRANSLATE);
    }
}
