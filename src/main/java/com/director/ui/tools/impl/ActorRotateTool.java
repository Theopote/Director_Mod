package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;

public final class ActorRotateTool extends ActorTransformGizmoTool {
    public static final String ID = "actor_rotate";

    public ActorRotateTool() {
        super(ID, Icons.ROTATE, GizmoMode.ROTATE);
    }
}
