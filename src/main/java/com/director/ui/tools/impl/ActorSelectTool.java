package com.director.ui.tools.impl;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolRegistry;

public final class ActorSelectTool extends ActorGizmoModeTool {

    public ActorSelectTool() {
        super(ToolRegistry.ACTOR_SELECT_TOOL_ID, "Actor Select", Icons.SELECT, "Select actor and show Gizmo", GizmoMode.TRANSLATE);
    }
}
