package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;

public final class ActorLookAtTool extends ActorGizmoModeTool {
    public static final String ID = "actor_look_at";

    public ActorLookAtTool() {
        super(ID, "Actor Look At", Icons.TARGET, "Set where the selected actor looks at the current time.", GizmoMode.LOOK_AT);
    }
}
