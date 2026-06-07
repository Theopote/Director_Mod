package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;

public final class ActorScaleTool extends ActorGizmoModeTool {
    public static final String ID = "actor_scale";

    public ActorScaleTool() {
        super(ID, "Actor Scale", Icons.SCALE, "Scale the selected actor and write a Transform clip.", GizmoMode.SCALE);
    }
}
