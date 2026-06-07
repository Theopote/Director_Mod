package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;

public final class ActorRotateTool extends ActorGizmoModeTool {
    public static final String ID = "actor_rotate";

    public ActorRotateTool() {
        super(ID, "Actor Rotate", Icons.ROTATE, "Rotate the selected actor and write a Transform clip.", GizmoMode.ROTATE);
    }
}
