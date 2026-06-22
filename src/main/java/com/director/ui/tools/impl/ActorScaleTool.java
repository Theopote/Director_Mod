package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;

public final class ActorScaleTool extends ActorGizmoModeTool {
    public static final String ID = "actor_scale";

    public ActorScaleTool() {
        super(ID, Icons.SCALE, GizmoMode.SCALE);
    }
}
